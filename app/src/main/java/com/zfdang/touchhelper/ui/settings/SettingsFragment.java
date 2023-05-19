package com.zfdang.touchhelper.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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

import static android.content.Context.WINDOW_SERVICE;

public class SettingsFragment extends PreferenceFragmentCompat {

    private final String TAG = getClass().getName();
    LayoutInflater inflater;
    PackageManager packageManager;
    WindowManager winManager;

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

        winManager = (WindowManager) getActivity().getSystemService(WINDOW_SERVICE);
        inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        packageManager = getActivity().getPackageManager();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        // get the height of BottomNavigationView
        int resourceId = getResources().getIdentifier("design_bottom_navigation_height", "dimen", getActivity().getPackageName());
        int height = 147;
        if (resourceId > 0) {
            height = getResources().getDimensionPixelSize(resourceId);
        }

        // set bottom padding for the preference fragment, so that all parts could be shown properly
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom() + height);
        return view;
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
            whitelist.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // find all packages
                    List<String> list = new ArrayList<>();
                    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
                    for (ResolveInfo e : ResolveInfoList) {
//                        Log.d(TAG, "launcher - " + e.activityInfo.packageName);
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
                            Log.e(TAG, Utilities.getTraceStackInString(e));
                        }
                    }

                    // sort apps
                    Collections.sort(listApp);

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
                                TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_PACKAGE);

                                dialog.dismiss();
                            }
                        });
                    }

                    // show the dialog
                    dialog.show();
                    return true;
                } // public boolean onPreferenceClick(Preference preference) {

                final HanyuPinyinOutputFormat outputFormat = new HanyuPinyinOutputFormat();
                class AppInformation implements Comparable{
                    String packageName;
                    String applicationName;
                    String applicationNamePinyin;
                    Drawable applicationIcon;
                    boolean isChecked;

                    public AppInformation(String packageName, String applicationName, Drawable applicationIcon) {
                        this.packageName = packageName;
                        this.applicationName = applicationName;
                        try {
                            applicationNamePinyin = PinyinHelper.toHanYuPinyinString(this.applicationName, outputFormat, "", true);
                        } catch (BadHanyuPinyinOutputFormatCombination badHanyuPinyinOutputFormatCombination) {
                            applicationNamePinyin = applicationName;
                            Log.e(TAG, Utilities.getTraceStackInString(badHanyuPinyinOutputFormatCombination));
                        }
                        this.applicationIcon = applicationIcon;
                        this.isChecked = false;
                    }

                    @Override
                    public int compareTo(Object o) {
                        AppInformation other = (AppInformation) o;

                        if(this.isChecked && !other.isChecked) {
                            return -11;
                        } else if (!this.isChecked && other.isChecked) {
                            return 1;
                        } else {
                            //
                            return this.applicationNamePinyin.compareTo(other.applicationNamePinyin);
                        }
                    }
                } // class AppInformation

                class ViewHolder {
                    TextView textView;
                    ImageView imageView;
                    CheckBox checkBox;

                    public ViewHolder(View v) {
                        textView = v.findViewById(R.id.name);
                        imageView = v.findViewById(R.id.img);
                        checkBox = v.findViewById(R.id.check);
                    }
                } // class ViewHolder {

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
                HashSet<String> results = (HashSet<String>) newValue;
//                Log.d(TAG, "size " + results.size());

                // update activity widgets
                Set<String> keys = new HashSet<>(mapActivityWidgets.keySet());
                for(String key: keys){
                    if(!results.contains(key)) {
                        // this key is not selected to keep, remove the entry
                        mapActivityWidgets.remove(key);
                    }
                }
                Settings.getInstance().setPackageWidgets(mapActivityWidgets);

                // refresh MultiSelectListPreference
                updateMultiSelectListPreferenceEntries(activity_widgets, mapActivityWidgets.keySet());

                // send message to accessibility service
                TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY);

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
                HashSet<String> results = (HashSet<String>) newValue;
//                Log.d(TAG, "size " + results.size());

                // update activity widgets
                Set<String> keys = new HashSet<>(mapActivityPositions.keySet());
                for(String key: keys){
                    if(!results.contains(key)) {
                        // this key is not selected to keep, remove the entry
                        mapActivityPositions.remove(key);
                    }
                }
                Settings.getInstance().setPackagePositions(mapActivityPositions);

                // refresh MultiSelectListPreference
                updateMultiSelectListPreferenceEntries(activity_positions, mapActivityPositions.keySet());

                // send message to accessibility service
                TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY);

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
}