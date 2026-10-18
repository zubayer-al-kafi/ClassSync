package com.example.smartattendancemanagementsystem;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.smartattendancemanagementsystem.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private String currentMenuRole = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int destId = destination.getId();
                if (destId == R.id.loginFragment || destId == R.id.registerFragment) {
                    binding.bottomNavigation.setVisibility(View.GONE);
                } else {
                    binding.bottomNavigation.setVisibility(View.VISIBLE);
                    updateBottomNavMenu();
                }
            });
        }
    }

    public void updateBottomNavMenu() {
        if (navController == null) return;

        SharedPreferences prefs = getSharedPreferences("app_session", Context.MODE_PRIVATE);
        String role = prefs.getString("role", "Student");
        String normalizedRole = ("Instructor".equalsIgnoreCase(role) || "Teacher".equalsIgnoreCase(role)) ? "Instructor" : "Student";

        if (normalizedRole.equals(currentMenuRole)) {
            // Already inflated for this role -> keep selection state untouched
            return;
        }

        currentMenuRole = normalizedRole;
        binding.bottomNavigation.getMenu().clear();

        if ("Instructor".equals(normalizedRole)) {
            binding.bottomNavigation.inflateMenu(R.menu.teacher_bottom_nav_menu);
        } else {
            binding.bottomNavigation.inflateMenu(R.menu.student_bottom_nav_menu);
        }

        // Connect NavigationUI with the newly inflated menu for singleTop & state restoration
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
    }
}
