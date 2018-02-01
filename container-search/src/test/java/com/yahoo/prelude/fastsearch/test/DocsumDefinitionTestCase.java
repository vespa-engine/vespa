// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.ByteField;
import com.yahoo.prelude.fastsearch.DataField;
import com.yahoo.prelude.fastsearch.DocsumDefinition;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.IntegerField;
import com.yahoo.prelude.fastsearch.StringField;
import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Tests docsum class functionality
 *
 * @author bratseth
 */
public class DocsumDefinitionTestCase extends junit.framework.TestCase {

    public DocsumDefinitionTestCase(String name) {
        super(name);
    }

    public void testReading() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/test/documentdb-info.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);

        String[] defs = new String[] { "[default,default]", "[version1,version1]",
                "[withranklog,withranklog]", "[version2,version2]", "[version3,version3]",
                "[version4,version4]", "[version5,version5]" };
        String setAsString = set.toString();
        for (String d : defs) {
            assertFalse(setAsString.indexOf(d) == -1);
        }
        assertEquals(7, set.size());

        DocsumDefinition docsum0 = set.getDocsumDefinition("default");

        assertNotNull(docsum0);
        assertEquals("default", docsum0.getName());
        assertEquals(19, docsum0.getFieldCount());
        assertNull(docsum0.getField(19));
        assertEquals("DSHOST", docsum0.getField(7).getName());

        assertTrue(docsum0.getField(1) instanceof StringField);
        assertTrue(docsum0.getField(6) instanceof ByteField);
        assertTrue(docsum0.getField(7) instanceof IntegerField);
        assertTrue(docsum0.getField(18) instanceof DataField);
    }

    public void testDecoding() {
        String summary_cf = "file:src/test/java/com/yahoo/prelude/fastsearch/test/documentdb-info.cfg";
        DocsumDefinitionSet set = createDocsumDefinitionSet(summary_cf);
        FastHit hit = new FastHit();

        set.lazyDecode(null, makeDocsum(), hit);
        assertEquals("Arts/Celebrities/Madonna", hit.getField("TOPIC"));
        assertEquals("1", hit.getField("EXTINFOSOURCE").toString());
        assertEquals("10", hit.getField("LANG1").toString());
        assertEquals("352", hit.getField("WORDS").toString());
        assertEquals("index:0/0/0/" + FastHit.asHexString(hit.getGlobalId()), hit.getId().toString());
    }

    public static GlobalId createGlobalId(int docId) {
        return new GlobalId((new DocumentId("doc:test:" + docId)).getGlobalId());
    }

    public static byte[] makeDocsum() {
        Slime slime = new Slime();
        Cursor docsum = slime.setObject();
        docsum.setString("TOPIC", "Arts/Celebrities/Madonna");
        docsum.setString("TITLE", "StudyOfMadonna.com - Interviews, Articles, Reviews, Quotes, Essays and more..");
        docsum.setString("DYNTEASER", "dynamic teaser");
        docsum.setLong("EXTINFOSOURCE", 1);
        docsum.setLong("LANG1", 10);
        docsum.setLong("WORDS", 352);
        docsum.setLong("BYTES", 9190);
        byte[] tmp = BinaryFormat.encode(slime);
        ByteBuffer buf = ByteBuffer.allocate(tmp.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(DocsumDefinitionSet.SLIME_MAGIC_ID);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(tmp);
        return buf.array();
    }

    public static DocsumDefinitionSet createDocsumDefinitionSet(String configID) {
        DocumentdbInfoConfig config = new ConfigGetter<>(DocumentdbInfoConfig.class).getConfig(configID);
        return new DocsumDefinitionSet(config.documentdb(0));
    }
}
