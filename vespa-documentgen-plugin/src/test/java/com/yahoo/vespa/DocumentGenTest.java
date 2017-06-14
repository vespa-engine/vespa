// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa;

import com.yahoo.document.DataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.searchdefinition.Search;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentGenTest {

    @Test
    public void testMusic() throws MojoExecutionException, MojoFailureException {
        DocumentGenMojo mojo = new DocumentGenMojo();
        mojo.execute(new File("etc/music/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "com.yahoo.vespa.document");
        Map<String, Search> searches = mojo.getSearches();
        assertEquals(searches.size(),1);
        assertEquals(searches.get("music").getDocument("music").getField("title").getDataType(), DataType.STRING);
    }

    @Test
    public void testComplex() throws MojoFailureException {
        DocumentGenMojo mojo = new DocumentGenMojo();
        mojo.execute(new File("etc/complex/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "com.yahoo.vespa.document");
        Map<String, Search> searches = mojo.getSearches();
        assertEquals(searches.get("video").getDocument("video").getField("weight").getDataType(), DataType.FLOAT);
        assertTrue(searches.get("book").getDocument("book").getField("mystruct").getDataType() instanceof StructDataType);
        assertTrue(searches.get("book").getDocument("book").getField("mywsfloat").getDataType() instanceof WeightedSetDataType);
        assertTrue(((WeightedSetDataType)(searches.get("book").getDocument("book").getField("mywsfloat").getDataType())).getNestedType() == DataType.FLOAT);
    }

    @Test
    public void testLocalApp() throws MojoFailureException {
        DocumentGenMojo mojo = new DocumentGenMojo();
        mojo.execute(new File("etc/localapp/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "com.yahoo.vespa.document");
        Map<String, Search> searches = mojo.getSearches();
        assertEquals(searches.get("video").getDocument("video").getField("weight").getDataType(), DataType.FLOAT);
        assertTrue(searches.get("book").getDocument("book").getField("mystruct").getDataType() instanceof StructDataType);
        assertTrue(searches.get("book").getDocument("book").getField("mywsfloat").getDataType() instanceof WeightedSetDataType);
        assertTrue(((WeightedSetDataType)(searches.get("book").getDocument("book").getField("mywsfloat").getDataType())).getNestedType() == DataType.FLOAT);
    }

    @Test
    public void testEmptyPkgNameForbidden() throws MojoFailureException {
        DocumentGenMojo mojo = new DocumentGenMojo();
        try {
            mojo.execute(new File("etc/localapp/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "");
            fail("Didn't throw in empty pkg");
        } catch (IllegalArgumentException e) {

        }
    }

}
