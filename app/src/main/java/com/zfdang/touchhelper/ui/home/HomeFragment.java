package com.zfdang.touchhelper.ui.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.TouchHelperService;
import com.zfdang.touchhelper.Utilities;

public class HomeFragment extends Fragment {

    private final String TAG = getClass().getName();

    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        final Drawable drawableYes = ContextCompat.getDrawable(getContext(), R.drawable.ic_right);
        final Drawable drawableNo = ContextCompat.getDrawable(getContext(), R.drawable.ic_wrong);

        // set observers for widget
        final ImageView imageAccessibilityPermission = root.findViewById(R.id.image_accessibility_permission);
        homeViewModel.getAccessibilityPermission().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if(aBoolean) {
                    imageAccessibilityPermission.setImageDrawable(drawableYes);
                } else {
                    imageAccessibilityPermission.setImageDrawable(drawableNo);
                }
            }
        });

        final ImageView imagePowerPermission = root.findViewById(R.id.image_power_permission);
        homeViewModel.getPowerOptimization().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if(aBoolean) {
                    imagePowerPermission.setImageDrawable(drawableYes);
                } else {
                    imagePowerPermission.setImageDrawable(drawableNo);
                }
            }
        });


        // set listener for buttons
        final ImageButton btAccessibilityPermission = root.findViewById(R.id.button_accessibility_permission);
        btAccessibilityPermission.setOnClickListener(v -> showAccessibilityDisclosure());

        final ImageButton btPowerPermission = root.findViewById(R.id.button_power_permission);
        btPowerPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开电池优化的界面，让用户设置
                try {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Utilities.toast("未找到系统电池优化设置页面！");
                }
            }
        });

        // get the service status
        checkServiceStatus();

        return root;
    }

    @Override
    public void onResume() {
        checkServiceStatus();
        super.onResume();
        // the service was enabled from the system settings (or before the disclosure existed)
        // but the user has not confirmed the disclosure yet: it stays idle until they do
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
        super.onPause();
    }

    /**
     * Google Play requires a prominent in-app disclosure before an app sends the user to enable
     * an accessibility service that is not an assistive tool: what the service reads, what it
     * does with it, and that nothing leaves the device.
     */
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

    private AlertDialog disclosureDialog;

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Utilities.toast("未找到系统无障碍设置页面！");
        }
    }

    public void checkServiceStatus(){
        Context context = getContext();
        if (context != null) {
            homeViewModel.refreshServiceStatus(context.getApplicationContext());
        }
    }
}