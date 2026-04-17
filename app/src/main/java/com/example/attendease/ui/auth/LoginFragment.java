package com.example.attendease.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.attendease.R;
import com.example.attendease.databinding.FragmentLoginBinding;
import com.example.attendease.utils.NetworkUtils;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.utils.Constants;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executors;

public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";
    private FragmentLoginBinding binding;
    private AuthViewModel authViewModel;
    private CredentialManager credentialManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            setUiVisibility(View.INVISIBLE);
        }

        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        credentialManager = CredentialManager.create(requireContext());

        setupObservers();
        setupClickListeners();
        
        // Restore existing session
        authViewModel.checkExistingSession();
    }

    private void setUiVisibility(int visibility) {
        if (binding == null) return;
        if (binding.logo != null) binding.logo.setVisibility(visibility);
        if (binding.title != null) binding.title.setVisibility(visibility);
        if (binding.subtitle != null) binding.subtitle.setVisibility(visibility);
        if (binding.emailLayout != null) binding.emailLayout.setVisibility(visibility);
        if (binding.loginButton != null) binding.loginButton.setVisibility(visibility);
        if (binding.googleLoginButton != null) binding.googleLoginButton.setVisibility(visibility);
        if (binding.divider != null) binding.divider.setVisibility(visibility);
    }

    private void setupObservers() {
        authViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.loginButton.setEnabled(!isLoading);
            binding.googleLoginButton.setEnabled(!isLoading);
            
            if (!isLoading && !Boolean.TRUE.equals(authViewModel.getLoginSuccess().getValue())) {
                setUiVisibility(View.VISIBLE);
            }
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setIcon(R.drawable.ic_lock)
                    .setTitle("Authentication Error")
                    .setMessage(error)
                    .setPositiveButton("Dismiss", (dialog, which) -> {
                         authViewModel.resetAuthStates();
                         dialog.dismiss();
                    })
                    .show();
            }
        });

        authViewModel.getIsNewUser().observe(getViewLifecycleOwner(), isNew -> {
            if (isNew != null) {
                if (isNew) {
                    Bundle bundle = new Bundle();
                    bundle.putString("email", binding.emailEditText.getText().toString());
                    authViewModel.resetAuthStates();
                    Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_setPasswordFragment, bundle);
                } else {
                    binding.passwordLayout.setVisibility(View.VISIBLE);
                    binding.forgotPasswordText.setVisibility(View.VISIBLE);
                    binding.loginButton.setText("Login to AttendEase");
                }
            }
        });

        authViewModel.getLoginSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                authViewModel.resetAuthStates();
                navigateToHome();
            }
        });
        
        authViewModel.getRegistrationSuccess().observe(getViewLifecycleOwner(), success -> {
             if (success != null && success) {
                 authViewModel.resetAuthStates();
                 navigateToHome();
             }
        });
    }

    private void setupClickListeners() {
        binding.passwordLayout.setVisibility(View.GONE);
        binding.loginButton.setText("Continue");

        binding.loginButton.setOnClickListener(v -> {
            String email = binding.emailEditText.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                binding.emailLayout.setError("Email is required");
                return;
            }

            if (binding.passwordLayout.getVisibility() == View.GONE) {
                authViewModel.checkEmail(email);
            } else {
                String password = binding.passwordEditText.getText().toString().trim();
                if (TextUtils.isEmpty(password)) {
                    binding.passwordLayout.setError("Password is required");
                    return;
                }
                authViewModel.login(email, password);
            }
        });

        binding.forgotPasswordText.setOnClickListener(v -> 
            Navigation.findNavController(v).navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        );

        binding.googleLoginButton.setOnClickListener(v -> signInWithGoogle());
    }

    private void signInWithGoogle() {
        if (!NetworkUtils.isInternetAvailable(requireContext())) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_lock) // No signal icon would be better but lock is there
                .setTitle("Login Failed")
                .setMessage("No internet connection was detected. Google Sign-In requires an active network connection to verify your identity.")
                .setPositiveButton("Check Settings", (dialog, which) -> {
                     startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                })
                .setNegativeButton("OK", null)
                .show();
            return;
        }

        String webClientId = getString(R.string.default_web_client_id);
        if (webClientId.equals("YOUR_WEB_CLIENT_ID_HERE")) {
            Toast.makeText(getContext(), "Please configure Web Client ID in strings.xml", Toast.LENGTH_LONG).show();
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                requireContext(),
                request,
                null,
                Executors.newSingleThreadExecutor(),
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignIn(result.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "Credential Manager error: " + e.getMessage(), e);
                        requireActivity().runOnUiThread(() -> {
                            if (e instanceof NoCredentialException) {
                                showAddAccountDialog();
                            } else {
                                Toast.makeText(getContext(), "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
    }

    private void showAddAccountDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("No Google Account Found")
                .setMessage("No Google accounts were found on this device. Would you like to add one now?")
                .setPositiveButton("Add Account", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                    intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"});
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleSignIn(Credential credential) {
        if (credential instanceof CustomCredential && 
            credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                requireActivity().runOnUiThread(() -> 
                    authViewModel.signInWithGoogle(googleIdTokenCredential.getIdToken())
                );
            } catch (Exception e) {
                Log.e(TAG, "Error parsing Google ID token", e);
            }
        } else {
            Log.e(TAG, "Unexpected credential type: " + credential.getType());
        }
    }

    private void navigateToHome() {
        if (authViewModel.getCurrentUserProfile().getValue() != null) {
            String role = authViewModel.getCurrentUserProfile().getValue().getRole();
            if (Constants.ROLE_STUDENT.equals(role)) {
                Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_studentHomeFragment);
            } else if (Constants.ROLE_FACULTY.equals(role)) {
                Navigation.findNavController(requireView()).navigate(R.id.action_loginFragment_to_facultyHomeFragment);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
