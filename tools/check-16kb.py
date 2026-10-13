"""Check every native library in an APK for 16 KB page support.

Two things must hold for each lib/<abi>/*.so: every ELF LOAD segment is aligned to at least 16 KB
(p_align >= 16384), and, if the .so is stored uncompressed, its offset in the zip is a multiple of
16 KB so Android can map it straight from the APK.
"""
import struct
import sys
import zipfile

apk = sys.argv[1]
ok = True
with zipfile.ZipFile(apk) as z:
    for info in z.infolist():
        if not (info.filename.startswith('lib/') and info.filename.endswith('.so')):
            continue
        data = z.read(info)
        is64 = data[4] == 2
        endian = '<' if data[5] == 1 else '>'
        if is64:
            phoff = struct.unpack_from(endian + 'Q', data, 0x20)[0]
            phentsize, phnum = struct.unpack_from(endian + 'HH', data, 0x36)
        else:
            phoff = struct.unpack_from(endian + 'I', data, 0x1C)[0]
            phentsize, phnum = struct.unpack_from(endian + 'HH', data, 0x2A)
        aligns = []
        for i in range(phnum):
            off = phoff + i * phentsize
            ptype = struct.unpack_from(endian + 'I', data, off)[0]
            if ptype != 1:  # PT_LOAD
                continue
            align = struct.unpack_from(endian + 'Q', data, off + 0x30)[0] if is64 else struct.unpack_from(endian + 'I', data, off + 0x1C)[0]
            aligns.append(align)
        elf_ok = all(a >= 16384 for a in aligns)
        stored = info.compress_type == zipfile.ZIP_STORED
        # data starts after the local header: 30 bytes + name + extra
        z.fp.seek(info.header_offset)
        hdr = z.fp.read(30)
        name_len, extra_len = struct.unpack('<HH', hdr[26:30])
        data_off = info.header_offset + 30 + name_len + extra_len
        zip_ok = (not stored) or data_off % 16384 == 0
        status = 'OK ' if elf_ok and zip_ok else 'BAD'
        if status == 'BAD':
            ok = False
        print(f"{status} {info.filename}  LOAD align {min(aligns) if aligns else '-'}  {'stored@' + str(data_off) if stored else 'compressed'}")
print('ALL 16 KB READY' if ok else 'NOT READY')
