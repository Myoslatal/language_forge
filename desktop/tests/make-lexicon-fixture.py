#!/usr/bin/env python3
"""Generate the desktop smoke-test lexicon fixture (/tmp/lexicon-sample.jsonl).

The smoke test needs a JSONL fixture with 5312 unique entries plus duplicated
"e"/"u" rows, a "_meta" type table and specific words (nız / ıu-vurg). Because
that file lives in /tmp it disappears between sessions, so regenerate it from a
project's exported vocabulary.json + pos.json:

    python3 desktop/tests/make-lexicon-fixture.py \
        /path/to/vocabulary.json /path/to/pos.json [/tmp/lexicon-sample.jsonl]
"""
import json
import sys

TARGET_UNIQUE = 5312
REQUIRED = ("nız", "ıu-vurg")
DUPLICATES = ("e", "u")


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def display_text(entry, by_id, depth=0):
    if depth > 8:
        return ""
    kind = entry.get("type", "text")
    if kind == "text":
        return (entry.get("text") or "").strip()
    if kind == "unicode":
        out = []
        for raw in entry.get("codepoints") or []:
            try:
                out.append(chr(int(str(raw), 16)))
            except (TypeError, ValueError):
                continue
        return "".join(out)
    if kind == "combo":
        parts = []
        for part_id in entry.get("parts") or []:
            part = by_id.get(part_id)
            if part is not None and part.get("id") != entry.get("id"):
                parts.append(display_text(part, by_id, depth + 1))
        return "".join(parts)
    codes = entry.get("codes") or []
    if codes:
        return str(codes[0])
    meanings = entry.get("meanings") or []
    return str(meanings[0]) if meanings else ""


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 1
    vocab = load(argv[1])
    pos = load(argv[2])
    out_path = argv[3] if len(argv) > 3 else "/tmp/lexicon-sample.jsonl"

    pos_code = {item.get("id"): item.get("code", "") for item in pos.get("entries", [])}
    entries = vocab.get("entries", [])
    by_id = {entry.get("id"): entry for entry in entries}

    rows = []
    seen = set()
    for entry in entries:
        word = display_text(entry, by_id)
        meanings = [str(m) for m in (entry.get("meanings") or []) if str(m).strip()]
        if not word or not meanings:
            continue
        key = word.lower()
        if key in seen:
            continue
        seen.add(key)
        kind = entry.get("type", "text")
        if kind != "text":
            # The import stores non-text entries under their rendered text.
            pass
        code = ""
        for group in entry.get("posMeanings") or []:
            code = pos_code.get(group.get("posId"), "")
            if code:
                break
        rows.append({
            "rendered": word,
            "type": code,
            "definition": "；".join(meanings),
            "detail": "",
            "pronunciation": (entry.get("phonetic") or "").strip(),
        })

    def key(row):
        return row["rendered"].lower()

    required = [row for row in rows if key(row) in REQUIRED]
    duplicates = [row for row in rows if key(row) in DUPLICATES]
    missing = [word for word in REQUIRED + DUPLICATES
               if word not in {key(row) for row in rows}]
    if missing:
        print("vocabulary is missing required fixture words:", ", ".join(missing))
        return 2

    keep_ids = {key(row) for row in required + duplicates}
    ordered = [row for row in rows if key(row) in keep_ids]
    rest = [row for row in rows if key(row) not in keep_ids]
    rest.sort(key=key)
    room = TARGET_UNIQUE - len(ordered)
    if room < 0:
        print("too many required rows")
        return 2
    ordered.extend(rest[:room])

    lines = [json.dumps({
        "_meta": {
            "types": [
                {"k": item.get("code", ""), "label": item.get("name", "")}
                for item in pos.get("entries", [])
                if item.get("code")
            ]
        }
    }, ensure_ascii=False)]
    lines.extend(json.dumps(row, ensure_ascii=False) for row in ordered)
    for word in DUPLICATES:
        row = next(row for row in rows if key(row) == word)
        lines.append(json.dumps(row, ensure_ascii=False))

    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
        handle.write("\n")
    print(f"wrote {out_path}: {len(lines) - 1} entry lines, "
          f"{len({key(row) for row in ordered})} unique words")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
