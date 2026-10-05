"""One event as iCalendar text (RFC 5545) – for passing it on as a QR code (card b483dadf). The
standard format: even an iPhone's camera offers "Add to Calendar" for it. Only this event goes into
the code – no calendar, no other people's data (privacy first). Twin: Ics.kt (shared/cases/ics.json)."""

from datetime import datetime, timedelta, timezone

from .rules import parse


def known_zone(name):
    """An IANA zone this computer knows (Outlook's "W. Europe Standard Time" is not one – then floating)."""
    from zoneinfo import ZoneInfo
    try:
        ZoneInfo(name)
        return "/" in name
    except Exception:
        return False


def escape(text):
    return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\n").replace("\n", "\\n")


def unescape(text):
    result, index = [], 0
    while index < len(text):
        char = text[index]
        if char == "\\" and index + 1 < len(text):
            following = text[index + 1]
            result.append("\n" if following in "nN" else following)
            index += 2
            continue
        result.append(char)
        index += 1
    return "".join(result)


def moment(value, all_day):
    when = parse(value)
    return when.strftime("%Y%m%d") if all_day else when.strftime("%Y%m%dT%H%M%S")


def to_ics(event):
    """The event as a VCALENDAR with one VEVENT (times as floating local time, all-day as dates)."""
    all_day = bool(event.get("allDay"))
    kind = ";VALUE=DATE" if all_day else (f";TZID={event['tz']}" if event.get("tz") else "")
    lines = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//LiCal//DE", "BEGIN:VEVENT",
             f"SUMMARY:{escape(event.get('title', ''))}",
             f"DTSTART{kind}:{moment(event['start'], all_day)}",
             f"DTEND{kind}:{moment(event['end'], all_day)}"]
    if event.get("location"):
        lines.append(f"LOCATION:{escape(event['location'])}")
    if event.get("notes"):
        lines.append(f"DESCRIPTION:{escape(event['notes'])}")
    if event.get("rrule"):
        lines.append(f"RRULE:{event['rrule']}")
    lines += ["END:VEVENT", "END:VCALENDAR"]
    return "\r\n".join(lines) + "\r\n"


def from_ics(text):
    """The first VEVENT of iCalendar text as a LiCal event without id/calendar (None if there is none).
    Times with "Z" (UTC) become local time; floating times stay; TZID times keep their zone ("tz")."""
    unfolded = text.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").replace("\n\t", "")
    inside = False
    fields = {}
    for line in unfolded.replace("\r\n", "\n").split("\n"):
        if line.strip() == "BEGIN:VEVENT":
            inside = True
            continue
        if line.strip() == "END:VEVENT":
            break
        if not inside or ":" not in line:
            continue
        name, value = line.split(":", 1)
        key, *params = name.split(";")
        fields.setdefault(key.upper(), (value, params))
    if "DTSTART" not in fields:
        return None

    def when(field):
        value, params = fields[field]
        value = value.strip()
        if "VALUE=DATE" in [param.upper() for param in params] or len(value) == 8:
            return f"{value[:4]}-{value[4:6]}-{value[6:8]}", True
        moment = datetime(int(value[:4]), int(value[4:6]), int(value[6:8]), int(value[9:11]), int(value[11:13]))
        if value.endswith("Z"):  # UTC → the local time of this computer
            moment = moment.replace(tzinfo=timezone.utc).astimezone().replace(tzinfo=None)
        return moment.strftime("%Y-%m-%dT%H:%M"), False

    start, all_day = when("DTSTART")
    if "DTEND" in fields:
        end, _ = when("DTEND")
    elif all_day:
        end = (parse(start) + timedelta(days=1)).isoformat()
    else:
        end = (parse(start) + timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M")
    event = {"title": unescape(fields.get("SUMMARY", ("Termin", []))[0]).strip() or "Termin", "allDay": all_day, "start": start, "end": end}
    zone = next((param.split("=", 1)[1].strip('"') for param in fields["DTSTART"][1] if param.upper().startswith("TZID=")), None)
    if zone and not all_day and known_zone(zone):
        event["tz"] = zone
    if "LOCATION" in fields and fields["LOCATION"][0].strip():
        event["location"] = unescape(fields["LOCATION"][0]).strip()
    if "DESCRIPTION" in fields and fields["DESCRIPTION"][0].strip():
        event["notes"] = unescape(fields["DESCRIPTION"][0]).strip()
    if "RRULE" in fields:
        event["rrule"] = fields["RRULE"][0].strip()
    return event

