// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.prelude.fastsearch.ByteField;
import com.yahoo.prelude.fastsearch.DataField;
import com.yahoo.prelude.fastsearch.DocsumDefinition;
import com.yahoo.prelude.fastsearch.DocsumDefinitionSet;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.IntegerField;
import com.yahoo.prelude.fastsearch.StringField;
import com.yahoo.document.DocumentId;
import com.yahoo.document.GlobalId;
import com.yahoo.search.schema.DocumentSummary;
import com.yahoo.search.schema.Schema;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests docsum class functionality
 *
 * @author bratseth
 */
public class DocsumDefinitionTestCase {

    @Test
    void testDecoding() {
        DocsumDefinitionSet set = createDocsumDefinitionSet();
        FastHit hit = new FastHit();

        set.lazyDecode(null, makeDocsum(), hit);
        assertEquals("Arts/Celebrities/Madonna", hit.getField("TOPIC"));
        assertEquals("1", hit.getField("EXTINFOSOURCE").toString());
        assertEquals("10", hit.getField("LANG1").toString());
        assertEquals("352", hit.getField("WORDS").toString());
        assertEquals("index:null/0/" + asHexString(hit.getGlobalId()), hit.getId().toString());
    }

    private static String asHexString(GlobalId gid) {
        StringBuilder sb = new StringBuilder();
        byte[] rawGid = gid.getRawId();
        for (byte b : rawGid) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1)
                sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
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

    public static DocsumDefinitionSet createDocsumDefinitionSet() {
        var schema = new Schema.Builder("test");
        var summary = new DocumentSummary.Builder("default");
        summary.add(new DocumentSummary.Field("TOPIC", "string"));
        summary.add(new DocumentSummary.Field("TITLE", "string"));
        summary.add(new DocumentSummary.Field("DYNTEASER", "string"));
        summary.add(new DocumentSummary.Field("EXTINFOSOURCE", "integer"));
        summary.add(new DocumentSummary.Field("LANG1", "integer"));
        summary.add(new DocumentSummary.Field("WORDS", "integer"));
        summary.add(new DocumentSummary.Field("BYTES", "byte"));
        schema.add(summary.build());
        return new DocsumDefinitionSet(schema.build());
    }

}
