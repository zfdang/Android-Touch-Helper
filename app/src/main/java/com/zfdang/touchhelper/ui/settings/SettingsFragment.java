package com.zfdang.touchhelper.ui.settings;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import androidx.preference.SwitchPreferenceCompat;

import com.google.gson.Gson;
import com.zfdang.touchhelper.ActivityPositionDescription;
import com.zfdang.touchhelper.ActivityWidgetDescription;
import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.Settings;
import com.zfdang.touchhelper.TouchHelperService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.content.Context.WINDOW_SERVICE;

public class SettingsFragment extends PreferenceFragmentCompat {

    private final String TAG = getClass().getName();
    LayoutInflater inflater;
    PackageManager packageManager;
    WindowManager winManager;

    Settings mSetting;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.touch_helper_preference, rootKey);

        mSetting = Settings.getInstance();

        initPreferences();

        winManager = (WindowManager) getActivity().getSystemService(WINDOW_SERVICE);
        inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        packageManager = getActivity().getPackageManager();
    }

    private void initPreferences() {

        // key words to detect skip-ad button
        EditTextPreference textKeyWords = findPreference("setting_key_words");
        if(textKeyWords != null) {
            textKeyWords.setText(mSetting.getKeyWordsAsString());
            textKeyWords.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String text = newValue.toString();
                    mSetting.setKeyWordList(text);

                    // notify accessibility to refresh packages
                    if (TouchHelperService.serviceImpl != null) {
                        TouchHelperService.serviceImpl.receiverHandler.sendEmptyMessage(TouchHelperService.ACTION_REFRESH_KEYWORDS);
                    }

                    return true;
                }
            });
        }

        // select packages to be whitelisted
        Preference whitelist = findPreference("setting_whitelist");
        if(whitelist != null) {
            whitelist.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // find all packages
                    List<String> list = new ArrayList<>();
                    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
                    for (ResolveInfo e : ResolveInfoList) {
                        Log.d(TAG, "launcher - " + e.activityInfo.packageName);
                        list.add(e.activityInfo.packageName);
                    }

                    // generate AppInformation for packages
                    final ArrayList<AppInformation> listApp = new ArrayList<>();
                    Set<String> pkgWhitelist = mSetting.getWhitelistPackages();
                    for (String pkgName : list) {
                        try {
                            ApplicationInfo info = packageManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA);
                            AppInformation appInfo = new AppInformation(pkgName, packageManager.getApplicationLabel(info).toString(), packageManager.getApplicationIcon(info));
                            appInfo.isChecked = pkgWhitelist.contains(pkgName);
                            listApp.add(appInfo);
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.d(TAG, e.getStackTrace().toString());
                        }
                    }

                    // listApp adapter
                    BaseAdapter baseAdapter = new BaseAdapter() {
                        @Override
                        public int getCount() {
                            return listApp.size();
                        }

                        @Override
                        public Object getItem(int position) {
                            return listApp.get(position);
                        }

                        @Override
                        public long getItemId(int position) {
                            return position;
                        }

                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            ViewHolder holder;
                            if (convertView == null) {
                                convertView = inflater.inflate(R.layout.layout_package_information, null);
                                holder = new ViewHolder(convertView);
                                convertView.setTag(holder);
                            } else {
                                holder = (ViewHolder) convertView.getTag();
                            }
                            AppInformation app = listApp.get(position);
                            holder.textView.setText(app.applicationName);
                            holder.imageView.setImageDrawable(app.applicationIcon);
                            holder.checkBox.setChecked(app.isChecked);
                            return convertView;
                        }
                    };

                    // inflate the dialog view
                    View viewAppList = inflater.inflate(R.layout.layout_select_packages, null);
                    ListView listView = viewAppList.findViewById(R.id.listView);
                    listView.setAdapter(baseAdapter);
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            CheckBox item = ((ViewHolder) view.getTag()).checkBox;
                            AppInformation app = listApp.get(position);
                            app.isChecked = !app.isChecked;
                            item.setChecked(app.isChecked);
                        }
                    });


                    final AlertDialog dialog = new AlertDialog.Builder(getContext())
                            .setView(viewAppList)
                            .create();

                    Button btCancel = viewAppList.findViewById(R.id.button_cancel);
                    if(btCancel != null) {
                        btCancel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                dialog.dismiss();
                            }
                        });
                    }
                    Button btConfirm = viewAppList.findViewById(R.id.button_confirm);
                    if(btConfirm != null) {
                        btConfirm.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // save checked packages
                                Set<String> pkgWhitelist = new HashSet<>();
                                for(AppInformation app: listApp) {
                                    if(app.isChecked) {
                                        pkgWhitelist.add(app.packageName);
                                    }
                                }
                                mSetting.setWhitelistPackages(pkgWhitelist);

                                // notify accessibility to refresh packages
                                if (TouchHelperService.serviceImpl != null) {
                                    TouchHelperService.serviceImpl.receiverHandler.sendEmptyMessage(TouchHelperService.ACTION_REFRESH_PACKAGE);
                                }

                                dialog.dismiss();
                            }
                        });
                    }

                    // show the dialog
                    dialog.show();
                    return true;
                }

                class AppInformation {
                    String packageName;
                    String applicationName;
                    Drawable applicationIcon;
                    boolean isChecked;

                    public AppInformation(String packageName, String applicationName, Drawable applicationIcon) {
                        this.packageName = packageName;
                        this.applicationName = applicationName;
                        this.applicationIcon = applicationIcon;
                        this.isChecked = false;
                    }
                }

                class ViewHolder {
                    TextView textView;
                    ImageView imageView;
                    CheckBox checkBox;

                    public ViewHolder(View v) {
                        textView = v.findViewById(R.id.name);
                        imageView = v.findViewById(R.id.img);
                        checkBox = v.findViewById(R.id.check);
                    }
                }

            });
        }

        // let user to customize skip-ad button or position for package
        Preference activity_customization = findPreference("setting_activity_customization");
        if(activity_customization != null) {
            activity_customization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if(TouchHelperService.serviceImpl != null) {
                        TouchHelperService.serviceImpl.receiverHandler.sendEmptyMessage(TouchHelperService.ACTION_ACTIVITY_CUSTOMIZATION);
                    } else {
                        Toast.makeText(getContext(),"触屏助手未运行，请打开无障碍服务", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

    }


}