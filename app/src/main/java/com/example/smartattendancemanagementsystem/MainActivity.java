package com.example.smartattendancemanagementsystem;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.smartattendancemanagementsystem.databinding.ActivityMainBinding;
import com.example.smartattendancemanagementsystem.session.SessionState;
import com.example.smartattendancemanagementsystem.session.SessionViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private SessionViewModel session;
    private String currentMenuRole = null;
    private boolean errorAlreadyShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        session = new ViewModelProvider(this).get(SessionViewModel.class);

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
                    applyMenuFor(session.getRole());
                }
            });
        }

        // The role comes from users/{uid}; the bottom bar is rebuilt when it arrives.
        session.getState().observe(this, state -> {
            if (state == null) return;
            applyMenuFor(state.normalizedRole());

            if (state.getStatus() == SessionState.Status.ERROR
                    && state.getMessage() != null
                    && !errorAlreadyShown) {
                errorAlreadyShown = true;
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_LONG).show();
            }
            if (state.getStatus() == SessionState.Status.READY) {
                errorAlreadyShown = false;
            }
        });

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            session.bind(currentUser.getUid());
        }
    }

    /**
     * Inflates the correct bottom bar for the given role. Safe to call repeatedly;
     * the menu is only rebuilt when the role actually changes.
     */
    public void applyMenuFor(String role) {
        if (navController == null) return;

        String normalized = "Instructor".equalsIgnoreCase(role) ? "Instructor" : "Student";
        if (normalized.equals(currentMenuRole)) return;

        currentMenuRole = normalized;
        binding.bottomNavigation.getMenu().clear();
        binding.bottomNavigation.inflateMenu(
                "Instructor".equals(normalized)
                        ? R.menu.teacher_bottom_nav_menu
                        : R.menu.student_bottom_nav_menu);

        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
    }

    public void updateBottomNavMenu() {
        applyMenuFor(session.getRole());
    }
}
