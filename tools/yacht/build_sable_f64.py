"""Build pinned double-precision Sable natives; native or Zig/XWin cross toolchains."""
import argparse, os, platform, subprocess
from pathlib import Path
from native_platforms import PLATFORMS, COMMIT
root = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser()
p.add_argument('--source', type=Path, default=root/'build/yacht/sable-f64-source')
p.add_argument('--platform', choices=PLATFORMS, action='append', required=True)
p.add_argument('--cross', action='store_true', help='Use cargo-zigbuild for Linux and cargo-xwin for Windows')
args = p.parse_args()
work = args.source.resolve()
def run(*cmd, cwd=None): subprocess.run(cmd, cwd=cwd, check=True)
if not work.exists():
    run('git', 'clone', 'https://github.com/ryanhcode/sable.git', str(work))
    run('git', 'checkout', '--detach', COMMIT, cwd=work)
    run('git', 'apply', str(root/'tools/yacht/native/sable-f64.patch'), cwd=work)
else:
    revision = subprocess.check_output(['git','rev-parse','HEAD'],cwd=work,text=True).strip()
    if revision != COMMIT: raise SystemExit('Source revision differs from the pinned Sable commit')
    # Reuse only an already-patched tree; never reset or overwrite source edits.
    run('git','apply','--reverse','--check',str(root/'tools/yacht/native/sable-f64.patch'),cwd=work)
manifest = work/'sable_rapier/src/main/rust/Cargo.toml'
for name in args.platform:
    triple, filename, _ = PLATFORMS[name]
    command = ['build']; target = triple
    if args.cross and name.startswith('linux'):
        command = ['zigbuild']; target += '.2.17'
    elif args.cross and name.startswith('windows'): command = ['xwin','build']
    run(os.environ.get('CARGO','cargo'),'+1.90.0',*command,'--release','--locked','-p','sable_rapier',
        '--target',target,'--manifest-path',str(manifest))
    print(f'{name}={manifest.parent / "target" / triple / "release" / filename}',flush=True)
