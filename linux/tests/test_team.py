"""Card ac0d1e1a, step 1 (local core): crypto building blocks with fixed vectors, streams – only the
owner writes, only key holders read, forged/changed/foreign entries are refused, any order and any
repetition give the same result – and the privacy levels. Cases for Android's TeamTest in
shared/cases/teamsync.json (python3 tests/test_team.py --update after a deliberate change)."""

import hashlib
import itertools
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lical import crypto, team  # noqa: E402

CASES_FILE = Path(__file__).resolve().parents[2] / "shared" / "cases" / "teamsync.json"


def fixed(label):
    return crypto.Identity.from_scalar(hashlib.sha256(f"lical test {label}".encode()).digest())


def nonce(label):
    return hashlib.sha256(f"nonce {label}".encode()).digest()[:12]


OLAF, ANNA, EVE = fixed("olaf"), fixed("anna"), fixed("eve")
KEY = hashlib.sha256(b"lical test stream key").digest()
EVENTS = [
    {"id": "e1", "calendar": "arbeit", "title": "Kundentermin Müller", "allDay": False, "start": "2026-10-07T10:00",
     "end": "2026-10-07T11:00", "location": "Köln", "notes": "geheim", "alerts": [15]},
    {"id": "e2", "calendar": "privat", "title": "Urlaub", "allDay": True, "start": "2026-10-12", "end": "2026-10-17",
     "absence": "urlaub", "deputy": "Jens", "notes": "Kreta"},
    {"id": "e3", "calendar": "privat", "title": "Arzt", "allDay": False, "start": "2026-10-08T08:00", "end": "2026-10-08T09:00",
     "rrule": "FREQ=WEEKLY", "tz": "Europe/Berlin"},
]


def ops_scenario():
    """Olaf's phone and laptop write in his stream; the laptop changes e1 later, the phone deletes e3."""
    phone = team.Stream("s-olaf", OLAF.public, KEY)
    laptop = team.Stream("s-olaf", OLAF.public, KEY)
    ops = [phone.put(OLAF, "phone", EVENTS[0], "details", nonce(1)), phone.put(OLAF, "phone", EVENTS[1], "busy", nonce(2)),
           phone.put(OLAF, "phone", EVENTS[2], "busy", nonce(3))]
    for op in ops:
        laptop.receive(op)
    ops.append(laptop.put(OLAF, "laptop", {**EVENTS[0], "title": "Kundentermin Müller (verschoben)", "start": "2026-10-07T14:00",
                                           "end": "2026-10-07T15:00"}, "details", nonce(4)))
    ops.append(phone.delete(OLAF, "phone", "e3", nonce(5)))  # at the same time (phone has not seen the laptop's entry)
    return ops


def build():
    cases = []
    cases.append({"kind": "hkdf", "secret": KEY.hex(), "info": "lical v1 test", "result": crypto.hkdf(KEY, "lical v1 test").hex()})
    cases.append({"kind": "seal", "key": KEY.hex(), "text": "Grüße – {\"a\":1}", "aad": "lical v1 s/d#1", "nonce": nonce(0).hex(),
                  "result": crypto.seal_text(KEY, "Grüße – {\"a\":1}", "lical v1 s/d#1", nonce(0))})
    for name, identity in (("olaf", OLAF), ("anna", ANNA)):
        cases.append({"kind": "public", "scalar": identity.scalar().hex(), "result": identity.public})
    cases.append({"kind": "ecdh", "scalar": OLAF.scalar().hex(), "public": ANNA.public, "result": OLAF.agree(ANNA.public).hex()})
    data = "lical v1 op\ns\nd\n1\n1\nn\nc"
    cases.append({"kind": "verify", "public": OLAF.public, "data": data, "signature": OLAF.sign(data.encode()), "result": True})
    cases.append({"kind": "verify", "public": ANNA.public, "data": data, "signature": OLAF.sign(data.encode()), "result": False})
    wrapped = crypto.wrap_key(KEY, ANNA.public, "share x", ephemeral=fixed("ephemeral"), nonce=nonce(9))
    cases.append({"kind": "wrap", "key": KEY.hex(), "recipient": ANNA.public, "ephemeral": fixed("ephemeral").scalar().hex(),
                  "nonce": nonce(9).hex(), "aad": "share x", "result": wrapped, "recipientScalar": ANNA.scalar().hex()})
    cases.append({"kind": "fingerprint", "public": OLAF.public, "result": crypto.fingerprint(OLAF.public)})
    cases.append({"kind": "safety", "a": OLAF.public, "b": ANNA.public, "result": crypto.safety_number(OLAF.public, ANNA.public)})
    for event in EVENTS:
        for level in team.LEVELS:
            cases.append({"kind": "projection", "event": event, "level": level, "result": team.projection(event, level)})
    ops = ops_scenario()
    for order in ([0, 1, 2, 3, 4], [4, 3, 2, 1, 0], [3, 0, 4, 1, 2]):
        stream = team.Stream("s-olaf", OLAF.public, KEY)
        for index in order + order:  # twice: repetitions change nothing
            stream.receive(ops[index])
        cases.append({"kind": "stream", "owner": OLAF.public, "key": KEY.hex(), "stream": "s-olaf", "ops": [ops[i] for i in order + order],
                      "result": {"state": stream.state(), "heads": stream.heads()}})
    forged = dict(ops[0], seq=9, sig=EVE.sign(team.signed_bytes("s-olaf", dict(ops[0], seq=9))))
    tampered = dict(ops[1], box=dict(ops[1]["box"], c=crypto.b64(bytes([crypto.unb64(ops[1]["box"]["c"])[0] ^ 1]) + crypto.unb64(ops[1]["box"]["c"])[1:])))
    moved = dict(ops[2], seq=7)  # signature no longer matches its place
    cases.append({"kind": "refused", "owner": OLAF.public, "key": KEY.hex(), "stream": "s-olaf", "ops": [forged, tampered, moved]})
    return cases


def main():
    ops = ops_scenario()
    # Every order and repetition gives the same events.
    results = set()
    for order in itertools.permutations(range(len(ops))):
        stream = team.Stream("s-olaf", OLAF.public, KEY)
        for index in order:
            stream.receive(ops[index])
            stream.receive(ops[index])
        results.add(json.dumps(stream.state(), sort_keys=True))
    assert len(results) == 1, results
    state = json.loads(results.pop())
    assert [event["id"] for event in state] == ["e1", "e2"]  # e3 deleted
    assert state[0]["title"] == "Kundentermin Müller (verschoben)" and state[0]["location"] == "Köln"
    assert state[1] == {"id": "e2", "allDay": True, "start": "2026-10-12", "end": "2026-10-17", "absence": "urlaub",
                        "title": "Urlaub", "deputy": "Jens"}
    # Never notes, alerts or the private calendar; "busy" hides title and place.
    for event in EVENTS:
        for level in team.LEVELS:
            view = team.projection(event, level)
            assert not {"notes", "alerts", "calendar"} & set(view), view
    assert team.projection(EVENTS[0])["title"] == "Belegt" and "location" not in team.projection(EVENTS[0])
    # Only the owner writes; a stranger, a changed byte or a moved entry is refused; without the key nothing reads.
    stream = team.Stream("s-olaf", OLAF.public, KEY)
    try:
        stream.put(EVE, "eve", EVENTS[0])
        raise AssertionError("Eve could write")
    except crypto.CryptoError:
        pass
    for bad in build()[-1]["ops"]:
        assert not stream.receive(bad), bad
    assert stream.state() == [] and stream.heads() == {}
    stranger = team.Stream("s-olaf", OLAF.public, hashlib.sha256(b"other key").digest())
    assert not any(stranger.receive(op) for op in ops)
    assert all("Müller" not in json.dumps(op) and "Urlaub" not in json.dumps(op) for op in ops)  # nothing in clear
    # Exchange: heads, then what is missing.
    phone = team.Stream("s-olaf", OLAF.public, KEY)
    for op in ops[:2]:
        phone.receive(op)
    full = team.Stream("s-olaf", OLAF.public, KEY)
    for op in ops:
        full.receive(op)
    missing = full.since(phone.heads())
    assert len(missing) == 3 and all(phone.receive(op) for op in missing) and phone.state() == full.state()
    # Keys: wrapped for Mia only she unwraps; the same safety number on both sides.
    wrapped = crypto.wrap_key(KEY, ANNA.public, "share x")
    assert crypto.unwrap_key(ANNA, wrapped, "share x") == KEY
    for wrong in ((EVE, "share x"), (ANNA, "share y")):
        try:
            crypto.unwrap_key(wrong[0], wrapped, wrong[1])
            raise AssertionError("unwrapped by the wrong one")
        except crypto.CryptoError:
            pass
    assert crypto.safety_number(OLAF.public, ANNA.public) == crypto.safety_number(ANNA.public, OLAF.public)
    cases = build()
    if "--update" in sys.argv or not CASES_FILE.exists():
        CASES_FILE.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n")
    stored = json.loads(CASES_FILE.read_text())
    # Signatures (ECDSA) differ on every run: the stored file is the reference; it must still verify here.
    for case in stored:
        if case["kind"] == "verify":
            assert crypto.verify(case["public"], case["data"].encode(), case["signature"]) == case["result"]
        if case["kind"] == "stream":
            stream = team.Stream(case["stream"], case["owner"], bytes.fromhex(case["key"]))
            for op in case["ops"]:
                stream.receive(op)
            assert {"state": stream.state(), "heads": stream.heads()} == case["result"]
    deterministic = [c for c in cases if c["kind"] not in ("verify", "stream", "refused")]
    assert [c for c in stored if c["kind"] not in ("verify", "stream", "refused")] == json.loads(json.dumps(deterministic)), \
        "shared/cases/teamsync.json veraltet (--update)"
    # Entries written by Android (TeamTest.writesForPython): Kotlin's signatures and JSON read here too.
    kotlin = Path(__file__).resolve().parents[2] / "android" / "app" / "build" / "team-kotlin-ops.json"
    if kotlin.exists():
        written = json.loads(kotlin.read_text())
        assert written["owner"] == OLAF.public
        stream = team.Stream("s-olaf", OLAF.public, KEY)
        assert all(stream.receive(op) for op in written["ops"])
        assert stream.state() == [{"id": "k2", "allDay": True, "start": "2026-10-19", "end": "2026-10-24", "absence": "urlaub",
                                   "title": "Urlaub", "deputy": "Jens"}], stream.state()
        print("   (Einträge von Android gelesen und geprüft)")
    print(f"ok – Team-Abgleich, lokaler Kern ({len(cases)} Zwillingsfälle)")


if __name__ == "__main__":
    main()
