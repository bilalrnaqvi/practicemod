"""Sable 2.0.6 native loader names and Rust targets (shared by build and packaging)."""
PLATFORMS = {
    'macos-arm64': ('aarch64-apple-darwin', 'libsable_rapier.dylib', 'sable_rapier_aarch64_macos.dylib'),
    'macos-x64': ('x86_64-apple-darwin', 'libsable_rapier.dylib', 'sable_rapier_x86_64_macos.dylib'),
    'linux-x64': ('x86_64-unknown-linux-gnu', 'libsable_rapier.so', 'sable_rapier_x86_64_linux.so'),
    'linux-arm64': ('aarch64-unknown-linux-gnu', 'libsable_rapier.so', 'sable_rapier_aarch64_linux.so'),
    'windows-x64': ('x86_64-pc-windows-msvc', 'sable_rapier.dll', 'sable_rapier_x86_64_windows.dll'),
    'windows-arm64': ('aarch64-pc-windows-msvc', 'sable_rapier.dll', 'sable_rapier_aarch64_windows.dll'),
}
COMMIT = 'd501fb3103c3247e20cb2151a99182ad5dd61adb'
