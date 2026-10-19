package com.zfdang.touchhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackagePositionDescriptionTest {

    @Test
    public void defaultConstructor_initializesDefaultFields() {
        PackagePositionDescription desc = new PackagePositionDescription();
        assertNotNull(desc.packageName);
        assertNotNull(desc.activityName);
        assertEquals("", desc.packageName);
        assertEquals("", desc.activityName);
        assertEquals(0, desc.x);
        assertEquals(0, desc.y);
    }

    @Test
    public void parameterizedConstructor_handlesNullsSafely() {
        PackagePositionDescription desc = new PackagePositionDescription(null, null, 100, 200);
        assertEquals("", desc.packageName);
        assertEquals("", desc.activityName);
        assertEquals(100, desc.x);
        assertEquals(200, desc.y);
    }

    @Test
    public void copyConstructor_clonesFieldsAccurately() {
        PackagePositionDescription original = new PackagePositionDescription("com.example.app", "com.example.app.MainActivity", 150, 250);
        PackagePositionDescription copy = new PackagePositionDescription(original);

        assertEquals("com.example.app", copy.packageName);
        assertEquals("com.example.app.MainActivity", copy.activityName);
        assertEquals(150, copy.x);
        assertEquals(250, copy.y);
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
    }

    @Test
    public void copyConstructor_handlesNullArgument() {
        PackagePositionDescription copy = new PackagePositionDescription(null);
        assertEquals("", copy.packageName);
        assertEquals("", copy.activityName);
        assertEquals(0, copy.x);
        assertEquals(0, copy.y);
    }

    @Test
    public void equalsAndHashCode_satisfyContract() {
        PackagePositionDescription desc1 = new PackagePositionDescription("pkg", "act", 10, 20);
        PackagePositionDescription desc2 = new PackagePositionDescription("pkg", "act", 10, 20);
        PackagePositionDescription descDiffPkg = new PackagePositionDescription("pkg2", "act", 10, 20);
        PackagePositionDescription descDiffAct = new PackagePositionDescription("pkg", "act2", 10, 20);
        PackagePositionDescription descDiffX = new PackagePositionDescription("pkg", "act", 11, 20);
        PackagePositionDescription descDiffY = new PackagePositionDescription("pkg", "act", 10, 21);

        // Reflexive
        assertEquals(desc1, desc1);
        // Symmetric
        assertEquals(desc1, desc2);
        assertEquals(desc2, desc1);
        assertEquals(desc1.hashCode(), desc2.hashCode());

        // Null and different type
        assertNotEquals(null, desc1);
        assertNotEquals("string", desc1);

        // Difference in each field
        assertNotEquals(desc1, descDiffPkg);
        assertNotEquals(desc1, descDiffAct);
        assertNotEquals(desc1, descDiffX);
        assertNotEquals(desc1, descDiffY);
    }

    @Test
    public void toString_containsFieldValues() {
        PackagePositionDescription desc = new PackagePositionDescription("com.test", "MainActivity", 50, 75);
        String s = desc.toString();
        assertTrue(s.contains("com.test"));
        assertTrue(s.contains("MainActivity"));
        assertTrue(s.contains("50"));
        assertTrue(s.contains("75"));
    }
}
