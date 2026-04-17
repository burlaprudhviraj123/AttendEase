package com.example.attendease.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.attendease.databinding.BottomSheetChangePasswordBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetChangePasswordBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetChangePasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.updatePasswordButton.setOnClickListener(v -> {
            String current = binding.currentPasswordEditText.getText().toString().trim();
            String newPass = binding.newPasswordEditText.getText().toString().trim();
            String confirm = binding.confirmPasswordEditText.getText().toString().trim();

            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPass.equals(confirm)) {
                binding.confirmPasswordEditText.setError("Passwords do not match");
                return;
            }
            if (newPass.length() < 8) {
                binding.newPasswordEditText.setError("Minimum 8 characters");
                return;
            }
            changePassword(current, newPass);
        });
    }

    private void changePassword(String currentPassword, String newPassword) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        binding.updatePasswordButton.setEnabled(false);
        binding.updatePasswordButton.setText("Updating…");

        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), currentPassword))
                .addOnCompleteListener(authTask -> {
                    if (authTask.isSuccessful()) {
                        user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                            binding.updatePasswordButton.setEnabled(true);
                            binding.updatePasswordButton.setText("Update Password");
                            if (updateTask.isSuccessful()) {
                                Toast.makeText(getContext(), "Password updated ✓", Toast.LENGTH_SHORT).show();
                                dismiss();
                            } else {
                                String msg = updateTask.getException() != null
                                        ? updateTask.getException().getMessage() : "Update failed";
                                Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        binding.updatePasswordButton.setEnabled(true);
                        binding.updatePasswordButton.setText("Update Password");
                        binding.currentPasswordEditText.setError("Incorrect current password");
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
