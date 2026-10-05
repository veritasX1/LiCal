"""Building blocks for the team sync (card ac0d1e1a, docs/TEAMSYNC.md) – the same standard ones as
LiNotes (P-256, ECDH, HKDF-SHA256, AES-256-GCM, ECDSA), with LiCal's own domain strings so keys of the
two apps never mix. Twin: Crypto.kt, shared test vectors in shared/cases/teamsync.json."""

import base64
import hashlib
import os

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF


class CryptoError(Exception):
    pass


def b64(data):
    return base64.b64encode(data).decode()


def unb64(text):
    return base64.b64decode(text)


def hkdf(secret, info, length=32, salt=None):
    return HKDF(algorithm=hashes.SHA256(), length=length, salt=salt, info=info.encode() if isinstance(info, str) else info).derive(secret)


def new_key():
    return os.urandom(32)


def seal_text(key, text, aad, nonce=None):
    """AES-256-GCM of a text; `aad` binds it to its place (stream, device, number) so nothing can be swapped."""
    nonce = nonce or os.urandom(12)
    return {"n": b64(nonce), "c": b64(AESGCM(key).encrypt(nonce, text.encode(), aad.encode()))}


def open_text(key, box, aad):
    try:
        return AESGCM(key).decrypt(unb64(box["n"]), unb64(box["c"]), aad.encode()).decode()
    except (InvalidTag, KeyError, ValueError) as error:
        raise CryptoError("decryption failed") from error


class Identity:
    """A P-256 key pair: signs a member's changes, receives keys (ECIES). The private part never leaves the device."""

    def __init__(self, private=None):
        self.private = private or ec.generate_private_key(ec.SECP256R1())
        self.public_bytes = self.private.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)

    @property
    def public(self):
        return b64(self.public_bytes)

    @staticmethod
    def from_scalar(raw):
        return Identity(ec.derive_private_key(int.from_bytes(raw, "big"), ec.SECP256R1()))

    def scalar(self):
        return self.private.private_numbers().private_value.to_bytes(32, "big")

    def sign(self, data):
        return b64(self.private.sign(data, ec.ECDSA(hashes.SHA256())))

    def agree(self, public):
        """ECDH with another public key (b64, uncompressed point)."""
        other = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), unb64(public))
        return self.private.exchange(ec.ECDH(), other)


def verify(public, data, signature):
    try:
        key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), unb64(public))
        key.verify(unb64(signature), data, ec.ECDSA(hashes.SHA256()))
        return True
    except (InvalidSignature, ValueError, TypeError):
        return False


def wrap_key(key, recipient_public, aad, ephemeral=None, nonce=None):
    """ECIES: a 32-byte key for the holder of `recipient_public` (ephemeral/nonce only fixed in tests)."""
    ephemeral = ephemeral or Identity()
    shared = ephemeral.agree(recipient_public)
    wrapping = hkdf(shared, b"lical v1 wrap" + ephemeral.public_bytes + unb64(recipient_public))
    nonce = nonce or os.urandom(12)
    return {"e": ephemeral.public, "n": b64(nonce), "c": b64(AESGCM(wrapping).encrypt(nonce, key, aad.encode()))}


def unwrap_key(identity, wrapped, aad):
    try:
        shared = identity.agree(wrapped["e"])
        wrapping = hkdf(shared, b"lical v1 wrap" + unb64(wrapped["e"]) + identity.public_bytes)
        return AESGCM(wrapping).decrypt(unb64(wrapped["n"]), unb64(wrapped["c"]), aad.encode())
    except (InvalidTag, KeyError, ValueError) as error:
        raise CryptoError("unwrap failed") from error


def fingerprint(public):
    """What a QR code carries to recognise a member's key."""
    return hashlib.sha256(b"lical v1 fingerprint" + unb64(public)).hexdigest()


def safety_number(public_a, public_b):
    """The same 20 digits on both devices – compared when joining a team."""
    first, second = sorted([unb64(public_a), unb64(public_b)])
    digest = hashlib.sha256(b"lical v1 safety" + first + second).digest()
    text = f"{int.from_bytes(digest[:12], 'big') % 10**20:020d}"
    return " ".join(text[index:index + 5] for index in range(0, 20, 5))
