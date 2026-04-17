import firebase_admin
from firebase_admin import credentials, auth, firestore
import sys

def create_secured_test_account(email, password):
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
        
    db = firestore.client()
    
    # 1. Check if institutional record exists
    docs = db.collection("users").where("email", "==", email).get()
    if not docs:
        print(f"❌ Error: No institutional record found in Firestore for '{email}'")
        return
        
    doc = docs[0]
    
    # 2. Create the secure backend Auth account
    try:
        user = auth.create_user(
            email=email,
            password=password,
            email_verified=True,
            display_name=doc.get("displayName")
        )
        print(f"✅ SUCCESS! Created secure test credentials for {email}")
        print(f"   Password: {password}")
        
        # 3. Mark the Firestore doc to accept the login
        doc.reference.update({"passwordSet": True})
        print("   Database synced. You can now log into the app manually.")
        
    except auth.EmailAlreadyExistsError:
        print(f"⚠️ Account already exists. Updating password instead...")
        user = auth.get_user_by_email(email)
        auth.update_user(user.uid, password=password)
        doc.reference.update({"passwordSet": True})
        print(f"✅ SUCCESS! Password updated to {password} for testing.")
    except Exception as e:
        print(f"❌ Firebase Auth Error: {e}")

if __name__ == "__main__":
    print("🛡️ AttendEase Secure Admin Testing Tool 🛡️")
    target_email = input("Enter Faculty/Student Email to test: ").strip()
    target_password = input("Enter temporary password (min 6 chars): ").strip()
    
    if len(target_password) < 6:
        print("Password must be at least 6 characters.")
    else:
        create_secured_test_account(target_email, target_password)
