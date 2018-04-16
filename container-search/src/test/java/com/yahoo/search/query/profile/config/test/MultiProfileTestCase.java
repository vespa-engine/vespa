// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config.test;

import java.util.HashMap;
import java.util.Map;

import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bratseth
 */
public class MultiProfileTestCase {

    @Test
    public void testValid() {
        QueryProfileRegistry registry=
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/multiprofile");

        QueryProfile multiprofile1=registry.getComponent("multiprofile1");
        assertNotNull(multiprofile1);
        assertGet("general-a","a",new String[] {null,null,null},multiprofile1);
        assertGet("us-nokia-test1-a","a",new String[] {"us","nok ia","test1"},multiprofile1);
        assertGet("us-nokia-b","b",new String[] {"us","nok ia","test1"},multiprofile1);
        assertGet("us-a","a",new String[] {"us",null,null},multiprofile1);
        assertGet("us-b","b",new String[] {"us",null,null},multiprofile1);
        assertGet("us-nokia-a","a",new String[] {"us","nok ia",null},multiprofile1);
        assertGet("us-test1-a","a",new String[] {"us",null,"test1"},multiprofile1);
        assertGet("us-test1-b","b",new String[] {"us",null,"test1"},multiprofile1);

        assertGet("us-a","a",new String[] {"us","unspecified","unspecified"},multiprofile1);
        assertGet("us-nokia-a","a",new String[] {"us","nok ia","unspecified"},multiprofile1);
        assertGet("us-test1-a","a",new String[] {"us","unspecified","test1"},multiprofile1);
        assertGet("us-nokia-b","b",new String[] {"us","nok ia","test1"},multiprofile1);

        // ...inherited
        assertGet("parent1-value","parent1",new String[] { "us","nok ia","-" }, multiprofile1);
        assertGet("parent2-value","parent2",new String[] { "us","nok ia","-" }, multiprofile1);
        assertGet(null,"parent1",new String[] { "us","-","-" }, multiprofile1);
        assertGet(null,"parent2",new String[] { "us","-","-" }, multiprofile1);
    }

    private void assertGet(String expectedValue,String parameter,String[] dimensionValues,QueryProfile profile) {
        Map<String,String> context=new HashMap<>();
        context.put("region",dimensionValues[0]);
        context.put("model",dimensionValues[1]);
        context.put("bucket",dimensionValues[2]);
        assertEquals("Looking up '" + parameter + "' for '" + toString(dimensionValues) + "'",expectedValue,profile.get(parameter,context,null));
    }

    private String toString(String[] array) {
        StringBuilder b=new StringBuilder("[");
        for (String value : array) {
            b.append(value);
            b.append(",");
        }
        b.deleteCharAt(b.length()-1); // Remove last comma  :-)
        b.append("]");
        return b.toString();
    }

}
