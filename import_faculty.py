"""
AttendEase — Faculty Import Script
====================================
Reads all .csv files in the faculty/ folder (one per department)
and uploads faculty records to Firebase Firestore.

CSV format (faculty/CSE.csv example):
    Name,Designation,Email,Phone
    Prof. Srinivas Gorla,HOD & Dean,gsrinivas.cse@anits.edu.in,9963199200
    Dr. R.V.V. Murali Krishna,Professor,rvvmuralikrishna.cse@anits.edu.in,-

Requirements:
    pip3 install firebase-admin

Usage:
    python3 import_faculty.py
"""

import csv
import os
import firebase_admin
from firebase_admin import credentials, firestore

# ─────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────
SERVICE_ACCOUNT_JSON = "serviceAccountKey.json"
FACULTY_FOLDER       = "./faculty"
ROLE                 = "faculty"
# ─────────────────────────────────────────────

# Exact column headers
COL_NAME        = "Name"
COL_DESIGNATION = "Designation"
COL_EMAIL       = "Email"


def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate(SERVICE_ACCOUNT_JSON)
        firebase_admin.initialize_app(cred)
    return firestore.client()


def clean_name(name: str) -> str:
    """Strip honorifics like Prof., Dr., Mr., Mrs., Ms. for a cleaner display name."""
    prefixes = ("Prof. ", "Dr. ", "Mr. ", "Mrs. ", "Ms. ")
    for prefix in prefixes:
        if name.startswith(prefix):
            return name[len(prefix):]
    return name


def import_department(db, csv_path: str, department: str) -> int:
    fs_batch = db.batch()
    count    = 0

    with open(csv_path, newline='', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)

        # Validate headers
        headers = reader.fieldnames or []
        for col in [COL_NAME, COL_EMAIL]:
            if col not in headers:
                print(f"  ⚠️  Column '{col}' not found. Headers: {headers}")
                return 0

        for row in reader:
            name        = row[COL_NAME].strip()
            designation = row.get(COL_DESIGNATION, "").strip()
            email       = row[COL_EMAIL].strip().lower()

            if not email or not name or "@" not in email:
                print(f"  ⚠️  Skipping invalid row: {row}")
                continue

            # Use email prefix as the document ID (unique + readable)
            doc_id  = email.split("@")[0]
            doc_ref = db.collection("users").document(doc_id)

            fs_batch.set(doc_ref, {
                "uid":         doc_id,
                "name":        name,          # full name with title e.g. "Dr. Rohini A"
                "displayName": clean_name(name),  # without prefix e.g. "Rohini A"
                "email":       email,
                "role":        ROLE,
                "department":  department,
                "designation": designation,   # e.g. "HOD & Dean", "Professor", "Assistant Professor"
                "passwordSet": False,         # set to True after first login
            })
            count += 1

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

    csv_files = sorted(f for f in os.listdir(FACULTY_FOLDER) if f.lower().endswith(".csv"))
    if not csv_files:
        print(f"❌ No CSV files found in '{FACULTY_FOLDER}'.")
        return

    total = 0
    for filename in csv_files:
        department = os.path.splitext(filename)[0].upper()  # CSE.csv → "CSE"
        path = os.path.join(FACULTY_FOLDER, filename)
        print(f"\n📂 {filename} → department: {department}")
        count = import_department(db, path, department)
        print(f"   ✅ {count} faculty uploaded")
        total += count

    print(f"\n🎉 Done! Total faculty imported: {total}")


if __name__ == "__main__":
    main()
