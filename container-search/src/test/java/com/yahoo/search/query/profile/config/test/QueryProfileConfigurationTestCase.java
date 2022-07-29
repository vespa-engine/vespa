// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config.test;

import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.search.test.QueryTestCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class QueryProfileConfigurationTestCase {

    public final String CONFIG_DIR ="src/test/java/com/yahoo/search/query/profile/config/test/";

    @Test
    void testConfiguration() {
        var profile = QueryProfileConfigurer.createFromConfigId("file:" + CONFIG_DIR + "query-profiles-configuration.cfg")
                .getComponent("default");

        assertEquals("a-value", profile.get("a"));
        assertEquals("b-value", profile.get("b"));
        assertEquals("c.d-value", profile.get("c.d"));
        assertFalse(profile.isDeclaredOverridable("c.d", null));
        assertEquals("e-value-inherited1", profile.get("e"));
        assertEquals("g.d2-value-inherited1", profile.get("g.d2")); // Even though we make an explicit reference to one not having this value, we still inherit it
        assertEquals("a-value-subprofile1", profile.get("sub1.a"));
        assertEquals("c.d-value-subprofile1", profile.get("sub1.c.d"));
        assertEquals("a-value-subprofile2", profile.get("sub2.a"));
        assertEquals("c.d-value-subprofile2", profile.get("sub2.c.d"));
        assertEquals("e-value-subprofile3", profile.get("g.e"));
    }

    @Test
    void testBug3197426() {
        var profile = QueryProfileConfigurer.createFromConfigId("file:" + CONFIG_DIR + "bug3197426.cfg")
                .getComponent("default").compile(null);

        Map<String, Object> properties = new QueryProfileProperties(profile).listProperties("source.image");
        assertEquals("yes", properties.get("mlr"));
        assertEquals("zh-Hant", properties.get("language"));
        assertEquals("tw", properties.get("custid2"));
        assertEquals("4", properties.get("hits"));
        assertEquals("0", properties.get("offset"));
        assertEquals("image", properties.get("catalog"));
        assertEquals("yahoo", properties.get("custid1"));
        assertEquals("utf-8", properties.get("encoding"));
        assertEquals("all", properties.get("imquality"));
        assertEquals("all", properties.get("dimensions"));
        assertEquals("1", properties.get("flickr"));
        assertEquals("yes", properties.get("ocr"));
    }

    @Test
    void testVariantConfiguration() {
        var registry = QueryProfileConfigurer.createFromConfigId("file:" + CONFIG_DIR + "query-profile-variants-configuration.cfg");

        // Variant 1
        QueryProfile variants1 = registry.getComponent("variants1");
        assertGet("x1.y1.a", "a", new String[]{"x1", "y1"}, variants1);
        assertGet("x1.y1.b", "b", new String[]{"x1", "y1"}, variants1);
        assertGet("x1.y?.a", "a", new String[]{"x1", "zz"}, variants1);
        assertGet("x?.y1.a", "a", new String[]{"zz", "y1"}, variants1);
        assertGet("a-deflt", "a", new String[]{"z1", "z2"}, variants1);
        // ...inherited
        assertGet("parent1-value", "parent1", new String[]{"x1", "y1"}, variants1);
        assertGet("parent2-value", "parent2", new String[]{"x1", "y1"}, variants1);
        assertGet(null, "parent1", new String[]{"x1", "y2"}, variants1);
        assertGet(null, "parent2", new String[]{"x1", "y2"}, variants1);

        // Variant 2
        QueryProfile variants2 = registry.getComponent("variants2");
        assertGet("variant2:y1.c", "c", new String[]{"*", "y1"}, variants2);
        assertGet("variant2:y2.c", "c", new String[]{"*", "y2"}, variants2);
        assertGet("variant2:c-df", "c", new String[]{"*", "z1"}, variants2);
        assertGet("variant2:c-df", "c", new String[]{          }, variants2);
        assertGet("variant2:c-df", "c", new String[]{"*"      }, variants2);
        assertGet(null,            "d", new String[]{"*", "y1"}, variants2);

        // Reference following from variant 1
        assertGet("variant2:y1.c", "toVariants.c", new String[]{"**", "y1"}, variants1);
        assertGet("variant3:c-df", "toVariants.c", new String[]{"x1", "**"}, variants1);
        assertGet("variant3:y1.c", "toVariants.c", new String[]{"x1", "y1"}, variants1); // variant3 by order priority
        assertGet("variant3:y2.c", "toVariants.c", new String[]{"x1", "y2"}, variants1);
    }

    @Test
    void testVariantConfigurationThroughQueryLookup() {
        var registry = QueryProfileConfigurer.createFromConfigId("file:" + CONFIG_DIR + "query-profile-variants-configuration.cfg")
                .compile();

        CompiledQueryProfile variants1 = registry.getComponent("variants1");

        // Variant 1
        assertEquals("x1.y1.a", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=y1"), variants1).properties().get("a"));
        assertEquals("x1.y1.b", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=y1"), variants1).properties().get("b"));
        assertEquals("x1.y1.defaultIndex", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=y1"), variants1).getModel().getDefaultIndex());
        assertEquals("x1.y?.a", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=zz"), variants1).properties().get("a"));
        assertEquals("x1.y?.defaultIndex", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=zz"), variants1).getModel().getDefaultIndex());
        assertEquals("x?.y1.a", new Query(QueryTestCase.httpEncode("?query=foo&x=zz&y=y1"), variants1).properties().get("a"));
        assertEquals("x?.y1.defaultIndex", new Query(QueryTestCase.httpEncode("?query=foo&x=zz&y=y1"), variants1).getModel().getDefaultIndex());
        assertEquals("x?.y1.filter", new Query(QueryTestCase.httpEncode("?query=foo&x=zz&y=y1"), variants1).getModel().getFilter());
        assertEquals("a-deflt", new Query(QueryTestCase.httpEncode("?query=foo&x=z1&y=z2"), variants1).properties().get("a"));

        // Variant 2
        CompiledQueryProfile variants2 = registry.getComponent("variants2");
        assertEquals("variant2:y1.c", new Query(QueryTestCase.httpEncode("?query=foo&x=*&y=y1"), variants2).properties().get("c"));
        assertEquals("variant2:y2.c", new Query(QueryTestCase.httpEncode("?query=foo&x=*&y=y2"), variants2).properties().get("c"));
        assertEquals("variant2:c-df", new Query(QueryTestCase.httpEncode("?query=foo&x=*&y=z1"), variants2).properties().get("c"));
        assertEquals("variant2:c-df", new Query(QueryTestCase.httpEncode("?query=foo"), variants2).properties().get("c"));
        assertEquals("variant2:c-df", new Query(QueryTestCase.httpEncode("?query=foo&x=x1"), variants2).properties().get("c"));
        assertNull(new Query(QueryTestCase.httpEncode("?query=foo&x=*&y=y1"), variants2).properties().get("d"));

        // Reference following from variant 1
        assertEquals("variant2:y1.c", new Query(QueryTestCase.httpEncode("?query=foo&x=**&y=y1"), variants1).properties().get("toVariants.c"));
        assertEquals("variant3:c-df", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=**"), variants1).properties().get("toVariants.c"));
        assertEquals("variant3:y1.c", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=y1"), variants1).properties().get("toVariants.c"));
        assertEquals("variant3:y2.c", new Query(QueryTestCase.httpEncode("?query=foo&x=x1&y=y2"), variants1).properties().get("toVariants.c"));
    }

    @Test
    void testVariant2ConfigurationThroughQueryLookup() {
        final double delta = 0.0000001;

        var registry = QueryProfileConfigurer.createFromConfigId("file:" + CONFIG_DIR + "query-profile-variants2.cfg")
                .compile();

        Query query = new Query(QueryTestCase.httpEncode("?query=heh&queryProfile=multi&myindex=default&myquery=lo ve&tracelevel=5"),
                registry.findQueryProfile("multi"));
        assertEquals("love", query.properties().get("model.queryString"));
        assertEquals("default", query.properties().get("model.defaultIndex"));

        assertEquals(-20.0, query.properties().get("ranking.features.query(scorelimit)"));
        assertEquals(-20.0, query.getRanking().getFeatures().getDouble("query(scorelimit)").getAsDouble(), delta);
        query.properties().set("rankfeature.query(scorelimit)", -30);
        assertEquals(-30.0, query.properties().get("ranking.features.query(scorelimit)"));
        assertEquals(-30, query.getRanking().getFeatures().getDouble("query(scorelimit)").getAsDouble(), delta);
    }

    private void assertGet(String expectedValue, String parameter, String[] dimensionValues, QueryProfile profile) {
        Map<String, String> context = new HashMap<>();
        for (int i = 0; i<dimensionValues.length; i++)
            context.put(profile.getVariants().getDimensions().get(i), dimensionValues[i]); // Lookup dim. names to ease test...
        assertEquals(expectedValue, profile.get(parameter, context, null), "Looking up '" + parameter + "' for '" + Arrays.toString(dimensionValues) + "'");
    }

}
