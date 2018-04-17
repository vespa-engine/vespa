// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types.test;

import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.QueryProfileType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests that matching query profiles by path name works
 *
 * @author bratseth
 */
public class PatchMatchingTestCase {

    @Test
    public void testPatchMatching() {
        QueryProfileType type=new QueryProfileType("type");

        type.setMatchAsPath(true);

        QueryProfile a=new QueryProfile("a");
        a.setType(type);
        QueryProfile abee=new QueryProfile("a/bee");
        abee.setType(type);
        abee.addInherited(a);
        QueryProfile abeece=new QueryProfile("a/bee/ce");
        abeece.setType(type);
        abeece.addInherited(abee);

        QueryProfileRegistry registry=new QueryProfileRegistry();
        registry.register(a);
        registry.register(abee);
        registry.register(abeece);
        registry.freeze();

        assertNull(registry.findQueryProfile(null)); // No "default" registered
        assertEquals("a",registry.findQueryProfile("a").getId().getName());
        assertEquals("a/bee",registry.findQueryProfile("a/bee").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce/dee").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce/dee/eee/").getId().getName());
        assertEquals("a/bee",registry.findQueryProfile("a/bee/cede").getId().getName());
        assertEquals("a",registry.findQueryProfile("a/foo/bee/cede").getId().getName());
        assertNull(registry.findQueryProfile("abee"));
    }

    @Test
    public void testNoPatchMatching() {
        QueryProfileType type=new QueryProfileType("type");

        type.setMatchAsPath(false); // Default, but set here for clarity

        QueryProfile a=new QueryProfile("a");
        a.setType(type);
        QueryProfile abee=new QueryProfile("a/bee");
        abee.setType(type);
        abee.addInherited(a);
        QueryProfile abeece=new QueryProfile("a/bee/ce");
        abeece.setType(type);
        abeece.addInherited(abee);

        QueryProfileRegistry registry=new QueryProfileRegistry();
        registry.register(a);
        registry.register(abee);
        registry.register(abeece);
        registry.freeze();

        assertNull(registry.findQueryProfile(null)); // No "default" registered
        assertEquals("a",registry.findQueryProfile("a").getId().getName());
        assertEquals("a/bee",registry.findQueryProfile("a/bee").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce").getId().getName());
        assertNull(registry.findQueryProfile("a/bee/ce/dee")); // Different from test above
        assertNull(registry.findQueryProfile("a/bee/ce/dee/eee/")); // Different from test above
        assertNull(registry.findQueryProfile("a/bee/cede")); // Different from test above
        assertNull(registry.findQueryProfile("a/foo/bee/cede")); // Different from test above
        assertNull(registry.findQueryProfile("abee"));
    }

    /** Check that the path matching property is inherited to subtypes */
    @Test
    public void testPatchMatchingInheritance() {
        QueryProfileType type=new QueryProfileType("type");
        QueryProfileType subType=new QueryProfileType("subType");
        subType.inherited().add(type);

        type.setMatchAsPath(true); // Supertype only

        QueryProfile a=new QueryProfile("a");
        a.setType(type);
        QueryProfile abee=new QueryProfile("a/bee");
        abee.setType(subType);
        abee.addInherited(a);
        QueryProfile abeece=new QueryProfile("a/bee/ce");
        abeece.setType(subType);
        abeece.addInherited(abee);

        QueryProfileRegistry registry=new QueryProfileRegistry();
        registry.register(a);
        registry.register(abee);
        registry.register(abeece);
        registry.freeze();

        assertNull(registry.findQueryProfile(null)); // No "default" registered
        assertEquals("a",registry.findQueryProfile("a").getId().getName());
        assertEquals("a/bee",registry.findQueryProfile("a/bee").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce/dee").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce/dee/eee/").getId().getName());
        assertEquals("a/bee",registry.findQueryProfile("a/bee/cede").getId().getName());
        assertEquals("a",registry.findQueryProfile("a/foo/bee/cede").getId().getName());
        assertNull(registry.findQueryProfile("abee"));
    }

    /** Check that the path matching works with versioned profiles */
    @Test
    public void testPatchMatchingVersions() {
        QueryProfileType type=new QueryProfileType("type");

        type.setMatchAsPath(true);

        QueryProfile a=new QueryProfile("a");
        a.setType(type);
        QueryProfile abee11=new QueryProfile("a/bee:1.1");
        abee11.setType(type);
        abee11.addInherited(a);
        QueryProfile abee13=new QueryProfile("a/bee:1.3");
        abee13.setType(type);
        abee13.addInherited(a);
        QueryProfile abeece=new QueryProfile("a/bee/ce");
        abeece.setType(type);
        abeece.addInherited(abee13);

        QueryProfileRegistry registry=new QueryProfileRegistry();
        registry.register(a);
        registry.register(abee11);
        registry.register(abee13);
        registry.register(abeece);
        registry.freeze();

        assertNull(registry.findQueryProfile(null)); // No "default" registered
        assertEquals("a",registry.findQueryProfile("a").getId().getName());
        assertEquals("a/bee:1.1",registry.findQueryProfile("a/bee:1.1").getId().toString());
        assertEquals("a/bee:1.3",registry.findQueryProfile("a/bee").getId().toString());
        assertEquals("a/bee:1.3",registry.findQueryProfile("a/bee:1").getId().toString());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce/dee").getId().getName());
        assertEquals("a/bee/ce",registry.findQueryProfile("a/bee/ce/dee/eee/").getId().getName());
        assertEquals("a/bee:1.1",registry.findQueryProfile("a/bee/cede:1.1").getId().toString());
        assertEquals("a/bee:1.3",registry.findQueryProfile("a/bee/cede").getId().toString());
        assertEquals("a/bee:1.3",registry.findQueryProfile("a/bee/cede:1").getId().toString());
        assertEquals("a",registry.findQueryProfile("a/foo/bee/cede").getId().getName());
        assertNull(registry.findQueryProfile("abee"));
    }

    @Test
    public void testQuirkyNames() {
        QueryProfileType type=new QueryProfileType("type");

        type.setMatchAsPath(true);

        QueryProfile a=new QueryProfile("/a");
        a.setType(type);
        QueryProfile abee=new QueryProfile("/a//bee");
        abee.setType(type);
        abee.addInherited(a);
        QueryProfile abeece=new QueryProfile("/a//bee/ce/");
        abeece.setType(type);
        abeece.addInherited(abee);

        QueryProfileRegistry registry=new QueryProfileRegistry();
        registry.register(a);
        registry.register(abee);
        registry.register(abeece);
        registry.freeze();

        assertNull(registry.findQueryProfile(null)); // No "default" registered
        assertEquals("/a",registry.findQueryProfile("/a").getId().getName());
        assertNull(registry.findQueryProfile("a"));
        assertEquals("/a//bee",registry.findQueryProfile("/a//bee").getId().getName());
        assertEquals("/a//bee/ce/",registry.findQueryProfile("/a//bee/ce/").getId().getName());
        assertEquals("/a//bee/ce/",registry.findQueryProfile("/a//bee/ce").getId().getName());
        assertEquals("/a//bee/ce/",registry.findQueryProfile("/a//bee/ce/dee").getId().getName());
        assertEquals("/a//bee/ce/",registry.findQueryProfile("/a//bee/ce/dee/eee/").getId().getName());
        assertEquals("/a//bee",registry.findQueryProfile("/a//bee/cede").getId().getName());
        assertEquals("/a",registry.findQueryProfile("/a/foo/bee/cede").getId().getName());
        assertEquals("/a",registry.findQueryProfile("/a/bee").getId().getName());
        assertNull(registry.findQueryProfile("abee"));
    }

}
