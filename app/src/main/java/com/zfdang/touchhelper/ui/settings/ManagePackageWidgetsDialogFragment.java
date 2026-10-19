package com.zfdang.touchhelper.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.Settings;
import com.zfdang.touchhelper.Utilities;

public class ManagePackageWidgetsDialogFragment extends DialogFragment {

    private String TAG = "DialogFragment";

    private EditText editRules;
    private String originalRules;
    private Settings setting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(false);
        }

        View view = inflater.inflate(R.layout.layout_manage_package_widgets, container, false);

        editRules = view.findViewById(R.id.editText_rules);
        setting = Settings.getInstance();
        originalRules = setting.getPackageWidgetsInString();
        if (savedInstanceState == null) {
            editRules.setText(originalRules);
        }

        Button btReset = view.findViewById(R.id.button_reset);
        if (btReset != null) {
            btReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    editRules.setText(originalRules);
                }
            });
        }

        Button btCopy = view.findViewById(R.id.button_copy);
        if (btCopy != null) {
            btCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Context context = getContext();
                    if (context == null) return;
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("Rules: Widget in Packages", editRules.getText().toString());
                        clipboard.setPrimaryClip(clip);
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                            Utilities.toast("规则已复制到剪贴板!");
                        }
                    }
                }
            });
        }

        Button btPaste = view.findViewById(R.id.button_paste);
        if (btPaste != null) {
            btPaste.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Context context = getContext();
                    if (context == null) return;
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null && clipboard.hasPrimaryClip()) {
                        ClipData clipData = clipboard.getPrimaryClip();
                        if (clipData != null && clipData.getItemCount() > 0) {
                            CharSequence text = clipData.getItemAt(0).coerceToText(context);
                            if (text != null && text.length() > 0) {
                                editRules.setText(text.toString());
                                Utilities.toast("已从剪贴板获取规则!");
                                return;
                            }
                        }
                    }
                    Utilities.toast("未从剪贴板发现规则!");
                }
            });
        }

        Button btCancel = view.findViewById(R.id.button_widgets_cancel);
        if (btCancel != null) {
            btCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Utilities.toast("修改已取消");
                    dismiss();
                }
            });
        }

        Button btRules = view.findViewById(R.id.button_widgets_rules);
        if (btRules != null) {
            btRules.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        String url = "https://touchhelper.zfdang.com/rules";
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        Utilities.toast("未找到可打开网页的应用！");
                    }
                }
            });
        }

        Button btConfirm = view.findViewById(R.id.button_widgets_confirm);
        if (btConfirm != null) {
            btConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean result = setting.setPackageWidgetsInString(editRules.getText().toString());
                    if (result) {
                        Utilities.toast("规则已保存!");
                        com.zfdang.touchhelper.TouchHelperService.dispatchAction(com.zfdang.touchhelper.TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY);
                        dismiss();
                    } else {
                        Utilities.toast("规则有误，请修改后再次保存!");
                    }
                }
            });
        }

        return view;
    }
}
