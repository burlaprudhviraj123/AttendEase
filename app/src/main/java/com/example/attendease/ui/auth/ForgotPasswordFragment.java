package com.example.attendease.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.attendease.databinding.FragmentForgotPasswordBinding;
import com.example.attendease.viewmodel.AuthViewModel;

public class ForgotPasswordFragment extends Fragment {

    private FragmentForgotPasswordBinding binding;
    private AuthViewModel authViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupObservers();
        setupClickListeners();
    }

    private void setupObservers() {
        authViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.sendResetButton.setEnabled(!isLoading);
        });

        authViewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        authViewModel.getResetEmailSent().observe(getViewLifecycleOwner(), sent -> {
            if (sent != null && sent) {
                // Success: Transition to State 2 (Check your inbox)
                binding.state1.setVisibility(View.GONE);
                binding.state2.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupClickListeners() {
        binding.sendResetButton.setOnClickListener(v -> {
            String email = binding.emailEditText.getText().toString().trim();
            
            if (TextUtils.isEmpty(email)) {
                binding.emailLayout.setError("Email is required");
                return;
            }

            if (!authViewModel.validateEmailPattern(email)) {
                binding.emailLayout.setError("Please enter a valid @anits.edu.in email");
                return;
            }

            authViewModel.sendResetEmail(email);
        });

        binding.backToLogin.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.backToLoginBtn.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
