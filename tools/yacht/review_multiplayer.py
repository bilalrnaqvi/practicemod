"""Launch two real clients and an isolated loopback server through a latency proxy.
Run prepareYachtMultiplayerReview first. Does not use or change normal game saves.
"""
import argparse, asyncio, json, os, shutil, time
from pathlib import Path
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--latency-ms',type=int,default=50);p.add_argument('--timeout',type=int,default=360);p.add_argument('--boarding-only',action='store_true');args=p.parse_args()
launch=json.loads((root/'build/yacht/multiplayer-launch.json').read_text())
stamp=time.strftime('%Y%m%d-%H%M%S');work=root/'build/yacht/multiplayer'/stamp;output=root/'output/yacht/multiplayer'/stamp
work.mkdir(parents=True);output.mkdir(parents=True)
# Freeze launch inputs so another Gradle run cannot rewrite files beneath the clients.
replacements={}
for relative in ('build/classes/java/main','build/resources/main','build/moddev','build/yacht/sable-2.0.6-yacht-f64.jar'):
    source=root/relative;dest=work/'launch'/relative
    dest.parent.mkdir(parents=True,exist_ok=True)
    if source.is_dir():shutil.copytree(source,dest)
    else:shutil.copy2(source,dest)
    replacements[str(source)]=str(dest)
def remap(text):
    for old,new in replacements.items():text=text.replace(old,new)
    return text
for file in (work/'launch/build/moddev').glob('*.txt'):file.write_text(remap(file.read_text()))
launch=json.loads(remap(json.dumps(launch)))
async def relay(reader,writer):
    try:
        while data:=await reader.read(262144):
            await asyncio.sleep(args.latency_ms/1000);writer.write(data);await writer.drain()
    except (ConnectionError,asyncio.CancelledError): pass
    finally: writer.close()
async def proxy(reader,writer):
    try:
        other,remote=await asyncio.open_connection('127.0.0.1',25574)
        await asyncio.gather(relay(reader,remote),relay(other,writer))
    except ConnectionError: writer.close()
class UDPReply(asyncio.DatagramProtocol):
    def __init__(self, front, address): self.front,self.address=front,address
    def datagram_received(self,data,address):
        asyncio.get_running_loop().call_later(args.latency_ms/1000,self.front.sendto,data,self.address)
class UDPProxy(asyncio.DatagramProtocol):
    def __init__(self): self.channels={}
    def connection_made(self,transport): self.transport=transport
    async def connect(self,address):
        transport,_=await asyncio.get_running_loop().create_datagram_endpoint(lambda:UDPReply(self.transport,address),remote_addr=('127.0.0.1',25574))
        return transport
    async def forward(self,data,address):
        if address not in self.channels:self.channels[address]=asyncio.create_task(self.connect(address))
        transport=await self.channels[address]
        asyncio.get_running_loop().call_later(args.latency_ms/1000,transport.sendto,data)
    def datagram_received(self,data,address): asyncio.create_task(self.forward(data,address))
    def close(self):
        self.transport.close()
        for task in self.channels.values():
            if task.done() and not task.cancelled() and task.exception() is None:task.result().close()
async def start(role,name,reconnect=False):
    cfg=launch[role];cwd=work/name;cwd.mkdir(exist_ok=True)
    if role=='server':
        (cwd/'eula.txt').write_text('eula=true\n')
        (cwd/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25574\nonline-mode=false\nenforce-secure-profile=false\nview-distance=8\nsimulation-distance=8\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=0\n')
    else:
        (cwd/'options.txt').write_text('renderDistance:8\nsimulationDistance:8\nmaxFps:30\npauseOnLostFocus:false\njoinedFirstServer:true\nonboardAccessibility:false\nautoJump:false\n')
    jvm=[v for v in cfg['jvm'] if not v.startswith('-Xmx')]
    cmd=[cfg['java'],*jvm,'-Xmx2G',f'-Dpracticemod.yachtMultiplayerReview={output}','-Dpracticemod.yachtReviewRequireReconnect=true']
    if reconnect:cmd+=['-Dpracticemod.yachtReviewReconnect=true']
    if args.boarding_only:cmd+=['-Dpracticemod.yachtReviewBoardingOnly=true']
    cmd+=['-cp',cfg['classpath'],cfg['main'],*cfg['args']]
    if role=='client':cmd+=['--username',name,'--quickPlayMultiplayer','127.0.0.1:25575','--width','854','--height','480']
    log=open(output/(name+('-reconnect' if reconnect else '')+'.log'),'wb')
    proc=await asyncio.create_subprocess_exec(*cmd,cwd=cwd,env={**os.environ,**cfg['environment']},stdout=log,stderr=log,stdin=asyncio.subprocess.PIPE)
    return proc
async def main():
    procs=[];proxy_server=await asyncio.start_server(proxy,'127.0.0.1',25575)
    _,udp=await asyncio.get_running_loop().create_datagram_endpoint(UDPProxy,local_addr=('127.0.0.1',25575))
    print(output,flush=True)
    try:
        server=await start('server','server');procs.append(server)
        for _ in range(120):
            await asyncio.sleep(1)
            if server.returncode is not None:raise RuntimeError('Server exited before readiness')
            if 'Done (' in (output/'server.log').read_text(errors='replace'):break
        else:raise RuntimeError('Server startup timeout')
        procs.append(await start('client','YachtOwner'));await asyncio.sleep(3);procs.append(await start('client','YachtGuest',reconnect=args.boarding_only))
        reconnected=args.boarding_only
        for _ in range(args.timeout):
            await asyncio.sleep(1)
            if not reconnected and (output/'YachtGuest-done.json').exists():
                procs[2].terminate();await procs[2].wait();await asyncio.sleep(2)
                procs[2]=await start('client','YachtGuest',reconnect=True);reconnected=True
            if (output/'server-result.json').exists():
                result=json.loads((output/'server-result.json').read_text());result['proxy_one_way_delay_ms']=args.latency_ms
                for name in (() if args.boarding_only else ('YachtOwner','YachtGuest')):
                    record=json.loads((output/(name+'-done.json')).read_text());result[name]=record
                    result['passed'] &= record['maxJump']>1 and record['maxLocalStep']<1.5 and record['otherPlayers']>=1
                if not args.boarding_only:result['passed'] &= result['YachtGuest']['stairComplete']
                (output/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
                if not result['passed']:raise RuntimeError('Multiplayer fixture failed')
                break
            if any(proc.returncode is not None for proc in procs):raise RuntimeError('A review process exited early')
        else:raise RuntimeError('Multiplayer fixture timed out')
    finally:
        if procs and procs[0].returncode is None:
            procs[0].stdin.write(b'stop\n');await procs[0].stdin.drain()
        for proc in procs[1:]:
            if proc.returncode is None:proc.terminate()
        for proc in procs:
            try:await asyncio.wait_for(proc.wait(),30)
            except asyncio.TimeoutError:proc.kill();await proc.wait()
        proxy_server.close();await proxy_server.wait_closed();udp.close()
asyncio.run(main())
