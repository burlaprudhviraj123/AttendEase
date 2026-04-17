package com.example.attendease.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.attendease.model.User;
import com.example.attendease.repository.AuthRepository;
import com.example.attendease.utils.Constants;

import java.util.List;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository;
    
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<User> currentUserProfile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNewUser = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resetEmailSent = new MutableLiveData<>();

    public AuthViewModel() {
        this.authRepository = new AuthRepository();
    }

    public void checkExistingSession() {
        com.google.firebase.auth.FirebaseUser user = authRepository.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            isLoading.setValue(true);
            fetchUserProfileByEmail(user.getEmail());
        }
    }


    public void checkEmail(String email) {
        if (!validateEmailPattern(email)) {
            errorMessage.setValue("Unauthorized Identity\n\nPlease sign in using your official administrative email address assigned by the institution.");
            return;
        }

        isLoading.setValue(true);
        // Check Firestore. If user is found and passwordSet is false, route to Set Password.
        // If passwordSet is true, show password field.
        // If not found, route to Set Password (brand new user).
        authRepository.getUserByEmail(email).addOnCompleteListener(task -> {
            isLoading.setValue(false);
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                // Found in Firestore
                com.google.firebase.firestore.DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                Boolean passwordSet = doc.getBoolean("passwordSet");
                if (passwordSet != null && passwordSet) {
                    Log.d("AuthViewModel", "checkEmail: found in Firestore, password IS set → existing user");
                    isNewUser.setValue(false);
                } else {
                    Log.d("AuthViewModel", "checkEmail: found in Firestore, password NOT set → provisioning via email link");
                    // 1. Provision a secure temporary backend identity
                    String tempIdentity = java.util.UUID.randomUUID().toString() + "A#1z";
                    authRepository.register(email, tempIdentity).addOnCompleteListener(regTask -> {
                        // 2. Dispatch the official configuration link directly to their inbox
                        authRepository.sendPasswordResetEmail(email).addOnCompleteListener(resetTask -> {
                            if (resetTask.isSuccessful()) {
                                // 3. Mark as provisioned so future attempts show the password field
                                doc.getReference().update("passwordSet", true);
                                errorMessage.setValue("Account Provisioned\n\nA secure password configuration link has been sent to your institutional inbox. Please open the email, set your preferred password, and then return here to log in.");
                            } else {
                                errorMessage.setValue("Error dispatching secure link. Please try again or use Google Sign-In.");
                            }
                        });
                    });
                }
            } else {
                // Not found in the pre-imported Firestore database
                Log.d("AuthViewModel", "checkEmail: not in Firestore → Denying access");
                errorMessage.setValue("Institutional Access Denied\n\nYour credentials were not found in the administrative registry. Access to academic records is restricted. For clarification, contact the administrative office.");
            }
        });
    }

    public void login(String email, String password) {
        isLoading.setValue(true);
        authRepository.login(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                fetchUserProfileByEmail(email);
            } else {
                isLoading.setValue(false);
                String error = task.getException() != null ? task.getException().getMessage() : "Login failed";
                // Firebase error when the email has NO account at all
                if (error != null && (error.contains("no user record") ||
                        error.contains("user may have been deleted") ||
                        error.contains("There is no user record"))) {
                    // This implies they bypassed checkEmail or their account was deleted manually
                    errorMessage.setValue("Account Configuration Missing\n\nYour profile requires initialization. Please restart the login process or simply use 'Continue with Google'.");
                } else {
                    errorMessage.setValue(error);
                }
            }
        });
    }

    public void signInWithGoogle(String idToken) {
        isLoading.setValue(true);
        authRepository.signInWithGoogle(idToken).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().getUser() != null) {
                String googleUid = task.getResult().getUser().getUid();
                String email = task.getResult().getUser().getEmail();
                
                authRepository.getUserByEmail(email).addOnCompleteListener(profileTask -> {
                    if (profileTask.isSuccessful() && profileTask.getResult() != null && !profileTask.getResult().isEmpty()) {
                        // User exists in our system (imported or already registered)
                        com.google.firebase.firestore.DocumentSnapshot doc = profileTask.getResult().getDocuments().get(0);
                        User user = doc.toObject(User.class);
                        
                        if (user != null) {
                            user.setInstitutionalId(doc.getId());
                            // Ensure the UID is aligned with the Google UID
                            user.setUid(googleUid);
                            user.setPasswordSet(true);
                            
                            // 🔑 CRITICAL: Move/Sync the data to the document indexed by the new Google UID
                            authRepository.createUserProfile(user).addOnCompleteListener(syncTask -> {
                                currentUserProfile.setValue(user);
                                isLoading.setValue(false);
                                loginSuccess.setValue(true);
                            });
                        }
                    } else {
                        // Brand new user via Google, but NOT in institutional records
                        Log.d("AuthViewModel", "signInWithGoogle: Denying access to unknown user");
                        isLoading.setValue(false);
                        errorMessage.setValue("Restricted System Access\n\nThis system is exclusive to registered academic personnel. Your current identity is not recognized in our secure administrative database.");
                        authRepository.logout();
                    }
                });
            } else {
                isLoading.setValue(false);
                String error = task.getException() != null ? task.getException().getMessage() : "Google Sign-In failed";
                errorMessage.setValue(error);
            }
        });
    }

    public void register(String email, String password) {
        isLoading.setValue(true);
        // Find them in the pre-imported database first!
        authRepository.getUserByEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                com.google.firebase.firestore.DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                User existingUser = doc.toObject(User.class);

                authRepository.register(email, password).addOnCompleteListener(regTask -> {
                    if (regTask.isSuccessful() && regTask.getResult() != null && regTask.getResult().getUser() != null) {
                        // DO NOT overwrite existingUser.setUid(...) -> we need to keep the imported ID!
                        existingUser.setPasswordSet(true);
                        
                        // Only update passwordSet — do NOT .set() which wipes rollNo/section/batch
                        doc.getReference().update("passwordSet", true).addOnCompleteListener(saveTask -> {
                            isLoading.setValue(false);
                            if (saveTask.isSuccessful()) {
                                existingUser.setInstitutionalId(doc.getId());
                                currentUserProfile.setValue(existingUser);
                                registrationSuccess.setValue(true);
                            } else {
                                errorMessage.setValue("Failed to update profile data");
                            }
                        });
                    } else {
                        isLoading.setValue(false);
                        errorMessage.setValue(regTask.getException() != null ? regTask.getException().getMessage() : "Registration failed");
                    }
                });
            } else {
                // User is not in our system at all
                isLoading.setValue(false);
                errorMessage.setValue("Registration failed: Account records not found.");
            }
        });
    }

    private void saveUserProfile(User user, boolean isLogin) {
        authRepository.createUserProfile(user).addOnCompleteListener(task -> {
            isLoading.setValue(false);
            if (task.isSuccessful()) {
                currentUserProfile.setValue(user);
                if (isLogin) {
                    loginSuccess.setValue(true);
                } else {
                    registrationSuccess.setValue(true);
                }
            } else {
                String error = task.getException() != null ? task.getException().getMessage() : "Failed to save profile";
                errorMessage.setValue(error);
            }
        });
    }

    private void fetchUserProfileByEmail(String email) {
        authRepository.getUserByEmail(email).addOnCompleteListener(task -> {
            isLoading.setValue(false);
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                com.google.firebase.firestore.DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                User user = doc.toObject(User.class);
                if (user != null) {
                    user.setInstitutionalId(doc.getId());
                    currentUserProfile.setValue(user);
                    loginSuccess.setValue(true);
                }
            } else {
                errorMessage.setValue("User profile not found in database");
            }
        });
    }

    public void sendResetEmail(String email) {
        isLoading.setValue(true);
        authRepository.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            isLoading.setValue(false);
            if (task.isSuccessful()) {
                resetEmailSent.setValue(true);
            } else {
                String error = task.getException() != null ? task.getException().getMessage() : "Failed to send reset email";
                errorMessage.setValue(error);
            }
        });
    }

    public boolean validateEmailPattern(String email) {
        if (email == null || !email.endsWith("@" + Constants.ALLOWED_DOMAIN)) {
            return false;
        }
        String prefix = email.split("@")[0];
        String[] parts = prefix.split("\\.");
        
        // Student: name.YY.dept (3 parts)
        // Faculty: name.dept (2 parts)
        return parts.length == 2 || parts.length == 3;
    }

    public User parseUserFromEmail(String email) {
        if (!validateEmailPattern(email)) return null;

        String prefix = email.split("@")[0];
        String[] parts = prefix.split("\\.");
        
        String name = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1);
        
        if (parts.length == 3) {
            // Student: name.YY.dept
            String batch = "20" + parts[1];
            String dept = parts[2].toUpperCase();
            return new User(null, name, email, Constants.ROLE_STUDENT, dept, batch, null, null);
        } else {
            // Faculty: name.dept
            String dept = parts[1].toUpperCase();
            return new User(null, name, email, Constants.ROLE_FACULTY, dept);
        }
    }

    public void resetAuthStates() {
        isNewUser.setValue(null);
        loginSuccess.setValue(null);
        registrationSuccess.setValue(null);
        resetEmailSent.setValue(null);
        errorMessage.setValue(null);
    }

    public void logout() {
        authRepository.logout();
    }

    // LiveData Getters
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getIsNewUser() { return isNewUser; }
    public LiveData<Boolean> getLoginSuccess() { return loginSuccess; }
    public LiveData<Boolean> getRegistrationSuccess() { return registrationSuccess; }
    public LiveData<User> getCurrentUserProfile() { return currentUserProfile; }
    public LiveData<Boolean> getResetEmailSent() { return resetEmailSent; }
}
