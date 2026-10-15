"""Fetch the exact audited prototype backend, verifying before accepting it."""
import hashlib
from pathlib import Path
import urllib.request
URL = 'https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar'
SHA512 = '76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8'
p = Path(__file__).resolve().parents[2] / 'build/yacht/sable-2.0.6.jar'
p.parent.mkdir(parents=True, exist_ok=True)
data = p.read_bytes() if p.exists() else urllib.request.urlopen(URL, timeout=60).read()
if hashlib.sha512(data).hexdigest() != SHA512:
    raise SystemExit('Sable integrity mismatch; refusing artifact')
p.write_bytes(data)
print(p)
