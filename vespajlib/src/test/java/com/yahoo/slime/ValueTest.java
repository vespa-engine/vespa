// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class ValueTest {
    @Test
    public void verifyObjectEquality() {
        Slime slimeLeft = new Slime();
        Cursor left = slimeLeft.setObject();
        left.setString("a", "A");
        left.setString("b", "B");

        Slime slimeRight = new Slime();
        Cursor right = slimeRight.setObject();
        right.setString("b", "B");
        right.setString("a", "A");

        assertTrue(left.equalTo(right));
        assertTrue(right.equalTo(left));
        assertTrue(left.equalTo(left));

        right.setString("c", "C");
        assertFalse(left.equalTo(right));
        assertFalse(right.equalTo(left));
    }

    @Test
    public void verifyArrayEquality() {
        Slime slimeLeft = new Slime();
        Cursor left = slimeLeft.setArray();
        left.addArray().addString("a");
        left.addArray().addString("b");

        Slime slimeRight = new Slime();
        Cursor right = slimeRight.setArray();
        right.addArray().addString("a");
        right.addArray().addString("b");

        assertTrue(left.equalTo(right));
        assertTrue(right.equalTo(left));
        assertTrue(left.equalTo(left));

        right.addArray().addString("c");
        assertFalse(left.equalTo(right));
        assertFalse(right.equalTo(left));

        // Order matters
        Slime slimeRight2 = new Slime();
        Cursor right2 = slimeRight2.setObject();
        right2.addArray().addString("b");
        right2.addArray().addString("a");
        assertFalse(left.equalTo(right2));
        assertFalse(right2.equalTo(left));
    }

    @Test
    public void verifyPrimitiveEquality() {
        Slime left = new Slime();
        Cursor leftObject = left.setObject();
        populateWithPrimitives(leftObject, true);

        Slime right = new Slime();
        Cursor rightObject = right.setObject();
        populateWithPrimitives(rightObject, true);

        assertEqualTo(left.get().field("bool"), right.get().field("bool"));
        assertEqualTo(left.get().field("nix"), right.get().field("nix"));
        assertEqualTo(left.get().field("long"), right.get().field("long"));
        assertEqualTo(left.get().field("string"), right.get().field("string"));
        assertEqualTo(left.get().field("data"), right.get().field("data"));
        assertEqualTo(left.get(), right.get());

        assertNotEqualTo(left.get().field("bool"), right.get().field("nix"));
        assertNotEqualTo(left.get().field("nix"), right.get().field("string"));
        assertNotEqualTo(left.get().field("string"), right.get().field("data"));
        assertNotEqualTo(left.get().field("bool"), right.get().field("data"));
        assertNotEqualTo(left.get().field("bool"), right.get().field("long"));
    }

    @Test
    public void verifyPrimitiveNotEquality() {
        Slime left = new Slime();
        Cursor leftObject = left.setObject();
        populateWithPrimitives(leftObject, true);

        Slime right = new Slime();
        Cursor rightObject = right.setObject();
        populateWithPrimitives(rightObject, false);

        assertNotEqualTo(left.get().field("bool"), right.get().field("bool"));
        assertEqualTo(left.get().field("nix"), right.get().field("nix"));
        assertNotEqualTo(left.get().field("long"), right.get().field("long"));
        assertNotEqualTo(left.get().field("string"), right.get().field("string"));
        assertNotEqualTo(left.get().field("data"), right.get().field("data"));
        assertNotEqualTo(left.get(), right.get());
    }

    @Test
    public void testNixEquality() {
        assertEqualTo(NixValue.invalid(), NixValue.invalid());
        assertEqualTo(NixValue.instance(), NixValue.instance());
        assertNotEqualTo(NixValue.instance(), NixValue.invalid());
        assertNotEqualTo(NixValue.invalid(), NixValue.instance());
    }

    private void populateWithPrimitives(Cursor cursor, boolean enabled) {
        cursor.setBool("bool", enabled ? true : false);
        cursor.setNix("nix");
        cursor.setLong("long", enabled ? 1 : 0);
        cursor.setString("string", enabled ? "enabled" : "disabled");
        cursor.setDouble("double", enabled ? 1.5 : 0.5);
        cursor.setData("data", (enabled ? "edata" : "ddata").getBytes(StandardCharsets.UTF_8));
    }

    private void assertEqualTo(Inspector left, Inspector right) {
        assertTrue("'" + left + "' is not equal to '" + right + "'", left.equalTo(right));
    }

    private void assertNotEqualTo(Inspector left, Inspector right) {
        assertTrue("'" + left + "' is equal to '" + right + "'", !left.equalTo(right));
    }
}