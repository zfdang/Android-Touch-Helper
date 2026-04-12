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
import androidx.lifecycle.ViewModelProvider;

import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.TouchHelperService;

public class HomeFragment extends Fragment {

    private final String TAG = getClass().getName();

    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);
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
        btAccessibilityPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                        .setTitle("重要说明 (Prominent Disclosure)")
                        .setMessage("本应用需要使用无障碍服务（AccessibilityService API）来帮您在其他应用中自动查找并点击屏幕上的指定控件（例如各类重复性的按钮）。\n\n本应用属于自动化工具，所有的点击操作均基于您的配置和触发策略。\n本应用完全离线运行，不会收集、存储、传输或分享您的任何个人数据及敏感信息。\n\n请点击“同意”以继续前往系统设置中授权此服务。")
                        .setPositiveButton("同意", (dialog, which) -> {
                            Intent intent_abs = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            intent_abs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent_abs);
                        })
                        .setNegativeButton("拒绝", null)
                        .show();
            }
        });

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
    }

    public void checkServiceStatus(){

        // detect the app storage permission
        boolean bAppPermission =
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        MutableLiveData<Boolean> liveData = homeViewModel.getAppPermission();
        liveData.setValue(bAppPermission);

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