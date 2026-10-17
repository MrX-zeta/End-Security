"""Vector propio de la cadena de llaves de End Security (DEC-05, DEC-31).

Calcula, sin Bouncy Castle, los valores fijados en MasterKeyChainTest y MasterKeyChainDeviceTest:
contraseña escrita -> quitar U+FE0E/U+FE0F -> NFKC -> UTF-8 -> Argon2id -> enc_key y auth_key.
También comprueba el HKDF de este script contra los casos 1 a 3 de RFC 5869.

Uso (en un entorno virtual, fuera del repo):
    python -m venv venv
    venv/Scripts/python -m pip install argon2-cffi==25.1.0
    venv/Scripts/python -I tools/vectors/master_key_chain.py
"""
import hashlib
import hmac
import unicodedata

from argon2.low_level import Type, hash_secret_raw


def hkdf_sha256(ikm: bytes, salt: bytes, info: bytes, length: int) -> bytes:
    """RFC 5869 con hmac de la biblioteca estándar; sin sal = 32 ceros."""
    prk = hmac.new(salt or b"\x00" * 32, ikm, hashlib.sha256).digest()
    out, block, counter = b"", b"", 1
    while len(out) < length:
        block = hmac.new(prk, block + info + bytes([counter]), hashlib.sha256).digest()
        out += block
        counter += 1
    return out[:length]


RFC5869 = [
    (bytes([0x0B] * 22), bytes(range(0x00, 0x0D)), bytes(range(0xF0, 0xFA)), 42,
     "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
    (bytes(range(0x00, 0x50)), bytes(range(0x60, 0xB0)), bytes(range(0xB0, 0x100)), 82,
     "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87"),
    (bytes([0x0B] * 22), b"", b"", 42,
     "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
]
for number, (ikm, salt, info, length, expected) in enumerate(RFC5869, start=1):
    assert hkdf_sha256(ikm, salt, info, length).hex() == expected, f"RFC 5869 caso {number}"

# «ñandú tortuga ２０２６ ❤ con U+FE0F» con la ñ descompuesta, dígitos de ancho completo y U+FE0F.
code_points = [0x6E, 0x303] + [ord(c) for c in "andú tortuga "] + [0xFF12, 0xFF10, 0xFF12, 0xFF16, 0x20, 0x2764, 0xFE0F]
typed = "".join(chr(c) for c in code_points)
normalized = unicodedata.normalize("NFKC", typed.replace(chr(0xFE0E), "").replace(chr(0xFE0F), ""))
password = normalized.encode("utf-8")
salt = bytes(range(16))
master_key = hash_secret_raw(password, salt, time_cost=2, memory_cost=19456, parallelism=1,
                             hash_len=32, type=Type.ID, version=19)

print("utf8      ", password.hex())
print("master_key", master_key.hex())
print("enc_key   ", hkdf_sha256(master_key, b"", b"end-security/enc", 32).hex())
print("auth_key  ", hkdf_sha256(master_key, b"", b"end-security/auth", 32).hex())
