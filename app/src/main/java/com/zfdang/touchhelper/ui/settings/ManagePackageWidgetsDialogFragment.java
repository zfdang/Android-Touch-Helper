package com.zfdang.touchhelper.ui.settings;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.zfdang.touchhelper.R;
import com.zfdang.touchhelper.Settings;
import com.zfdang.touchhelper.Utilities;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class ManagePackageWidgetsDialogFragment extends DialogFragment {

    private EditText editRules;
    private String originalRules;
    private Settings setting;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        //
        getDialog().setCanceledOnTouchOutside(false);

        setting = Settings.getInstance();

        originalRules = setting.getPackageWidgetsInString();

        View view = inflater.inflate(R.layout.layout_manage_package_widgets, container, false);

        editRules = view.findViewById(R.id.editText_rules);
        editRules.setText(originalRules);

        Button btReset = view.findViewById(R.id.button_reset);
        if(btReset != null) {
            btReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    editRules.setText(originalRules);
                }
            });
        }

        Button btCopy = view.findViewById(R.id.button_copy);
        if(btCopy != null) {
            btCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Gets a handle to the clipboard service.
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Rules: Widget in Packages", editRules.getText().toString());
                    clipboard.setPrimaryClip(clip);

                    Utilities.toast("规则已复制到剪贴板!");
                }
            });
        }


        Button btPaste = view.findViewById(R.id.button_paste);
        if(btPaste != null) {
            btPaste.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clipData = clipboard.getPrimaryClip();
                    if(clipData != null && clipData.getItemCount() > 0) {
                        CharSequence pasteData = clipData.getItemAt(0).getText();
                        editRules.setText(pasteData);
                        Utilities.toast("已从剪贴板获取规则!");
                    } else {
                        Utilities.toast("未从剪贴板发现规则!");
                    }

                    editRules.setText(originalRules);
                }
            });
        }


        Button btCancel = view.findViewById(R.id.button_widgets_cancel);
        if(btCancel != null) {
            btCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Utilities.toast("修改已取消");
                    getDialog().dismiss();
                }
            });
        }

        Button btConfirm = view.findViewById(R.id.button_widgets_confirm);
        if(btConfirm != null) {
            btConfirm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    setting.setPackageWidgetsInString(editRules.getText().toString());
                    Utilities.toast("规则已保存");
                    getDialog().dismiss();
                }
            });
        }


        return view;
    }
}
