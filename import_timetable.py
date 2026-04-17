"""
AttendEase — Timetable & Classes Import Script
==============================================
Creates two Firestore collections:

  classes/    — one doc per section (e.g. CSE-A-2024)
                  fields: classId, department, section, batch, room, studentIds

  timetable/  — one doc per period per day per section
                  fields: facultyId, classId, subject, day, startTime, endTime

Sources:
  - timetable/<SECTION>.csv   e.g. CSE-A.csv, CSE-B.csv ...
  - Course & Faculty Mapping.csv
  - Room Assignments.csv
  - Firestore users (to fetch student roll numbers per section)

Requirements:
    pip3 install firebase-admin

Usage:
    1. Place timetable CSVs in the folder set in TIMETABLE_FOLDER.
    2. Edit CONFIG below if paths differ.
    3. Run:  python3 import_timetable.py
"""

import csv, os, re
import firebase_admin
from firebase_admin import credentials, firestore

# ─────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────
SERVICE_ACCOUNT_JSON   = "serviceAccountKey.json"
TIMETABLE_FOLDER       = "./timetables"          # CSE-A.csv, CSE-B.csv …
COURSE_FACULTY_CSV     = "./timetables/Course & Faculty Mapping.csv"
ROOM_ASSIGNMENTS_CSV   = "./timetables/Room Assignments.csv"
DEPARTMENT             = "TEST"
BATCH                  = "2024-28"
# ─────────────────────────────────────────────

# Period slot → (startTime, endTime)
PERIOD_TIMES = {
    "8:50-9:40":   ("00:00", "03:00"),
    "9:40-10:30":  ("09:40", "10:30"),
    "10:30-11:20": ("10:30", "11:20"),
    "11:20-12:10": ("11:20", "12:10"),
    "12:10-1:00":  ("12:10", "13:00"),   # LUNCH — skipped in code
    "1:00-1:50":   ("13:00", "13:50"),
    "1:50-2:40":   ("13:50", "14:40"),
    "2:50-3:30":   ("14:50", "15:30"),
}

# Day abbreviations → full name
DAY_MAP = {
    "Mon": "MONDAY", "Tue": "TUESDAY", "Wed": "WEDNESDAY",
    "Thu": "THURSDAY", "Fri": "FRIDAY", "Sat": "SATURDAY",
}

# Subject abbreviations → full subject name
SUBJECT_MAP = {
    "P&S":      "Probability and Statistics",
    "FLAT":     "Formal Language and Automata Theory",
    "DBMS":     "Database Management Systems",
    "CO MPI":   "Computer Organization and Microprocessor Interfacing",
    "COMPI":    "Computer Organization and Microprocessor Interfacing",
    "CO MPII":  "Computer Organization and Microprocessor Interfacing",
    "DAA":      "Design and Analysis of Algorithms",
    "FS LAB":   "Full Stack Development Lab",
    "MAD LAB":  "Mobile Application Development",
    "DBMS LAB": "Database Management Systems Lab",
    "COMP LAB": "Computer Organization Lab",
    "NA":       "Numerical Ability",
    "PC":       "Professional Communication Skills",
    "PCH":      "Counselling",
    "TRAINING": "Training",
    "ED-IPR":   "Entrepreneurship Development and IPR",
    "LUNCH":    None,   # skip
}

# Partial name → faculty doc ID (email prefix in Firestore)
# Used to resolve names from Course & Faculty Mapping CSV
FACULTY_NAME_TO_ID = {
    "S. Anusha":          "sanusha.cse",
    "Mrs. S. Anusha":     "sanusha.cse",
    "S. Radhika":         "radhika.cse",
    "Mrs. S. Radhika":    "radhika.cse",
    "Dr. S. Radhika":     "radhika.cse",
    "S.A. Bhavani":       "sabhavani.cse",
    "S. A. Bhavani":      "sabhavani.cse",
    "Mrs. S.A. Bhavani":  "sabhavani.cse",
    "D. Aswani":          "daswani.cse",
    "Mrs. D. Aswani":     "daswani.cse",
    "A. Swarnalatha":     "aswarnalatha.cse",
    "Mrs. A. Swarnalatha":"aswarnalatha.cse",
    "B. Vineela Rani":    "botchavineelarani.cse",
    "Mrs. B. Vineela Rani":"botchavineelarani.cse",
    "Botcha Vineela Rani":"botchavineelarani.cse",
    "B. Ravi Kumar":      "ravikumar.cse",
    "Mr. B. Ravi Kumar":  "ravikumar.cse",
    "G. Gowri Pushpa":    "gowripushpa.cse",
    "Mrs. G. Gowri Pushpa":"gowripushpa.cse",
    "S. Bosu Babu":       "bosubabu.cse",
    "Mr. S. Bosu Babu":   "bosubabu.cse",
    "Y Padma Sri":        "ypadmasri.cse",
    "Y. Padmasri":        "ypadmasri.cse",
    "Mrs. Y Padma Sri":   "ypadmasri.cse",
    "Mrs. Y. Padmasri":   "ypadmasri.cse",
    "A. Rohini":          "arohini.cse",
    "Dr. A. Rohini":      "arohini.cse",
    "Rohini A":           "arohini.cse",
    "G. Pranitha":        "pranitha.cse",
    "Mrs. G. Pranitha":   "pranitha.cse",
    "Pranitha Gadde":     "pranitha.cse",
    "N. Preethi":         "npreethi.cse",
    "Mrs. N. Preethi":    "npreethi.cse",
    "Preethi Nutipalli":  "npreethi.cse",
    "K. Venkata Ratnam":  "kvenkataratnam.cse",
    "Mrs. K. Venkata Ratnam":"kvenkataratnam.cse",
    "N. Durga Prasad":    "durgaprasad.cse",
    "Mr. N. Durga Prasad":"durgaprasad.cse",
    "Dr. Test Faculty":   "test_fac_01",
    # Cross-dept faculty (store name as-is; add proper imports later)
    "M SLR Mallika":      "mslrmallika.math",
    "M.V. Subba Rao":     "mvsubbarao.math",
    "B. Devaki Rani":     "bdevakirani.math",
    "G. Lalitha Devi":    "glalithadevi.math",
}


def resolve_faculty(raw_name: str) -> str:
    """Try to match a name from the CSV to a faculty doc ID."""
    name = raw_name.strip()
    # Direct lookup
    if name in FACULTY_NAME_TO_ID:
        return FACULTY_NAME_TO_ID[name]
    # Try stripping title prefix
    for prefix in ("Prof. ", "Dr. ", "Mr. ", "Mrs. ", "Ms. "):
        if name.startswith(prefix):
            stripped = name[len(prefix):]
            if stripped in FACULTY_NAME_TO_ID:
                return FACULTY_NAME_TO_ID[stripped]
    # Partial match on last-name fragment
    for key, val in FACULTY_NAME_TO_ID.items():
        if key in name or name in key:
            return val
    # Give up — store sanitised version as placeholder
    placeholder = re.sub(r'[^a-zA-Z0-9]', '', name.lower())[:20]
    print(f"  ⚠️  Could not resolve faculty: '{name}' → using '{placeholder}' as ID")
    return placeholder


def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate(SERVICE_ACCOUNT_JSON)
        firebase_admin.initialize_app(cred)
    return firestore.client()


def load_rooms() -> dict:
    """Returns {section: room} e.g. {'A': 'C 306'}"""
    rooms = {}
    with open(ROOM_ASSIGNMENTS_CSV, newline='', encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            section_raw = row.get("Section", "").strip()   # "CSE - A"
            room        = row.get("Room Number", "").strip()
            # Extract the letter after the dash: "CSE - A" → "A"
            m = re.search(r'-\s*([A-Z])', section_raw)
            if m:
                rooms[m.group(1)] = room
    return rooms


def load_subject_faculty_map() -> dict:
    """
    Returns {(subject_abbrev, section): faculty_doc_id}
    Uses the Course & Faculty Mapping CSV.
    """
    mapping = {}
    sections = ["A", "B", "C", "D", "T"]
    with open(COURSE_FACULTY_CSV, newline='', encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            subject = row.get("Subject", "").strip()
            # Find the abbreviation reverse-lookup
            abbrev = next((k for k, v in SUBJECT_MAP.items() if v == subject), None)
            if not abbrev:
                # Try matching by keyword
                abbrev = subject  # fall back

            for i, sec in enumerate(sections):
                col = f"Faculty (Section {sec})"
                if col not in row:
                    continue
                raw = row.get(col)
                if not raw or not raw.strip():
                    continue
                raw = raw.strip()
                # Take only primary faculty (before " / ")
                primary = raw.split(" / ")[0].strip()
                # Remove trailing "+N" notes
                primary = re.sub(r'\s*\+\d+$', '', primary).strip()
                faculty_id = resolve_faculty(primary)
                mapping[(abbrev, sec)]    = faculty_id
                mapping[(subject, sec)]   = faculty_id   # full name key too
    return mapping


def get_students_by_section(db, department: str) -> dict:
    """Query Firestore users to get {section: [rollNo, ...]}"""
    print("  ↳ Fetching students from Firestore…")
    result = {}
    docs = db.collection("users") \
             .where("department", "==", department) \
             .where("role", "==", "student") \
             .stream()
    for doc in docs:
        d = doc.to_dict()
        sec  = d.get("section", "A")
        roll = d.get("rollNo", doc.id)
        result.setdefault(sec, []).append(roll)
    return result


def create_classes(db, dept: str, batch: str, rooms: dict, students_by_section: dict) -> dict:
    """Creates/overwrites classes/ documents. Returns {section: classId}"""
    class_ids = {}
    fs_batch = db.batch()
    for section, student_ids in students_by_section.items():
        class_id = f"{dept}-{section}-{batch[:4]}"   # e.g. CSE-A-2024
        room     = rooms.get(section, "TBD")
        doc_ref  = db.collection("classes").document(class_id)
        fs_batch.set(doc_ref, {
            "classId":    class_id,
            "department": dept,
            "section":    section,
            "batch":      batch,
            "room":       room,
            "studentIds": student_ids,
        })
        class_ids[section] = class_id
        print(f"  ↳ Class {class_id}: {len(student_ids)} students, room {room}")
    fs_batch.commit()
    return class_ids


def import_section_timetable(db, csv_path: str, section: str,
                              class_id: str, subject_faculty: dict) -> int:
    """Reads one section timetable CSV and writes timetable/ docs."""
    with open(csv_path, newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        periods = [h.strip() for h in (reader.fieldnames or []) if h.strip() != "Day"]

    with open(csv_path, newline='', encoding='utf-8-sig') as f:
        rows = list(csv.DictReader(f))

    fs_batch = db.batch()
    count = 0

    for row in rows:
        day_abbrev = row.get("Day", "").strip()
        day = DAY_MAP.get(day_abbrev)
        if not day:
            continue

        for period in periods:
            subject_abbrev = row.get(period, "").strip()
            if not subject_abbrev:
                continue

            # Expand abbreviation
            subject_full = SUBJECT_MAP.get(subject_abbrev, subject_abbrev)
            if subject_full is None:
                continue  # LUNCH — skip

            # Resolve faculty for this subject + section
            faculty_id = (subject_faculty.get((subject_abbrev, section))
                          or subject_faculty.get((subject_full, section))
                          or "TBD")

            # Period times
            times = PERIOD_TIMES.get(period)
            if not times:
                continue
            start_time, end_time = times

            doc_ref = db.collection("timetable").document()
            fs_batch.set(doc_ref, {
                "facultyId": faculty_id,
                "classId":   class_id,
                "subject":   subject_full,
                "day":       day,
                "startTime": start_time,
                "endTime":   end_time,
                "section":   section,
                "department": DEPARTMENT,
            })
            count += 1

            if count % 499 == 0:
                fs_batch.commit()
                fs_batch = db.batch()

    if count > 0:
        fs_batch.commit()
    return count


def main():
    print("🔥 Initialising Firebase…")
    db = init_firebase()

    print("\n📋 Loading configuration…")
    rooms           = load_rooms()
    subject_faculty = load_subject_faculty_map()

    print("\n👥 Loading students by section from Firestore…")
    students_by_section = get_students_by_section(db, DEPARTMENT)
    for sec, ids in sorted(students_by_section.items()):
        print(f"  Section {sec}: {len(ids)} students")

    print("\n🏛️  Creating classes/ documents…")
    class_ids = create_classes(db, DEPARTMENT, BATCH, rooms, students_by_section)

    print("\n📅 Importing timetables…")
    csv_files = sorted(f for f in os.listdir(TIMETABLE_FOLDER)
                       if f.upper().startswith(f"{DEPARTMENT}-") and f.endswith(".csv"))

    total = 0
    for filename in csv_files:
        # e.g. CSE-A.csv → section = "A"
        m = re.search(r'-([A-Z])\.csv$', filename, re.IGNORECASE)
        if not m:
            continue
        section  = m.group(1).upper()
        class_id = class_ids.get(section)
        if not class_id:
            print(f"  ⚠️  No class found for section {section}, skipping {filename}")
            continue

        path = os.path.join(TIMETABLE_FOLDER, filename)
        print(f"\n  📂 {filename} → {class_id}")
        count = import_section_timetable(db, path, section, class_id, subject_faculty)
        print(f"     ✅ {count} timetable entries written")
        total += count

    print(f"\n🎉 Done! Total timetable entries: {total}")
    print(f"   Classes created: {len(class_ids)}")


if __name__ == "__main__":
    main()
