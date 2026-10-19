package com.zfdang.touchhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;

import com.zfdang.TouchHelperApp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsTest {

    private Settings settings;

    @Before
    public void setUp() {
        settings = Settings.getInstance();
    }

    @Test
    public void getInstance_returnsNonNullSingleton() {
        assertNotNull(settings);
        Settings second = Settings.getInstance();
        assertTrue(settings == second);
    }

    @Test
    public void getPackageWidgets_returnsDefensiveCopy() {
        Map<String, Set<PackageWidgetDescription>> widgets = new HashMap<>();
        Set<PackageWidgetDescription> set = new HashSet<>();
        PackageWidgetDescription desc = new PackageWidgetDescription("com.test", "Act", "Cls", "id", "desc", "text", new Rect(0, 0, 10, 10), true, false);
        set.add(desc);
        widgets.put("com.test", set);

        settings.setPackageWidgets(widgets);

        Map<String, Set<PackageWidgetDescription>> retrieved = settings.getPackageWidgets();
        assertNotNull(retrieved);
        assertTrue(retrieved.containsKey("com.test"));

        // Modifying retrieved map should not affect internal state
        try {
            retrieved.clear();
        } catch (UnsupportedOperationException ignored) {
            // Unmodifiable map is also acceptable
        }

        Map<String, Set<PackageWidgetDescription>> retrievedAgain = settings.getPackageWidgets();
        assertTrue(retrievedAgain.containsKey("com.test"));
    }

    @Test
    public void getPackagePositions_returnsDefensiveCopy() {
        Map<String, PackagePositionDescription> positions = new HashMap<>();
        positions.put("com.test", new PackagePositionDescription("com.test", "Act", 100, 200));

        settings.setPackagePositions(positions);

        Map<String, PackagePositionDescription> retrieved = settings.getPackagePositions();
        assertNotNull(retrieved);
        assertTrue(retrieved.containsKey("com.test"));

        try {
            retrieved.clear();
        } catch (UnsupportedOperationException ignored) {
        }

        Map<String, PackagePositionDescription> retrievedAgain = settings.getPackagePositions();
        assertTrue(retrievedAgain.containsKey("com.test"));
    }

    @Test
    public void getWhitelistPackages_returnsDefensiveCopy() {
        Set<String> whitelist = new HashSet<>();
        whitelist.add("com.example.one");
        whitelist.add("com.example.two");

        settings.setWhitelistPackages(whitelist);

        Set<String> retrieved = settings.getWhitelistPackages();
        retrieved.add("com.example.three");

        Set<String> retrievedAgain = settings.getWhitelistPackages();
        assertFalse(retrievedAgain.contains("com.example.three"));
    }

    @Test
    public void initSettings_handlesCorruptedJsonGracefully() throws Exception {
        Context context = TouchHelperApp.getAppContext();
        SharedPreferences pref = context.getSharedPreferences("TouchHelper_Config", Activity.MODE_PRIVATE);
        pref.edit()
                .putString("PACKAGE_WIDGETS", "{invalid-json-data:::")
                .putString("PACKAGE_POSITIONS", "{\"invalid\"::broken}")
                .apply();

        // Create a new Settings instance via reflection to trigger initSettings()
        Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Settings corruptedSettings = constructor.newInstance();

        assertNotNull(corruptedSettings.getPackageWidgets());
        assertNotNull(corruptedSettings.getPackagePositions());
        assertTrue(corruptedSettings.getPackageWidgets().isEmpty());
        assertTrue(corruptedSettings.getPackagePositions().isEmpty());
    }

    @Test
    public void skipAdPreferences_canBeToggledAndPersisted() {
        settings.setSkipAdNotification(false);
        assertFalse(settings.isSkipAdNotification());
        settings.setSkipAdNotification(true);
        assertTrue(settings.isSkipAdNotification());

        settings.setSkipAdDuration(6);
        assertEquals(6, settings.getSkipAdDuration());

        settings.setDisclosureAccepted(true);
        assertTrue(settings.isDisclosureAccepted());
    }
}
