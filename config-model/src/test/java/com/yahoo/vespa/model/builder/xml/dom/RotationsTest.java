// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.provision.RotationName;
import com.yahoo.text.XML;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class RotationsTest {

    @Test
    public void invalid_ids() {
        assertInvalid("<rotation/>"); // Unset
        assertInvalid("<rotation id=''/>"); // Blank
        assertInvalid("<rotation id='FOO'/>"); // Uppercaes
        assertInvalid("<rotation id='123'/>"); // Starting with non-character
        assertInvalid("<rotation id='foo!'/>"); // Non-alphanumeric
        assertInvalid("<rotation id='foo--bar'/>"); // Multiple consecutive dashes
        assertInvalid("<rotation id='foo-'/>"); // Trailing dash
        assertInvalid("<rotation id='foooooooooooo'/>"); // Too long
        assertInvalid("<rotation id='foo'/><rotation id='foo'/>"); // Duplicate ID
    }

    @Test
    public void valid_ids() {
        assertEquals(rotations(), xml(""));
        assertEquals(rotations("f"), xml("<rotation id='f'/>"));
        assertEquals(rotations("foo"), xml("<rotation id='foo'/>"));
        assertEquals(rotations("foo-bar"), xml("<rotation id='foo-bar'/>"));
        assertEquals(rotations("foo", "bar"), xml("<rotation id='foo'/><rotation id='bar'/>"));
        assertEquals(rotations("fooooooooooo"), xml("<rotation id='fooooooooooo'/>"));
    }

    private static Set<RotationName> rotations(String... rotation) {
        return Arrays.stream(rotation).map(RotationName::from).collect(Collectors.toSet());
    }

    private static void assertInvalid(String rotations) {
        try {
            xml(rotations);
            fail("Expected exception for input '" + rotations + "'");
        } catch (IllegalArgumentException ignored) {}
    }

    private static Set<RotationName> xml(String rotations) {
        return Rotations.from(XML.getDocument("<rotations>" + rotations + "</rotations>")
                                 .getDocumentElement());
    }

}
