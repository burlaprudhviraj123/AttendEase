package com.example.attendease.ui.faculty;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.attendease.R;
import com.example.attendease.databinding.FragmentFacultyHomeBinding;
import com.example.attendease.model.SessionState;
import com.example.attendease.model.TimetableSession;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.viewmodel.FacultyViewModel;

import java.util.ArrayList;
import java.util.List;

public class FacultyHomeFragment extends Fragment {

    private FragmentFacultyHomeBinding binding;
    private AuthViewModel authViewModel;
    private FacultyViewModel facultyViewModel;
    private SessionCardAdapter sessionAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFacultyHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        facultyViewModel = new ViewModelProvider(requireActivity()).get(FacultyViewModel.class);

        setupRecyclerView();
        setupObservers();
        setupClickListeners();
    }

    private void setupRecyclerView() {
        sessionAdapter = new SessionCardAdapter(new ArrayList<>(), session -> navigateToMarkAttendance(session));
        binding.scheduleRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.scheduleRecyclerView.setAdapter(sessionAdapter);
        binding.scheduleRecyclerView.setNestedScrollingEnabled(false);
    }

    private void setupClickListeners() {
        // My Classes button
        if (binding.myClassesButton != null) {
            binding.myClassesButton.setOnClickListener(v -> {
                NavController controller = Navigation.findNavController(v);
                if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.facultyHomeFragment) {
                   controller.navigate(R.id.action_facultyHomeFragment_to_myClassesFragment);
                }
            });
        }
        // Analytics button
        if (binding.analyticsButton != null) {
            binding.analyticsButton.setOnClickListener(v -> {
                NavController controller = Navigation.findNavController(v);
                if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.facultyHomeFragment) {
                    controller.navigate(R.id.action_facultyHomeFragment_to_analyticsDashboardFragment);
                }
            });
        }
        // Profile button
        if (binding.profileButton != null) {
            binding.profileButton.setOnClickListener(v -> {
                NavController controller = Navigation.findNavController(v);
                if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.facultyHomeFragment) {
                    controller.navigate(R.id.action_facultyHomeFragment_to_profileFragment);
                }
            });
        }
    }

    private void setupObservers() {
        authViewModel.getCurrentUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                binding.facultyNameText.setText(user.getName());
                // Load data: Firestore automatically uses cache if offline
                if (facultyViewModel.getTodaysSessions().getValue() == null) {
                    facultyViewModel.loadTodaysSessions(user.getInstitutionalId());
                }
            }
        });

        facultyViewModel.getTodaysSessions().observe(getViewLifecycleOwner(), sessions -> {
            if (sessions != null) {
                sessionAdapter.updateSessions(sessions);
                updateHeroCard(sessions);
            }
        });

        facultyViewModel.getTotalClasses().observe(getViewLifecycleOwner(), count -> {
            if (binding.totalClassesValue != null)
                binding.totalClassesValue.setText(String.valueOf(count));
        });

        facultyViewModel.getAvgAttendance().observe(getViewLifecycleOwner(), pct -> {
            if (binding.avgAttendanceValue != null)
                binding.avgAttendanceValue.setText(pct + "%");
        });
    }

    private void updateHeroCard(List<TimetableSession> sessions) {
        for (TimetableSession s : sessions) {
            if (s.getState() == SessionState.ACTIVE) {
                binding.currentSessionTitle.setText(s.getSubject());
                binding.currentSessionInfo.setText(s.getClassName() + " · " +
                        s.getStartTime() + " – " + s.getEndTime() +
                        ". Ready to log student presence?");
                binding.markAttendanceButton.setVisibility(View.VISIBLE);
                binding.markAttendanceButton.setOnClickListener(v -> navigateToMarkAttendance(s));
                return;
            }
        }
        // No active session
        boolean hasUpcoming = sessions.stream().anyMatch(s -> s.getState() == SessionState.UPCOMING);
        binding.currentSessionTitle.setText(hasUpcoming ? "Upcoming Session" : "No Classes Today");
        binding.currentSessionInfo.setText(hasUpcoming
                ? "Attendance will open when the class starts."
                : "Enjoy your free time!");
        binding.markAttendanceButton.setVisibility(View.GONE);
    }

    private void navigateToMarkAttendance(TimetableSession session) {
        Bundle args = new Bundle();
        args.putString("classId", session.getClassId());
        args.putString("className", session.getClassName());
        args.putString("subject", session.getSubject());
        args.putString("startTime", session.getStartTime());
        args.putString("endTime", session.getEndTime());
        args.putString("facultyId", session.getFacultyId());
        if (session.getStudentIds() != null) {
            args.putStringArrayList("studentIds", new ArrayList<>(session.getStudentIds()));
        }
        try {
            NavController controller = Navigation.findNavController(requireView());
            if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.facultyHomeFragment) {
                controller.navigate(R.id.action_facultyHomeFragment_to_markAttendanceFragment, args);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
