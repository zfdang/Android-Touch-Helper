package com.zfdang.touchhelper;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class Utilities {
    static final String TAG = "Utilities";

    static public void printNodeStack(AccessibilityNodeInfo node) {
        Log.d(TAG, "Show Node information: ");
        String indent = "";
        while (node != null) {
            Log.d(TAG, indent + "class = " + node.getClassName() + " id = "  +node.getWindowId() + " label = " + node.getText());
            node = node.getParent();
            indent += "  ";
        }
    }

}
