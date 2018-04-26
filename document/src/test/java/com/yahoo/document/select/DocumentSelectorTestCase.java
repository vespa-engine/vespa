// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.select.convert.SelectionExpressionConverter;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.TokenMgrError;
import com.yahoo.yolean.Exceptions;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen
 * @author bratseth
 */
public class DocumentSelectorTestCase {

    private static DocumentTypeManager manager = new DocumentTypeManager();

    @Before
    public void setUp() {
        DocumentType type = new DocumentType("test");
        type.addHeaderField("hint", DataType.INT);
        type.addHeaderField("hfloat", DataType.FLOAT);
        type.addHeaderField("hstring", DataType.STRING);
        type.addField("content", DataType.STRING);

        StructDataType mystruct = new StructDataType("mystruct");
        mystruct.addField(new Field("key", DataType.INT, false));
        mystruct.addField(new Field("value", DataType.STRING, false));
        type.addHeaderField("mystruct", mystruct);

        ArrayDataType structarray = new ArrayDataType(mystruct);
        type.addField("structarray", structarray);

        type.addField("stringweightedset", new WeightedSetDataType(DataType.STRING, false, false));
        type.addField("mymap", new MapDataType(DataType.INT, DataType.STRING));
        type.addField("structarrmap", new MapDataType(DataType.STRING, structarray));

        ArrayDataType intarray = new ArrayDataType(DataType.INT);
        type.addField("intarray", intarray);

        manager.registerDocumentType(type);

        // Create strange doctypes using identifiers within them, which we
        // can use to verify they are still parsed as doctypes.
        manager.registerDocumentType(new DocumentType("notandor"));
        manager.registerDocumentType(new DocumentType("ornotand"));
        manager.registerDocumentType(new DocumentType("andornot"));
        manager.registerDocumentType(new DocumentType("idid"));
        manager.registerDocumentType(new DocumentType("usergroup"));
    }

    @Test
    public void testParsing() throws ParseException {
        assertParse("3.14 > 0");
        assertParse("-999 > 0");
        assertParse("150000.0 > 0", "15e4 > 0");
        assertParse("3.4E-4 > 0", "3.4e-4 > 0");
        assertParse("\" Test \" = \"*\"");
        assertParse("id = \"*\"", "id = '*'");
        assertParse("id.group == 3");
        assertParse("id.namespace = \"*\"");
        assertParse("id.hash() > 0");
        assertParse("id.namespace.hash() > 0");
        assertParse("id.order(5,2) > 100");
        assertParse("searchcolumn.10 = 6");
        assertParse("music.artist = \"*\"");
        assertParse("music.artist.lowercase() = \"*\"");
        assertParse("music_.artist = \"*\"");
        assertParse("music_foo.artist = \"*\"");
        assertParse("music_foo_.artist = \"*\"");
        assertParse("(4 + 3) > 0", "(4+3) > 0");
        assertParse("1 + 1 > 0", "1 +1 > 0");
        assertParse("1 + -1 > 0", "1 + -1 > 0");
        assertParse("1 + 1.0 > 0", "1 + +1.0 > 0");
        assertParse("1 - 1 > 0", "1 -1 > 0");
        assertParse("1 - -1 > 0", "1 - -1 > 0");
        assertParse("1 - 1.0 > 0", "1 - +1.0 > 0");
        assertParse("1 + 2 * 3 - 10 % 2 / 3 > 0", "1   +2 *  3- 10%2   /3  > 0");
        assertParse("((43 + 14) / 34) > 0");
        assertParse("(34 * ((3 - 1) % 4)) > 0");
        assertParse("true");
        assertParse("false");
        assertParse("music");
        assertParse("(music or book)");
        assertParse("music or book", "music or book");
        assertParse("(music or (book and video))");
        assertParse("music or (book and video)", "music or (book and video)");
        assertParse("((music or book) and video)");
        assertParse("(music or book) and video", "(music or book) and video");
        assertParse("music.test > 0");
        assertParse("music.artist = \"*john*\"");
        assertParse("music.length >= 180");
        assertParse("true or not false and true", "true oR nOt false And true");
        assertParse("(true or false) and true", "(true oR false) aNd true");
        assertParse("music.expire > now()");
        assertParse("music.expire > now() - 300");
        assertParse("now or now_search");
        assertParse("(music.expire / 1000) > (now() - 300)");
    }

    @Test
    public void testReservedWords() throws ParseException {
        assertParse(null, "id == 'id' or id_t or idtype"); // ignore canonical form
        assertParse(null, "searchcolumn == 1 or searchcolumn_t or searchcolumntype");
        assertParse(null, "id.scheme == 'scheme' or scheme_t or schemetype");
        assertParse(null, "id.namespace == 'namespace' or namespace_t or namespacetype");
        assertParse(null, "id.specific == 'specific' or specific_t or specifictype");
        assertParse(null, "id.user == 'user' or user_t or usertype");
        assertParse(null, "id.group == 'group' or group_t or grouptype");
        assertParse(null, "id.bucket == 'bucket' or bucket_t or buckettype");
        assertParse(null, "null == 'null' or null_t or nulltype");
        assertParse(null, "true or true_t or truetype");
        assertParse(null, "false or false_t or falsetype");
        assertParse(null, "true or and_t or andtype");
        assertParse(null, "true or or_t or ortype");
    }

    @Test
    public void testCjkParsing() throws ParseException {
        assertParse("music.artist = \"\\u4f73\\u80fd\\u7d22\\u5c3c\\u60e0\\u666e\"",
                    "music.artist = \"\u4f73\u80fd\u7d22\u5c3c\u60e0\u666e\"");
        assertParse("music.artist = \"\\u4f73\\u80fd\\u7d22\\u5c3c\\u60e0\\u666e\"",
                    "music.artist = \"\\u4f73\\u80fd\\u7d22\\u5c3c\\u60e0\\u666e\"");
    }

    @Test
    public void testParseTerminals() throws ParseException {
        // Test number values.
        assertParse("true");
        assertParse("music.hmm == 123");
        assertParse("music.hmm == 123.53", "music.hmm == +123.53");
        assertParse("music.hmm == -123.5");
        assertParse("music.hmm == 2.3412352E8", "music.hmm == 234123.52e3");
        assertParse("music.hmm == -234.12352", "music.hmm == -234123.52E-3");
        assertParse("music.hmm < aaa");

        // Test string values.
        assertParse("music.hmm == \"test\"");

        // Test map and struct stuff.
        assertParse("music.hmm{test} == \"test\"");
        assertParse("music.hmm{test}.foo[3].key == \"test\"");

        // Test whitespaces.
        assertParse("music.hmm == \"te st \"");
        assertParse("music.hmm == \"test\"", " \t music.hmm\t==  \t \"test\"\t");

        // Test escaping.
        assertParse("music.hmm == \"tab\\ttest\"");
        assertParse("music.hmm == \"tab\\u0666test\"", "music.hmm == \"tab\\u0666test\"");
        assertParse("music.hmm == \"tabcomplete\"", "music.hmm == \"tabcomplete\"");
        assertParse("music.hmm == \"tabysf\"", "music.hmm == \"tab\\ysf\""); // excessive escapes are removed
        assertParse("music.h == \"\\ttx48 \\n\"", "music.h == \"\\tt\\x48 \\n\"");

        // Test illegal operator.
        assertParseError("music.hmm <> 12", "Exception parsing document selector 'music.hmm <> 12': Encountered \" \">\" \"> \"\" at line 1, column 12.");

        // Test comparison operators.
        assertParse("music.hmm >= 123");
        assertParse("music.hmm > 123");
        assertParse("music.hmm <= 123");
        assertParse("music.hmm < 123");
        assertParse("music.hmm != 123");

        // Test defined.
        assertParse("music.hmm");

        // Test boolean expressions.
        assertParse("true", "TRUE");
        assertParse("false", "FALSE");
        assertParse("true", "true");
        assertParse("false", "false");
        assertParse("false", "faLSe");

        // Test document types.
        assertParse("mytype");

        // Test document id.
        assertParse("id == \"userdoc:ns:mytest\"");
        assertParse("id.namespace == \"myspace\"");
        assertParse("id.scheme == \"userdoc\"");
        assertParse("id.type == \"mytype\"");
        assertParse("id.user == 1234");
        assertParse("id.bucket == 8388608", "id.bucket == 0x800000");
        assertParse("id.bucket == 8429568", "id.bucket == 0x80a000");
        assertParse("id.bucket == -9223372036854775566",
                    "id.bucket == 0x80000000000000f2");
        assertParse("id.group == \"yahoo.com\"");
        assertParse("id.specific == \"mypart\"");

        // Test search column stuff.
        assertParse("searchcolumn.10 = 6");

        // Test other operators.
        assertParse("id.scheme = \"*doc\"");
        assertParse("music.artist =~ \"(john|barry|shrek)\"");

        // Verify functions.
        assertParse("id.hash() == 124");
        assertParse("id.specific.hash() == 124");
        assertParse("music.artist.lowercase() == \"chang\"");
        assertParse("music.artist.lowercase().hash() == 124");
        assertParse("music.version() == 8");
        assertParse("music == 8"); // will evaluate to false

        // Value grouping.
        assertParse("(123) < (200)", "(123) < (200)");
        assertParse("(\"hmm\") < (id.scheme)", "(\"hmm\") < (id.scheme)");

        // Arithmetics.
        assertParse("(1 + 2) > 1");
        assertParse("1 + 2 > 1", "1 + 2 > 1");
        assertParse("(1 - 2) > 1");
        assertParse("(1 * 2) > 1");
        assertParse("(1 / 2) > 1");
        assertParse("(1 % 2) > 1");
        assertParse("((1 + 2) * (4 - 2)) == 1");
        assertParse("(1 + 2) * (4 - 2) == 1", "(1 + 2) * (4 - 2) == 1");
        assertParse("((23 + 643) / (34 % 10)) > 34");
        assertParse("23 + 643 / 34 % 10 > 34", "23 + 643 / 34 % 10 > 34");
    }

    @Test
    public void testParseReservedTokens() throws ParseException {
        assertParse("user.fieldName == \"fieldValue\""); // reserved word as document type name
        assertParse("documentName.user == \"fieldValue\""); // reserved word as field type name
        assertParse("group.fieldName == \"fieldValue\""); // reserved word as document type name
        assertParse("documentName.group == \"fieldValue\""); // reserved word as field type name
    }

    @Test
    public void testParseBranches() throws ParseException {
        assertParse("((true or false) and (false or true))");
        assertParse("(true or (not false and not true))");
        assertParse("((243) < 300 and (\"FOO\").lowercase() == (\"foo\"))");
    }

    @Test
    public void testDocumentUpdate() throws ParseException {
        DocumentUpdate upd = new DocumentUpdate(manager.getDocumentType("test"), new DocumentId("doc:myspace:anything"));
        assertEquals(Result.TRUE, evaluate("test", upd));
        assertEquals(Result.FALSE, evaluate("music", upd));
        assertEquals(Result.TRUE, evaluate("test or music", upd));
        assertEquals(Result.FALSE, evaluate("test and music", upd));
        assertEquals(Result.INVALID, evaluate("test.hint", upd));
        assertEquals(Result.INVALID, evaluate("test.anything", upd));
        assertEquals(Result.INVALID, evaluate("test.hint < 24", upd));
        // TODO Fails: assertEquals(Result.TRUE, evaluate("test.hint + 1 > 13", upd));
    }

    @Test
    public void testDocumentRemove() throws ParseException {
        assertEquals(Result.TRUE, evaluate("test", createRemove("id:ns:test::1")));
        assertEquals(Result.FALSE, evaluate("test", createRemove("id:ns:null::1")));
        assertEquals(Result.FALSE, evaluate("test", createRemove("userdoc:test:1234:1")));
        assertEquals(Result.INVALID, evaluate("test.hint", createRemove("id:ns:test::1")));
        assertEquals(Result.FALSE, evaluate("test.hint", createRemove("id:ns:null::1")));
        assertEquals(Result.INVALID, evaluate("test.hint == 0", createRemove("id:ns:test::1")));
        assertEquals(Result.INVALID, evaluate("test.anything", createRemove("id:ns:test::1")));
        assertEquals(Result.INVALID, evaluate("test and test.hint == 0", createRemove("id:ns:test::1")));
    }

    private DocumentRemove createRemove(String docId) {
        return new DocumentRemove(new DocumentId(docId));
    }

    @Test
    public void testInvalidLogic() throws ParseException {
        DocumentPut put = new DocumentPut(manager.getDocumentType("test"), new DocumentId("doc:scheme:"));
        DocumentUpdate upd = new DocumentUpdate(manager.getDocumentType("test"), new DocumentId("doc:scheme:"));

        assertEquals(Result.FALSE, evaluate("test.content", put)); // BROKEN
        assertEquals(Result.INVALID, evaluate("test.content", upd));

        assertEquals(Result.FALSE, evaluate("test.content = 1", put)); // BROKEN
        assertEquals(Result.INVALID, evaluate("test.content = 1", upd));

        assertEquals(Result.FALSE, evaluate("test.content = 1 and true", put)); // BROKEN
        assertEquals(Result.INVALID, evaluate("test.content = 1 and true", upd));

        assertEquals(Result.TRUE, evaluate("test.content = 1 or true", put));
        assertEquals(Result.TRUE, evaluate("test.content = 1 or true", upd));

        assertEquals(Result.FALSE, evaluate("test.content = 1 and false",  put));
        assertEquals(Result.FALSE, evaluate("test.content = 1 and false",  upd));

        assertEquals(Result.FALSE, evaluate("test.content = 1 or false", put)); // BROKEN
        assertEquals(Result.INVALID, evaluate("test.content = 1 or false", upd));

        assertEquals(Result.FALSE, evaluate("true and test.content = 1",  put)); // BROKEN
        assertEquals(Result.INVALID, evaluate("true and test.content = 1",  upd));

        assertEquals(Result.TRUE, evaluate("true or test.content = 1", put));
        assertEquals(Result.TRUE, evaluate("true or test.content = 1", upd));

        assertEquals(Result.FALSE, evaluate("false and test.content = 1",  put));
        assertEquals(Result.FALSE, evaluate("false and test.content = 1",  upd));

        assertEquals(Result.FALSE, evaluate("false or test.content = 1",  put)); // BROKEN
        assertEquals(Result.INVALID, evaluate("false or test.content = 1",  upd));
    }

    List<DocumentPut> createDocs() {
        List<DocumentPut> documents = new ArrayList<>();
        documents.add(createDocument("doc:myspace:anything", 24, 2.0f, "foo", "bar"));
        documents.add(createDocument("doc:anotherspace:foo", 13, 4.1f, "bar", "foo"));
        documents.add(createDocument("userdoc:myspace:1234:mail1", 15, 1.0f, "some", "some"));
        documents.add(createDocument("userdoc:myspace:5678:bar", 14, 2.4f, "Yet", "More"));
        documents.add(createDocument("orderdoc(31,19):ns2:1234:5678:foo", 14, 2.4f, "Yet", "More"));
        documents.add(createDocument("id:myspace:test:n=2345:mail2", 15, 1.0f, "bar", "baz"));
        documents.add(createDocument("id:myspace:test:g=mygroup:qux", 15, 1.0f, "quux", "corge"));
        documents.add(createDocument("doc:myspace:missingint", null, 2.0f, null, "bar"));

        // Add some array/struct info to doc 1
        Struct sval = new Struct(documents.get(1).getDocument().getField("mystruct").getDataType());
        sval.setFieldValue("key", new IntegerFieldValue(14));
        sval.setFieldValue("value", new StringFieldValue("structval"));
        documents.get(1).getDocument().setFieldValue("mystruct", sval);
        Array<Struct> aval = new Array<>(documents.get(1).getDocument().getField("structarray").getDataType());
        {
            Struct sval1 = new Struct(aval.getDataType().getNestedType());
            sval1.setFieldValue("key", new IntegerFieldValue(15));
            sval1.setFieldValue("value", new StringFieldValue("structval1"));
            Struct sval2 = new Struct(aval.getDataType().getNestedType());
            sval2.setFieldValue("key", new IntegerFieldValue(16));
            sval2.setFieldValue("value", new StringFieldValue("structval2"));
            aval.add(sval1);
            aval.add(sval2);
        }
        documents.get(1).getDocument().setFieldValue("structarray", aval);

        MapFieldValue<IntegerFieldValue, StringFieldValue> mval =
                new MapFieldValue<>((MapDataType)documents.get(1).getDocument().getField("mymap")
                                                                                             .getDataType());
        mval.put(new IntegerFieldValue(3), new StringFieldValue("a"));
        mval.put(new IntegerFieldValue(5), new StringFieldValue("b"));
        mval.put(new IntegerFieldValue(7), new StringFieldValue("c"));
        documents.get(1).getDocument().setFieldValue("mymap", mval);

        MapFieldValue<StringFieldValue, Array> amval =
                new MapFieldValue<>((MapDataType)documents.get(1).getDocument().getField("structarrmap")
                                                                                 .getDataType());
        amval.put(new StringFieldValue("foo"), aval);

        Array<Struct> abval = new Array<>(documents.get(1).getDocument().getField("structarray").getDataType());
        {
            Struct sval1 = new Struct(aval.getDataType().getNestedType());
            sval1.setFieldValue("key", new IntegerFieldValue(17));
            sval1.setFieldValue("value", new StringFieldValue("structval3"));
            Struct sval2 = new Struct(aval.getDataType().getNestedType());
            sval2.setFieldValue("key", new IntegerFieldValue(18));
            sval2.setFieldValue("value", new StringFieldValue("structval4"));
            abval.add(sval1);
            abval.add(sval2);
        }

        amval.put(new StringFieldValue("bar"), abval);
        documents.get(1).getDocument().setFieldValue("structarrmap", amval);

        WeightedSet<StringFieldValue> wsval = new WeightedSet<>(documents.get(1).getDocument().getField("stringweightedset")
                                                                     .getDataType());
        wsval.add(new StringFieldValue("foo"));
        wsval.add(new StringFieldValue("val1"));
        wsval.add(new StringFieldValue("val2"));
        wsval.add(new StringFieldValue("val3"));
        wsval.add(new StringFieldValue("val4"));
        documents.get(1).getDocument().setFieldValue("stringweightedset", wsval);

        // Add empty array/struct to doc 2
        Struct sval3 = new Struct(documents.get(2).getDocument().getField("mystruct").getDataType());
        documents.get(2).getDocument().setFieldValue("mystruct", sval3);
        Array aval2 = new Array(documents.get(2).getDocument().getField("structarray").getDataType());
        documents.get(2).getDocument().setFieldValue("structarray", aval2);

        Array<IntegerFieldValue> intvals1 = new Array<>(documents.get(0).getDocument().getField("intarray")
                                                                                  .getDataType());
        intvals1.add(new IntegerFieldValue(12));
        intvals1.add(new IntegerFieldValue(40));
        intvals1.add(new IntegerFieldValue(60));
        intvals1.add(new IntegerFieldValue(84));
        documents.get(0).getDocument().setFieldValue("intarray", intvals1);

        Array<IntegerFieldValue> intvals2 = new Array<>(documents.get(1).getDocument().getField("intarray")
                                                                                  .getDataType());
        intvals2.add(new IntegerFieldValue(3));
        intvals2.add(new IntegerFieldValue(56));
        intvals2.add(new IntegerFieldValue(23));
        intvals2.add(new IntegerFieldValue(9));
        documents.get(1).getDocument().setFieldValue("intarray", intvals2);

        return documents;
    }

    @Test
    public void testOperators() throws ParseException {
        List<DocumentPut> documents = createDocs();

        // Check that comparison operators work.
        assertEquals(Result.TRUE, evaluate("", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("30 < 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("10 < 30", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("30 < 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("10 < 30", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("30 <= 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("10 <= 30", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 <= 30", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("10 >= 30", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 >= 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 >= 30", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("10 > 30", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 > 10", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("30 == 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 == 30", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 != 10", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("30 != 30", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"foo\" != \"bar\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"foo\" != \"foo\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("'foo' == \"bar\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("'foo' == \"foo\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"bar\" = \"a\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"bar\" = \"*a*\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"bar\" = \"\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"\" = \"\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"bar\" =~ \"^a$\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"bar\" =~ \"a\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"bar\" =~ \"\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"\" =~ \"\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("30 = 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 = 30", documents.get(0)));

        // Mix of types should within numbers, but otherwise not match
        assertEquals(Result.FALSE, evaluate("30 < 10.2", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("10.2 < 30", documents.get(0)));
        assertEquals(Result.INVALID, evaluate("30 < \"foo\"", documents.get(0)));
        assertEquals(Result.INVALID, evaluate("30 > \"foo\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("30 != \"foo\"", documents.get(0)));
        assertEquals(Result.INVALID, evaluate("14.2 <= \"foo\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("null == null", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("null = null", documents.get(0))); // Glob operator falls back to == comparison
        assertEquals(Result.FALSE, evaluate("null != null", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"bar\" == null", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("null == \"bar\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("14.3 == null", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("null = 0", documents.get(0)));

        // Field values
        assertEquals(Result.TRUE, evaluate("test.hint = 24", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.hint = 24", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.hint = 13", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.hint = 13", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.hfloat = 2.0", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.hfloat = 1.0", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.hfloat = 4.1", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.hfloat > 4.09 and test.hfloat < 4.11", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.content = \"bar\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.content = \"bar\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.content = \"foo\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.content = \"foo\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.hstring == test.content", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.hstring == test.content", documents.get(2)));
        assertEquals(Result.TRUE, evaluate("test.hint + 1 > 13", documents.get(1)));
        // Case where field is not present (i.e. null) is defined for (in)equality comparisons, but
        // not for other relations.
        assertEquals(Result.TRUE, evaluate("test.hint != 1234", documents.get(7)));
        assertEquals(Result.FALSE, evaluate("test.hint == 1234", documents.get(7)));
        assertEquals(Result.INVALID, evaluate("test.hint < 1234", documents.get(7)));
        // Propagation of Invalid through logical operators should match C++ implementation
        assertEquals(Result.FALSE, evaluate("test.hint < 1234 and false", documents.get(7)));
        assertEquals(Result.INVALID, evaluate("test.hint < 1234 and true", documents.get(7)));
        assertEquals(Result.TRUE, evaluate("test.hint < 1234 or true", documents.get(7)));
        assertEquals(Result.INVALID, evaluate("test.hint < 1234 or false", documents.get(7)));
        // Must be possible to predicate a sub-expression on the presence of a field without
        // propagating up an Invalid value from the comparison.
        assertEquals(Result.FALSE, evaluate("test.hint and test.hint < 1234", documents.get(7)));
        assertEquals(Result.FALSE, evaluate("test.hint != null and test.hint < 1234", documents.get(7)));

        // Document types.
        assertEquals(Result.TRUE, evaluate("test", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("nonexisting", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("nonexisting.reallynonexisting", documents.get(0)));
        assertEquals(Result.INVALID, evaluate("nonexisting.reallynonexisting > 13", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("true.foo", documents.get(0)));

        // Field existence
        assertEquals(Result.TRUE, evaluate("test.hint", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.hstring", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.hint", documents.get(7)));
        assertEquals(Result.FALSE, evaluate("test.hstring", documents.get(7)));
        assertEquals(Result.TRUE, evaluate("not test.hint", documents.get(7)));
        assertEquals(Result.TRUE, evaluate("not test.hstring", documents.get(7)));

        assertEquals(Result.TRUE, evaluate("test.hint != null", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("null != test.hint", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.hint == null", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("null == test.hint", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("null == test.hint", documents.get(7)));
        assertEquals(Result.TRUE, evaluate("test.hint == null", documents.get(7)));
        assertEquals(Result.FALSE, evaluate("test.hint != null", documents.get(7)));
        assertEquals(Result.FALSE, evaluate("null != test.hint", documents.get(7)));

        assertEquals(Result.TRUE, evaluate("test.hint or true", documents.get(7)));
        assertEquals(Result.TRUE, evaluate("not test.hint and true", documents.get(7)));
        assertEquals(Result.FALSE, evaluate("not test.hint and false", documents.get(7)));

        // Id values.
        assertEquals(Result.TRUE, evaluate("id == \"doc:myspace:anything\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate(" iD==  \"doc:myspace:anything\"  ", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("id == \"doc:myspa:nything\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("Id.scHeme == \"doc\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("id.scheme == \"userdoc\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("id.type == \"test\"", documents.get(5)));
        assertEquals(Result.FALSE, evaluate("id.type == \"wrong\"", documents.get(5)));
        assertEquals(Result.TRUE, evaluate("Id.namespaCe == \"myspace\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("id.NaMespace == \"pace\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("id.specific == \"anything\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("id.user=1234", documents.get(2)));
        assertEquals(Result.TRUE, evaluate("id.user=1234", documents.get(4)));
        assertEquals(Result.TRUE, evaluate("id.group=\"1234\"", documents.get(4)));
        assertEquals(Result.TRUE, evaluate("id.user=2345", documents.get(5)));
        assertEquals(Result.TRUE, evaluate("id.group=\"mygroup\"", documents.get(6)));

        assertEquals(Result.TRUE, evaluate("id.order(31,19)=5678", documents.get(4)));
        assertEquals(Result.TRUE, evaluate("id.order(31,19)<=5678", documents.get(4)));
        assertEquals(Result.FALSE, evaluate("id.order(31,19)>5678", documents.get(4)));
        assertEquals(Result.FALSE, evaluate("id.order(25,23)==5678", documents.get(4)));
        assertEquals(Result.FALSE, evaluate("id.order(31,19)=5678", documents.get(3)));

        assertError("id.user == 1234", documents.get(0), "User identifier is null.");
        assertError("id.group == 1234", documents.get(3), "Group identifier is null.");
        assertError("id.group == \"yahoo\"", documents.get(3), "Group identifier is null.");
        assertError("id.type == \"unknown\"", documents.get(0), "Document id doesn't have doc type.");

        assertEquals(Result.TRUE, evaluate("searchcolumn.10 == 6", documents.get(3)));

        // Branch operators.
        assertEquals(Result.FALSE, evaluate("true and false", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("true and true", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("true or false", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("false or false", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("false and true or true and true", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("false or true and true or false", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("not false", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("not true", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("true and not false or false", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("((243 < 300) and (\"FOO\".lowercase() == \"foo\"))", documents.get(0)));

        // Invalid branching. test.content = 1 is invalid.
        assertEquals(Result.FALSE, evaluate("test.content = 1 and true", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.content = 1 or true", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.content = 1 and false", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.content = 1 or false", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("true and test.content = 1", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("true or test.content = 1", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("false and test.content = 1", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("false or test.content = 1", documents.get(0)));

        // Functions.
        assertEquals(Result.FALSE, evaluate("test.hstring.lowercase() == \"Yet\"", documents.get(3)));
        assertEquals(Result.TRUE, evaluate("test.hstring.lowercase() == \"yet\"", documents.get(3)));
        assertEquals(Result.FALSE, evaluate("test.hfloat.lowercase() == \"yet\"", documents.get(3)));
        assertEquals(Result.TRUE, evaluate("\"bar\".hash() == -270124981", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"bar\".hash().abs() == 270124981", documents.get(0)));
        assertError("null.hash() == 22460089", documents.get(0), "Can not invoke 'hash()' on 'null' because that term evaluated to null");
        assertEquals(Result.FALSE, evaluate("(0.234).hash() == 123", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("(0.234).lowercase() == 123", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"foo\".hash() == 123", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("(234).hash() == 123", documents.get(0)));
        // Arithmetics
        assertEquals(Result.TRUE, evaluate("id.specific.hash() = 596580044", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("id.specific.hash() % 10 = 4", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("id.specific.hash() % 10 = 2", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("\"foo\" + \"bar\" = \"foobar\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("\"foo\" + 4 = 25", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("34.0 % 4 = 4", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("-6 % 10 = -6", documents.get(0)));

        // Test now(). Assumes that now() is never 0
        assertEquals(Result.FALSE, evaluate("0 > now()", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("0 < now()", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("0 < now() - 10", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("now() - 20 < now() - 10", documents.get(0)));
        long secondsNow = System.currentTimeMillis() / 1000;
        assertEquals(Result.TRUE, evaluate("now() - " + secondsNow + " < 2", documents.get(0)));

        // Structs and arrays
        // Commented out tests work in C++.. Don't want to start altering
        // java code before talking to Simon.. Leaving it as is, as the
        // needed functionality of testing for existance already works.
        assertEquals(Result.FALSE, evaluate("test.mystruct", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.mystruct", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mystruct", documents.get(2)));
        assertEquals(Result.TRUE, evaluate("test.mystruct == test.mystruct", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.mystruct == test.mystruct", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mystruct != test.mystruct", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.mystruct != test.mystruct", documents.get(1)));
        assertEquals(Result.INVALID, evaluate("test.mystruct < test.mystruct", documents.get(0)));
        //assertEquals(Result.FALSE, evaluate("test.mystruct < test.mystruct", documents.get(1)));
        //assertEquals(Result.INVALID, evaluate("test.mystruct < 5", documents.get(1)));
        //assertEquals(Result.INVALID, evaluate("test.mystruct == \"foo\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarray", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.structarray", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarray", documents.get(2)));
        assertEquals(Result.TRUE, evaluate("test.structarray == test.structarray", documents.get(0)));
        //assertEquals(Result.INVALID, evaluate("test.structarray < test.structarray", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.structarray == test.structarray", documents.get(1)));
        //assertEquals(Result.FALSE, evaluate("test.structarray < test.structarray", documents.get(1)));

        assertEquals(Result.FALSE, evaluate("test.structarray.key == 15", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.structarray[4].key == 15", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.structarray", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarray.key == 15", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarray[1].key == 16", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarray[1].key = 16", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarray.value == \"structval1\"", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.structarray[4].value == \"structval1\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.structarray.value == \"structval1\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarray[0].value == \"structval1\"", documents.get(1)));
        // Globbing of array-of-struct fields
        assertEquals(Result.FALSE, evaluate("test.structarray.key = 15", documents.get(0))); // Fallback
        assertEquals(Result.FALSE, evaluate("test.structarray.key = 15", documents.get(2))); // Fallback
        assertEquals(Result.TRUE, evaluate("test.structarray.key = 15", documents.get(1))); // Fallback
        assertEquals(Result.FALSE, evaluate("test.structarray.value = \"structval2\"", documents.get(2)));
        assertEquals(Result.TRUE, evaluate("test.structarray.value = \"*ctval*\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarray[1].value = \"structval2\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarray[1].value = \"batman\"", documents.get(1)));
        // Regexp of array-of-struct fields
        assertEquals(Result.TRUE, evaluate("test.structarray.value =~ \"structval[1-9]\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarray.value =~ \"structval[a-z]\"", documents.get(1)));
        // Globbing/regexp of struct fields
        assertEquals(Result.FALSE, evaluate("test.mystruct.value = \"struc?val\"", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.mystruct.value = \"struc?val\"", documents.get(1)));
        assertEquals(Result.INVALID, evaluate("test.mystruct.value =~ \"struct.*\"", documents.get(0))); // Invalid since lhs is null
        assertEquals(Result.TRUE, evaluate("test.mystruct.value =~ \"struct.*\"", documents.get(1)));

        assertEquals(Result.FALSE, evaluate("test.intarray < 5", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.intarray < 5", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.intarray > 80", documents.get(0)));
        assertEquals(Result.FALSE, evaluate("test.intarray > 80", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.intarray >= 84", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.intarray <= 3", documents.get(1)));

        // Interesting property ...
        assertEquals(Result.TRUE, evaluate("test.intarray == 84", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.intarray != 84", documents.get(0)));

        assertEquals(Result.TRUE, evaluate("test.structarray[$x].key == 15 AND test.structarray[$x].value == \"structval1\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarray[$x].key == 15 AND test.structarray[$x].value == \"structval2\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarray[$x].key == 15 AND test.structarray[$y].value == \"structval2\"", documents.get(1)));

        assertEquals(Result.FALSE, evaluate("test.mymap", documents.get(0)));
        assertEquals(Result.TRUE, evaluate("test.mymap", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap{3}", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap{9}", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap{3} == \"a\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap{3} == \"b\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap{9} == \"b\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap == \"a\"", documents.get(1))); // Keys only
        assertEquals(Result.TRUE, evaluate("test.mymap{3} = \"a\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap{3} = \"b\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap{3} =~ \"a\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap{3} =~ \"b\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap.value = \"a\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap.value = \"d\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap.value =~ \"a\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap.value =~ \"d\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap == 3", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.mymap == 4", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.mymap = 3", documents.get(1))); // Fallback to ==
        assertEquals(Result.FALSE, evaluate("test.mymap = 4", documents.get(1))); // Fallback to ==

        assertEquals(Result.TRUE, evaluate("test.structarrmap{$x}[$y].key == 15 AND test.structarrmap{$x}[$y].value == \"structval1\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.structarrmap.value[$y].key == 15 AND test.structarrmap.value[$y].value == \"structval1\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarrmap{$x}[$y].key == 15 AND test.structarrmap{$x}[$y].value == \"structval2\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarrmap.value[$y].key == 15 AND test.structarrmap.value[$y].value == \"structval2\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarrmap{$x}[$y].key == 15 AND test.structarrmap{$y}[$x].value == \"structval2\"", documents.get(1)));

        assertEquals(Result.TRUE, evaluate("test.stringweightedset", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.stringweightedset{val1}", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.stringweightedset{val1} == 1", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.stringweightedset{val1} == 2", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.stringweightedset == \"val1\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.stringweightedset = \"val*\"", documents.get(1)));
        assertEquals(Result.TRUE, evaluate("test.stringweightedset =~ \"val[0-9]\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.stringweightedset == \"val5\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.stringweightedset = \"val5\"", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.stringweightedset =~ \"val5\"", documents.get(1)));

        assertEquals(Result.TRUE, evaluate("test.structarrmap{$x}.key == 15 AND test.stringweightedset{$x}", documents.get(1)));
        assertEquals(Result.FALSE, evaluate("test.structarrmap{$x}.key == 17 AND test.stringweightedset{$x}", documents.get(1)));
    }

    @Test
    public void testTicket1769674() {
        assertParseError("music.uri=\"junk",
                         "Lexical error at line -1, column 17.  Encountered: <EOF> after : \"\\\"junk\"");
    }

    @Test
    public void testThatVisitingReportsCorrectResult() throws ParseException {
        assertVisitWithValidNowWorks("music.expire > now()");
        assertVisitWithValidNowWorks("music.expire > now() and video.expire > now()");
        assertVisitWithValidNowWorks("music.expire > now() or video");
        assertVisitWithValidNowWorks("music.expire > now() or video.date < 300");
        assertVisitWithValidNowWorks("video.date < 300 or music.expire > now()");
        assertVisitWithValidNowWorks("video.date < 300 and music.expire > now()");
        assertVisitWithValidNowWorks("music.insertdate > now() - 300 and video.expire > now() - 3600");

        assertVisitWithoutNowWorks("test.structarrmap{$x}[$y].key == 15 AND test.structarrmap{$x}[$y].value == \"structval1\"");
        assertVisitWithoutNowWorks("music.artist.lowercase() == \"chang\"");

        assertVisitWithInvalidNowFails("music.expire > now() + 300", "Arithmetic operator '+' is not supported");
        assertVisitWithInvalidNowFails("music.expire < now()", "Comparison operator '<' is not supported");
        assertVisitWithInvalidNowFails("music.expire >= now()", "Comparison operator '>=' is not supported");
        assertVisitWithInvalidNowFails("now() > now()", "Left hand side of comparison must be a document field");
        assertVisitWithInvalidNowFails("music.name.hash() > now()", "Only attribute items are supported");
    }

    @Test
    public void testThatSelectionIsConvertedToQueries() throws ParseException {
        assertThatQueriesAreCreated("music.expire > now()", Arrays.asList("music"), Arrays.asList("expire:>now(0)"));
        assertThatQueriesAreCreated("music.expire > now() - 300", Arrays.asList("music"), Arrays.asList("expire:>now(300)"));
        assertThatQueriesAreCreated("music.expire > now() - 300 and video.expire > now() - 3600", Arrays.asList("music", "video"), Arrays.asList("expire:>now(300)", "expire:>now(3600)"));
        assertThatQueriesAreCreated("music.expire > now() - 300 or video", Arrays.asList("music"), Arrays.asList("expire:>now(300)"));
        assertVisitWithInvalidNowFails("music.field1 > now() - 300 and music.field2 > now() - 300", "Specifying multiple document types is not allowed");
        assertVisitWithInvalidNowFails("music.field1 > now() - 300 and video.field1 > now() and music.field2 > now() - 300", "Specifying multiple document types is not allowed");
        assertVisitWithInvalidNowFails("now() > music.field", "Left hand side of comparison must be a document field");
    }

    public void assertThatQueriesAreCreated(String selection, List<String> expectedDoctypes, List<String> expectedQueries) throws ParseException {
        DocumentSelector selector = new DocumentSelector(selection);
        NowCheckVisitor visitor = new NowCheckVisitor();
        selector.visit(visitor);
        assertTrue(visitor.requiresConversion());
        SelectionExpressionConverter converter = new SelectionExpressionConverter();
        selector.visit(converter);
        Map<String, String> queryMap = converter.getQueryMap();
        assertEquals(expectedQueries.size(), queryMap.size());
        for (int i = 0; i < expectedQueries.size(); i++) {
            assertTrue(queryMap.containsKey(expectedDoctypes.get(i)));
            assertEquals(expectedQueries.get(i), queryMap.get(expectedDoctypes.get(i)));
        }
    }

    public void assertVisitWithoutNowWorks(String expression) throws ParseException {
        DocumentSelector selector = new DocumentSelector(expression);
        NowCheckVisitor visitor = new NowCheckVisitor();
        selector.visit(visitor);
        assertFalse(visitor.requiresConversion());
    }

    public void assertVisitWithValidNowWorks(String expression) throws ParseException {
        DocumentSelector selector = new DocumentSelector(expression);
        NowCheckVisitor visitor = new NowCheckVisitor();
        selector.visit(visitor);
        assertTrue(visitor.requiresConversion());
        SelectionExpressionConverter converter = new SelectionExpressionConverter();
        try {
            selector.visit(converter);
        } catch (Exception e) {
            assertFalse("Converter throws exception : " + e.getMessage(), true);
        }
    }

    public void assertVisitWithInvalidNowFails(String expression, String expectedError) throws ParseException {
        DocumentSelector selector = new DocumentSelector(expression);
        NowCheckVisitor visitor = new NowCheckVisitor();
        selector.visit(visitor);
        assertTrue(visitor.requiresConversion());
        SelectionExpressionConverter converter = new SelectionExpressionConverter();
        try {
            selector.visit(converter);
            assertFalse("Should not be able to convert " + expression + " query", true);
        } catch (Exception e) {
            assertEquals(expectedError, e.getMessage());
        }
    }

    private static DocumentPut createDocument(String id, Integer hInt, float hFloat, String hString, String content) {
        Document doc = new Document(manager.getDocumentType("test"), new DocumentId(id));
        if (hInt != null)
            doc.setFieldValue("hint", new IntegerFieldValue(hInt));
        doc.setFieldValue("hfloat", new FloatFieldValue(hFloat));
        if (hString != null)
            doc.setFieldValue("hstring", new StringFieldValue(hString));
        doc.setFieldValue("content", new StringFieldValue(content));
        return new DocumentPut(doc);
    }

    private static void assertParse(String expression) throws ParseException {
        assertParse(expression, expression);
    }

    private static void assertParse(String expectedString, String expressionString) throws ParseException {
        DocumentSelector selector = new DocumentSelector(expressionString);
        if (expectedString != null) {
            assertEquals(expectedString, selector.toString());
        }
    }

    private static void assertParseError(String expressionString, String expectedError) {
        try {
            new DocumentSelector(expressionString);
            fail("The expression '" + expressionString + "' should throw an exception.");
        }
        catch (ParseException e) {
            Throwable t = e;
            if (t.getCause() instanceof TokenMgrError) {
                t = t.getCause();
            }
            assertEquals(expectedError, Exceptions.toMessageString(t).substring(0, expectedError.length()));
        }
    }

    private static Result evaluate(String expressionString, DocumentOperation op) throws ParseException {
        return new DocumentSelector(expressionString).accepts(op);
    }

    private static void assertError(String expressionString, DocumentOperation op, String expectedError) {
        try {
            evaluate(expressionString, op);
            fail("The evaluation of '" + expressionString + "' should throw an exception.");
        } catch (ParseException e) {
            fail("The expression '" + expressionString + "' should assertEquals ok.");
        } catch (RuntimeException e) {
            System.err.println("Error was : " + e);
            assertTrue(e.getMessage().length() >= expectedError.length());
            assertEquals(expectedError, e.getMessage().substring(0, expectedError.length()));
        }
    }

}
