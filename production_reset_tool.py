import firebase_admin
from firebase_admin import credentials, firestore

def prepare_for_production():
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)
    db = firestore.client()

    print("🚀 Preparing AttendEase for Fresh Production Launch...")
    print("Resetting all test security flags in the 'users' collection...")

    users_ref = db.collection("users").get()
    batch = db.batch()
    count = 0

    for doc in users_ref:
        doc_data = doc.to_dict()
        if doc_data.get("passwordSet") == True:
            # Revert the test flag back to false
            batch.update(doc.reference, {"passwordSet": False})
            count += 1
            
            if count >= 400:
                batch.commit()
                batch = db.batch()
                count = 0

    if count > 0:
        batch.commit()

    print("✅ All user accounts have been locked & reset!")
    print("Every single person will now be treated as a Brand New user and must verify via Google Sign-In.")

if __name__ == "__main__":
    prepare_for_production()
