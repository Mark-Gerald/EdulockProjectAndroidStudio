package com.example.edulock.ui.acitvity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageButton;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.example.edulock.ui.about.AboutUsFragment;
import com.example.edulock.ui.contact.ContactUsFragment;
import com.example.edulock.ui.control.ControlFragment;
import com.example.edulock.ui.faq.FactsFragment;
import com.example.edulock.ui.profile.ProfileFragment;
import com.example.edulock.R;
import com.example.edulock.ui.restrict.RestrictFragment;
import com.example.edulock.ui.stats.StatsFragment;
import com.example.edulock.service.BlockOverlayService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class statistics_usage_data extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, RestrictFragment.OnDeviceControlListener {

    DrawerLayout drawerLayout;
    BottomNavigationView bottomNavigationView;
    FragmentManager fragmentManager;
    Toolbar toolbar;

    @Override
    public void onDeviceControlStatusChanged(boolean isControlled) {
        // Start or update the overlay service based on control status
        Intent intent = new Intent(this, BlockOverlayService.class);
        intent.putExtra("SHOW_OVERLAY", isControlled);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_statistics_usage_data);

        // Initialize Firebase Auth first
        FirebaseAuth auth = FirebaseAuth.getInstance();

        // Initialize toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize and setup profile button
        ImageButton profileButton = findViewById(R.id.profile_button);
        profileButton.setOnClickListener(v -> showProfileDialog());

        // Now load profile image after Firebase and view initialization
        if (auth.getCurrentUser() != null) {
            loadProfileImage();
        }

        // Rest of your onCreate code remains the same
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.navigation_drawer);
        navigationView.setNavigationItemSelectedListener(this);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setBackground(null);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.bottom_stats) {
                    openFragment(new StatsFragment());
                    return true;
                } else if (itemId == R.id.bottom_control) {
                    openFragment(new ControlFragment());
                    return true;
                } else if (itemId == R.id.bottom_restrict) {
                    openFragment(new RestrictFragment());
                    return true;
                }
                return false;
            }
        });

        fragmentManager = getSupportFragmentManager();
        openFragment(new StatsFragment());
    }

    private void showProfileDialog() {
        ProfileFragment profileFragment = new ProfileFragment();
        profileFragment.show(getSupportFragmentManager(), "profile_dialog");
    }

    private void loadProfileImage() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        ImageButton profileButton = findViewById(R.id.profile_button);

        if (user != null && user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(profileButton);
        } else {
            profileButton.setImageResource(R.drawable.default_profile);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.nav_faqs) {
            openFragment(new FactsFragment());
        } else if (itemId == R.id.nav_about_us) {
            openFragment(new AboutUsFragment());
        } else if (itemId == R.id.nav_contact_us) {
            openFragment(new ContactUsFragment());
        } else if (itemId == R.id.nav_logout) {
            showLogoutDialog(); // Call the method to show logout confirmation
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    // Method to show a logout confirmation dialog
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to Log out?")
                .setPositiveButton("Confirm", (dialog, which) -> {
                    // Navigate back to login/register screen
                    Intent intent = new Intent(statistics_usage_data.this, login_register.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear activity stack
                    startActivity(intent);
                    finish(); // Close the current activity
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss()) // Close dialog if cancel is clicked
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)){
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void openFragment(Fragment fragment) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
}
