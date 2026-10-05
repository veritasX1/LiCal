"""The team sync's core (card ac0d1e1a, docs/TEAMSYNC.md §4) – local only, no network here.

A member's events reach the team as a *stream*: an append-only log of signed, encrypted changes
("put" an event, "delete" one). Each device of the member numbers its own entries; a Lamport clock
orders them. Merging is deterministic: per event the change with the highest (clock, device) wins, so
it does not matter by which way (WLAN, Bluetooth, file, mailbox) or in which order entries arrive.
Only the stream's owner can write in it (signature); only who holds its key can read it.

What a stream carries is a *view* of the member's events chosen by privacy level (§2.3):
"busy" (default: only "Belegt" and absences) or "details" (title and place) – never notes or alerts.
Twin: Team.kt (shared/cases/teamsync.json)."""

import json

from . import crypto, editing

LEVELS = ("busy", "details")


def projection(event, level="busy"):
    """The event as the team sees it at this level (absences always as absence + deputy)."""
    view = {"id": event["id"], "allDay": bool(event.get("allDay")), "start": event["start"], "end": event["end"]}
    for key in ("rrule", "exdates", "tz"):
        if event.get(key):
            view[key] = event[key]
    if event.get("absence"):
        view["absence"] = event["absence"]
        view["title"] = editing.ABSENCE_LABELS.get(event["absence"], event["absence"])
        if event.get("deputy"):
            view["deputy"] = event["deputy"]
    elif level == "details":
        view["title"] = event.get("title", "")
        if event.get("location"):
            view["location"] = event["location"]
    else:
        view["title"] = "Belegt"
    return view


def signed_bytes(stream, op):
    """What the owner signs: a plain line format (identical on every platform, unlike JSON)."""
    box = op["box"]
    return f"lical v1 op\n{stream}\n{op['device']}\n{op['seq']}\n{op['clock']}\n{box['n']}\n{box['c']}".encode()


def aad(stream, device, seq):
    return f"lical v1 {stream}/{device}#{seq}"


class Stream:
    """One member's stream as a device holds it: the entries it has, and the events they amount to."""

    def __init__(self, stream_id, owner_public, key):
        self.id = stream_id
        self.owner = owner_public
        self.key = key
        self.ops = {}        # (device, seq) → entry
        self.versions = {}   # event id → (clock, device)
        self.events = {}     # event id → event (None: deleted)
        self.clock = 0

    # ---- writing (only the owner's devices) ----

    def write(self, identity, device, change, nonce=None):
        """A new entry by this device: change = {"kind": "put", "event": …} or {"kind": "delete", "id": …}."""
        seq = max((s for d, s in self.ops if d == device), default=0) + 1
        self.clock += 1
        text = json.dumps(change, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        op = {"device": device, "seq": seq, "clock": self.clock, "box": crypto.seal_text(self.key, text, aad(self.id, device, seq), nonce)}
        op["sig"] = identity.sign(signed_bytes(self.id, op))
        if not self.receive(op):
            raise crypto.CryptoError("only the owner writes in a stream")
        return op

    def put(self, identity, device, event, level="busy", nonce=None):
        return self.write(identity, device, {"kind": "put", "event": projection(event, level)}, nonce)

    def delete(self, identity, device, event_id, nonce=None):
        return self.write(identity, device, {"kind": "delete", "id": event_id}, nonce)

    # ---- receiving (any way, any order, any number of times) ----

    def receive(self, op):
        """Take an entry: False if known, forged or damaged (then nothing changes)."""
        try:
            place = (str(op["device"]), int(op["seq"]))
            clock = int(op["clock"])
        except (KeyError, TypeError, ValueError):
            return False
        if place in self.ops:
            return False
        if not crypto.verify(self.owner, signed_bytes(self.id, op), op.get("sig", "")):
            return False
        try:
            change = json.loads(crypto.open_text(self.key, op["box"], aad(self.id, *place)))
        except (crypto.CryptoError, ValueError):
            return False
        event_id = change["event"]["id"] if change.get("kind") == "put" else change.get("id")
        if not event_id:
            return False
        self.ops[place] = op
        self.clock = max(self.clock, clock)
        version = (clock, place[0])
        if event_id not in self.versions or version > self.versions[event_id]:
            self.versions[event_id] = version
            self.events[event_id] = change["event"] if change["kind"] == "put" else None
        return True

    # ---- exchanging ----

    def heads(self):
        """How far this device is, per writing device – sent first when two devices meet."""
        heads = {}
        for device, seq in self.ops:
            heads[device] = max(heads.get(device, 0), seq)
        return heads

    def since(self, heads):
        """The entries the other side lacks, in order."""
        return [self.ops[place] for place in sorted(self.ops) if place[1] > heads.get(place[0], 0)]

    def state(self):
        """The member's events as the team sees them (deleted ones left out), by start."""
        return sorted((event for event in self.events.values() if event is not None), key=lambda event: (event["start"], event["id"]))
