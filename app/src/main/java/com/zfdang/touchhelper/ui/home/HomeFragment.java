package com.zfdang.touchhelper.ui.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.TouchHelperService;

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
                //  打开电池优化的界面，让用户设置
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String packageName = getActivity().getPackageName();

                    // open battery optimization setting page
                    Intent intent = new Intent();
                    PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
//                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
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
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void checkServiceStatus(){
        // detect the accessibility permission
        MutableLiveData<Boolean> accessibility = homeViewModel.getAccessibilityPermission();
        accessibility.setValue(TouchHelperService.isServiceRunning());

        // detect power optimization
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        boolean hasIgnored = pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
        MutableLiveData<Boolean> power = homeViewModel.getPowerOptimization();
        power.setValue(hasIgnored);
    }
}