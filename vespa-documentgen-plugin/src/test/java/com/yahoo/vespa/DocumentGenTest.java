// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa;

import com.yahoo.document.DataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.schema.Schema;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentGenTest {

    @Test
    public void testMusic() {
        DocumentGenMojo mojo = new DocumentGenMojo();
        mojo.execute(new File("etc/music/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "com.yahoo.vespa.document");
        Map<String, Schema> searches = mojo.getSearches();
        assertEquals(searches.size(),1);
        assertEquals(searches.get("music").getDocument("music").getField("title").getDataType(), DataType.STRING);
        assertEquals(searches.get("music").getDocument("music").getField("eitheror").getDataType(), DataType.BOOL);
    }

    @Test
    public void testComplex() {
        DocumentGenMojo mojo = new DocumentGenMojo();
        mojo.execute(new File("etc/complex/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "com.yahoo.vespa.document");
        Map<String, Schema> searches = mojo.getSearches();
        assertEquals(searches.get("video").getDocument("video").getField("weight").getDataType(), DataType.FLOAT);
        assertEquals(searches.get("book").getDocument("book").getField("sw1").getDataType(), DataType.FLOAT);
        assertTrue(searches.get("music3").getDocument("music3").getField("pos").getDataType() instanceof StructDataType);
        assertEquals(searches.get("music3").getDocument("music3").getField("pos").getDataType().getName(), "position");
        assertTrue(searches.get("book").getDocument("book").getField("mystruct").getDataType() instanceof StructDataType);
        assertTrue(searches.get("book").getDocument("book").getField("mywsinteger").getDataType() instanceof WeightedSetDataType);
        assertTrue(((WeightedSetDataType)(searches.get("book").getDocument("book").getField("mywsinteger").getDataType())).getNestedType() == DataType.INT);
    }

    @Test
    public void testLocalApp() {
        DocumentGenMojo mojo = new DocumentGenMojo();
        mojo.execute(new File("etc/localapp/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "com.yahoo.vespa.document");
        Map<String, Schema> searches = mojo.getSearches();
        assertEquals(searches.get("video").getDocument("video").getField("weight").getDataType(), DataType.FLOAT);
        assertTrue(searches.get("book").getDocument("book").getField("mystruct").getDataType() instanceof StructDataType);
        assertTrue(searches.get("book").getDocument("book").getField("mywsinteger").getDataType() instanceof WeightedSetDataType);
        assertTrue(((WeightedSetDataType)(searches.get("book").getDocument("book").getField("mywsinteger").getDataType())).getNestedType() == DataType.INT);
    }

    @Test
    public void testEmptyPkgNameForbidden() {
        DocumentGenMojo mojo = new DocumentGenMojo();
        try {
            mojo.execute(new File("etc/localapp/"), new File("target/generated-test-sources/vespa-documentgen-plugin/"), "");
            fail("Didn't throw in empty pkg");
        } catch (IllegalArgumentException e) {

        }
    }

}
