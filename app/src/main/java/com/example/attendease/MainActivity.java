package com.example.attendease;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.example.attendease.databinding.ActivityMainBinding;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    // Destinations where the bottom nav shows for FACULTY
    private static final Set<Integer> FACULTY_DESTINATIONS = new HashSet<>(Arrays.asList(
            R.id.facultyHomeFragment,
            R.id.myClassesFragment,
            R.id.analyticsDashboardFragment,
            R.id.facultyProfileFragment
    ));

    // Destinations where the bottom nav shows for STUDENT
    private static final Set<Integer> STUDENT_DESTINATIONS = new HashSet<>(Arrays.asList(
            R.id.studentHomeFragment,
            R.id.attendanceFragment,
            R.id.sessionHistoryFragment,
            R.id.studentProfileFragment
    ));

    // Auth screens — always hide bottom nav
    private static final Set<Integer> AUTH_DESTINATIONS = new HashSet<>(Arrays.asList(
            R.id.loginFragment,
            R.id.setPasswordFragment,
            R.id.forgotPasswordFragment
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;
        navController = navHostFragment.getNavController();

        setupBottomNav();
    }

    private void setupBottomNav() {
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();

            if (AUTH_DESTINATIONS.contains(destId) || destId == R.id.markAttendanceFragment) {
                // Hide on all auth screens and mark attendance (full-screen action)
                binding.bottomNav.setVisibility(View.GONE);

            } else if (FACULTY_DESTINATIONS.contains(destId)) {
                // Show faculty menu
                ensureMenu(R.menu.bottom_nav_faculty);
                binding.bottomNav.setVisibility(View.VISIBLE);
                
                // 🛡️ Safety: only check if item exists in menu
                android.view.MenuItem item = binding.bottomNav.getMenu().findItem(destId);
                if (item != null && !item.isChecked()) {
                    item.setChecked(true);
                }

            } else if (STUDENT_DESTINATIONS.contains(destId)) {
                // Show student menu
                ensureMenu(R.menu.bottom_nav_student);
                binding.bottomNav.setVisibility(View.VISIBLE);
                
                // 🛡️ Safety: only check if item exists in menu
                android.view.MenuItem item = binding.bottomNav.getMenu().findItem(destId);
                if (item != null && !item.isChecked()) {
                    item.setChecked(true);
                }

            } else {
                binding.bottomNav.setVisibility(View.GONE);
            }
        });

        // When a tab is tapped, navigate to its destination
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            NavController controller = Navigation.findNavController(this, R.id.nav_host_fragment);
            if (controller.getCurrentDestination() != null
                    && controller.getCurrentDestination().getId() != id) {
                
                // 🛡️ Standard tab behavior: launchSingleTop + popUpTo(start)
                androidx.navigation.NavOptions options = new androidx.navigation.NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(controller.getGraph().getStartDestinationId(), false)
                        .build();

                try {
                    controller.navigate(id, null, options);
                } catch (Exception ignored) {}
            }
            return true;
        });
    }

    /** Inflate the given menu only if it's not already the current menu. */
    private int currentMenuRes = -1;
    private void ensureMenu(int menuRes) {
        if (currentMenuRes != menuRes) {
            binding.bottomNav.getMenu().clear();
            binding.bottomNav.inflateMenu(menuRes);
            currentMenuRes = menuRes;
        }
    }
}
