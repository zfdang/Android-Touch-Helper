package com.zfdang.touchhelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java matching rules used by {@link TouchHelperServiceImpl}.
 *
 * <p>This class deliberately avoids every Android framework call so the hot matching logic
 * can be unit tested on the JVM and so the accessibility traversal can call it once per node
 * with already-extracted strings instead of re-reading {@code CharSequence}s per keyword.
 */
public final class SkipAdRules {

    /** A node text may be at most this many characters longer than the keyword it contains. */
    static final int KEYWORD_MAX_EXTRA_LENGTH = 6;

    private SkipAdRules() {
    }

    /**
     * Splits the user-entered keyword string on whitespace and drops blank entries.
     * A blank keyword would otherwise match every short label on screen.
     */
    public static List<String> parseKeywords(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        String[] parts = text.trim().split("\\s+");
        ArrayList<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty() && !result.contains(part)) {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * Finds the first keyword contained in either the node text or its content description.
     *
     * @param text         node text, may be null
     * @param description  node content description, may be null
     * @param keywords     keywords in priority order
     * @param excludedText a label that must never be treated as a match (this app's own name)
     * @return the matched keyword, or null when nothing matches
     */
    public static String findKeyword(String text, String description, List<String> keywords, String excludedText) {
        boolean hasText = text != null && !text.isEmpty();
        boolean hasDescription = description != null && !description.isEmpty();
        if (!hasText && !hasDescription) {
            return null;
        }
        if (keywords == null) {
            return null;
        }
        for (int i = 0, n = keywords.size(); i < n; i++) {
            String keyword = keywords.get(i);
            if (keyword == null || keyword.isEmpty()) {
                continue;
            }
            if ((hasText && matchesKeyword(text, keyword, excludedText))
                    || (hasDescription && matchesKeyword(description, keyword, excludedText))) {
                return keyword;
            }
        }
        return null;
    }

    private static boolean matchesKeyword(String label, String keyword, String excludedText) {
        return label.length() <= keyword.length() + KEYWORD_MAX_EXTRA_LENGTH
                && label.contains(keyword)
                && !label.equals(excludedText);
    }

    /**
     * Finds the first widget rule matching a node. Rules are compared in the same order as
     * before: exact screen bounds, then view id, then content description, then text.
     * All rule fields are null-safe because rules imported from JSON may omit fields.
     *
     * @return the matching rule, or null
     */
    public static PackageWidgetDescription findWidget(int left, int top, int right, int bottom,
                                                      String id, String description, String text,
                                                      Collection<PackageWidgetDescription> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (PackageWidgetDescription rule : rules) {
            if (rule == null) {
                continue;
            }
            if (boundsEqual(rule, left, top, right, bottom)) {
                return rule;
            }
            if (id != null && isSet(rule.idName) && id.equals(rule.idName)) {
                return rule;
            }
            if (description != null && isSet(rule.description) && description.contains(rule.description)) {
                return rule;
            }
            if (text != null && isSet(rule.text) && text.contains(rule.text)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean boundsEqual(PackageWidgetDescription rule, int left, int top, int right, int bottom) {
        return rule.position != null
                && rule.position.left == left
                && rule.position.top == top
                && rule.position.right == right
                && rule.position.bottom == bottom;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isEmpty();
    }
}
