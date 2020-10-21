package com.zfdang.touchhelper.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.method.Touch;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.TouchHelperService;

public class HomeFragment extends Fragment {

    private final String TAG = getClass().getName();

    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                ViewModelProviders.of(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        final Drawable drawableYes = ContextCompat.getDrawable(getContext(), R.drawable.ic_yes_24);
        final Drawable drawableNo = ContextCompat.getDrawable(getContext(), R.drawable.ic_no_24);

        // set observers for widget
        final TextView textView = root.findViewById(R.id.text_instructions);
        homeViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                textView.setText(s);
            }
        });
        final ImageView imageAppPermission = root.findViewById(R.id.image_app_permission);
        homeViewModel.getAppPermission().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if(aBoolean) {
                    imageAppPermission.setImageDrawable(drawableYes);
                } else {
                    imageAppPermission.setImageDrawable(drawableNo);
                }
            }
        });
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
        final ImageButton btAppPermission = root.findViewById(R.id.button_app_permission);
        btAppPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getActivity().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        });

        final ImageButton btAccessibilityPermission = root.findViewById(R.id.button_accessibility_permission);
        btAccessibilityPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent_abs = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent_abs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent_abs);
            }
        });

        final ImageButton btPowerPermission = root.findViewById(R.id.button_power_permission);
        btPowerPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //  打开电池优化的界面，让用户设置
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent();
                    String packageName = getActivity().getPackageName();
                    PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
                    if (pm.isIgnoringBatteryOptimizations(packageName)) {
                        // open battery optimization setting page
                        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    } else {
                        // request ignore settings
                        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + packageName));
                    }
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
    }

    public void checkServiceStatus(){

        // detect the app storage permission
        boolean bAppPermission =
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        MutableLiveData<Boolean> liveData = homeViewModel.getAppPermission();
        liveData.setValue(bAppPermission);

        // detect the accessibility permission
        MutableLiveData<Boolean> accessibility = homeViewModel.getAccessibilityPermission();
        accessibility.setValue(TouchHelperService.serviceImpl != null);

        // detect power optimization
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        boolean hasIgnored = pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
        MutableLiveData<Boolean> power = homeViewModel.getPowerOptimization();
        power.setValue(hasIgnored);
    }
}