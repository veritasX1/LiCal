"""Calendar rules shared by Ubuntu and Android – the twin of Rules.kt (RulesTest reads the same cases
from shared/cases/rules.json, written by tests/test_rules.py). Plain data in, plain data out: no GTK.

Events are dicts in the format of docs/DATA.md:
    {"id", "calendar", "title", "allDay": bool,
     "start": "2026-10-04T09:00" | "2026-10-04", "end": … (all-day: the day after the last, like iCalendar),
     "rrule": "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=10" (optional), "exdates": ["2026-10-12", …]}
Times are wall-clock times. A timed event may carry "tz" (an IANA zone like "America/New_York", card
7a9187d6): its times are wall-clock times there; occurrences come out in the local zone."""

import os
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

WEEKDAYS = ("MO", "TU", "WE", "TH", "FR", "SA", "SU")
MAX_OCCURRENCES = 5000


def parse(text):
    """date for "2026-10-04", datetime for "2026-10-04T09:00"."""
    if "T" in text:
        return datetime.strptime(text[:16], "%Y-%m-%dT%H:%M")
    return datetime.strptime(text[:10], "%Y-%m-%d").date()


def stamp(value):
    if isinstance(value, datetime):
        return value.strftime("%Y-%m-%dT%H:%M")
    return value.strftime("%Y-%m-%d")


def as_datetime(value):
    return value if isinstance(value, datetime) else datetime(value.year, value.month, value.day)


# ============================================================
# TIME ZONES (card 7a9187d6)
# ============================================================

def local_zone():
    """This computer's zone as an IANA name (TZ, else /etc/localtime)."""
    name = os.environ.get("TZ", "").lstrip(":")
    if name and "/" in name:
        return name
    try:
        target = os.path.realpath("/etc/localtime")
        return target.split("zoneinfo/", 1)[1] if "zoneinfo/" in target else "UTC"
    except OSError:
        return "UTC"


def convert(moment, source, target):
    """A wall-clock datetime in zone `source` as wall-clock time in zone `target`."""
    if not source or not target or source == target:
        return moment
    try:
        return moment.replace(tzinfo=ZoneInfo(source)).astimezone(ZoneInfo(target)).replace(tzinfo=None)
    except (ZoneInfoNotFoundError, ValueError):
        return moment


def event_zone(event, zone):
    """The zone an event's times are in, if it differs from `zone` (None: floating, local)."""
    tz = event.get("tz")
    return tz if tz and not event.get("allDay") and tz != zone else None


def to_event_zone(event, moment, zone=None):
    """A local wall-clock moment (a drag, a typed time in the view) in the event's own zone."""
    own = event_zone(event, zone or local_zone())
    return convert(moment, zone or local_zone(), own) if own and isinstance(moment, datetime) else moment


def for_editing(item):
    """An occurrence with its times back in the event's own zone – what the inspector and editor change."""
    if "zoneStart" not in item:
        return item
    result = dict(item)
    length = as_datetime(parse(item["zoneEnd"])) - as_datetime(parse(item["zoneStart"]))
    result["start"] = item["zoneStart"]
    result["end"] = stamp(as_datetime(parse(item["zoneStart"])) + length)
    for key in ("zoneStart", "zoneEnd"):
        result.pop(key, None)
    return result


# ============================================================
# MONTHS AND WEEKS
# ============================================================

def week_start(day, first_weekday=0):
    """The first day of day's week (first_weekday 0 = Monday, 6 = Sunday)."""
    return day - timedelta(days=(day.weekday() - first_weekday) % 7)


def month_weeks(year, month, first_weekday=0):
    """The weeks a month touches, each as its 7 days (days of the neighbouring months included)."""
    first = date(year, month, 1)
    following = date(year + month // 12, month % 12 + 1, 1)
    start = week_start(first, first_weekday)
    weeks = []
    while start < following:
        weeks.append([start + timedelta(days=offset) for offset in range(7)])
        start += timedelta(days=7)
    return weeks


def add_months(day, months):
    index = day.year * 12 + day.month - 1 + months
    year, month = divmod(index, 12)
    month += 1
    last = (date(year + month // 12, month % 12 + 1, 1) - timedelta(days=1)).day
    return date(year, month, min(day.day, last))


# ============================================================
# REPETITION (a subset of iCalendar RRULE)
# ============================================================

def rule_parts(rrule):
    parts = {}
    for item in (rrule or "").split(";"):
        if "=" in item:
            key, value = item.split("=", 1)
            parts[key.strip().upper()] = value.strip().upper()
    return parts


def occurrence_starts(event, until):
    """Start dates/times of all occurrences that begin before `until` (date or datetime), in order.
    Supported: FREQ=DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, COUNT, UNTIL, BYDAY (weekly)."""
    first = parse(event["start"])
    limit = as_datetime(until)
    parts = rule_parts(event.get("rrule"))
    frequency = parts.get("FREQ")
    excluded = set(event.get("exdates") or [])
    if frequency not in ("DAILY", "WEEKLY", "MONTHLY", "YEARLY"):
        return [first] if as_datetime(first) < limit and stamp(first)[:10] not in excluded else []
    interval = max(1, int(parts.get("INTERVAL", "1") or 1))
    count = int(parts["COUNT"]) if parts.get("COUNT", "").isdigit() else None
    last = parse(parts["UNTIL"][:4] + "-" + parts["UNTIL"][4:6] + "-" + parts["UNTIL"][6:8]) if len(parts.get("UNTIL", "")) >= 8 else None
    weekdays = [WEEKDAYS.index(day[-2:]) for day in parts.get("BYDAY", "").split(",") if day[-2:] in WEEKDAYS]
    result = []
    produced = 0

    def candidates():
        step = 0
        while True:
            if frequency == "DAILY":
                yield first + timedelta(days=step * interval)
            elif frequency == "WEEKLY":
                base = first + timedelta(weeks=step * interval)
                if weekdays:
                    monday = base - timedelta(days=base.weekday())
                    for weekday in sorted(weekdays):
                        candidate = monday + timedelta(days=weekday)
                        if candidate >= first:
                            yield candidate
                else:
                    yield base
            else:
                base = first.date() if isinstance(first, datetime) else first
                moved = add_months(base, step * interval * (12 if frequency == "YEARLY" else 1))
                if moved.day == base.day:  # months without that day (31st, 29 Feb) are left out, like Apple
                    yield datetime.combine(moved, first.time()) if isinstance(first, datetime) else moved
            step += 1
            if step > MAX_OCCURRENCES * 4:
                return

    for candidate in candidates():
        if as_datetime(candidate) >= limit or (last is not None and as_datetime(candidate).date() > last):
            break
        produced += 1
        if count is not None and produced > count:
            break
        if stamp(candidate)[:10] not in excluded:
            result.append(candidate)
        if len(result) >= MAX_OCCURRENCES:
            break
    return result


def occurrences(events, start, end, zone=None):
    """All occurrences overlapping [start, end) (dates), sorted: earlier first, all-day before timed,
    longer before shorter, then by title. Each one: the event's fields plus "start"/"end" of the
    occurrence and "key" (event id + occurrence start). An event in another zone is repeated there and
    shown in `zone` (default: this computer's); its own times stay in "zoneStart"/"zoneEnd"."""
    window_start = as_datetime(start)
    window_end = as_datetime(end)
    zone = zone or local_zone()
    found = []
    for event in events:
        first = parse(event["start"])
        length = as_datetime(parse(event["end"])) - as_datetime(first)
        if length.total_seconds() < 0:
            length = timedelta(0)
        own = event_zone(event, zone)
        # In another zone the local window is up to a day off: repeat a little further, filter after converting.
        for begin in occurrence_starts(event, window_end + (timedelta(days=2) if own else timedelta(0))):
            finish = as_datetime(begin) + length
            shown_begin, shown_finish = (convert(begin, own, zone), convert(finish, own, zone)) if own else (begin, finish)
            visible_end = shown_finish if shown_finish > as_datetime(shown_begin) else as_datetime(shown_begin) + timedelta(minutes=1)
            if visible_end <= window_start or as_datetime(shown_begin) >= window_end:
                continue
            item = dict(event)
            item["start"] = stamp(shown_begin)
            item["end"] = stamp(shown_finish if isinstance(begin, datetime) else shown_finish.date())
            item["key"] = f"{event['id']}@{stamp(begin)}"
            if own:
                item["zoneStart"], item["zoneEnd"] = stamp(begin), stamp(finish)
            found.append(item)
    found.sort(key=sort_key)
    return found


def sort_key(item):
    start = as_datetime(parse(item["start"]))
    end = as_datetime(parse(item["end"]))
    return (start.date(), 0 if item.get("allDay") else 1, -(end - start).total_seconds() if item.get("allDay") else 0,
            start, item.get("title", ""), item.get("key", ""))


def days_covered(item):
    """The days an occurrence shows on (all-day: start up to the day before end; timed: every day
    it touches, ending exactly at midnight does not count)."""
    start = parse(item["start"])
    end = parse(item["end"])
    if item.get("allDay"):
        first, last = start, max(start, end - timedelta(days=1))
    else:
        first = start.date()
        last = end.date() if end.time() != datetime.min.time() or end.date() == first else end.date() - timedelta(days=1)
        last = max(first, last)
    return first, last


# ============================================================
# A WEEK ROW OF THE MONTH VIEW (Mac): bars for all-day and multi-day, lines for the rest
# ============================================================

def week_layout(items, week):
    """Place the occurrences of one week row (week = its 7 days). Returns
    {"bars": [{"key", "first": column, "last": column, "lane"}], "lines": {column: [key, …]},
     "lanes": [lanes used per column]} – bars are all-day or multi-day events (lanes counted from 0,
    each bar in the lowest free lane over its columns), lines the single-day timed events per day."""
    first_day, last_day = week[0], week[-1]
    bars, lines = [], {column: [] for column in range(7)}
    lanes_used = [0] * 7
    occupied = [[] for _ in range(7)]
    for item in items:
        start, end = days_covered(item)
        if end < first_day or start > last_day:
            continue
        first = max(0, (start - first_day).days)
        last = min(6, (end - first_day).days)
        if item.get("allDay") or end > start:
            lane = 0
            while any(lane in occupied[column] for column in range(first, last + 1)):
                lane += 1
            for column in range(first, last + 1):
                occupied[column].append(lane)
                lanes_used[column] = max(lanes_used[column], lane + 1)
            bars.append({"key": item["key"], "first": first, "last": last, "lane": lane})
        else:
            lines[first].append(item["key"])
    return {"bars": bars, "lines": {str(column): keys for column, keys in lines.items()}, "lanes": lanes_used}


def cell_rows(layout, column, capacity):
    """What fits into one day cell with room for `capacity` rows: bar lanes first (a lane taken by a
    bar elsewhere in the week still costs its row, so bars stay straight), then lines. Returns
    (number of bar lanes shown, line keys shown, number hidden – shown as "+N weitere")."""
    lanes = layout["lanes"][column]
    lines = layout["lines"][str(column)]
    bar_keys_here = sum(1 for bar in layout["bars"] if bar["first"] <= column <= bar["last"])
    total = bar_keys_here + len(lines)
    if lanes + len(lines) <= capacity:
        return lanes, lines, 0
    room = max(0, capacity - 1)  # one row for "+N"
    shown_lanes = min(lanes, room)
    shown_bars = sum(1 for bar in layout["bars"] if bar["first"] <= column <= bar["last"] and bar["lane"] < shown_lanes)
    shown_lines = lines[:max(0, room - shown_lanes)]
    return shown_lanes, shown_lines, total - shown_bars - len(shown_lines)


# ============================================================
# TIMELINE (day and week): overlapping events side by side
# ============================================================

def timeline_layout(items, day):
    """Timed occurrences on `day` as blocks: {"key", "top", "bottom" (minutes from midnight, cut to
    the day), "column", "columns"}. Events that overlap share the width; a group of overlapping
    events uses as many columns as it needs at its busiest."""
    blocks = []
    day_start = datetime(day.year, day.month, day.day)
    day_end = day_start + timedelta(days=1)
    for item in items:
        if item.get("allDay"):
            continue
        start = as_datetime(parse(item["start"]))
        end = as_datetime(parse(item["end"]))
        if end <= day_start or start >= day_end:
            continue
        top = max(0, int((start - day_start).total_seconds() // 60))
        bottom = min(24 * 60, int((end - day_start).total_seconds() // 60))
        bottom = max(bottom, top + 15)  # very short events stay readable
        blocks.append({"key": item["key"], "top": top, "bottom": bottom, "column": 0, "columns": 1})
    blocks.sort(key=lambda block: (block["top"], -block["bottom"], block["key"]))
    group, group_end = [], -1
    for block in blocks + [None]:
        if block is None or block["top"] >= group_end:
            if group:
                columns_end = []
                for member in group:
                    for index, end in enumerate(columns_end):
                        if end <= member["top"]:
                            member["column"] = index
                            columns_end[index] = member["bottom"]
                            break
                    else:
                        member["column"] = len(columns_end)
                        columns_end.append(member["bottom"])
                for member in group:
                    member["columns"] = len(columns_end)
            if block is None:
                break
            group, group_end = [], -1
        group.append(block)
        group_end = max(group_end, block["bottom"])
    return blocks
