package com.zfdang.touchhelper.ui.about;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.zfdang.touchhelper.R;

public class AboutFragment extends Fragment {
    private final String TAG = getClass().getName();


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_about, container, false);

        TextView tvVersion = root.findViewById(R.id.textView_version);

        String versionName = "unknown";
        int versionCode = 0;
        PackageManager pm = getActivity().getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(getActivity().getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
            // generate about_content with version from manifest
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "showInfoDialog: " + Log.getStackTraceString(e));
        }

        String content_with_version = getString(R.string.app_version, versionName, versionCode);
        tvVersion.setText(content_with_version);
        return root;
    }
}