// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config.test;

import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class TypedProfilesConfigurationTestCase {

    /** Asserts that everything is read correctly from this configuration */
    @Test
    void testIt() {
        var registry = QueryProfileConfigurer.createFromConfigId("file:src/test/java/com/yahoo/search/query/profile/config/test/typed-profiles.cfg");
        var types = registry.getTypeRegistry();

        // Assert that each type was read correctly

        QueryProfileType testType = types.getComponent("testtype");
        assertEquals("testtype", testType.getId().getName());
        assertFalse(testType.isStrict());
        assertFalse(testType.getMatchAsPath());
        assertEquals(7, testType.fields().size());
        assertEquals("myString", testType.getField("myString").getName());
        assertTrue(testType.getField("myString").isMandatory());
        assertTrue(testType.getField("myString").isOverridable());
        assertFalse(testType.getField("myInteger").isMandatory());
        assertFalse(testType.getField("myInteger").isOverridable());
        FieldDescription field = testType.getField("myUserQueryProfile");
        assertEquals("reference to a query profile of type 'user'", field.getType().toInstanceDescription());
        assertTrue(field.getAliases().contains("myqp"));
        assertTrue(field.getAliases().contains("user-profile"));

        QueryProfileType testTypeStrict = types.getComponent("testtypestrict");
        assertTrue(testTypeStrict.isStrict());
        assertTrue(testTypeStrict.getMatchAsPath());
        assertEquals(7, testTypeStrict.fields().size());
        assertEquals("reference to a query profile of type 'userstrict'",
                testTypeStrict.getField("myUserQueryProfile").getType().toInstanceDescription());

        QueryProfileType user = types.getComponent("user");
        assertFalse(user.isStrict());
        assertFalse(user.getMatchAsPath());
        assertEquals(2, user.fields().size());
        assertEquals(String.class, user.getField("myUserString").getType().getValueClass());

        QueryProfileType userStrict = types.getComponent("userstrict");
        assertTrue(userStrict.isStrict());
        assertFalse(userStrict.getMatchAsPath());
        assertEquals(2, userStrict.fields().size());
    }

}
