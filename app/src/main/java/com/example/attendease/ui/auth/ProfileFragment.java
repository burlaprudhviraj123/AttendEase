package com.example.attendease.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.attendease.R;
import com.example.attendease.databinding.FragmentProfileBinding;
import com.example.attendease.model.User;
import com.example.attendease.viewmodel.AuthViewModel;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthViewModel authViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        setupUI();
        setupClickListeners();
    }

    private void setupUI() {
        User user = authViewModel.getCurrentUserProfile().getValue();
        if (user == null) return;

        String initials = user.getName().length() >= 2
                ? user.getName().substring(0, 2).toUpperCase()
                : user.getName().toUpperCase();
        binding.avatarText.setText(initials);
        binding.profileNameText.setText(user.getName());
        binding.profileEmailText.setText(user.getEmail());
        binding.profileRoleBadge.setText(user.getRole().toUpperCase());
        binding.profileDeptText.setText(user.getDepartment());
        if (user.isStudent() && user.getBatch() != null) {
            binding.profileBatchText.setVisibility(View.VISIBLE);
            binding.profileBatchText.setText("Batch " + user.getBatch());
        } else {
            binding.profileBatchText.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        // "Change Password" row → opens bottom sheet
        binding.changePasswordRow.setOnClickListener(v -> {
            new ChangePasswordBottomSheet()
                    .show(getChildFragmentManager(), "changePassword");
        });

        // "Sign Out" row → logout + navigate to login
        binding.logoutButton.setOnClickListener(v -> {
            authViewModel.logout();
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_profileFragment_to_loginFragment);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
