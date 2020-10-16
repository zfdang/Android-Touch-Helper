package com.zfdang.touchhelper.ui.home;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

public class HomeFragment extends Fragment {

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


        // set listener for buttons
        final Button btAppPermission = root.findViewById(R.id.button_app_permission);
        btAppPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getActivity().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                Toast.makeText(getContext(), "请授予\"读写手机存储\"权限，并设置允许后台运行/关闭电池优化！", Toast.LENGTH_LONG).show();

//                MutableLiveData<Boolean> liveData = homeViewModel.getAppPermission();
//                boolean temp = (liveData.getValue() != null && liveData.getValue().booleanValue());
//                liveData.setValue(!temp);
            }
        });

        final Button btAccessibilityPermission = root.findViewById(R.id.button_accessibility_permission);
        btAccessibilityPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent_abs = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent_abs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent_abs);
                Toast.makeText(getContext(), "无障碍服务冲突，请关闭其中一个", Toast.LENGTH_SHORT).show();

                MutableLiveData<Boolean> liveData = homeViewModel.getAccessibilityPermission();
                boolean temp = (liveData.getValue() != null && liveData.getValue().booleanValue());
                liveData.setValue(!temp);
            }
        });

        // get the service status
        checkServiceStatus();

        return root;
    }

    public void checkServiceStatus(){

        // detect the app permission
        boolean bAppPermission = false;

        // set value to viewmodel
        MutableLiveData<Boolean> liveData;
        liveData = homeViewModel.getAppPermission();
        liveData.setValue(bAppPermission);
    }
}