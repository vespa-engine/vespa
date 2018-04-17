// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.test;

import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.query.Model;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;


/**
 * @author Arne Bergene Fossaa
 */
public class ModelTestCase {

    String oldConfigId;

    @Before
    public void setUp() {
        oldConfigId = System.getProperty("config.id");
        System.setProperty("config.id", "file:src/test/java/com/yahoo/prelude/test/fieldtypes/field-info.cfg");
    }

    @After
    public void tearDown() {
        if (oldConfigId == null)
            System.getProperties().remove("config.id");
        else
            System.setProperty("config.id", oldConfigId);
    }

    @Test
    public void testCopyParameters() {
        Query q1 = new Query("?query=test1&filter=test2&defidx=content&default-index=lala&encoding=iso8859-1");
        Query q2 = q1.clone();
        Model r1 = q1.getModel();
        Model r2 = q2.getModel();
        assertTrue(r1 != r2);
        assertEquals(r1,r2);
        assertEquals("test1",r2.getQueryString());
    }

    @Test
    public void testSetQuery() {
        Query q1 = new Query("?query=test1");
        Item r1 = q1.getModel().getQueryTree();
        q1.properties().set("query","test2");
        q1.getModel().setQueryString(q1.getModel().getQueryString()); // Force reparse
        assertNotSame(r1,q1.getModel().getQueryTree());
        q1.properties().set("query","test1");
        q1.getModel().setQueryString(q1.getModel().getQueryString()); // Force reparse
        assertEquals(r1,q1.getModel().getQueryTree());
    }

    @Test
    public void testSetofSetters() {
        Query q1 = new Query("?query=test1&encoding=iso-8859-1&language=en&default-index=subject&filter=" + enc("\u00C5"));
        Model r1 = q1.getModel();
        assertEquals(r1.getQueryString(), "test1");
        assertEquals("iso-8859-1", r1.getEncoding());
        assertEquals("\u00C5", r1.getFilter());
        assertEquals("subject", r1.getDefaultIndex());
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSearchPath() {
        assertEquals("c6/r8",new Query("?query=test1&model.searchPath=c6/r8").getModel().getSearchPath());
        assertEquals("c6/r8",new Query("?query=test1&searchpath=c6/r8").getModel().getSearchPath());
    }

    @Test
    public void testClone() {
        Query q= new Query();
        Model sr = new Model(q);
        sr.setRestrict("music, cheese,other");
        sr.setSources("cluster1");
        assertEquals(sr.getSources(), new LinkedHashSet<>(Arrays.asList(new String[]{"cluster1"})));
        assertEquals(sr.getRestrict(),new LinkedHashSet<>(Arrays.asList(new String[]{"cheese","music","other"})));
    }

    @Test
    public void testEquals() {
        Query q = new Query();
        Model sra = new Model(q);
        sra.setRestrict("music,cheese");
        sra.setSources("cluster1,cluster2");

        Model srb = new Model(q);
        srb.setRestrict(" cheese , music");
        srb.setSources("cluster1,cluster2");
        assertEquals(sra,srb);
        srb.setRestrict("music,cheese");
        assertNotSame(sra,srb);
    }

    @Test
    public void testSearchRestrictQueryParameters() {
        Query query=new Query("?query=test&search=news,archive&restrict=fish,bird");
        assertTrue(query.getModel().getSources().contains("news"));
        assertTrue(query.getModel().getSources().contains("archive"));
        assertEquals(2,query.getModel().getSources().size());
        assertTrue(query.getModel().getRestrict().contains("fish"));
        assertTrue(query.getModel().getRestrict().contains("bird"));
        assertEquals(2,query.getModel().getRestrict().size());
    }

}
