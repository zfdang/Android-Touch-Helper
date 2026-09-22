package com.zfdang.touchhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure JVM tests for the matching rules; no Android runtime needed. */
public class SkipAdRulesTest {

    private static final String SELF = "开屏跳过";
    private static final List<String> KEYWORDS = Arrays.asList("跳过", "Skip");

    // ---------------------------------------------------------------- parseKeywords

    @Test
    public void parseKeywords_splitsOnWhitespaceAndDropsBlanks() {
        assertEquals(Arrays.asList("跳过", "Skip", "关闭"), SkipAdRules.parseKeywords("  跳过   Skip\t关闭 "));
    }

    @Test
    public void parseKeywords_removesDuplicates() {
        assertEquals(Collections.singletonList("跳过"), SkipAdRules.parseKeywords("跳过 跳过"));
    }

    @Test
    public void parseKeywords_handlesNullAndBlank() {
        assertTrue(SkipAdRules.parseKeywords(null).isEmpty());
        assertTrue(SkipAdRules.parseKeywords("").isEmpty());
        assertTrue(SkipAdRules.parseKeywords("   ").isEmpty());
    }

    // ---------------------------------------------------------------- findKeyword

    @Test
    public void findKeyword_matchesText() {
        assertEquals("跳过", SkipAdRules.findKeyword("跳过广告", null, KEYWORDS, SELF));
    }

    @Test
    public void findKeyword_matchesDescriptionWhenTextIsEmpty() {
        assertEquals("Skip", SkipAdRules.findKeyword("", "Skip Ad", KEYWORDS, SELF));
    }

    @Test
    public void findKeyword_returnsFirstKeywordInPriorityOrder() {
        assertEquals("跳过", SkipAdRules.findKeyword("Skip 跳过", null, KEYWORDS, SELF));
    }

    @Test
    public void findKeyword_rejectsLabelsTooMuchLongerThanKeyword() {
        // keyword length 2 + 6 = 8 chars allowed
        assertEquals("跳过", SkipAdRules.findKeyword("点击跳过广告啦啦", null, KEYWORDS, SELF));
        assertNull(SkipAdRules.findKeyword("点击跳过广告啦啦啦", null, KEYWORDS, SELF));
    }

    @Test
    public void findKeyword_ignoresOwnAppName() {
        // the app's own label contains the keyword but must never be clicked
        assertNull(SkipAdRules.findKeyword(SELF, null, KEYWORDS, SELF));
        assertNull(SkipAdRules.findKeyword(null, SELF, KEYWORDS, SELF));
    }

    @Test
    public void findKeyword_returnsNullWithoutTextOrDescription() {
        assertNull(SkipAdRules.findKeyword(null, null, KEYWORDS, SELF));
        assertNull(SkipAdRules.findKeyword("", "", KEYWORDS, SELF));
    }

    @Test
    public void findKeyword_returnsNullForEmptyOrNullKeywordList() {
        assertNull(SkipAdRules.findKeyword("跳过", null, Collections.<String>emptyList(), SELF));
        assertNull(SkipAdRules.findKeyword("跳过", null, null, SELF));
    }

    @Test
    public void findKeyword_skipsBlankKeywordsInsteadOfMatchingEverything() {
        List<String> withBlank = new ArrayList<>(Arrays.asList("", "跳过"));
        assertNull(SkipAdRules.findKeyword("确定", null, withBlank, SELF));
        assertEquals("跳过", SkipAdRules.findKeyword("跳过", null, withBlank, SELF));
    }

    // ---------------------------------------------------------------- findWidget

    private static PackageWidgetDescription rule(int l, int t, int r, int b, String id, String desc, String text) {
        PackageWidgetDescription d = new PackageWidgetDescription();
        d.position = new Rect();
        d.position.left = l;
        d.position.top = t;
        d.position.right = r;
        d.position.bottom = b;
        d.idName = id;
        d.description = desc;
        d.text = text;
        return d;
    }

    @Test
    public void findWidget_matchesExactBounds() {
        PackageWidgetDescription rule = rule(10, 20, 110, 60, "", "", "");
        assertSame(rule, SkipAdRules.findWidget(10, 20, 110, 60, null, null, null, Collections.singleton(rule)));
        assertNull(SkipAdRules.findWidget(11, 20, 110, 60, null, null, null, Collections.singleton(rule)));
    }

    @Test
    public void findWidget_matchesViewId() {
        PackageWidgetDescription rule = rule(0, 0, 0, 0, "com.app:id/skip", "", "");
        assertSame(rule, SkipAdRules.findWidget(1, 2, 3, 4, "com.app:id/skip", null, null, Collections.singleton(rule)));
        assertNull(SkipAdRules.findWidget(1, 2, 3, 4, "com.app:id/other", null, null, Collections.singleton(rule)));
    }

    @Test
    public void findWidget_matchesDescriptionAndTextByContains() {
        PackageWidgetDescription byDesc = rule(0, 0, 0, 0, "", "关闭广告", "");
        PackageWidgetDescription byText = rule(0, 0, 0, 0, "", "", "跳过");
        List<PackageWidgetDescription> rules = Arrays.asList(byDesc, byText);
        assertSame(byDesc, SkipAdRules.findWidget(1, 2, 3, 4, null, "点击关闭广告", null, rules));
        assertSame(byText, SkipAdRules.findWidget(1, 2, 3, 4, null, null, "跳过 5s", rules));
    }

    @Test
    public void findWidget_emptyRuleFieldsNeverMatch() {
        // a rule with only bounds must not match a node just because both id/desc/text are empty
        PackageWidgetDescription rule = rule(10, 20, 110, 60, "", "", "");
        assertNull(SkipAdRules.findWidget(1, 2, 3, 4, "", "", "", Collections.singleton(rule)));
    }

    @Test
    public void findWidget_toleratesNullFieldsFromImportedJson() {
        PackageWidgetDescription rule = new PackageWidgetDescription();
        rule.position = null;
        rule.idName = null;
        rule.description = null;
        rule.text = "跳过";
        assertSame(rule, SkipAdRules.findWidget(1, 2, 3, 4, "id", "desc", "跳过", Collections.singleton(rule)));
        assertNull(SkipAdRules.findWidget(1, 2, 3, 4, "id", "desc", "其他", Collections.singleton(rule)));
    }

    @Test
    public void findWidget_handlesNullOrEmptyRuleSets() {
        assertNull(SkipAdRules.findWidget(1, 2, 3, 4, "id", "desc", "跳过", null));
        assertNull(SkipAdRules.findWidget(1, 2, 3, 4, "id", "desc", "跳过", Collections.<PackageWidgetDescription>emptySet()));
        assertNull(SkipAdRules.findWidget(1, 2, 3, 4, "id", "desc", "跳过", Collections.<PackageWidgetDescription>singleton(null)));
    }
}
