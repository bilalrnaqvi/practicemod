#!/usr/bin/env python3
"""Run the real Minecraft player mesh sweep and render a six-stage review sheet after compiling."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1]
cp=':'.join([str(root/'build/classes/java/main'),str(root/'build/moddev/artifacts/neoforge-21.1.235-merged.jar'),*(root/'build/moddev/clientLegacyClasspath.txt').read_text().splitlines()])
java=Path('/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin')
if not java.exists(): java=Path('')
out=root/'build/boarding-validation';out.mkdir(exist_ok=True)
subprocess.run([str(java/'javac'),'-proc:none','-cp',cp,'-d',str(out),*map(str,[root/'tools/ValidateSubmarineEntryMesh.java',root/'tools/PreviewDeepseeker.java',root/'tools/PreviewSubmarineBoarding.java'])],check=True,cwd=root)
subprocess.run([str(java/'java'),'-Xmx1G','-Djava.awt.headless=true','-cp',cp+':'+str(out),'com.bilal.practicemod.PreviewSubmarineBoarding'],check=True,cwd=root)
with (root/'build/boarding-clearance-run.log').open('w') as f:
 r=subprocess.run([str(java/'java'),'-Xmx1G','-cp',cp+':'+str(out),'com.bilal.practicemod.ValidateSubmarineEntryMesh'],stdout=f,stderr=subprocess.STDOUT,cwd=root)
print((root/'build/boarding-clearance-run.log').read_text());sys.exit(r.returncode)
