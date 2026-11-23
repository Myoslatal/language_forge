#!/usr/bin/env python3
"""Migrate the built-in HTML demo project (freshProject) into an app .conlang.

Reads NostongueForge_v1.3.1.html, extracts BUILTIN (vocabulary + font) and the
freshProject() demo course, maps it to the app project schema, generates a
project directory and a .conlang zip. Idempotent: removes/recreates outputs.
"""
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import uuid
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HTML = os.path.join(ROOT, "NostongueForge_v1.3.1.html")
OUT_DIR = os.path.join(ROOT, "migration")
GEN_DIR = os.path.join(OUT_DIR, "generated")
PROJECT_NAME = "Nortu 自由语 · 入门"
FOLDER = "Nortu-Intro-html"


def read_html():
    with open(HTML, encoding="utf-8") as handle:
        return handle.read()


def extract_object(source, marker):
    start = source.find(marker)
    if start < 0:
        raise SystemExit(f"marker not found: {marker}")
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
    raise SystemExit("unbalanced object")


def extract_function(source, name):
    start = source.find(f"function {name}()")
    if start < 0:
        raise SystemExit(f"function not found: {name}")
    brace = source.find("{", start)
    depth = 0
    in_string = False
    escape = False
    for pos in range(brace, len(source)):
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
    raise SystemExit("unbalanced function")


def run_fresh_project(source):
    fn = extract_function(source, "freshProject")
    js = (
        "let seq=0;"
        "const uid=(p)=>p+(++seq);"
        "const newQ=(t)=>({id:uid('q'),type:t});"
        "function blankTypeNames(){return {}}\n"
        "function blankSfx(){return {}}\n"
        "function blankAnim(){return {}}\n"
        "function blankImgMax(){return {}}\n"
        + fn
        + "\nconsole.log(JSON.stringify(freshProject()));\n"
    )
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "seed.js")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(js)
        out = subprocess.check_output(["node", path], text=True)
    return json.loads(out.strip())


def build_vocab(builtin, kp_ids):
    words = {}
    for row in builtin["words"]:
        if not isinstance(row[0], (int, str)):
            continue
        words[str(row[0])] = row
    meta = {item["k"]: item["label"] for item in builtin["meta"]}
    pos_entries = []
    pos_by_code = {}
    entries = []
    now = 1795600000000
    for raw_id in kp_ids:
        wid = str(raw_id)
        row = words.get(wid)
        if not row:
            continue
        entry, pos_code, pron = row[1], row[2], row[3]
        rendered = row[6] if len(row) > 6 and isinstance(row[6], str) else entry
        meaning = row[4] if isinstance(row[4], str) else ""
        detail = row[5] if isinstance(row[5], str) else ""
        pos_id = ""
        if pos_code:
            if pos_code not in pos_by_code:
                pid = "pos-nortu-" + re.sub(r"[^a-z0-9-]", "-", pos_code.lower())
                pos_by_code[pos_code] = pid
                pos_entries.append({
                    "id": pid,
                    "code": pos_code,
                    "name": meta.get(pos_code, pos_code),
                    "description": "",
                    "createdAt": now,
                    "updatedAt": now,
                })
            pos_id = pos_by_code[pos_code]
        entry_obj = {
            "id": "nortu-" + wid,
            "type": "text",
            "text": rendered or entry,
            "codes": [entry] if entry and entry != rendered else [],
            "meanings": [meaning] if meaning else [],
            "phonetic": pron or "",
            "phonetics": [pron] if pron else [],
            "posMeanings": ([{"posId": pos_id, "meanings": [meaning] if meaning else []}]
                            if pos_id else []),
            "detail": detail,
            "parts": [],
            "codepoints": [],
            "image": "",
            "imageWidth": 0,
            "imageHeight": 0,
            "audio": "",
            "audioDuration": 0,
            "audioStart": 0,
            "audioEnd": 0,
            "createdAt": now,
            "updatedAt": now,
        }
        entries.append(entry_obj)
    return entries, pos_entries


def make_wav(path, text):
    try:
        subprocess.run(["espeak-ng", "-v", "en", "-w", path, text], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        with wave.open(path, "w") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(22050)
            handle.writeframes(b"\x00\x00" * 4000)
    with wave.open(path, "rb") as handle:
        return int(1000.0 * handle.getnframes() / max(1, handle.getframerate()))


def token_segments(text, by_text):
    segments = []
    ids = []
    for match in re.finditer(r"[\w'\u00C0-\u024F\u1E00-\u1EFF]+", text, re.UNICODE):
        token = match.group()
        entry = by_text.get(token.lower())
        if entry:
            segments.append({"kind": "word", "vocabId": entry["id"]})
            ids.append(entry["id"])
        else:
            segments.append({"kind": "text", "text": token})
    return segments, ids


def main():
    source = read_html()
    builtin = json.loads(extract_object(source, "const BUILTIN ="))
    project = run_fresh_project(source)
    unit = project["units"][0]
    questions = unit["questions"]

    kp_ids = []
    for q in questions:
        for rid in q.get("kp") or []:
            text = str(rid)
            if text not in kp_ids:
                kp_ids.append(text)
    for q in questions:
        for gloss in q.get("glosses") or []:
            w = gloss.get("w")
            if not w:
                continue
            for row in builtin["words"]:
                if isinstance(row[0], (int, str)) and (str(row[0]) not in kp_ids) and (
                        row[1] == w or (len(row) > 6 and row[6] == w)):
                    kp_ids.append(str(row[0]))
                    break
    vocab, pos = build_vocab(builtin, kp_ids)
    by_text = {}
    for entry in vocab:
        for value in [entry["text"]] + entry["codes"]:
            if value:
                by_text.setdefault(value.lower(), entry)
    # Glosses define inflected forms (e.g. no's) that are not dictionary headwords;
    # create synthetic vocabulary entries so passage tokens still show a hint.
    for q in questions:
        for gloss in q.get("glosses") or []:
            word = gloss.get("w") or ""
            if not word or word.lower() in by_text:
                continue
            gid = "html-gloss-" + re.sub(r"[^a-z0-9]+", "-", word.lower()).strip("-")
            entry = {
                "id": gid,
                "type": "text",
                "text": word,
                "codes": [],
                "meanings": [gloss.get("d", "")] if gloss.get("d") else [],
                "phonetic": gloss.get("p", "") or "",
                "phonetics": [gloss.get("p")] if gloss.get("p") else [],
                "posMeanings": [],
                "detail": gloss.get("x", "") or "",
                "parts": [],
                "codepoints": [],
                "image": "",
                "imageWidth": 0,
                "imageHeight": 0,
                "audio": "",
                "audioDuration": 0,
                "audioStart": 0,
                "audioEnd": 0,
                "createdAt": 1795600000000,
                "updatedAt": 1795600000000,
            }
            vocab.append(entry)
            by_text[word.lower()] = entry

    project_dir = os.path.join(GEN_DIR, FOLDER)
    if os.path.isdir(project_dir):
        shutil.rmtree(project_dir)
    os.makedirs(os.path.join(project_dir, "resources", "fonts"), exist_ok=True)
    os.makedirs(os.path.join(project_dir, "resources", "audio"), exist_ok=True)
    os.makedirs(os.path.join(project_dir, "resources", "images"), exist_ok=True)

    font_entries = []
    active_font = ""
    for font in builtin.get("fonts") or []:
        data_url = font.get("dataUrl", "")
        if not data_url.startswith("data:"):
            continue
        payload = data_url.split(",", 1)[1]
        raw = base64.b64decode(payload)
        fid = str(uuid.uuid5(uuid.NAMESPACE_URL, "nortu-font-" + font.get("family", "font")))
        filename = "resources/fonts/nozu_sans.ttf"
        with open(os.path.join(project_dir, filename), "wb") as handle:
            handle.write(raw)
        font_entries.append({
            "id": fid,
            "name": font.get("family", "Nozu Sans"),
            "file": filename,
            "createdAt": 1795600000000,
        })
        active_font = fid
        break

    audio_name = "resources/audio/listen_et.wav"
    duration = make_wav(os.path.join(project_dir, audio_name), "et")
    audios = [{
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "nortu-listen-et")),
        "name": "et (TTS)",
        "code": "listen-et",
        "file": audio_name,
        "duration": duration,
        "createdAt": 1795600000000,
    }]

    template = {
        "acceptedAnswers": [], "alt": [], "answers": [], "audio": "",
        "audioDuration": 0, "audioEnd": 0, "audioSource": "", "audioStart": 0,
        "blocks": [], "correctIndex": -1, "hintsEnabled": True, "ignoreCase": True,
        "ignoreSpace": True, "near": True, "name": "", "optionItems": [],
        "options": ["", "", "", ""], "pairs": [], "parts": [], "prompt": "",
        "promptItems": [], "segments": [], "sentence": "", "warn": [],
        "card": None, "lines": None, "passage": None, "answerMode": None,
        "clozeText": None, "clozeOrder": None, "clozeItems": None,
        "customLabel": None, "customBody": None, "customMode": None,
    }

    def option_texts(options):
        texts = [o.get("text", "") for o in options]
        items = [[{"kind": "text", "text": text}] for text in texts]
        correct = next((i for i, o in enumerate(options) if o.get("ok")), -1)
        return texts, items, correct

    mapped = []
    for q in questions:
        item = dict(template)
        qid = "html-" + q["id"]
        item["id"] = qid
        item["type"] = q["type"] if q["type"] != "choice" else "translation_choice"
        item["name"] = q.get("prompt", "") or item["type"]
        item["prompt"] = q.get("prompt", "")
        if q["type"] == "choice":
            texts, items, correct = option_texts(q.get("options", []))
            item["options"], item["optionItems"], item["correctIndex"] = texts, items, correct
            segments, parts = token_segments(item["prompt"], by_text)
            item["segments"], item["parts"], item["sentence"] = segments, parts, item["prompt"]
        elif q["type"] == "arrange":
            blocks = []
            ok_ids = []
            for index, block in enumerate(q.get("blocks", [])):
                bid = f"{qid}-b{index}"
                entry = by_text.get(block.get("text", "").lower())
                blocks.append({"id": bid, "text": block.get("text", ""),
                               "ok": bool(block.get("ok")), "vocabId": entry["id"] if entry else ""})
                if block.get("ok"):
                    ok_ids.append(bid)
            item["blocks"], item["answers"] = blocks, [ok_ids]
        elif q["type"] == "match":
            pairs = []
            for pair in q.get("pairs", []):
                pairs.append({"l": pair.get("l", ""), "r": pair.get("r", ""),
                              "leftItems": [{"kind": "text", "text": pair.get("l", "")}],
                              "rightItems": [{"kind": "text", "text": pair.get("r", "")}]})
            item["pairs"] = pairs
        elif q["type"] == "type":
            item["acceptedAnswers"] = q.get("answers", [])
            item["warn"] = [{"m": w.get("m", ""), "k": w.get("k", "eq"), "t": w.get("t", "")}
                             for w in q.get("warn", [])]
        elif q["type"] == "card":
            card = q.get("card") or {}
            item["card"] = {"word": card.get("word", ""), "pron": card.get("pron", ""),
                            "def": card.get("def", ""), "detail": card.get("detail", ""),
                            "wordVocabId": "",
                            "examples": [{"l": e.get("l", ""), "z": e.get("z", "")}
                                         for e in card.get("examples", [])]}
            item["name"] = item["card"]["word"] or item["name"]
            card_entry = by_text.get(item["card"]["word"].lower())
            if card_entry:
                item["card"]["wordVocabId"] = card_entry["id"]
        elif q["type"] == "listen":
            texts, items, correct = option_texts(q.get("options", []))
            item["options"], item["optionItems"], item["correctIndex"] = texts, items, correct
            item["audio"] = audio_name
            item["audioDuration"] = duration
            item["audioStart"] = 0
            item["audioEnd"] = duration
            item["audioSource"] = "tts"
        elif q["type"] == "dialog":
            item["lines"] = [{"who": line.get("who", ""), "text": line.get("text", "")}
                             for line in q.get("lines", [])]
            texts, items, correct = option_texts(q.get("options", []))
            item["options"], item["optionItems"], item["correctIndex"] = texts, items, correct
        elif q["type"] == "reading":
            item["passage"] = q.get("passage", "")
            item["answerMode"] = q.get("answerMode", "choice")
            texts, items, correct = option_texts(q.get("options", []))
            item["options"], item["optionItems"], item["correctIndex"] = texts, items, correct
        elif q["type"] == "cloze":
            texts, items, correct = option_texts(q.get("options", []))
            item["clozeText"] = q.get("clozeText", "")
            item["options"], item["optionItems"] = texts, items
            item["clozeOrder"] = [correct] if correct >= 0 else []
            item["clozeItems"] = items
        elif q["type"] == "custom":
            item["customLabel"] = q.get("customLabel", "")
            item["customBody"] = q.get("customBody", "")
            item["customMode"] = q.get("customMode", "choice")
            texts, items, correct = option_texts(q.get("options", []))
            item["options"], item["optionItems"], item["correctIndex"] = texts, items, correct
        mapped.append({key: value for key, value in item.items() if value is not None})

    now = 1795600000000
    structure = {"schema": 1, "chapters": [{
        "id": "html-chapter-1", "name": "第一章",
        "units": [{"id": "html-unit-1", "name": "第一单元", "courses": [{
            "id": "html-course-nortu-intro", "name": unit.get("title", "第 1 关 · 问候与身份"),
            "questions": mapped,
        }]}],
    }]}
    project_json = {
        "schema": 1, "name": PROJECT_NAME, "courseLine": project["course"].get("langName", "Nortu 自由语"),
        "lessons": 1, "questions": len(mapped), "words": len(vocab), "pinned": False,
        "createdAt": now, "updated": now,
    }
    write_json = lambda name, value: open(os.path.join(project_dir, name), "w", encoding="utf-8").write(
        json.dumps(value, ensure_ascii=False, indent=2))
    write_json("project.json", project_json)
    write_json("structure.json", structure)
    write_json("vocabulary.json", {"schema": 1, "entries": vocab})
    write_json("pos.json", {"schema": 1, "entries": pos})
    write_json("images.json", {"schema": 1, "images": []})
    write_json("audios.json", {"schema": 1, "audios": audios})
    write_json("fonts.json", {"schema": 1, "active": active_font, "fonts": font_entries})

    os.makedirs(OUT_DIR, exist_ok=True)
    conlang = os.path.join(OUT_DIR, "Nortu-Intro-html.conlang")
    if os.path.exists(conlang):
        os.remove(conlang)
    shutil.make_archive(conlang[:-len(".conlang")], "zip", project_dir)
    os.replace(conlang[:-len(".conlang")] + ".zip", conlang)
    print(json.dumps({
        "project_dir": project_dir, "conlang": conlang,
        "questions": len(mapped), "vocabulary": len(vocab), "fonts": len(font_entries),
        "types": [q["type"] for q in mapped],
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
