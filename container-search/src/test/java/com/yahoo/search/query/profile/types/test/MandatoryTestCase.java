// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.component.ComponentId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.search.test.QueryTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class MandatoryTestCase {

    private static class Fixture1 {

        final QueryProfileRegistry registry = new QueryProfileRegistry();
        final QueryProfileTypeRegistry typeRegistry = new QueryProfileTypeRegistry();
        final QueryProfileType type = new QueryProfileType(new ComponentId("testtype"));
        final QueryProfileType user = new QueryProfileType(new ComponentId("user"));

        public Fixture1() {
            typeRegistry.register(type);
            typeRegistry.register(user);

            addTypeFields(type, typeRegistry);
            addUserFields(user, typeRegistry);
        }

        private static void addTypeFields(QueryProfileType type, QueryProfileTypeRegistry registry) {
            type.addField(new FieldDescription("myString", FieldType.fromString("string", registry), true));
            type.addField(new FieldDescription("myInteger", FieldType.fromString("integer", registry)));
            type.addField(new FieldDescription("myLong", FieldType.fromString("long", registry)));
            type.addField(new FieldDescription("myFloat", FieldType.fromString("float", registry)));
            type.addField(new FieldDescription("myDouble", FieldType.fromString("double", registry)));
            type.addField(new FieldDescription("myQueryProfile", FieldType.fromString("query-profile", registry)));
            type.addField(new FieldDescription("myUserQueryProfile", FieldType.fromString("query-profile:user", registry), true));
        }

        private static void addUserFields(QueryProfileType user, QueryProfileTypeRegistry registry) {
            user.addField(new FieldDescription("myUserString", FieldType.fromString("string", registry), true));
            user.addField(new FieldDescription("myUserInteger", FieldType.fromString("integer", registry), true));
        }

    }

    @Test
    public void testMandatoryFullySpecifiedQueryProfile() {
        Fixture1 fixture = new Fixture1();

        QueryProfile test=new QueryProfile("test");
        test.setType(fixture.type);
        test.set("myString", "aString", fixture.registry);
        fixture.registry.register(test);

        QueryProfile myUser=new QueryProfile("user");
        myUser.setType(fixture.user);
        myUser.set("myUserInteger",1, fixture.registry);
        myUser.set("myUserString",1, fixture.registry);
        test.set("myUserQueryProfile", myUser, fixture.registry);
        fixture.registry.register(myUser);

        CompiledQueryProfileRegistry cRegistry = fixture.registry.compile();

        // Fully specified request
        assertError(null, new Query(QueryTestCase.httpEncode("?queryProfile=test"), cRegistry.getComponent("test")));
    }

    @Test
    public void testMandatoryRequestPropertiesNeeded() {
        Fixture1 fixture = new Fixture1();

        QueryProfile test = new QueryProfile("test");
        test.setType(fixture.type);
        fixture.registry.register(test);

        QueryProfile myUser = new QueryProfile("user");
        myUser.setType(fixture.user);
        myUser.set("myUserInteger", 1, fixture.registry);
        test.set("myUserQueryProfile", myUser, fixture.registry);
        fixture.registry.register(myUser);

        CompiledQueryProfileRegistry cRegistry = fixture.registry.compile();

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
    @Test
    public void testMandatoryNestedInMaps() {
        Fixture1 fixture = new Fixture1();

        QueryProfile topMap = new QueryProfile("topMap");
        fixture.registry.register(topMap);

        QueryProfile subMap = new QueryProfile("topSubMap");
        topMap.set("subMap", subMap, fixture.registry);
        fixture.registry.register(subMap);

        QueryProfile test = new QueryProfile("test");
        test.setType(fixture.type);
        subMap.set("test", test, fixture.registry);
        fixture.registry.register(test);

        QueryProfile myUser = new QueryProfile("user");
        myUser.setType(fixture.user);
        myUser.set("myUserInteger",1, fixture.registry);
        test.set("myUserQueryProfile", myUser, fixture.registry);
        fixture.registry.register(myUser);


        CompiledQueryProfileRegistry cRegistry = fixture.registry.compile();

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
    @Test
    public void testMandatoryUserProfileSetInRequest() {
        Fixture1 fixture = new Fixture1();

        QueryProfile test = new QueryProfile("test");
        test.setType(fixture.type);

        QueryProfile myUser = new QueryProfile("user");
        myUser.setType(fixture.user);
        myUser.set("myUserInteger", 1, null);

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
    @Test
    public void testNonMandatoryUnderspecifiedUserProfileSetInRequest() {
        Fixture1 fixture = new Fixture1();

        QueryProfile test = new QueryProfile("test");
        test.setType(fixture.type);
        fixture.registry.register(test);

        QueryProfile myUser = new QueryProfile("user");
        myUser.setType(fixture.user);
        myUser.set("myUserInteger", 1, fixture.registry);
        myUser.set("myUserString", "userValue", fixture.registry);
        test.set("myUserQueryProfile", myUser, fixture.registry);
        fixture.registry.register(myUser);

        QueryProfile otherUser = new QueryProfile("otherUser");
        otherUser.setType(fixture.user);
        otherUser.set("myUserInteger", 2, fixture.registry);
        fixture.registry.register(otherUser);

        CompiledQueryProfileRegistry cRegistry = fixture.registry.compile();

        // Fully specified request
        assertError(null, new Query(HttpRequest.createTestRequest("?myString=aString", Method.GET), cRegistry.getComponent("test")));

        // Underspecified because an underspecified profile is added
        assertError("Incomplete query: Parameter 'myQueryProfile.myUserString' is mandatory in query profile 'test' of type 'testtype' but is not set",
                    new Query(HttpRequest.createTestRequest("?myString=aString&myQueryProfile=otherUser", Method.GET), cRegistry.getComponent("test")));

        // Back to fully specified
        assertError(null, new Query(HttpRequest.createTestRequest("?myString=aString&myQueryProfile=otherUser&myQueryProfile.myUserString=userString", Method.GET), cRegistry.getComponent("test")));
    }

    private static class Fixture2 {

        final QueryProfileRegistry registry = new QueryProfileRegistry();
        final QueryProfileTypeRegistry typeRegistry = new QueryProfileTypeRegistry();
        final QueryProfileType rootType = new QueryProfileType(new ComponentId("root"));
        final QueryProfileType mandatoryType = new QueryProfileType(new ComponentId("mandatory-type"));

        public Fixture2() {
            typeRegistry.register(rootType);
            typeRegistry.register(mandatoryType);

            mandatoryType.inherited().add(rootType);
            mandatoryType.addField(new FieldDescription("foobar", FieldType.fromString("string", typeRegistry), true));
        }

    }

    @Test
    public void testMandatoryInParentType() {
        Fixture2 fixture = new Fixture2();

        QueryProfile defaultProfile = new QueryProfile("default");
        defaultProfile.setType(fixture.rootType);

        QueryProfile mandatoryProfile = new QueryProfile("mandatory");
        mandatoryProfile.setType(fixture.rootType);
        mandatoryProfile.setType(fixture.mandatoryType);

        fixture.registry.register(defaultProfile);
        fixture.registry.register(mandatoryProfile);
        CompiledQueryProfileRegistry cRegistry = fixture.registry.compile();

        assertError("Incomplete query: Parameter 'foobar' is mandatory in query profile 'mandatory' of type 'mandatory-type' but is not set",
                    new Query(QueryTestCase.httpEncode("?queryProfile=mandatory"), cRegistry.getComponent("mandatory")));
    }

    @Test
    public void testMandatoryInParentTypeWithInheritance() {
        Fixture2 fixture = new Fixture2();

        QueryProfile defaultProfile = new QueryProfile("default");
        defaultProfile.setType(fixture.rootType);

        QueryProfile mandatoryProfile = new QueryProfile("mandatory");
        mandatoryProfile.setType(fixture.rootType);
        mandatoryProfile.addInherited(defaultProfile); // The single difference from the test above
        mandatoryProfile.setType(fixture.mandatoryType);

        fixture.registry.register(defaultProfile);
        fixture.registry.register(mandatoryProfile);
        CompiledQueryProfileRegistry cRegistry = fixture.registry.compile();

        assertError("Incomplete query: Parameter 'foobar' is mandatory in query profile 'mandatory' of type 'mandatory-type' but is not set",
                    new Query(QueryTestCase.httpEncode("?queryProfile=mandatory"), cRegistry.getComponent("mandatory")));
    }

    private void assertError(String message,Query query) {
        assertEquals(message, query.validate());
    }

}
