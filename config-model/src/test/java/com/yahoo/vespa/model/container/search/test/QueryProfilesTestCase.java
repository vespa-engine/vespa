// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.test;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.DimensionValues;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.searchdefinition.derived.TestableDeployLogger;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.Test;

import java.io.IOException;
import java.util.logging.Level;

import static helpers.CompareConfigTestHelper.assertSerializedConfigFileEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests generation of config from query profiles (XML reading is tested elsewhere)
 *
 * @author bratseth
 */
public class QueryProfilesTestCase {

    private final static String root="src/test/java/com/yahoo/vespa/model/container/search/test/";

    @Test
    public void testVariants() {
        QueryProfileRegistry registry = new QueryProfileXMLReader().read(root + "variants");
        QueryProfiles profiles = new QueryProfiles(registry, new TestableDeployLogger());
        QueryProfileRegistry registryFromConfig = QueryProfileConfigurer.createFromConfig(profiles.getConfig());
        CompiledQueryProfile profile = registryFromConfig.compile().getComponent("default");

        assertEquals("a.b.c-value", new Query("?d1=d1v", profile).properties().get("a.b.c"));
        assertEquals("a.b.c-variant-value", new Query("?d1=d1v&d2=d2v", profile).properties().get("a.b.c"));

        assertEquals("query-value", new Query("?d1=d1v&a.b.c=query-value", profile).properties().get("a.b.c"));
        assertEquals("a.b.c-variant-value", new Query("?d1=d1v&d2=d2v&a.b.c=query-value", profile).properties().get("a.b.c"));
    }

    @Test
    public void testEmpty() throws IOException {
        QueryProfileRegistry reg = new QueryProfileRegistry();
        assertConfig("empty.cfg", reg);
    }

    @Test
    public void testQueryProfiles() throws IOException {
        final boolean mandatory=true;
        final boolean overridable=true;
        QueryProfileRegistry registry=new QueryProfileRegistry();
        QueryProfileTypeRegistry typeRegistry=registry.getTypeRegistry();

        QueryProfileType userType=new QueryProfileType("user");
        userType.setStrict(true);
        userType.addField(new FieldDescription("robot", FieldType.fromString("boolean",typeRegistry), "machine automaton", mandatory, !overridable));
        userType.addField(new FieldDescription("ads", FieldType.fromString("string",typeRegistry), mandatory, overridable));
        userType.addField(new FieldDescription("age", FieldType.fromString("integer",typeRegistry), !mandatory, overridable));
        typeRegistry.register(userType);

        QueryProfileType rootType=new QueryProfileType("root");
        QueryProfileType nativeProfile=typeRegistry.getComponent("native");
        assertNotNull(nativeProfile);
        assertTrue(nativeProfile.isBuiltin());
        rootType.inherited().add(nativeProfile);
        rootType.setMatchAsPath(true);
        rootType.addField(new FieldDescription("user", FieldType.fromString("query-profile:user",typeRegistry), mandatory, overridable));
        typeRegistry.register(rootType);

        QueryProfileType marketType=new QueryProfileType("market");
        marketType.inherited().add(rootType);
        marketType.addField(new FieldDescription("market", FieldType.fromString("string",typeRegistry), !mandatory, !overridable));
        typeRegistry.register(marketType);

        QueryProfile defaultProfile=new QueryProfile("default");
        defaultProfile.set("ranking","production23", registry);
        defaultProfile.set("representation.defaultIndex", "title", registry);
        defaultProfile.setOverridable("representation.defaultIndex", false, DimensionValues.empty);
        registry.register(defaultProfile);

        QueryProfile test=new QueryProfile("test");
        test.set("tracelevel",2,registry);
        registry.register(test);

        QueryProfile genericUser = new QueryProfile("genericUser");
        genericUser.setType(userType);
        genericUser.set("robot", false, registry);
        genericUser.set("ads", "all", registry);
        registry.register(genericUser);

        QueryProfile root = new QueryProfile("root");
        root.setType(rootType);
        root.addInherited(defaultProfile);
        root.addInherited(test);
        root.set("hits", 30, registry);
        root.setOverridable("hits", false, DimensionValues.empty);
        root.set("unique", "category", registry);
        root.set("user", genericUser, registry);
        root.set("defaultage", "7d", registry);
        registry.register(root);

        QueryProfile marketUser=new QueryProfile("marketUser");
        marketUser.setType(userType);
        marketUser.addInherited(genericUser);
        marketUser.set("ads","none",registry);
        marketUser.set("age",25,registry);
        registry.register(marketUser);

        QueryProfile market = new QueryProfile("root/market");
        market.setType(marketType);
        market.addInherited(root);
        market.set("hits", 15, registry);
        market.set("user", marketUser, registry);
        market.set("market", "some market", registry);
        market.set("marketHeading", "Market of %{market}", registry);
        registry.register(market);

        QueryProfile untypedUser = new QueryProfile("untypedUser");
        untypedUser.set("robot", false, registry);
        untypedUser.set("robot.type", "continent-class", registry);
        registry.register(untypedUser);

        assertConfig("query-profiles.cfg", registry);

        DeployLoggerStub logger = new DeployLoggerStub();
        new QueryProfiles(registry, logger);
        assertTrue(logger.entries.isEmpty());
    }

    @Test
    public void testValidation() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfileTypeRegistry typeRegistry = registry.getTypeRegistry();

        QueryProfileType userType = new QueryProfileType("user");
        typeRegistry.register(userType);

        DeployLoggerStub logger = new DeployLoggerStub();
        new QueryProfiles(registry, logger);
        assertEquals(1, logger.entries.size());
        assertEquals("This application define query profile types, but has no query profiles referencing them " +
                     "so they have no effect. " +
                     "See https://docs.vespa.ai/en/query-profiles.html",
                     logger.entries.get(0).message);
    }

    @Test
    public void testValidationWithTensorFields() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfileTypeRegistry typeRegistry = registry.getTypeRegistry();

        QueryProfileType userType = new QueryProfileType("user");
        userType.addField(new FieldDescription("vector", FieldType.fromString("tensor(x[5])", typeRegistry)));
        userType.addField(new FieldDescription("matrix", FieldType.fromString("tensor(x[5],y[5])", typeRegistry)));
        typeRegistry.register(userType);

        DeployLoggerStub logger = new DeployLoggerStub();
        new QueryProfiles(registry, logger);
        assertEquals(1, logger.entries.size());
        assertEquals("This application define query profile types, but has no query profiles referencing them " +
                     "so they have no effect. " +
                     "In particular, the tensors (vector, matrix) will be interpreted as strings, not tensors if sent in requests. " +
                     "See https://docs.vespa.ai/en/query-profiles.html",
                     logger.entries.get(0).message);
    }

    protected void assertConfig(String correctFileName, QueryProfileRegistry check) throws IOException {
        assertSerializedConfigFileEquals(root + "/" + correctFileName,
                com.yahoo.text.StringUtilities.implodeMultiline(com.yahoo.config.ConfigInstance.serialize(new QueryProfiles(check, new SilentDeployLogger()).getConfig())));

        // Also assert that the correct config config can actually be read as a config source
        QueryProfileConfigurer configurer = new QueryProfileConfigurer("file:" + root + "empty.cfg");
        configurer.shutdown();
    }

    private static class SilentDeployLogger implements DeployLogger {

        @Override
        public void log(Level level, String message) {}

    }

}
