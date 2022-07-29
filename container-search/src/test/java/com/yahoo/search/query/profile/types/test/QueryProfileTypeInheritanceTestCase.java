// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.component.ComponentId;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class QueryProfileTypeInheritanceTestCase {

    private QueryProfileTypeRegistry registry;

    private QueryProfileType type, typeStrict, user, userStrict;

    @BeforeEach
    public void setUp() {
        type=new QueryProfileType(new ComponentId("testtype"));
        typeStrict=new QueryProfileType(new ComponentId("testtypeStrict"));
        typeStrict.setStrict(true);
        user=new QueryProfileType(new ComponentId("user"));
        userStrict=new QueryProfileType(new ComponentId("userStrict"));
        userStrict.setStrict(true);
        registry=new QueryProfileTypeRegistry();
        registry.register(type);
        registry.register(typeStrict);
        registry.register(user);
        registry.register(userStrict);

        addTypeFields(type);
        type.addField(new FieldDescription("myUserQueryProfile", FieldType.fromString("query-profile:user",registry)));
        addTypeFields(typeStrict);
        typeStrict.addField(new FieldDescription("myUserQueryProfile",FieldType.fromString("query-profile:userStrict",registry)));
        addUserFields(user);
        addUserFields(userStrict);
    }

    private void addTypeFields(QueryProfileType type) {
        type.addField(new FieldDescription("myString", FieldType.fromString("string",registry)));
        type.addField(new FieldDescription("myInteger",FieldType.fromString("integer",registry)));
        type.addField(new FieldDescription("myLong",FieldType.fromString("long",registry)));
        type.addField(new FieldDescription("myFloat",FieldType.fromString("float",registry)));
        type.addField(new FieldDescription("myDouble",FieldType.fromString("double",registry)));
        type.addField(new FieldDescription("myQueryProfile",FieldType.fromString("query-profile",registry)));
    }

    private void addUserFields(QueryProfileType user) {
        user.addField(new FieldDescription("myUserString",FieldType.fromString("string",registry),true,false));
        user.addField(new FieldDescription("myUserInteger",FieldType.fromString("integer",registry)));
    }

    @Test
    void testInheritance() {
        type.inherited().add(user);
        type.freeze();
        user.freeze();

        assertFalse(type.isOverridable("myUserString"));
        assertEquals("myUserInteger", type.getField("myUserInteger").getName());

        QueryProfile test = new QueryProfile("test");
        test.setType(type);

        test.set("myUserInteger", "37", (QueryProfileRegistry) null);
        test.set("myUnknownInteger", "38", (QueryProfileRegistry) null);
        CompiledQueryProfile ctest = test.compile(null);

        assertEquals(37, ctest.get("myUserInteger"));
        assertEquals("38", ctest.get("myUnknownInteger"));
    }

    @Test
    void testInheritanceStrict() {
        typeStrict.inherited().add(userStrict);
        typeStrict.freeze();
        userStrict.freeze();

        QueryProfile test = new QueryProfile("test");
        test.setType(typeStrict);

        test.set("myUserInteger", "37", (QueryProfileRegistry) null);
        try {
            test.set("myUnknownInteger", "38", (QueryProfileRegistry) null);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("'myUnknownInteger' is not declared in query profile type 'testtypeStrict', and the type is strict",
                    e.getCause().getMessage());
        }

        assertEquals(37, test.get("myUserInteger"));
        assertNull(test.get("myUnknownInteger"));
    }

    @Test
    void testStrictIsInherited() {
        type.inherited().add(userStrict);
        type.freeze();
        userStrict.freeze();

        QueryProfile test = new QueryProfile("test");
        test.setType(type);

        test.set("myUserInteger", "37", (QueryProfileRegistry) null);
        try {
            test.set("myUnknownInteger", "38", (QueryProfileRegistry) null);
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("'myUnknownInteger' is not declared in query profile type 'testtype', and the type is strict",
                    e.getCause().getMessage());
        }

        CompiledQueryProfile ctest = test.compile(null);
        assertEquals(37, ctest.get("myUserInteger"));
        assertNull(ctest.get("myUnknownInteger"));
    }

}
