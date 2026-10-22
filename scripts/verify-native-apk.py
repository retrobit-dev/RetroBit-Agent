#!/usr/bin/env python3
"""Check CPU ABIs and Android 16 KB page compatibility of packaged native code."""
import struct
import sys
import zipfile


def verify(path):
    with zipfile.ZipFile(path) as apk:
        for abi in ("arm64-v8a", "x86_64"):
            for library in ("libretrobit_local.so", "libc++_shared.so"):
                name = f"lib/{abi}/{library}"
                info = apk.getinfo(name)  # Missing ABI/dependency is a build failure.
                elf = apk.read(info)
                assert elf[:6] == b"\x7fELF\x02\x01", f"not 64-bit LE ELF: {name}"
                offset = struct.unpack_from("<Q", elf, 32)[0]
                entry_size, count = struct.unpack_from("<HH", elf, 54)
                loads = 0
                for i in range(count):
                    fields = struct.unpack_from("<IIQQQQQQ", elf, offset + i * entry_size)
                    kind, _, file_offset, vaddr, _, _, _, alignment = fields
                    if kind != 1:  # PT_LOAD
                        continue
                    loads += 1
                    assert alignment >= 16384, f"4 KB-only ELF LOAD: {name}"
                    assert (vaddr - file_offset) % 16384 == 0, f"incompatible ELF offsets: {name}"
                assert loads > 0, f"no ELF load segments: {name}"
                if info.compress_type == zipfile.ZIP_STORED:
                    with open(path, "rb") as stream:
                        stream.seek(info.header_offset + 26)
                        filename_len, extra_len = struct.unpack("<HH", stream.read(4))
                    data_offset = info.header_offset + 30 + filename_len + extra_len
                    assert data_offset % 16384 == 0, f"uncompressed APK entry not 16 KB aligned: {name}"
                print(f"native=OK {name} bytes={len(elf)} LOADs={loads} alignment>=16384")


if __name__ == "__main__":
    for argument in sys.argv[1:]:
        verify(argument)
