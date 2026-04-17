"""
AttendEase — Bulk Student Import Script
========================================
Handles the ANITS CSV format:
  Row 1  → Branch title  e.g. "CHEMICAL,,,,"   (used as department if filename doesn't override)
  Row 2  → Headers       e.g. "S.NO.,PERMANENT ROLL NO.,NAME OF THE STUDENT,,Email ID"
  Row 3+ → Data rows     (name column is ALL CAPS — converted to Title Case)

CSV columns (5 total):
  0: S.NO.
  1: PERMANENT ROLL NO.
  2: NAME OF THE STUDENT   (ALL CAPS)
  3: <lowercase name — ignored>
  4: Email ID

Requirements:
    pip install firebase-admin

Usage:
    1. Place all branch CSVs in the folder set in CSV_FOLDER (one file per branch).
    2. Name each file after its branch, e.g.  CSE.csv  ECE.csv  CHEMICAL.csv
    3. Put your Firebase service account key JSON next to this script.
    4. Edit CONFIG below, then run:
          python import_students.py
"""

import csv
import os
import uuid
import firebase_admin
from firebase_admin import credentials, firestore

# ─────────────────────────────────────────────
# CONFIG — edit before running
# ─────────────────────────────────────────────
SERVICE_ACCOUNT_JSON = "serviceAccountKey.json"  # Firebase service account key path
CSV_FOLDER           = "./students"              # folder with all branch CSV files
BATCH_YEAR           = "2024-28"                 # your batch year e.g. "2024-28"
ROLE                 = "student"
# ─────────────────────────────────────────────

# Exact column headers in row 2 of the CSV
COL_SNO   = "S.NO."
COL_ROLL  = "PERMANENT ROLL NO."
COL_NAME  = "NAME OF THE STUDENT"
COL_EMAIL = "Email ID"


def init_firebase():
    cred = credentials.Certificate(SERVICE_ACCOUNT_JSON)
    firebase_admin.initialize_app(cred)
    return firestore.client()


def title_case(name: str) -> str:
    """Convert ALLCAPS name to Title Case, e.g. ALLATEJASWANI → Allatejaswani."""
    return name.strip().title()


def import_branch(db, csv_path: str, dept_from_filename: str) -> int:
    """
    Reads one branch CSV and batch-uploads students to Firestore.
    Returns the number of students uploaded.
    """
    fs_batch = db.batch()
    count = 0
    department = dept_from_filename  # default; overridden by row-1 title if readable

    with open(csv_path, newline='', encoding='utf-8-sig') as f:
        raw = list(csv.reader(f))

    if len(raw) < 2:
        print(f"  ⚠️  File is empty or too short: {csv_path}")
        return 0

    # Row 0: branch title row — SKIP IT, always use filename as department
    # (the title row may contain section letter "A" or other non-dept text)

    # Row 1: actual headers
    headers = [h.strip() for h in raw[1]]

    # Find column indices
    try:
        idx_roll  = headers.index(COL_ROLL)
        idx_name  = headers.index(COL_NAME)
        idx_email = headers.index(COL_EMAIL)
    except ValueError as e:
        print(f"  ⚠️  Missing column: {e}")
        print(f"       Headers found: {headers}")
        print(f"       Expected: '{COL_ROLL}', '{COL_NAME}', '{COL_EMAIL}'")
        return 0

    # Rows 2+: data
    section_index = 0          # 0=A, 1=B, 2=C …
    prev_sno      = 0          # track to detect resets

    for row in raw[2:]:
        if not row or not any(cell.strip() for cell in row):
            continue  # skip blank rows

        # S.NO. column (index 0 in most files)
        try:
            idx_sno = headers.index(COL_SNO)
            sno_val = int(row[idx_sno].strip())
        except (ValueError, IndexError):
            sno_val = prev_sno + 1  # fallback: just increment

        # Detect section reset: SNO went back to a lower value (e.g. 60→1)
        if sno_val <= prev_sno and prev_sno > 0:
            section_index += 1
        prev_sno = sno_val

        section = chr(ord('A') + section_index)  # 0→A, 1→B, 2→C …

        roll  = row[idx_roll].strip()  if len(row) > idx_roll  else ""
        name  = row[idx_name].strip()  if len(row) > idx_name  else ""
        email = row[idx_email].strip().lower() if len(row) > idx_email else ""

        if not email or not name:
            print(f"  ⚠️  Skipping row (missing name/email): {row}")
            continue

        name = title_case(name)  # "ALLATEJASWANI" → "Allatejaswani"

        doc_id  = roll if roll else str(uuid.uuid4())
        doc_ref = db.collection("users").document(doc_id)

        fs_batch.set(doc_ref, {
            "uid":         doc_id,
            "name":        name,
            "email":       email,
            "role":        ROLE,
            "department":  department,
            "batch":       BATCH_YEAR,
            "rollNo":      roll,
            "section":     section,          # ← NEW: "A", "B", "C" …
            "passwordSet": False,
        })
        count += 1

        # Firestore batch limit = 500 writes; commit and start fresh
        if count % 499 == 0:
            fs_batch.commit()
            fs_batch = db.batch()
            print(f"  ↳ Committed 499 records…")

    if count > 0:
        fs_batch.commit()

    return count


def main():
    print("🔥 Initialising Firebase…")
    db = init_firebase()

    csv_files = sorted(f for f in os.listdir(CSV_FOLDER) if f.lower().endswith(".csv"))
    if not csv_files:
        print(f"❌ No CSV files found in '{CSV_FOLDER}'.")
        return

    total = 0
    for filename in csv_files:
        dept_from_filename = os.path.splitext(filename)[0].upper()
        path = os.path.join(CSV_FOLDER, filename)
        print(f"\n📂 {filename}")
        count = import_branch(db, path, dept_from_filename)
        print(f"   ✅ {count} students uploaded")
        total += count

    print(f"\n🎉 Done! Total students imported: {total}")


if __name__ == "__main__":
    main()
