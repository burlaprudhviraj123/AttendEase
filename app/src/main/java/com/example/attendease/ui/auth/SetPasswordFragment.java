package com.example.attendease.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.attendease.R;
import com.example.attendease.databinding.FragmentSetPasswordBinding;
import com.example.attendease.model.User;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.utils.Constants;

public class SetPasswordFragment extends Fragment {

    private FragmentSetPasswordBinding binding;
    private AuthViewModel authViewModel;
    private String email;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSetPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        if (getArguments() != null) {
            email = getArguments().getString("email");
            displayUserInfo(email);
        }

        setupObservers();
        setupClickListeners();
        setupPasswordValidation();
    }

    private void displayUserInfo(String email) {
        User tempUser = authViewModel.parseUserFromEmail(email);
        if (tempUser != null) {
            binding.title.setText("Welcome, " + tempUser.getName());
            binding.securityProtocolTag.setText(tempUser.getRole().toUpperCase() + " • " + tempUser.getDepartment());
            
            if (tempUser.isStudent()) {
                binding.subtitle.setText("Set a password to secure your academic records for " + tempUser.getDepartment() + " Batch " + tempUser.getBatch());
            } else {
                binding.subtitle.setText("Establish a unique password for your Faculty account in the " + tempUser.getDepartment() + " department.");
            }
        }
    }

    private void setupObservers() {
        authViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.setPasswordButton.setEnabled(!isLoading);
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        authViewModel.getRegistrationSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                authViewModel.resetAuthStates();
                Toast.makeText(getContext(), "Account created successfully!", Toast.LENGTH_SHORT).show();
                navigateToHome();
            }
        });
    }

    private void setupClickListeners() {
        binding.backButton.setOnClickListener(v -> {
            Navigation.findNavController(v).navigateUp();
        });

        binding.setPasswordButton.setOnClickListener(v -> {
            String password = binding.newPasswordEditText.getText().toString().trim();
            String confirmPassword = binding.confirmPasswordEditText.getText().toString().trim();

            if (validatePasswords(password, confirmPassword)) {
                authViewModel.register(email, password);
            }
        });
    }

    private void setupPasswordValidation() {
        binding.newPasswordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                binding.strengthProgress.setProgress(Math.min(length * 10, 100));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private boolean validatePasswords(String p1, String p2) {
        if (p1.length() < 8) {
            binding.newPasswordLayout.setError("Password must be at least 8 characters");
            return false;
        }
        if (!p1.equals(p2)) {
            binding.confirmPasswordLayout.setError("Passwords do not match");
            return false;
        }
        return true;
    }

    private void navigateToHome() {
        if (authViewModel.getCurrentUserProfile().getValue() != null) {
            String role = authViewModel.getCurrentUserProfile().getValue().getRole();
            if (Constants.ROLE_STUDENT.equals(role)) {
                Navigation.findNavController(requireView()).navigate(R.id.action_setPasswordFragment_to_studentHomeFragment);
            } else if (Constants.ROLE_FACULTY.equals(role)) {
                Navigation.findNavController(requireView()).navigate(R.id.action_setPasswordFragment_to_facultyHomeFragment);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
