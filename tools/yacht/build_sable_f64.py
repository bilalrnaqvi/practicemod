"""Build the pinned experimental native port with an existing Rust 1.90.0 toolchain."""
import os, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
work=root/'build/yacht/sable-f64-source'
commit='d501fb3103c3247e20cb2151a99182ad5dd61adb'
def run(*args,cwd=None): subprocess.run(args,cwd=cwd,check=True)
if work.exists(): raise SystemExit('Source directory exists; keep it for inspection or choose a fresh checkout manually. Refusing to reset edits.')
run('git','clone','https://github.com/ryanhcode/sable.git',str(work))
run('git','checkout','--detach',commit,cwd=work)
run('git','apply',str(root/'tools/yacht/native/sable-f64.patch'),cwd=work)
run(os.environ.get('CARGO','cargo'),'+1.90.0','build','--release','--locked','-p','sable_rapier',
    '--manifest-path',str(work/'sable_rapier/src/main/rust/Cargo.toml'))
print(work/'sable_rapier/src/main/rust/target/release/libsable_rapier.dylib')
