"""Alerts before events (card 91adf47e) – the choices of Apple's Calendar and when they ring. The twin of
Alerts.kt (cases in shared/cases/alerts.json, written by tests/test_alerts.py).

An event keeps "alerts": up to two numbers, minutes before its start (Apple: "Hinweis" and "Zweiter
Hinweis"). All-day events count from midnight of their first day, so "Am Tag des Ereignisses (9:00)"
is -540 and "1 Tag vorher (9:00)" is 900."""

from datetime import datetime, timedelta

from .rules import as_datetime, occurrences, parse

TIMED = (
    (0, "Zum Zeitpunkt des Ereignisses"),
    (5, "5 Minuten vorher"),
    (10, "10 Minuten vorher"),
    (15, "15 Minuten vorher"),
    (30, "30 Minuten vorher"),
    (60, "1 Stunde vorher"),
    (120, "2 Stunden vorher"),
    (1440, "1 Tag vorher"),
    (2880, "2 Tage vorher"),
    (10080, "1 Woche vorher"),
)
ALL_DAY = (
    (-540, "Am Tag des Ereignisses (9:00)"),
    (900, "1 Tag vorher (9:00)"),
    (2340, "2 Tage vorher (9:00)"),
    (9540, "1 Woche vorher (9:00)"),
)
MOST = 2  # like Apple: an alert and a second one


def choices(all_day):
    return ALL_DAY if all_day else TIMED


def label(minutes, all_day):
    """"15 Minuten vorher"; a value from elsewhere (a phone calendar) in the same words."""
    if minutes is None:
        return "Keiner"
    for value, text in choices(all_day):
        if value == minutes:
            return text
    if all_day:  # counted from midnight of the first day
        if minutes <= 0:
            when, clock = "Am Tag des Ereignisses", -minutes
        else:
            days = -(-minutes // 1440)
            when, clock = ("1 Tag vorher" if days == 1 else f"{days} Tage vorher"), days * 1440 - minutes
        return f"{when} ({clock // 60}:{clock % 60:02d})"
    if minutes < 0:
        return f"{-minutes} Minuten danach"
    if minutes % 10080 == 0:
        return "1 Woche vorher" if minutes == 10080 else f"{minutes // 10080} Wochen vorher"
    if minutes % 1440 == 0:
        return f"{minutes // 1440} Tage vorher"
    if minutes % 60 == 0:
        return f"{minutes // 60} Stunden vorher"
    return f"{minutes} Minuten vorher"


def set_alerts(event, alerts):
    """Set the alerts (None entries are "Keiner"); at most two, no doubles, in the given order."""
    clean = []
    for minutes in alerts:
        if minutes is not None and minutes not in clean:
            clean.append(int(minutes))
    result = dict(event)
    if clean:
        result["alerts"] = clean[:MOST]
    else:
        result.pop("alerts", None)
    return result


def due(events, after, until):
    """The alerts that ring after `after` up to and including `until` (datetimes, local wall time):
    [{"key", "at", "minutes", "id", "title", "start", "end", "allDay", "location"}] by time. The key
    (occurrence key + "#" + minutes) tells a rung alert from a new one."""
    with_alerts = [event for event in events if event.get("alerts")]
    if not with_alerts:
        return []
    earliest = min(min(event["alerts"]) for event in with_alerts)
    latest = max(max(event["alerts"]) for event in with_alerts)
    first = (after + timedelta(minutes=min(earliest, 0))).date()
    last = (until + timedelta(minutes=max(latest, 0))).date() + timedelta(days=1)
    found = []
    for item in occurrences(with_alerts, first, last):
        start = as_datetime(parse(item["start"]))
        for minutes in item["alerts"]:
            at = start - timedelta(minutes=minutes)
            if after < at <= until:
                found.append({"key": f"{item['key']}#{minutes}", "at": at.strftime("%Y-%m-%dT%H:%M"), "minutes": minutes,
                              "id": item["id"], "title": item.get("title", ""), "start": item["start"], "end": item["end"],
                              "allDay": bool(item.get("allDay")), "location": item.get("location", "")})
    found.sort(key=lambda alert: (alert["at"], alert["key"]))
    return found


def next_time(events, after, days=40):
    """When the next alert rings (None if none within `days`) – what an alarm is set to."""
    upcoming = due(events, after, after + timedelta(days=days))
    return datetime.fromisoformat(upcoming[0]["at"]) if upcoming else None


def text(alert, now):
    """The notification's line under the title: "Heute, 08:30–09:15 · Dr. Weber" (like Apple's)."""
    start = parse(alert["start"])
    day = start if not isinstance(start, datetime) else start.date()
    today = now.date()
    if day == today:
        when = "Heute"
    elif day == today + timedelta(days=1):
        when = "Morgen"
    else:
        when = f"{['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'][day.weekday()]}, {day.day}.{day.month}."
    if alert["allDay"]:
        when += ", ganztägig"
    else:
        end = parse(alert["end"])
        when += f", {start:%H:%M}–{end:%H:%M}"
    return when + (f" · {alert['location']}" if alert.get("location") else "")
