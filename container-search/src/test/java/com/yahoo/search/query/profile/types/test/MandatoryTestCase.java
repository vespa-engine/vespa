// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.search.test.QueryTestCase;

/**
 * @author bratseth
 */
public class MandatoryTestCase extends junit.framework.TestCase {

    private QueryProfileTypeRegistry registry;

    private QueryProfileType type, user;

    protected @Override void setUp() {
        type=new QueryProfileType(new ComponentId("testtype"));
        user=new QueryProfileType(new ComponentId("user"));
        registry=new QueryProfileTypeRegistry();
        registry.register(type);
        registry.register(user);

        addTypeFields(type);
        addUserFields(user);
    }

    private void addTypeFields(QueryProfileType type) {
        boolean mandatory=true;
        type.addField(new FieldDescription("myString", FieldType.fromString("string",registry), mandatory));
        type.addField(new FieldDescription("myInteger",FieldType.fromString("integer",registry)));
        type.addField(new FieldDescription("myLong",FieldType.fromString("long",registry)));
        type.addField(new FieldDescription("myFloat",FieldType.fromString("float",registry)));
        type.addField(new FieldDescription("myDouble",FieldType.fromString("double",registry)));
        type.addField(new FieldDescription("myQueryProfile",FieldType.fromString("query-profile",registry)));
        type.addField(new FieldDescription("myUserQueryProfile", FieldType.fromString("query-profile:user",registry),mandatory));
    }

    private void addUserFields(QueryProfileType user) {
        boolean mandatory=true;
        user.addField(new FieldDescription("myUserString",FieldType.fromString("string",registry),mandatory));
        user.addField(new FieldDescription("myUserInteger",FieldType.fromString("integer",registry),mandatory));
    }

    public void testMandatoryFullySpecifiedQueryProfile() {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile test=new QueryProfile("test");
        test.setType(type);
        test.set("myString","aString", registry);
        registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserInteger",1, registry);
        myUser.set("myUserString",1, registry);
        test.set("myUserQueryProfile", myUser, registry);
        registry.register(myUser);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        // Fully specified request
        assertError(null, new Query(QueryTestCase.httpEncode("?queryProfile=test"), cRegistry.getComponent("test")));
    }

    public void testMandatoryRequestPropertiesNeeded() {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile test=new QueryProfile("test");
        test.setType(type);
        registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserInteger",1, registry);
        test.set("myUserQueryProfile",myUser, registry);
        registry.register(myUser);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        // Underspecified request 1
        assertError("Incomplete query: Parameter 'myString' is mandatory in query profile 'test' of type 'testtype' but is not set",
                    new Query(HttpRequest.createTestRequest("", Method.GET), cRegistry.getComponent("test")));

        // Underspecified request 2
        assertError("Incomplete query: Parameter 'myUserQueryProfile.myUserString' is mandatory in query profile 'test' of type 'testtype' but is not set",
                    new Query(HttpRequest.createTestRequest("?myString=aString", Method.GET), cRegistry.getComponent("test")));

        // Fully specified request
        assertError(null, new Query(HttpRequest.createTestRequest("?myString=aString&myUserQueryProfile.myUserString=userString", Method.GET), cRegistry.getComponent("test")));
    }

    /** Same as above except the whole thing is nested in maps */
    public void testMandatoryNestedInMaps() {
        QueryProfileRegistry registry = new QueryProfileRegistry();

        QueryProfile topMap=new QueryProfile("topMap");
        registry.register(topMap);

        QueryProfile subMap=new QueryProfile("topSubMap");
        topMap.set("subMap",subMap, registry);
        registry.register(subMap);

        QueryProfile test=new QueryProfile("test");
        test.setType(type);
        subMap.set("test",test, registry);
        registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserInteger",1, registry);
        test.set("myUserQueryProfile",myUser, registry);
        registry.register(myUser);


        CompiledQueryProfileRegistry cRegistry = registry.compile();

        // Underspecified request 1
        assertError("Incomplete query: Parameter 'subMap.test.myString' is mandatory in query profile 'topMap' but is not set",
                    new Query(HttpRequest.createTestRequest("", Method.GET), cRegistry.getComponent("topMap")));

        // Underspecified request 2
        assertError("Incomplete query: Parameter 'subMap.test.myUserQueryProfile.myUserString' is mandatory in query profile 'topMap' but is not set",
                    new Query(HttpRequest.createTestRequest("?subMap.test.myString=aString", Method.GET), cRegistry.getComponent("topMap")));

        // Fully specified request
        assertError(null, new Query(HttpRequest.createTestRequest("?subMap.test.myString=aString&subMap.test.myUserQueryProfile.myUserString=userString", Method.GET), cRegistry.getComponent("topMap")));
    }

    /** Here, no user query profile is referenced in the query profile, but one is chosen in the request */
    public void testMandatoryUserProfileSetInRequest() {
        QueryProfile test=new QueryProfile("test");
        test.setType(type);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserInteger",1, (QueryProfileRegistry)null);

        QueryProfileRegistry registry = new QueryProfileRegistry();
        registry.register(test);
        registry.register(myUser);
        CompiledQueryProfileRegistry cRegistry = registry.compile();

        // Underspecified request 1
        assertError("Incomplete query: Parameter 'myUserQueryProfile' is mandatory in query profile 'test' of type 'testtype' but is not set",
                    new Query(HttpRequest.createTestRequest("?myString=aString", Method.GET), cRegistry.getComponent("test")));

        // Underspecified request 1
        assertError("Incomplete query: Parameter 'myUserQueryProfile.myUserString' is mandatory in query profile 'test' of type 'testtype' but is not set",
                    new Query(HttpRequest.createTestRequest("?myString=aString&myUserQueryProfile=user", Method.GET), cRegistry.getComponent("test")));

        // Fully specified request
        assertError(null, new Query(HttpRequest.createTestRequest("?myString=aString&myUserQueryProfile=user&myUserQueryProfile.myUserString=userString", Method.GET), cRegistry.getComponent("test")));
    }

    /** Here, a partially specified query profile is added to a non-mandatory field, making the request underspecified */
    public void testNonMandatoryUnderspecifiedUserProfileSetInRequest() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfile test = new QueryProfile("test");
        test.setType(type);
        registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(user);
        myUser.set("myUserInteger", 1, registry);
        myUser.set("myUserString","userValue", registry);
        test.set("myUserQueryProfile",myUser, registry);
        registry.register(myUser);

        QueryProfile otherUser=new QueryProfile("otherUser");
        otherUser.setType(user);
        otherUser.set("myUserInteger", 2, registry);
        registry.register(otherUser);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        // Fully specified request
        assertError(null, new Query(HttpRequest.createTestRequest("?myString=aString", Method.GET), cRegistry.getComponent("test")));

        // Underspecified because an underspecified profile is added
        assertError("Incomplete query: Parameter 'myQueryProfile.myUserString' is mandatory in query profile 'test' of type 'testtype' but is not set",
                    new Query(HttpRequest.createTestRequest("?myString=aString&myQueryProfile=otherUser", Method.GET), cRegistry.getComponent("test")));

        // Back to fully specified
        assertError(null, new Query(HttpRequest.createTestRequest("?myString=aString&myQueryProfile=otherUser&myQueryProfile.myUserString=userString", Method.GET), cRegistry.getComponent("test")));
    }

    private void assertError(String message,Query query) {
        assertEquals(message, query.validate());
    }

}
