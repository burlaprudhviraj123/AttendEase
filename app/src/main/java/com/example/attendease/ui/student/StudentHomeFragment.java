package com.example.attendease.ui.student;

import android.content.Context;
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
import com.example.attendease.databinding.FragmentStudentHomeBinding;
import com.example.attendease.viewmodel.AuthViewModel;
import com.example.attendease.viewmodel.StudentViewModel;

import java.util.List;

public class StudentHomeFragment extends Fragment {

    private FragmentStudentHomeBinding binding;
    private AuthViewModel authViewModel;
    private StudentViewModel studentViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStudentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        studentViewModel = new ViewModelProvider(requireActivity()).get(StudentViewModel.class);

        setupObservers();
        setupRecyclerViews();
        setupClickListeners();
    }

    private void setupClickListeners() {
        // Tap on the Attendance or View All buttons
        if (binding.viewAttendanceButton != null) {
            binding.viewAttendanceButton.setOnClickListener(v -> {
                NavController controller = Navigation.findNavController(v);
                if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.studentHomeFragment) {
                  controller.navigate(R.id.action_studentHomeFragment_to_attendanceFragment);
                }
            });
        }
        if (binding.viewHistoryButton != null) {
            binding.viewHistoryButton.setOnClickListener(v -> {
                NavController controller = Navigation.findNavController(v);
                if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.studentHomeFragment) {
                   controller.navigate(R.id.action_studentHomeFragment_to_sessionHistoryFragment);
                }
            });
        }
        if (binding.profileButton != null) {
            binding.profileButton.setOnClickListener(v -> {
                NavController controller = Navigation.findNavController(v);
                if (controller.getCurrentDestination() != null && controller.getCurrentDestination().getId() == R.id.studentHomeFragment) {
                    controller.navigate(R.id.action_studentHomeFragment_to_profileFragment);
                }
            });
        }
    }

    private void setupObservers() {
        authViewModel.getCurrentUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                binding.welcomeText.setText(getString(R.string.student_welcome_format, user.getName()));
                if (studentViewModel.getOverallAttendance().getValue() == null) {
                    studentViewModel.loadStudentData(user.getUid());
                }
            }
        });

        studentViewModel.getOverallAttendance().observe(getViewLifecycleOwner(), percentage -> {
            Context context = getContext();
            if (context == null) return;
            
            binding.overallPercentageText.setText(getString(R.string.percentage_format, percentage));
            binding.attendanceProgress.setProgress(percentage);
            
            // Update status badge based on percentage
            if (percentage >= 90) {
                binding.statusBadge.setText("EXCEPTIONAL");
                binding.statusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                binding.statusBadge.setTextColor(context.getColor(R.color.success));
                binding.attendanceProgress.setIndicatorColor(context.getColor(R.color.success));
                binding.administrativeAlertCard.setVisibility(View.GONE);
            } else if (percentage >= 75) {
                binding.statusBadge.setText("GOOD");
                binding.statusBadge.setBackgroundResource(R.drawable.bg_badge_surface_low);
                binding.statusBadge.setTextColor(context.getColor(R.color.primary));
                binding.attendanceProgress.setIndicatorColor(context.getColor(R.color.primary));
                binding.administrativeAlertCard.setVisibility(View.GONE);
            } else {
                binding.statusBadge.setText("AT RISK");
                binding.statusBadge.setBackgroundResource(R.drawable.bg_badge_surface_low);
                binding.statusBadge.setTextColor(context.getColor(R.color.error));
                binding.attendanceProgress.setIndicatorColor(context.getColor(R.color.error));
                binding.administrativeAlertCard.setVisibility(View.VISIBLE);
            }
        });

        studentViewModel.getAttendanceDetails().observe(getViewLifecycleOwner(), details -> 
            binding.attendanceDetailsText.setText(details));

        studentViewModel.getCourseAttendanceList().observe(getViewLifecycleOwner(), courses -> {
            if (courses != null) {
                CourseAttendanceAdapter adapter = new CourseAttendanceAdapter(courses);
                binding.coursesRecyclerView.setAdapter(adapter);
            }
        });

        // Dynamic Alerts: show subjects below 75%
        studentViewModel.getAlertSubjects().observe(getViewLifecycleOwner(), alerts -> {
            if (alerts != null && !alerts.isEmpty()) {
                AlertAdapter alertAdapter = new AlertAdapter(alerts);
                binding.alertsRecyclerView.setAdapter(alertAdapter);
                binding.alertsRecyclerView.setVisibility(View.VISIBLE);
                binding.alertsTitle.setVisibility(View.VISIBLE);
            } else {
                binding.alertsRecyclerView.setVisibility(View.GONE);
                binding.alertsTitle.setVisibility(View.GONE);
            }
        });

        // Next class banner
        studentViewModel.getNextClass().observe(getViewLifecycleOwner(), nextClass -> {
            if (nextClass != null) {
                if (binding.nextClassTitle != null) binding.nextClassTitle.setText(nextClass.subject);
                
                if (binding.nextClassLocation != null) {
                    if (nextClass.location != null && !nextClass.location.isEmpty()) {
                        binding.nextClassLocation.setText(nextClass.location);
                        binding.nextClassLocation.setVisibility(View.VISIBLE);
                    } else {
                        binding.nextClassLocation.setVisibility(View.GONE);
                    }
                }
                
                if (binding.timeRemainingBadge != null) binding.timeRemainingBadge.setText(nextClass.timeRemaining);
                if (binding.nextClassTag != null) binding.nextClassTag.setText(nextClass.timeRange);
            }
        });

    }

    private void setupRecyclerViews() {
        binding.coursesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.alertsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
