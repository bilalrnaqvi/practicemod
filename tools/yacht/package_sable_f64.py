"""Package only explicitly supplied f64 natives; never mix in upstream f32 binaries."""
import argparse, hashlib, io, json, os, struct, subprocess, zipfile
from pathlib import Path
from native_platforms import PLATFORMS, COMMIT
root = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser()
p.add_argument('--native', action='append', required=True, metavar='PLATFORM=PATH')
p.add_argument('--require-all', action='store_true')
args = p.parse_args()
base = root/'build/yacht/sable-2.0.6.jar'
lock = json.loads((root/'docs/yacht/SABLE_PROTOTYPE_LOCK.json').read_text())
sha = lambda data: hashlib.sha512(data).hexdigest()
if sha(base.read_bytes()) != lock['sha512']: raise SystemExit('Official base hash mismatch')
natives = {}
for spec in args.native:
    name, path = spec.split('=',1)
    if name not in PLATFORMS or name in natives: raise SystemExit('Unknown or duplicate platform: '+name)
    data = Path(path).read_bytes()
    # Reject a wrong architecture/platform before making an apparently portable jar.
    arm = name.endswith('arm64')
    if name.startswith('linux'):
        valid = data[:6] == b'\x7fELF\x02\x01' and struct.unpack_from('<H',data,18)[0] == (183 if arm else 62)
    elif name.startswith('macos'):
        valid = data[:4] == b'\xcf\xfa\xed\xfe' and struct.unpack_from('<I',data,4)[0] == (0x100000c if arm else 0x1000007)
    else:
        offset = struct.unpack_from('<I',data,60)[0] if data[:2] == b'MZ' else 0
        valid = data[offset:offset+4] == b'PE\0\0' and struct.unpack_from('<H',data,offset+4)[0] == (0xaa64 if arm else 0x8664)
    if not valid: raise SystemExit('Native binary format/architecture mismatch: '+name)
    natives[name] = data
if args.require_all and set(natives) != set(PLATFORMS): raise SystemExit('Missing platforms: '+str(set(PLATFORMS)-set(natives)))
work = root/'build/yacht'; work.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(work/'f64-natives.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name,data in sorted(natives.items()):
        info = zipfile.ZipInfo(PLATFORMS[name][2],date_time=(2026,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,data)
lz4 = list((Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/modules-2/files-2.1/org.lz4/lz4-java/1.8.0').glob('*/*.jar'))
if len(lz4)!=1: raise SystemExit('Resolve Minecraft dependencies first (lz4-java 1.8.0 required)')
java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
subprocess.run([java,'-cp',str(lz4[0]),str(root/'tools/yacht/Lz4Pack.java'),str(work/'f64-natives.zip'),str(work/'f64-natives.zip.l4z')],check=True)
inner='META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.6.jar'
resource='natives/sable_rapier/sable_rapier_binaries.zip.l4z'
def rewrite(data,replacements):
    output=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as src,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as dst:
        if not replacements.keys() <= set(src.namelist()): raise SystemExit('Missing upstream archive entry')
        for item in src.infolist(): dst.writestr(item,replacements.get(item.filename,src.read(item.filename)))
    return output.getvalue()
with zipfile.ZipFile(base) as z: nested=rewrite(z.read(inner),{resource:(work/'f64-natives.zip.l4z').read_bytes()})
candidate=rewrite(base.read_bytes(),{inner:nested})
out=work/'sable-2.0.6-yacht-f64.jar'; out.write_bytes(candidate)
manifest={'schema':2,'precision':'f64','source_commit':COMMIT,'patch_sha512':sha((root/'tools/yacht/native/sable-f64.patch').read_bytes()),
          'base_sha512':lock['sha512'],'natives':{name:sha(data) for name,data in sorted(natives.items())},'sha512':sha(candidate)}
(work/'sable-f64-local.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(out)
