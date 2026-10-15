"""Package a local, experimental macOS arm64 f64 native. Never replace the official jar."""
import argparse, hashlib, io, json, os, platform, subprocess, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('native',type=Path);args=p.parse_args()
if platform.system()!='Darwin' or platform.machine()!='arm64':
    raise SystemExit('This candidate has only been prepared for macOS arm64')
base=root/'build/yacht/sable-2.0.6.jar'
lock=json.loads((root/'docs/yacht/SABLE_PROTOTYPE_LOCK.json').read_text())
if hashlib.sha512(base.read_bytes()).hexdigest()!=lock['sha512']: raise SystemExit('Official base hash mismatch')
native=args.native.read_bytes()
work=root/'build/yacht';work.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(work/'f64-natives.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('sable_rapier_aarch64_macos.dylib',native)
lz4=list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.lz4/lz4-java/1.8.0').glob('*/*.jar'))
if len(lz4)!=1: raise SystemExit('Resolve Minecraft dependencies first (lz4-java 1.8.0 required)')
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
subprocess.run([java,'-cp',str(lz4[0]),str(root/'tools/yacht/Lz4Pack.java'),str(work/'f64-natives.zip'),str(work/'f64-natives.zip.l4z')],check=True)
inner='META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.6.jar'
resource='natives/sable_rapier/sable_rapier_binaries.zip.l4z'
def rewrite(data,replacements):
    output=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as src,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist(): dst.writestr(item,replacements.get(item.filename,src.read(item.filename)))
    return output.getvalue()
with zipfile.ZipFile(base) as z:
    nested=rewrite(z.read(inner),{resource:(work/'f64-natives.zip.l4z').read_bytes()})
candidate=rewrite(base.read_bytes(),{inner:nested})
out=work/'sable-2.0.6-yacht-f64.jar';out.write_bytes(candidate)
manifest={'experimental':True,'platform':'macos-arm64-only','base_sha512':lock['sha512'],'native_sha512':hashlib.sha512(native).hexdigest(),'sha512':hashlib.sha512(candidate).hexdigest()}
(work/'sable-f64-local.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(out)
