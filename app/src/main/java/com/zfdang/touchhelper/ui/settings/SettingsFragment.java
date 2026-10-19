package com.zfdang.touchhelper.ui.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import java.util.concurrent.Executors;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.zfdang.touchhelper.PackagePositionDescription;
import com.zfdang.touchhelper.PackageWidgetDescription;
import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.Settings;
import com.zfdang.touchhelper.TouchHelperService;
import com.zfdang.touchhelper.Utilities;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsFragment extends PreferenceFragmentCompat {

    private final String TAG = getClass().getName();
    LayoutInflater inflater;
    PackageManager packageManager;
    private AlertDialog whitelistDialog;

    Settings mSetting;

    MultiSelectListPreference activity_positions;
    MultiSelectListPreference activity_widgets;
    Map<String, Set<PackageWidgetDescription>> mapActivityWidgets;
    Map<String, PackagePositionDescription> mapActivityPositions;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.touch_helper_preference, rootKey);

        mSetting = Settings.getInstance();

        initPreferences();

        inflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        packageManager = requireActivity().getPackageManager();
    }

    @Override
    public void onDestroyView() {
        if (whitelistDialog != null && whitelistDialog.isShowing()) {
            whitelistDialog.dismiss();
            whitelistDialog = null;
        }
        super.onDestroyView();
    }

    private void initPreferences() {

        CheckBoxPreference notification = findPreference("skip_ad_notification");
        if(notification != null) {
            notification.setChecked(mSetting.isSkipAdNotification());
            notification.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Boolean value = (Boolean) newValue;
                    mSetting.setSkipAdNotification(value);

                    return true;
                }
            });
        }

        final SeekBarPreference duration = findPreference("skip_ad_duration");
        if(duration != null) {
            duration.setMax(10);
            duration.setMin(1);
            duration.setUpdatesContinuously(true);
            duration.setValue(mSetting.getSkipAdDuration());

            duration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    try {
                        int value = (int) newValue;
                        mSetting.setSkipAdDuration(value);
                    } catch (ClassCastException e) {

                    }

                    return true;
                }
            });
        }


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
                    TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_KEYWORDS);

                    return true;
                }
            });
        }

        // select packages to be whitelisted
        Preference whitelist = findPreference("setting_whitelist");
        if(whitelist != null) {
            whitelist.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "正在加载应用列表...", Toast.LENGTH_SHORT).show();
                }
                Executors.newSingleThreadExecutor().execute(() -> {
                    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
                    final ArrayList<AppInformation> listApp = new ArrayList<>(resolveInfoList.size());
                    Set<String> seen = new HashSet<>();
                    Set<String> pkgWhitelist = mSetting.getWhitelistPackages();
                    HanyuPinyinOutputFormat outputFormat = new HanyuPinyinOutputFormat();
                    for (ResolveInfo e : resolveInfoList) {
                        ApplicationInfo info = e.activityInfo.applicationInfo;
                        String pkgName = e.activityInfo.packageName;
                        if (info == null || !seen.add(pkgName)) {
                            continue;
                        }
                        AppInformation appInfo = new AppInformation(pkgName, packageManager.getApplicationLabel(info).toString(), info, outputFormat);
                        appInfo.isChecked = pkgWhitelist.contains(pkgName);
                        listApp.add(appInfo);
                    }

                    // sort apps
                    Collections.sort(listApp);

                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded() || getContext() == null) return;
                        showWhitelistDialog(listApp);
                    });
                });
                return true;
            });
        }

        // let user to customize skip-ad button or position for package
        Preference activity_customization = findPreference("setting_activity_customization");
        if(activity_customization != null) {
            activity_customization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if(!TouchHelperService.dispatchAction(TouchHelperService.ACTION_ACTIVITY_CUSTOMIZATION)) {
                        Toast.makeText(getContext(),"开屏跳过服务未运行，请打开无障碍服务!", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        // manage saved activity widgets
        activity_widgets = (MultiSelectListPreference) findPreference("setting_activity_widgets");
        mapActivityWidgets = Settings.getInstance().getPackageWidgets();
        updateMultiSelectListPreferenceEntries(activity_widgets, mapActivityWidgets.keySet());
        activity_widgets.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> results = (Set<String>) newValue;
                    mapActivityWidgets.keySet().retainAll(results);
                    Settings.getInstance().setPackageWidgets(mapActivityWidgets);
                    updateMultiSelectListPreferenceEntries(activity_widgets, mapActivityWidgets.keySet());
                    TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY);
                }
                return true;
            }
        });


        // advanced method to manage "customized package widgets", by editing the raw setting
        Preference package_widgets_advance = findPreference("setting_activity_widgets_advanced");
        if(package_widgets_advance != null) {
            package_widgets_advance.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                    ManagePackageWidgetsDialogFragment newFragment = new ManagePackageWidgetsDialogFragment();
                    newFragment.show(fragmentManager, "dialog");
                    return true;
                }
            });
        }



            // manage saved activity positions
        activity_positions = (MultiSelectListPreference) findPreference("setting_activity_positions");
        mapActivityPositions = Settings.getInstance().getPackagePositions();
        updateMultiSelectListPreferenceEntries(activity_positions, mapActivityPositions.keySet());
        activity_positions.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> results = (Set<String>) newValue;
                    mapActivityPositions.keySet().retainAll(results);
                    Settings.getInstance().setPackagePositions(mapActivityPositions);
                    updateMultiSelectListPreferenceEntries(activity_positions, mapActivityPositions.keySet());
                    TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY);
                }
                return true;
            }
        });


    }

    void updateMultiSelectListPreferenceEntries(MultiSelectListPreference preference, Set<String> keys){
        if(preference == null || keys == null)
            return;
        CharSequence[] entries = keys.toArray(new CharSequence[keys.size()]);
        preference.setEntries(entries);
        preference.setEntryValues(entries);
        preference.setValues(keys);
    }

    @Override
    public void onResume() {
        super.onResume();

        // these values might be changed by adding new widget or positions, update entries for these two multipeline
        mapActivityWidgets = Settings.getInstance().getPackageWidgets();
        updateMultiSelectListPreferenceEntries(activity_widgets, mapActivityWidgets.keySet());

        mapActivityPositions = Settings.getInstance().getPackagePositions();
        updateMultiSelectListPreferenceEntries(activity_positions, mapActivityPositions.keySet());
    }

    private void showWhitelistDialog(List<AppInformation> listApp) {
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
                    convertView = inflater.inflate(R.layout.layout_package_information, parent, false);
                    holder = new ViewHolder(convertView);
                    convertView.setTag(holder);
                } else {
                    holder = (ViewHolder) convertView.getTag();
                }
                AppInformation app = listApp.get(position);
                holder.textView.setText(app.applicationName);
                holder.imageView.setImageDrawable(app.getIcon(packageManager));
                holder.checkBox.setChecked(app.isChecked);
                return convertView;
            }
        };

        View viewAppList = inflater.inflate(R.layout.layout_select_packages, null);
        ListView listView = viewAppList.findViewById(R.id.listView);
        listView.setAdapter(baseAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ViewHolder holder = (ViewHolder) view.getTag();
            if (holder != null) {
                AppInformation app = listApp.get(position);
                app.isChecked = !app.isChecked;
                holder.checkBox.setChecked(app.isChecked);
            }
        });

        whitelistDialog = new AlertDialog.Builder(requireContext())
                .setView(viewAppList)
                .create();

        Button btCancel = viewAppList.findViewById(R.id.button_cancel);
        if (btCancel != null) {
            btCancel.setOnClickListener(view -> whitelistDialog.dismiss());
        }
        Button btConfirm = viewAppList.findViewById(R.id.button_confirm);
        if (btConfirm != null) {
            btConfirm.setOnClickListener(view -> {
                Set<String> pkgWhitelist = new HashSet<>();
                for (AppInformation app : listApp) {
                    if (app.isChecked) {
                        pkgWhitelist.add(app.packageName);
                    }
                }
                mSetting.setWhitelistPackages(pkgWhitelist);
                TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_PACKAGE);
                whitelistDialog.dismiss();
            });
        }

        whitelistDialog.show();
    }

    static class AppInformation implements Comparable<AppInformation> {
        String packageName;
        String applicationName;
        String applicationNamePinyin;
        final ApplicationInfo applicationInfo;
        Drawable applicationIcon;
        boolean isChecked;

        public AppInformation(String packageName, String applicationName, ApplicationInfo applicationInfo, HanyuPinyinOutputFormat outputFormat) {
            this.packageName = packageName;
            this.applicationName = applicationName;
            this.applicationInfo = applicationInfo;
            try {
                applicationNamePinyin = PinyinHelper.toHanYuPinyinString(this.applicationName, outputFormat, "", true);
            } catch (BadHanyuPinyinOutputFormatCombination badHanyuPinyinOutputFormatCombination) {
                applicationNamePinyin = applicationName;
                Log.e("SettingsFragment", Utilities.getTraceStackInString(badHanyuPinyinOutputFormatCombination));
            }
            this.isChecked = false;
        }

        Drawable getIcon(PackageManager pm) {
            if (applicationIcon == null) {
                applicationIcon = pm.getApplicationIcon(applicationInfo);
            }
            return applicationIcon;
        }

        @Override
        public int compareTo(AppInformation other) {
            if (other == null) return 1;
            if (this.isChecked && !other.isChecked) {
                return -1;
            } else if (!this.isChecked && other.isChecked) {
                return 1;
            } else {
                return this.applicationNamePinyin.compareTo(other.applicationNamePinyin);
            }
        }
    }

    static class ViewHolder {
        TextView textView;
        ImageView imageView;
        CheckBox checkBox;

        public ViewHolder(View v) {
            textView = v.findViewById(R.id.name);
            imageView = v.findViewById(R.id.img);
            checkBox = v.findViewById(R.id.check);
        }
    }
}