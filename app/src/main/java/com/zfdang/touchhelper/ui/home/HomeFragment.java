package com.zfdang.touchhelper.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.TouchHelperService;
import com.zfdang.touchhelper.Utilities;

public class HomeFragment extends Fragment {

    private final String TAG = getClass().getName();

    private HomeViewModel homeViewModel;
    private AlertDialog disclosureDialog;
    private AlertDialog tipDialog;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. Hero Status Card
        final MaterialCardView cardServiceStatus = root.findViewById(R.id.card_service_status);
        final ImageView imageServiceStatusHero = root.findViewById(R.id.image_service_status_hero);
        final TextView textServiceStatusTitle = root.findViewById(R.id.text_service_status_title);
        final TextView textServiceStatusDesc = root.findViewById(R.id.text_service_status_desc);
        final TextView statusBadgeHero = root.findViewById(R.id.status_badge_hero);
        final MaterialButton buttonStartService = root.findViewById(R.id.button_start_service);

        // 2. Checklist items & badges
        final MaterialCardView cardAccessibility = root.findViewById(R.id.card_accessibility_permission);
        final TextView textAccessibilityBadge = root.findViewById(R.id.text_accessibility_badge);

        final MaterialCardView cardPower = root.findViewById(R.id.card_power_permission);
        final TextView textPowerBadge = root.findViewById(R.id.text_power_badge);

        final MaterialCardView cardRomBattery = root.findViewById(R.id.card_rom_battery);
        final MaterialCardView cardTaskLock = root.findViewById(R.id.card_task_lock);
        final MaterialCardView cardHelpLink = root.findViewById(R.id.card_help_link);

        // Observe Accessibility status
        homeViewModel.getAccessibilityPermission().observe(getViewLifecycleOwner(), isRunning -> {
            Context context = getContext();
            if (context == null) return;

            boolean running = Boolean.TRUE.equals(isRunning);
            if (running) {
                // Running state
                cardServiceStatus.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_running_bg));
                cardServiceStatus.setStrokeColor(ContextCompat.getColor(context, R.color.status_running_stroke));
                imageServiceStatusHero.setImageResource(R.drawable.ic_shield_check_24);
                textServiceStatusTitle.setText(R.string.status_hero_running_title);
                textServiceStatusTitle.setTextColor(ContextCompat.getColor(context, R.color.status_running_text));
                textServiceStatusDesc.setText(R.string.status_hero_running_desc);
                textServiceStatusDesc.setTextColor(ContextCompat.getColor(context, R.color.status_running_text));

                statusBadgeHero.setText(R.string.status_pill_running);
                statusBadgeHero.setBackgroundResource(R.drawable.bg_badge_running);
                statusBadgeHero.setTextColor(ContextCompat.getColor(context, R.color.status_running_badge_text));

                buttonStartService.setVisibility(View.GONE);

                textAccessibilityBadge.setText(R.string.status_badge_enabled);
                textAccessibilityBadge.setBackgroundResource(R.drawable.bg_badge_running);
                textAccessibilityBadge.setTextColor(ContextCompat.getColor(context, R.color.status_running_badge_text));
            } else {
                // Stopped state
                cardServiceStatus.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_stopped_bg));
                cardServiceStatus.setStrokeColor(ContextCompat.getColor(context, R.color.status_stopped_stroke));
                imageServiceStatusHero.setImageResource(R.drawable.ic_shield_alert_24);
                textServiceStatusTitle.setText(R.string.status_hero_stopped_title);
                textServiceStatusTitle.setTextColor(ContextCompat.getColor(context, R.color.status_stopped_text));
                textServiceStatusDesc.setText(R.string.status_hero_stopped_desc);
                textServiceStatusDesc.setTextColor(ContextCompat.getColor(context, R.color.status_stopped_text));

                statusBadgeHero.setText(R.string.status_pill_stopped);
                statusBadgeHero.setBackgroundResource(R.drawable.bg_badge_stopped);
                statusBadgeHero.setTextColor(ContextCompat.getColor(context, R.color.status_stopped_badge_text));

                buttonStartService.setVisibility(View.VISIBLE);

                textAccessibilityBadge.setText(R.string.status_badge_enable);
                textAccessibilityBadge.setBackgroundResource(R.drawable.bg_badge_action);
                textAccessibilityBadge.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
            }
        });

        // Observe Battery Optimization status
        homeViewModel.getPowerOptimization().observe(getViewLifecycleOwner(), isOptimized -> {
            Context context = getContext();
            if (context == null) return;

            boolean optimized = Boolean.TRUE.equals(isOptimized);
            if (optimized) {
                textPowerBadge.setText(R.string.status_badge_optimized);
                textPowerBadge.setBackgroundResource(R.drawable.bg_badge_running);
                textPowerBadge.setTextColor(ContextCompat.getColor(context, R.color.status_running_badge_text));
            } else {
                textPowerBadge.setText(R.string.status_badge_optimize);
                textPowerBadge.setBackgroundResource(R.drawable.bg_badge_action);
                textPowerBadge.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
            }
        });

        // Listeners
        buttonStartService.setOnClickListener(v -> showAccessibilityDisclosure());
        cardAccessibility.setOnClickListener(v -> showAccessibilityDisclosure());

        cardPower.setOnClickListener(v -> openBatteryOptimization());

        cardRomBattery.setOnClickListener(v -> showTipDialog(
                R.string.dialog_rom_battery_title,
                R.string.dialog_rom_battery_message));

        cardTaskLock.setOnClickListener(v -> showTipDialog(
                R.string.dialog_task_lock_title,
                R.string.dialog_task_lock_message));

        cardHelpLink.setOnClickListener(v -> openWebUrl("https://touchhelper.zfdang.com/enable"));

        // Refresh state
        checkServiceStatus();

        return root;
    }

    private void openBatteryOptimization() {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Utilities.toast("未找到系统电池优化设置页面！");
        }
    }

    private void openWebUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Utilities.toast("无法打开浏览器: " + url);
        }
    }

    private void showTipDialog(int titleRes, int messageRes) {
        if (tipDialog != null && tipDialog.isShowing()) {
            tipDialog.dismiss();
        }
        tipDialog = new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    @Override
    public void onResume() {
        checkServiceStatus();
        super.onResume();
        if (TouchHelperService.isServiceRunning()
                && !com.zfdang.touchhelper.Settings.getInstance().isDisclosureAccepted()) {
            showAccessibilityDisclosure();
        }
    }

    @Override
    public void onPause() {
        if (disclosureDialog != null && disclosureDialog.isShowing()) {
            disclosureDialog.dismiss();
        }
        if (tipDialog != null && tipDialog.isShowing()) {
            tipDialog.dismiss();
        }
        super.onPause();
    }

    private void showAccessibilityDisclosure() {
        if (com.zfdang.touchhelper.Settings.getInstance().isDisclosureAccepted()) {
            openAccessibilitySettings();
            return;
        }
        if (disclosureDialog != null && disclosureDialog.isShowing()) {
            return;
        }
        final boolean serviceRunning = TouchHelperService.isServiceRunning();
        disclosureDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.accessibility_disclosure_title)
                .setMessage(R.string.accessibility_disclosure_message)
                .setPositiveButton(serviceRunning ? R.string.accessibility_disclosure_agree_only : R.string.accessibility_disclosure_agree,
                        (dialog, which) -> {
                            com.zfdang.touchhelper.Settings.getInstance().setDisclosureAccepted(true);
                            if (!serviceRunning) {
                                openAccessibilitySettings();
                            }
                        })
                .setNegativeButton(R.string.accessibility_disclosure_cancel, null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Utilities.toast("未找到系统无障碍设置页面！");
        }
    }

    public void checkServiceStatus() {
        Context context = getContext();
        if (context != null) {
            homeViewModel.refreshServiceStatus(context.getApplicationContext());
        }
    }
}