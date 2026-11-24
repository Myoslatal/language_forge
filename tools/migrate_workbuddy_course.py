#!/usr/bin/env python3
"""Migrate the WorkBuddy share-site Nortu course (L1-2) into an app .conlang.

Inputs:
  /tmp/nortu-pack.json  (schema clf-pack/2) and /tmp/nortu-site.html
  (or --pack/--html/--url; the PACK JSON and Nozu Sans TTF are extracted from
  the HTML when the JSON file is not available).

Outputs (idempotent, outside the repository by default):
  /home/forin/language-forge-imports/Nortu-L1-2/        app project directory
  /home/forin/language-forge-imports/nortu-l1-2.conlang importable ZIP (files at ZIP root)

Course data is intentionally not written into the repository or bundled into the
app/APK; use this external .conlang for manual import.

The script converts the 23 embedded webm/opus recordings to mono 22.05 kHz PCM
WAV with ffmpeg, registers them in audios.json, extracts the Nozu Sans TTF from
the page @font-face, and maps all 46 questions to the app schema. Option images
are deliberately skipped (their text is kept); unsupported HTML fields are
listed in migration/WORKBUDDY_MIGRATION.md.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import wave

DEFAULT_PACK = "/tmp/nortu-pack.json"
DEFAULT_HTML = "/tmp/nortu-site.html"
COURSE_URL = ("https://nortu-free-language-l1-2.app.workbuddy.host/"
              "?sharecode=TNWRVMOI4N&shareId=b9a912e4a4844f22ac2017fd7985e543")
EXTERNAL_DIR = os.environ.get("FORGE_MIGRATION_OUT", "/home/forin/language-forge-imports")
GEN_DIR = EXTERNAL_DIR
FOLDER = "Nortu-L1-2"
PROJECT_NAME = "Nortu 自由语 · L1-2（分享站）"
CONLANG = os.path.join(EXTERNAL_DIR, "nortu-l1-2.conlang")
NOW = 1795600000000

POS_CODES = {
    "名词": "n",
    "动词": "v",
    "形容词": "adj",
    "副词": "adv",
    "代词": "pron",
    "叹词/语气词": "interj",
    "介词/介词性助词": "prep",
    "连词": "conj",
    "数词类": "num",
}


def read_text(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def extract_balanced_object(source, marker):
    start = source.find(marker)
    if start < 0:
        raise SystemExit("marker not found: " + marker)
    start = source.find("{", start)
    depth = 0
    in_string = False
    escape = False
    for pos in range(start, len(source)):
        ch = source[pos]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[start:pos + 1]
    raise SystemExit("unbalanced object for " + marker)


def load_pack(pack_path, html):
    if pack_path and os.path.isfile(pack_path):
        return json.loads(read_text(pack_path))
    if not html:
        raise SystemExit("missing --pack and --html")
    return json.loads(extract_balanced_object(html, "const PACK="))


def extract_font(html, font_path):
    if font_path:
        with open(font_path, "rb") as handle:
            return handle.read()
    if not html:
        raise SystemExit("missing HTML; provide --font path for Nozu Sans TTF")
    match = re.search(
        r"@font-face\{[^}]*font-family:\s*'Nozu Sans'[^}]*src:url\(\"data:font/ttf;base64,([^\"]+)\"\)",
        html, re.S)
    if not match:
        raise SystemExit("Nozu Sans TTF @font-face not found")
    return base64.b64decode(match.group(1))


def decode_data_url(value):
    if not isinstance(value, str) or not value.startswith("data:"):
        raise ValueError("not a data URL")
    if "," not in value:
        raise ValueError("malformed data URL")
    return base64.b64decode(value.split(",", 1)[1])


def ffprobe_duration(path):
    out = subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=nw=1:nk=1", path
    ], text=True).strip()
    return int(round(float(out) * 1000.0))


def wav_duration(path):
    with wave.open(path, "rb") as handle:
        return int(round(1000.0 * handle.getnframes() / max(1, handle.getframerate())))


def convert_recordings(pack, project_dir):
    audio_dir = os.path.join(project_dir, "resources", "audio")
    os.makedirs(audio_dir, exist_ok=True)
    results = {}
    with tempfile.TemporaryDirectory(prefix="wb-audio-") as temp_dir:
        for rec_id, data_url in sorted(pack.get("recs", {}).items()):
            raw = decode_data_url(data_url)
            source = os.path.join(temp_dir, rec_id + ".webm")
            target = os.path.join(audio_dir, rec_id + ".wav")
            with open(source, "wb") as handle:
                handle.write(raw)
            command = [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-i", source, "-ar", "22050", "-ac", "1", "-c:a", "pcm_s16le", target,
            ]
            subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            duration = wav_duration(target)
            if duration <= 0:
                raise SystemExit("decoded empty audio: " + rec_id)
            results[rec_id] = {"file": "resources/audio/" + rec_id + ".wav",
                               "duration": duration, "bytes": os.path.getsize(target)}
    return results


def trim_to_ms(trim, duration):
    if not isinstance(trim, dict):
        return 0, duration
    try:
        start = int(round(float(trim.get("s", 0)) * 1000.0))
        end = int(round(float(trim.get("e", 0)) * 1000.0))
    except (TypeError, ValueError):
        return 0, duration
    start = max(0, min(start, max(0, duration - 1)))
    end = max(start, min(end, duration))
    if end - start < 100:
        return 0, duration
    return start, end


def tokenize_prompt(text, by_text):
    """Return app segments that preserve spaces/punctuation and link known words."""
    if not text:
        return [], []
    terms = sorted(by_text.keys(), key=len, reverse=True)
    segments = []
    ids = []
    index = 0
    length = len(text)

    def is_boundary(pos):
        if pos < 0 or pos >= length:
            return True
        return not text[pos].isalnum()

    while index < length:
        found = None
        for term in terms:
            if not text[index:index + len(term)].lower() == term:
                continue
            if not is_boundary(index - 1) or not is_boundary(index + len(term)):
                continue
            found = term
            break
        if found:
            entry = by_text[found]
            segments.append({"kind": "word", "vocabId": entry["id"]})
            if entry["id"] not in ids:
                ids.append(entry["id"])
            index += len(found)
            continue
        start = index
        index += 1
        while index < length:
            match = None
            for term in terms:
                if text[index:index + len(term)].lower() == term and (
                        is_boundary(index - 1) and is_boundary(index + len(term))):
                    match = term
                    break
            if match:
                break
            index += 1
        segments.append({"kind": "punct", "text": text[start:index]})
    return segments, ids


def build_vocabulary(pack, questions, audio_by_rec):
    now = NOW
    pos_entries = []
    pos_by_name = {}
    for glossary in pack.get("glossary", []):
        pos_name = (glossary.get("t") or "").strip()
        if not pos_name or pos_name in pos_by_name:
            continue
        code = POS_CODES.get(pos_name)
        if not code:
            code = "pos" + str(len(pos_by_name) + 1)
        pos_id = "wb-pos-" + re.sub(r"[^a-z0-9-]", "-", code.lower()).strip("-")
        pos_by_name[pos_name] = pos_id
        pos_entries.append({
            "id": pos_id, "code": code, "name": pos_name, "description": "",
            "createdAt": now, "updatedAt": now,
        })

    def slug(text):
        value = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
        return value or "word"

    def new_entry(text, meaning="", phonetic="", detail="", pos_name=""):
        entry_id = ("wb-vocab-" + slug(text) + "-"
                    + hashlib.sha1(text.encode("utf-8")).hexdigest()[:8])
        meanings = [meaning] if meaning else []
        pos_id = pos_by_name.get(pos_name, "")
        entry = {
            "id": entry_id, "type": "text", "text": text, "codes": [],
            "meanings": meanings,
            "phonetic": phonetic or "",
            "phonetics": [phonetic] if phonetic else [],
            "posMeanings": ([{"posId": pos_id, "meanings": list(meanings)}]
                            if pos_id and meanings else []),
            "detail": detail or "", "parts": [], "codepoints": [],
            "image": "", "imageWidth": 0, "imageHeight": 0,
            "audio": "", "audioDuration": 0, "audioStart": 0, "audioEnd": 0,
            "createdAt": now, "updatedAt": now,
        }
        return entry

    entries = []
    by_text = {}
    for glossary in pack.get("glossary", []):
        word = (glossary.get("w") or "").strip()
        if not word or word.lower() in by_text:
            continue
        entry = new_entry(word, glossary.get("d", ""), glossary.get("p", ""),
                          glossary.get("x", ""), glossary.get("t", ""))
        entries.append(entry)
        by_text[word.lower()] = entry

    # Question glosses enrich existing entries; Chinese gloss keys map back to
    # the Nortu entry in d (e.g. {"w":"你好","d":"et"}).
    for question in questions:
        for gloss in question.get("glosses", []) or []:
            word = (gloss.get("w") or "").strip()
            definition = (gloss.get("d") or "").strip()
            if not word and not definition:
                continue
            target = by_text.get(word.lower()) or by_text.get(definition.lower())
            if target is None:
                target = new_entry(word or definition, definition if word else "",
                                   gloss.get("p", ""), gloss.get("x", ""))
                entries.append(target)
                by_text[(word or definition).lower()] = target
            addition = definition if target["text"].lower() == word.lower() else word
            if addition and not any(addition == m for m in target["meanings"]):
                target["meanings"].append(addition)
                for group in target["posMeanings"]:
                    if addition not in group["meanings"]:
                        group["meanings"].append(addition)

    # kp words are dictionary keys; all should already be in glossary, but keep
    # unknown ones as named entries with an empty definition so hints still open.
    for question in questions:
        for key in question.get("kp", []) or []:
            word = str(key).strip()
            if word and word.lower() not in by_text:
                entry = new_entry(word)
                entries.append(entry)
                by_text[word.lower()] = entry

    # Reuse question audio for a word when the whole prompt is that word. This
    # makes the underlined word hint pronounce the migrated recording.
    for question in questions:
        audio = question.get("audio") or {}
        rec = audio.get("rec")
        prompt = (question.get("prompt") or "").strip().lower()
        entry = by_text.get(prompt)
        if not rec or not entry or entry.get("audio") or rec not in audio_by_rec:
            continue
        duration = audio_by_rec[rec]["duration"]
        start, end = trim_to_ms(audio.get("trim"), duration)
        entry["audio"] = audio_by_rec[rec]["file"]
        entry["audioDuration"] = duration
        entry["audioStart"] = start
        entry["audioEnd"] = end

    return entries, pos_entries, by_text


def map_question(question, by_text, audio_by_rec, skipped):
    qid = "wb-" + question["id"]
    source_type = question.get("type", "choice")
    type_map = {
        "choice": "translation_choice",
        "arrange": "arrange",
        "match": "match",
        "type": "type",
        "listen": "listen",
    }
    if source_type not in type_map:
        raise SystemExit("unsupported question type: " + source_type)

    prompt = (question.get("prompt") or "").strip()
    sub = (question.get("sub") or "").strip()
    display_prompt = prompt + ((" " + sub) if sub else "")
    if not display_prompt:
        display_prompt = (question.get("label") or source_type)
    segments, parts = tokenize_prompt(display_prompt, by_text)

    options = question.get("options") or []
    option_texts = []
    option_items = []
    correct_index = -1
    for index, option in enumerate(options):
        text = (option.get("text") or "").strip()
        if option.get("image"):
            skipped["option_images"] += 1
            if not text:
                text = (option["image"].get("name") or "（图片）")
        if option.get("ok") and correct_index < 0:
            correct_index = index
        option_texts.append(text)
        option_items.append([{"kind": "text", "text": text}])

    blocks = []
    answers = []
    if source_type == "arrange":
        ok_ids = []
        for index, block in enumerate(question.get("blocks") or []):
            text = (block.get("text") or "")
            bid = qid + "-b" + str(index)
            entry = by_text.get(text.strip().lower())
            blocks.append({
                "id": bid, "text": text, "ok": bool(block.get("ok")),
                "vocabId": entry["id"] if entry else "",
            })
            if block.get("ok"):
                ok_ids.append(bid)
        answers = [ok_ids]
    elif source_type == "type":
        answers = []
    elif source_type == "match":
        answers = []

    pairs = []
    if source_type == "match":
        for pair in question.get("pairs") or []:
            left = (pair.get("l") or "").strip()
            right = (pair.get("r") or "").strip()
            entry = by_text.get(left.lower())
            left_items = ([{"kind": "vocab", "vocabId": entry["id"]}]
                          if entry else [{"kind": "text", "text": left}])
            pairs.append({
                "l": left, "r": right,
                "leftItems": left_items,
                "rightItems": [{"kind": "text", "text": right}],
            })

    audio = question.get("audio") or {}
    rec = audio.get("rec")
    audio_path = ""
    audio_duration = 0
    audio_start = 0
    audio_end = 0
    if rec:
        if rec not in audio_by_rec:
            skipped["missing_recordings"] += 1
        else:
            audio_path = audio_by_rec[rec]["file"]
            audio_duration = audio_by_rec[rec]["duration"]
            audio_start, audio_end = trim_to_ms(audio.get("trim"), audio_duration)

    warn = []
    if source_type == "type":
        for rule in question.get("warn") or []:
            warn.append({"m": rule.get("m", ""), "k": rule.get("k", "eq"),
                         "t": rule.get("t", "")})

    accepted_answers = []
    if source_type == "type":
        accepted_answers = [str(value).strip() for value in (question.get("answers") or [])
                            if str(value).strip()]

    for field in ("label", "alias", "hint", "tag", "note", "level", "ff"):
        if question.get(field) not in (None, "", [], {}):
            skipped[field] += 1
    if question.get("shuffle") is not True:
        skipped["shuffle_false"] += 1
    if question.get("multi"):
        skipped["multi_true"] += 1
    if question.get("image"):
        skipped["question_images"] += 1

    result = {
        "id": qid,
        "type": type_map[source_type],
        "name": display_prompt,
        "prompt": display_prompt,
        "sentence": display_prompt,
        "parts": parts,
        "segments": segments,
        "hintsEnabled": True,
        "options": option_texts,
        "optionItems": option_items,
        "promptItems": [],
        "blocks": blocks,
        "answers": answers,
        "pairs": pairs,
        "acceptedAnswers": accepted_answers,
        "warn": warn,
        "alt": list(question.get("alt") or []),
        "near": question.get("near", True),
        "ignoreCase": question.get("ignoreCase", True),
        "ignoreSpace": question.get("ignoreSpace", True),
        "correctIndex": correct_index if source_type in ("choice", "listen") else -1,
        "audio": audio_path,
        "audioDuration": audio_duration,
        "audioStart": audio_start,
        "audioEnd": audio_end,
        "audioSource": "rec" if audio_path else "",
        "tts": question.get("tts", "") or "",
        "ttsLang": question.get("ttsLang", "") or "",
        "explain": question.get("explain", "") or "",
    }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", default=DEFAULT_PACK)
    parser.add_argument("--html", default=DEFAULT_HTML)
    parser.add_argument("--url", default=COURSE_URL)
    parser.add_argument("--font", default="", help="optional Nozu Sans TTF path")
    parser.add_argument("--output-dir", default=os.path.join(GEN_DIR, FOLDER),
                        help="external output dir (default: /home/forin/language-forge-imports/Nortu-L1-2)")
    parser.add_argument("--conlang", default=CONLANG,
                        help="external .conlang path (default: /home/forin/language-forge-imports/nortu-l1-2.conlang)")
    parser.add_argument("--project-name", default=PROJECT_NAME)
    args = parser.parse_args()

    if not shutil.which("ffmpeg") or not shutil.which("ffprobe"):
        raise SystemExit("ffmpeg/ffprobe are required to convert the recs")

    html = ""
    if args.html and os.path.isfile(args.html):
        html = read_text(args.html)
    elif args.url:
        try:
            with urllib.request.urlopen(args.url, timeout=30) as response:
                html = response.read().decode("utf-8", "replace")
        except Exception as exc:
            print("warning: could not download source page:", exc, file=sys.stderr)
    if not os.path.isfile(args.pack) and not html:
        raise SystemExit("missing pack JSON and source HTML/URL")
    pack = load_pack(args.pack, html)
    if pack.get("schema") != "clf-pack/2":
        raise SystemExit("unexpected pack schema: " + str(pack.get("schema")))

    project_dir = os.path.abspath(args.output_dir)
    if os.path.isdir(project_dir):
        shutil.rmtree(project_dir)
    os.makedirs(project_dir, exist_ok=True)
    os.makedirs(os.path.join(project_dir, "resources", "audio"), exist_ok=True)
    os.makedirs(os.path.join(project_dir, "resources", "fonts"), exist_ok=True)

    audio_by_rec = convert_recordings(pack, project_dir)

    font_bytes = extract_font(html, args.font)
    if len(font_bytes) < 1024 or font_bytes[:4] not in (b"\x00\x01\x00\x00", b"OTTO", b"ttcf"):
        raise SystemExit("extracted font does not look like a TTF/OTF")
    font_file = os.path.join(project_dir, "resources", "fonts", "nozu_sans.ttf")
    with open(font_file, "wb") as handle:
        handle.write(font_bytes)
    font_id = "wb-font-nozu-sans"

    skipped = {"option_images": 0, "label": 0, "alias": 0, "hint": 0,
               "tag": 0, "note": 0, "level": 0, "ff": 0,
               "shuffle_false": 0, "multi_true": 0, "question_images": 0,
               "missing_recordings": 0}
    questions = []
    for unit in pack.get("units", []):
        for question in unit.get("questions", []):
            questions.append(question)
    if len(questions) != 46:
        raise SystemExit("expected 46 questions, got " + str(len(questions)))

    vocab, pos_entries, by_text = build_vocabulary(pack, questions, audio_by_rec)
    mapped_questions = []
    for question in questions:
        mapped_questions.append(map_question(question, by_text, audio_by_rec, skipped))

    units = []
    cursor = 0
    for unit in pack.get("units", []):
        count = len(unit.get("questions", []))
        unit_id = "wb-unit-" + re.sub(r"[^a-zA-Z0-9-]", "-", unit.get("id", "unit"))
        course_id = "wb-course-" + re.sub(r"[^a-zA-Z0-9-]", "-", unit.get("id", "course"))
        units.append({
            "id": unit_id,
            "name": unit.get("title") or "课程",
            "courses": [{
                "id": course_id,
                "name": unit.get("title") or "课程",
                "questions": mapped_questions[cursor:cursor + count],
            }],
        })
        cursor += count

    structure = {"schema": 1, "chapters": [{
        "id": "wb-chapter-nortu", "name": "Nortu 自由语", "units": units,
    }]}

    audios = []
    for rec in sorted(audio_by_rec):
        first_prompt = ""
        for question in questions:
            audio = question.get("audio") or {}
            if audio.get("rec") == rec:
                first_prompt = (question.get("prompt") or "").strip()
                break
        label = (first_prompt[:48] + "（" + rec + "）") if first_prompt else rec
        audios.append({
            "id": "wb-audio-" + rec,
            "name": label,
            "code": rec,
            "file": audio_by_rec[rec]["file"],
            "duration": audio_by_rec[rec]["duration"],
            "createdAt": NOW,
        })

    project_json = {
        "schema": 1,
        "name": args.project_name,
        "courseLine": (pack.get("course") or {}).get("langName", "Nortu 自由语"),
        "lessons": 3,
        "questions": len(mapped_questions),
        "words": len(vocab),
        "pinned": False,
        "ttsLang": (pack.get("course") or {}).get("ttsLang", "en-US") or "en-US",
        "createdAt": NOW,
        "updated": NOW,
    }
    fonts = {
        "schema": 1, "active": font_id,
        "fonts": [{"id": font_id, "name": "Nozu Sans",
                   "file": "resources/fonts/nozu_sans.ttf", "createdAt": NOW}],
    }

    def write_json(name, value):
        with open(os.path.join(project_dir, name), "w", encoding="utf-8") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)

    write_json("project.json", project_json)
    write_json("structure.json", structure)
    write_json("vocabulary.json", {"schema": 1, "entries": vocab})
    write_json("pos.json", {"schema": 1, "entries": pos_entries})
    write_json("images.json", {"schema": 1, "images": []})
    write_json("audios.json", {"schema": 1, "audios": audios})
    write_json("fonts.json", fonts)

    conlang = os.path.abspath(args.conlang)
    os.makedirs(os.path.dirname(conlang) or ".", exist_ok=True)
    if os.path.exists(conlang):
        os.remove(conlang)
    base = conlang[:-len(".conlang")]
    shutil.make_archive(base, "zip", project_dir)
    os.replace(base + ".zip", conlang)

    audio_questions = sum(1 for q in mapped_questions if q["audio"])
    missing_audio = sum(1 for q in mapped_questions if q["audio"] and not os.path.isfile(
        os.path.join(project_dir, q["audio"])))
    print(json.dumps({
        "project_dir": project_dir,
        "conlang": conlang,
        "chapters": 1,
        "units": len(units),
        "courses": sum(len(u["courses"]) for u in units),
        "questions": len(mapped_questions),
        "types": {t: sum(1 for q in mapped_questions if q["type"] == t)
                  for t in ("translation_choice", "arrange", "match", "type", "listen")},
        "audio_questions": audio_questions,
        "missing_audio": missing_audio,
        "recordings": len(audios),
        "audio_duration_total_ms": sum(a["duration"] for a in audios),
        "fonts": len(fonts["fonts"]),
        "vocabulary": len(vocab),
        "pos": len(pos_entries),
        "skipped": skipped,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
