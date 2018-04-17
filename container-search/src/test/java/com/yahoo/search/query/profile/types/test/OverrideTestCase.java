// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests overriding of field values
 *
 * @author bratseth
 */
public class OverrideTestCase {

    private QueryProfileTypeRegistry registry;

    private QueryProfileType type, user;

    @Before
    public void setUp() {
        type=new QueryProfileType(new ComponentId("testtype"));
        user=new QueryProfileType(new ComponentId("user"));
        registry=new QueryProfileTypeRegistry();
        registry.register(type);
        registry.register(user);

        addTypeFields(type);
        addUserFields(user);
    }

    private void addTypeFields(QueryProfileType type) {
        boolean overridable=true;
        type.addField(new FieldDescription("myString", FieldType.fromString("string",registry),false,!overridable));
        type.addField(new FieldDescription("myInteger",FieldType.fromString("integer",registry)));
        type.addField(new FieldDescription("myLong",FieldType.fromString("long",registry)));
        type.addField(new FieldDescription("myFloat",FieldType.fromString("float",registry)));
        type.addField(new FieldDescription("myDouble",FieldType.fromString("double",registry)));
        type.addField(new FieldDescription("myQueryProfile",FieldType.fromString("query-profile",registry)));
        type.addField(new FieldDescription("myUserQueryProfile", FieldType.fromString("query-profile:user",registry),false,!overridable));
    }

    private void addUserFields(QueryProfileType user) {
        boolean overridable=true;
        user.addField(new FieldDescription("myUserString",FieldType.fromString("string",registry),false,!overridable));
        user.addField(new FieldDescription("myUserInteger",FieldType.fromString("integer",registry)));
    }

    /** Check that a simple non-overridable string cannot be overridden */
    @Test
    public void testSimpleUnoverridable() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfile test=new QueryProfile("test");
        test.setType(type);
        test.set("myString","finalString", (QueryProfileRegistry)null);
        registry.register(test);
        registry.freeze();

        // Assert request assignment does not work
        Query query = new Query(HttpRequest.createTestRequest("?myString=newValue", Method.GET), registry.compile().getComponent("test"));
        assertEquals(0,query.errors().size());
        assertEquals("finalString",query.properties().get("myString"));

        // Assert direct assignment does not work
        query.properties().set("myString","newValue");
        assertEquals("finalString",query.properties().get("myString"));
    }

    /** Check that a query profile cannot be overridden */
    @Test
    public void testUnoverridableQueryProfile() {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile test = new QueryProfile("test");
        test.setType(type);
        registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserInteger",1, registry);
        myUser.set("myUserString","userValue", registry);
        test.set("myUserQueryProfile",myUser, registry);
        registry.register(myUser);

        QueryProfile otherUser = new QueryProfile("otherUser");
        otherUser.setType(user);
        otherUser.set("myUserInteger", 2, registry);
        registry.register(otherUser);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        Query query = new Query(HttpRequest.createTestRequest("?myUserQueryprofile=otherUser", Method.GET), cRegistry.getComponent("test"));
        assertEquals(0,query.errors().size());
        assertEquals(1,query.properties().get("myUserQueryProfile.myUserInteger"));
    }

    /** Check that non-overridables are protected also in nested untyped references */
    @Test
    public void testUntypedNestedUnoverridable() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfile topMap = new QueryProfile("topMap");
        registry.register(topMap);

        QueryProfile subMap=new QueryProfile("topSubMap");
        topMap.set("subMap",subMap, registry);
        registry.register(subMap);

        QueryProfile test = new QueryProfile("test");
        test.setType(type);
        subMap.set("test",test, registry);
        registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserString","finalValue", registry);
        test.set("myUserQueryProfile",myUser, registry);
        registry.register(myUser);

        registry.freeze();
        Query query = new Query(HttpRequest.createTestRequest("?subMap.test.myUserQueryProfile.myUserString=newValue", Method.GET), registry.compile().getComponent("topMap"));
        assertEquals(0,query.errors().size());
        assertEquals("finalValue",query.properties().get("subMap.test.myUserQueryProfile.myUserString"));

        query.properties().set("subMap.test.myUserQueryProfile.myUserString","newValue");
        assertEquals("finalValue",query.properties().get("subMap.test.myUserQueryProfile.myUserString"));
    }

    /** Tests overridability in an inherited field */
    @Test
    public void testInheritedNonOverridableInType() {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile test=new QueryProfile("test");
        test.setType(type);
        test.set("myString","finalString", (QueryProfileRegistry)null);
        registry.register(test);

        QueryProfile profile=new QueryProfile("profile");
        profile.addInherited(test);
        registry.register(profile);

        registry.freeze();

        Query query = new Query(HttpRequest.createTestRequest("?myString=newString", Method.GET), registry.compile().getComponent("test"));

        assertEquals(0,query.errors().size());
        assertEquals("finalString",query.properties().get("myString"));

        query.properties().set("myString","newString");
        assertEquals("finalString",query.properties().get("myString"));
    }

    /** Tests overridability in an inherited field */
    @Test
    public void testInheritedNonOverridableInProfile() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfile test = new QueryProfile("test");
        test.setType(type);
        test.set("myInteger", 1, registry);
        test.setOverridable("myInteger", false, null);
        registry.register(test);

        QueryProfile profile=new QueryProfile("profile");
        profile.addInherited(test);
        registry.register(profile);

        registry.freeze();

        Query query = new Query(HttpRequest.createTestRequest("?myInteger=32", Method.GET), registry.compile().getComponent("test"));

        assertEquals(0,query.errors().size());
        assertEquals(1,query.properties().get("myInteger"));

        query.properties().set("myInteger",32);
        assertEquals(1,query.properties().get("myInteger"));
    }

}
