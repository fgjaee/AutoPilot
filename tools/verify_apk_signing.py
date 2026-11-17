#!/usr/bin/env python3
"""Check APK v2/v3 certificate against expected DER. Also run apksigner verify."""
import hashlib
import struct
import sys
from pathlib import Path

MAGIC = b"APK Sig Block 42"
SCHEMES = {0x7109871A, 0xF05368C0}

def u32(data, off):
    return struct.unpack_from("<I", data, off)[0]

def u64(data, off):
    return struct.unpack_from("<Q", data, off)[0]

def lp(data, off):
    length = u32(data, off)
    end = off + 4 + length
    if end > len(data):
        raise ValueError("Truncated signer data")
    return data[off + 4:end], end

def cert_from_apk(path):
    data = Path(path).read_bytes()
    marker = data.rfind(MAGIC)
    if marker < 8:
        raise ValueError("No APK Signing Block")
    size = u64(data, marker - 8)
    start = marker + len(MAGIC) - size - 8
    if start < 0 or u64(data, start) != size:
        raise ValueError("Bad APK Signing Block size")
    cursor = start + 8
    while cursor < marker - 8:
        entry_size = u64(data, cursor)
        entry_id = u32(data, cursor + 8)
        end = cursor + 8 + entry_size
        if end > marker - 8:
            raise ValueError("Truncated APK Signing Block")
        if entry_id in SCHEMES:
            signers, _ = lp(data[cursor + 12:end], 0)
            signer, _ = lp(signers, 0)
            signed, _ = lp(signer, 0)
            _, off = lp(signed, 0)
            certs, _ = lp(signed, off)
            cert, _ = lp(certs, 0)
            return cert
        cursor = end
    raise ValueError("No v2/v3 APK signer")

if __name__ == "__main__":
    if len(sys.argv) not in (2, 3):
        raise SystemExit("Usage: verify_apk_signing.py app.apk [cert.der]")
    actual = hashlib.sha256(cert_from_apk(sys.argv[1])).hexdigest()
    print("APK signer SHA256:", actual)
    if len(sys.argv) == 3:
        expected = hashlib.sha256(Path(sys.argv[2]).read_bytes()).hexdigest()
        print("Key signer SHA256:", expected)
        if actual != expected:
            raise SystemExit("FAIL: APK signer differs from stored key")
        print("PASS: matching signing certificates")
