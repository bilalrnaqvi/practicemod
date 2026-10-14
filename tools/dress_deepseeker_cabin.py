#!/usr/bin/env python3
"""Legacy entry point: the utility room is now a sealed compact machinery section."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name('compact_deepseeker.py')), run_name='__main__')
