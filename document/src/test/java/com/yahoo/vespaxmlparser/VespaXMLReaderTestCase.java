// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.document.update.AddValueUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.RemoveValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.text.Utf8;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Simple test case for Vespa XML parser.
 *
 * @author sveina
 */
public class VespaXMLReaderTestCase {

    private final DocumentTypeManager manager = new DocumentTypeManager();

    //NOTE: You generally want to extend com.yahoo.vespaxmlparser.test.documentxmltests.VespaXMLParserTestCase
    //instead  -- and do remember to update the C++ test case also

    @Before
    public void setUp() {
        DocumentTypeManagerConfigurer.configure(manager, "file:src/test/vespaxmlparser/documentmanager2.cfg");
    }

    @Test
    public void testMapNoKey() throws Exception {
        try {
            VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/testmapnokey.xml", manager);
            parser.readAll();
            assertTrue(false);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Test
    public void testMapNoValue() throws Exception {
        try {
            VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/testmapnovalue.xml", manager);
            parser.readAll();
            assertTrue(false);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Test
    public void testNews1() throws Exception {
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/testalltypes.xml", manager);
        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertTrue(VespaXMLFeedReader.OperationType.INVALID != op.getType());
        Document doc = op.getDocument();
        assertEquals(new StringFieldValue("testUrl"), doc.getFieldValue("url"));
        assertEquals(new StringFieldValue("testTitle"), doc.getFieldValue("title"));
        assertEquals(new IntegerFieldValue(1), doc.getFieldValue("last_downloaded"));
        assertEquals(new LongFieldValue(2), doc.getFieldValue("value_long"));
        assertEquals("foobar", Utf8.toString(((Raw)doc.getFieldValue("value_raw")).getByteBuffer()));

        Array strArr = (Array)doc.getFieldValue("stringarr");
        assertEquals(new StringFieldValue("stringarrItem1"), strArr.get(0));
        assertEquals(new StringFieldValue("stringarrItem2"), strArr.get(1));

        Array intArr = (Array)doc.getFieldValue("intarr");
        assertEquals(new IntegerFieldValue(-1311224359), intArr.get(0));
        assertEquals(new IntegerFieldValue(-1311224358), intArr.get(1));
        assertEquals(new IntegerFieldValue(-1), intArr.get(2));
        assertEquals(new IntegerFieldValue(-2147483648), intArr.get(3));

        Array longArr = (Array)doc.getFieldValue("longarr");
        assertEquals(new LongFieldValue(5L), longArr.get(0));
        assertEquals(new LongFieldValue(6L), longArr.get(1));

        Array byteArr = (Array)doc.getFieldValue("bytearr");
        assertEquals(new ByteFieldValue(7), byteArr.get(0));
        assertEquals(new ByteFieldValue(8), byteArr.get(1));

        Array floatArr = (Array)doc.getFieldValue("floatarr");
        assertEquals(new FloatFieldValue(9.0f), floatArr.get(0));
        assertEquals(new FloatFieldValue(10.0f), floatArr.get(1));

        WeightedSet intWset = (WeightedSet)doc.getFieldValue("weightedsetint");
        assertEquals(Integer.valueOf(11), intWset.get(new IntegerFieldValue(11)));
        assertEquals(Integer.valueOf(12), intWset.get(new IntegerFieldValue(12)));

        WeightedSet strWset = (WeightedSet)doc.getFieldValue("weightedsetstring");
        assertEquals(Integer.valueOf(13), strWset.get(new StringFieldValue("string13")));
        assertEquals(Integer.valueOf(14), strWset.get(new StringFieldValue("string14")));

        MapFieldValue strMap = (MapFieldValue)doc.getFieldValue("stringmap");
        assertEquals(new StringFieldValue("slovakia"), strMap.get(new StringFieldValue("italia")));
        assertEquals(new StringFieldValue("japan"), strMap.get(new StringFieldValue("danmark")));
        assertEquals(new StringFieldValue("new zealand"), strMap.get(new StringFieldValue("paraguay")));

        Struct struct = (Struct)doc.getFieldValue("structfield");
        assertEquals(new StringFieldValue("star wars"), struct.getFieldValue("title"));
        assertEquals(new StringFieldValue("dummy"), struct.getFieldValue("structfield"));

        List structArr = (List)doc.getFieldValue("structarr");
        assertEquals(2, structArr.size());
        assertEquals(new StringFieldValue("title1"), ((Struct)structArr.get(0)).getFieldValue("title"));
        assertEquals(new StringFieldValue("title2"), ((Struct)structArr.get(1)).getFieldValue("title"));
        assertEquals(new StringFieldValue("value1"),
                     ((MapFieldValue)((Struct)structArr.get(0)).getFieldValue("mymap")).get(
                             new StringFieldValue("key1")));
        assertEquals(new StringFieldValue("value2"),
                     ((MapFieldValue)((Struct)structArr.get(0)).getFieldValue("mymap")).get(
                             new StringFieldValue("key2")));
        assertEquals(new StringFieldValue("value1.1"),
                     ((MapFieldValue)((Struct)structArr.get(1)).getFieldValue("mymap")).get(
                             new StringFieldValue("key1.1")));
        assertEquals(new StringFieldValue("value1.2"),
                     ((MapFieldValue)((Struct)structArr.get(1)).getFieldValue("mymap")).get(
                             new StringFieldValue("key1.2")));

        MapFieldValue arrMap = (MapFieldValue)doc.getFieldValue("arrmap");
        assertEquals(2, arrMap.size());
        Array arr = (Array)arrMap.get(new StringFieldValue("foo"));
        assertEquals(3, arr.size());
        assertEquals(new StringFieldValue("hei1"), arr.get(0));
        assertEquals(new StringFieldValue("hei2"), arr.get(1));
        assertEquals(new StringFieldValue("hei3"), arr.get(2));
        arr = (Array)arrMap.get(new StringFieldValue("bar"));
        assertEquals(3, arr.size());
        assertEquals(new StringFieldValue("hei4"), arr.get(0));
        assertEquals(new StringFieldValue("hei5"), arr.get(1));
        assertEquals(new StringFieldValue("hei6"), arr.get(2));
    }

    @Test
    public void testNews3() throws Exception {
        // Updating all elements in a documentType
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test03.xml", manager);
        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertEquals(VespaXMLFeedReader.OperationType.UPDATE, op.getType());

        DocumentUpdate docUpdate = op.getDocumentUpdate();

        //url
        assertEquals(new StringFieldValue("assignUrl"), docUpdate.getFieldUpdate("url").getValueUpdate(0).getValue());

        //title
        assertEquals(new StringFieldValue("assignTitle"),
                     docUpdate.getFieldUpdate("title").getValueUpdate(0).getValue());

        //last_downloaded
        assertEquals(new IntegerFieldValue(1),
                     docUpdate.getFieldUpdate("last_downloaded").getValueUpdate(0).getValue());

        //value_long
        assertEquals(new LongFieldValue((long)2), docUpdate.getFieldUpdate("value_long").getValueUpdate(0).getValue());

        //stringarr
        List stringarr = (List)docUpdate.getFieldUpdate("stringarr").getValueUpdate(0).getValue();
        assertEquals(new StringFieldValue("assignString1"), stringarr.get(0));
        assertEquals(new StringFieldValue("assignString2"), stringarr.get(1));

        //intarr
        List intarr = (List)docUpdate.getFieldUpdate("intarr").getValueUpdate(0).getValue();
        assertEquals(new IntegerFieldValue(3), intarr.get(0));
        assertEquals(new IntegerFieldValue(4), intarr.get(1));

        //longarr
        List longarr = (List)docUpdate.getFieldUpdate("longarr").getValueUpdate(0).getValue();
        assertEquals(new LongFieldValue((long)5), longarr.get(0));
        assertEquals(new LongFieldValue((long)6), longarr.get(1));

        //bytearr
        List bytearr = (List)docUpdate.getFieldUpdate("bytearr").getValueUpdate(0).getValue();
        assertEquals(new ByteFieldValue((byte)7), bytearr.get(0));
        assertEquals(new ByteFieldValue((byte)8), bytearr.get(1));

        //floatarr
        List floatarr = (List)docUpdate.getFieldUpdate("floatarr").getValueUpdate(0).getValue();
        assertEquals(new FloatFieldValue((float)9), floatarr.get(0));
        assertEquals(new FloatFieldValue((float)10), floatarr.get(1));

        //weightedsetint
        WeightedSet weightedsetint =
                (WeightedSet)docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(0).getValue();
        assertEquals(Integer.valueOf(11), weightedsetint.get(new IntegerFieldValue(11)));
        assertEquals(Integer.valueOf(12), weightedsetint.get(new IntegerFieldValue(12)));

        //weightedsetstring
        WeightedSet weightedsetstring =
                (WeightedSet)docUpdate.getFieldUpdate("weightedsetstring").getValueUpdate(0).getValue();
        assertEquals(Integer.valueOf(13), weightedsetstring.get(new StringFieldValue("assign13")));
        assertEquals(Integer.valueOf(14), weightedsetstring.get(new StringFieldValue("assign14")));

    }

    @Test
    public void testNews4() throws Exception {
        // Test on adding just a few fields to a DocumentUpdate (implies other fields to null)
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test04.xml", manager);

        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertEquals(VespaXMLFeedReader.OperationType.UPDATE, op.getType());

        DocumentUpdate docUpdate = op.getDocumentUpdate();
        //url
        assertEquals(new StringFieldValue("assignUrl"), docUpdate.getFieldUpdate("url").getValueUpdate(0).getValue());

        //title
        assertNull(docUpdate.getFieldUpdate("title"));

        //last_downloaded
        assertNull(docUpdate.getFieldUpdate("last_downloaded"));

        //value_long
        assertEquals(new LongFieldValue((long)2), docUpdate.getFieldUpdate("value_long").getValueUpdate(0).getValue());

        //value_content
        assertNull(docUpdate.getFieldUpdate("value_content"));

        //stringarr
        List stringarr = (List)docUpdate.getFieldUpdate("stringarr").getValueUpdate(0).getValue();
        assertEquals(new StringFieldValue("assignString1"), stringarr.get(0));
        assertEquals(new StringFieldValue("assignString2"), stringarr.get(1));

        //intarr
        List intarr = (List)docUpdate.getFieldUpdate("intarr").getValueUpdate(0).getValue();
        assertEquals(new IntegerFieldValue(3), intarr.get(0));
        assertEquals(new IntegerFieldValue(4), intarr.get(1));

        //longarr
        assertNull(docUpdate.getFieldUpdate("longarr"));

        //bytearr
        assertNull(docUpdate.getFieldUpdate("bytearr"));

        //floatarr
        assertNull(docUpdate.getFieldUpdate("floatarr"));

        //weightedsetint
        WeightedSet weightedsetint =
                (WeightedSet)docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(0).getValue();
        assertEquals(Integer.valueOf(11), weightedsetint.get(new IntegerFieldValue(11)));
        assertEquals(Integer.valueOf(12), weightedsetint.get(new IntegerFieldValue(12)));

        //weightedsetstring
        assertNull(docUpdate.getFieldUpdate("weightedsetstring"));
    }

    @Test
    public void testNews5() throws Exception {
        // Adding a few new fields to a Document using different syntax
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test05.xml", manager);

        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertEquals(VespaXMLFeedReader.OperationType.UPDATE, op.getType());

        DocumentUpdate docUpdate = op.getDocumentUpdate();

        //url
        assertNull(docUpdate.getFieldUpdate("url"));

        //title
        assertNull(docUpdate.getFieldUpdate("title"));

        //last_downloaded
        assertNull(docUpdate.getFieldUpdate("last_downloaded"));

        //value_long
        //assertNull(docUpdate.getFieldUpdate("value_long"));

        //value_content
        assertNull(docUpdate.getFieldUpdate("value_content"));

        //stringarr
        List stringarr = docUpdate.getFieldUpdate("stringarr").getValueUpdates();//.getValueUpdate(0).getValue();
        assertEquals(new StringFieldValue("addString1"), ((ValueUpdate)stringarr.get(0)).getValue());
        assertEquals(new StringFieldValue("addString2"), ((ValueUpdate)stringarr.get(1)).getValue());

        //intarr
        assertNull(docUpdate.getFieldUpdate("intarr"));

        //longarr
        List longarr = docUpdate.getFieldUpdate("longarr").getValueUpdates();
        assertEquals(new LongFieldValue((long)5), ((ValueUpdate)longarr.get(0)).getValue());

        //bytearr
        assertNull(docUpdate.getFieldUpdate("bytearr"));

        //floatarr
        assertNull(docUpdate.getFieldUpdate("floatarr"));

        //weightedsetint
        assertEquals(new IntegerFieldValue(11), docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(0).getValue());
        assertEquals(Integer.valueOf(11),
                     (Integer)((AddValueUpdate)docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(0))
                             .getWeight());
        assertEquals(new IntegerFieldValue(12), docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(1).getValue());
        assertEquals(Integer.valueOf(12),
                     (Integer)((AddValueUpdate)docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(1))
                             .getWeight());

        //weightedsetstring
        assertEquals(new StringFieldValue("add13"), docUpdate.getFieldUpdate("weightedsetstring").getValueUpdate(0).getValue());
        assertEquals(Integer.valueOf(1),
                     (Integer)((AddValueUpdate)docUpdate.getFieldUpdate("weightedsetstring").getValueUpdate(0))
                             .getWeight());
    }

    @Test
    public void testNews6() throws Exception {
        // A document containing fields with invalid values. Different variants are used All of the updates specified in
        // XML-file should be skipped and not added to queue Except the three of them
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test06.xml", manager);

        // long value with txt
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // empty string
        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);
        assertEquals("doc:news:http://news6b", op.getDocument().getId().toString());

        // int array with text
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // long array with whitespace
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // byte array with value
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // float array with string
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // weighted set of int with string
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // weighted set of int with string as weight
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // weighted set of string with string as weight
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        parser.read(op = new VespaXMLFeedReader.Operation());
        assertEquals("doc:news:http://news6j", op.getDocument().getId().toString());

        parser.read(op = new VespaXMLFeedReader.Operation());
        assertEquals(VespaXMLFeedReader.OperationType.INVALID, op.getType());
    }

    @Test
    public void testNews7() throws Exception {
        // Testing different variants of increment/decrement/multiply/divide among with alterupdate. In test07.xml there
        // are also some updates that will fail (be skipped).
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test07.xml", manager);

        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertEquals(VespaXMLFeedReader.OperationType.UPDATE, op.getType());

        DocumentUpdate docUpdate = op.getDocumentUpdate();

        List<ValueUpdate> vuList = docUpdate.getFieldUpdate("last_downloaded").getValueUpdates();
        assertEquals(new DoubleFieldValue(2.0), vuList.get(0).getValue());
        assertEquals(new DoubleFieldValue(3.0), vuList.get(1).getValue());
        assertEquals(new DoubleFieldValue(4.0), vuList.get(2).getValue());
        assertEquals(new DoubleFieldValue(5.0), vuList.get(3).getValue());

        assertEquals(new IntegerFieldValue(7), docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(0).getValue());
        assertEquals(new DoubleFieldValue(6.0), ((MapValueUpdate)docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(0))
                .getUpdate().getValue());

        assertEquals(new IntegerFieldValue(9), docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(1).getValue());
        assertEquals(new DoubleFieldValue(8.0), ((MapValueUpdate)docUpdate.getFieldUpdate("weightedsetint").getValueUpdate(1))
                .getUpdate().getValue());

        assertEquals(new IntegerFieldValue(11), docUpdate.getFieldUpdate("intarr").getValueUpdate(0).getValue());
        assertEquals(new DoubleFieldValue(10.0),
                     ((MapValueUpdate)docUpdate.getFieldUpdate("intarr").getValueUpdate(0)).getUpdate().getValue());

        assertEquals(new IntegerFieldValue(13), docUpdate.getFieldUpdate("floatarr").getValueUpdate(0).getValue());
        assertEquals(new DoubleFieldValue(12.0),
                     ((MapValueUpdate)docUpdate.getFieldUpdate("floatarr").getValueUpdate(0)).getUpdate().getValue());

        assertEquals(new IntegerFieldValue(15), docUpdate.getFieldUpdate("floatarr").getValueUpdate(1).getValue());
        assertEquals(new DoubleFieldValue(14.0),
                     ((MapValueUpdate)docUpdate.getFieldUpdate("floatarr").getValueUpdate(1)).getUpdate().getValue());

        // Trying arithmetic on string (b)
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // "By" as string (c)
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // Empty key in weighted set of int (d)
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // No "by" attribute (e)
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // Float key as string (f)
        try {
            parser.read(new VespaXMLFeedReader.Operation());
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testNews8() throws Exception {
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test08.xml", manager);

        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertEquals(VespaXMLFeedReader.OperationType.UPDATE, op.getType());

        DocumentUpdate docUpdate = op.getDocumentUpdate();

        //stringarr
        List<ValueUpdate> vuList = docUpdate.getFieldUpdate("stringarr").getValueUpdates();
        assertTrue(vuList.get(0) instanceof RemoveValueUpdate);
        assertEquals(new StringFieldValue("removeString1"), vuList.get(0).getValue());
        assertTrue(vuList.get(1) instanceof RemoveValueUpdate);
        assertEquals(new StringFieldValue("removeString2"), vuList.get(1).getValue());

        //weightedsetint
        vuList = docUpdate.getFieldUpdate("weightedsetint").getValueUpdates();
        assertEquals(2, vuList.size());
        assertTrue(vuList.contains(new RemoveValueUpdate(new IntegerFieldValue(5))));
        assertTrue(vuList.contains(new RemoveValueUpdate(new IntegerFieldValue(4))));
    }

    @Test
    public void testNews9() throws Exception {
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test09.xml", manager);

        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);

            assertEquals(VespaXMLFeedReader.OperationType.REMOVE, op.getType());
            assertEquals("doc:news:http://news9a", op.getRemove().toString());
        }
        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);

            assertEquals(VespaXMLFeedReader.OperationType.REMOVE, op.getType());
            assertEquals("doc:news:http://news9b", op.getRemove().toString());
        }
        {
            // Remove without documentid. Not supported.
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            try {
                parser.read(op);
                fail();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    @Test
    public void testNews10() throws Exception {
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test10.xml", manager);
        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);
            Document doc = op.getDocument();

            assertEquals(new StringFieldValue("testUrl"), doc.getFieldValue("url"));
            assertEquals(new StringFieldValue("testTitle"), doc.getFieldValue("title"));
            assertEquals(new IntegerFieldValue(1), doc.getFieldValue("last_downloaded"));
            assertEquals(new LongFieldValue(2), doc.getFieldValue("value_long"));

            Array strArr = (Array)doc.getFieldValue("stringarr");
            assertEquals(new StringFieldValue("stringarrItem1"), strArr.get(0));
            assertEquals(new StringFieldValue("stringarrItem2"), strArr.get(1));

            Array intArr = (Array)doc.getFieldValue("intarr");
            assertEquals(new IntegerFieldValue(3), intArr.get(0));
            assertEquals(new IntegerFieldValue(4), intArr.get(1));

            Array longArr = (Array)doc.getFieldValue("longarr");
            assertEquals(new LongFieldValue(5L), longArr.get(0));
            assertEquals(new LongFieldValue(6L), longArr.get(1));

            Array byteArr = (Array)doc.getFieldValue("bytearr");
            assertEquals(new ByteFieldValue((byte)7), byteArr.get(0));
            assertEquals(new ByteFieldValue((byte)8), byteArr.get(1));

            Array floatArr = (Array)doc.getFieldValue("floatarr");
            assertEquals(new FloatFieldValue(9.0f), floatArr.get(0));
            assertEquals(new FloatFieldValue(10.0f), floatArr.get(1));

            WeightedSet intWset = (WeightedSet)doc.getFieldValue("weightedsetint");
            assertEquals(Integer.valueOf(11), intWset.get(new IntegerFieldValue(11)));
            assertEquals(Integer.valueOf(12), intWset.get(new IntegerFieldValue(12)));

            WeightedSet strWset = (WeightedSet)doc.getFieldValue("weightedsetstring");
            assertEquals(Integer.valueOf(13), strWset.get(new StringFieldValue("string13")));
            assertEquals(Integer.valueOf(14), strWset.get(new StringFieldValue("string14")));
        }
        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);
            Document doc = op.getDocument();
            assertNotNull(doc);
            assertEquals(new StringFieldValue("testUrl2"), doc.getFieldValue("url"));
        }
        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);
            DocumentUpdate upd = op.getDocumentUpdate();

            assertNull(upd.getFieldUpdate("url"));
            assertNull(upd.getFieldUpdate("title"));
            assertNull(upd.getFieldUpdate("last_downloaded"));
            assertNull(upd.getFieldUpdate("value_long"));
            assertNull(upd.getFieldUpdate("value_content"));

            List<ValueUpdate> lst = upd.getFieldUpdate("stringarr").getValueUpdates();
            assertEquals(new StringFieldValue("addString1"), lst.get(0).getValue());
            assertEquals(new StringFieldValue("addString2"), lst.get(1).getValue());

            assertNull(upd.getFieldUpdate("intarr"));

            lst = upd.getFieldUpdate("longarr").getValueUpdates();
            assertEquals(new LongFieldValue((long)5), lst.get(0).getValue());

            assertNull(upd.getFieldUpdate("bytearr"));
            assertNull(upd.getFieldUpdate("floatarr"));

            assertEquals(new IntegerFieldValue(11), upd.getFieldUpdate("weightedsetint").getValueUpdate(0).getValue());
            assertEquals(Integer.valueOf(11),
                         (Integer)((AddValueUpdate)upd.getFieldUpdate("weightedsetint").getValueUpdate(0))
                                 .getWeight());
            assertEquals(new IntegerFieldValue(12), upd.getFieldUpdate("weightedsetint").getValueUpdate(1).getValue());
            assertEquals(Integer.valueOf(12),
                         (Integer)((AddValueUpdate)upd.getFieldUpdate("weightedsetint").getValueUpdate(1))
                                 .getWeight());

            assertEquals(new StringFieldValue("add13"), upd.getFieldUpdate("weightedsetstring").getValueUpdate(0).getValue());
            assertEquals(Integer.valueOf(1),
                         (Integer)((AddValueUpdate)upd.getFieldUpdate("weightedsetstring").getValueUpdate(0))
                                 .getWeight());
        }
        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);
            DocumentUpdate upd = op.getDocumentUpdate();

            assertEquals(new StringFieldValue("assignUrl"),
                         upd.getFieldUpdate("url").getValueUpdate(0).getValue());

            assertNull(upd.getFieldUpdate("title"));
            assertNull(upd.getFieldUpdate("last_downloaded"));
            assertEquals(new LongFieldValue(2),
                         upd.getFieldUpdate("value_long").getValueUpdate(0).getValue());
            assertNull(upd.getFieldUpdate("value_content"));

            Array strArr = (Array)upd.getFieldUpdate("stringarr").getValueUpdate(0).getValue();
            assertEquals(new StringFieldValue("assignString1"), strArr.get(0));
            assertEquals(new StringFieldValue("assignString2"), strArr.get(1));

            Array intArr = (Array)upd.getFieldUpdate("intarr").getValueUpdate(0).getValue();
            assertEquals(new IntegerFieldValue(3), intArr.get(0));
            assertEquals(new IntegerFieldValue(4), intArr.get(1));

            assertNull(upd.getFieldUpdate("longarr"));
            assertNull(upd.getFieldUpdate("bytearr"));
            assertNull(upd.getFieldUpdate("floatarr"));

            WeightedSet intWset = (WeightedSet)upd.getFieldUpdate("weightedsetint").getValueUpdate(0).getValue();
            assertEquals(Integer.valueOf(11), intWset.get(new IntegerFieldValue(11)));
            assertEquals(Integer.valueOf(12), intWset.get(new IntegerFieldValue(12)));

            assertNull(upd.getFieldUpdate("weightedsetstring"));
        }
        {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);
            assertEquals("doc:news:http://news10e", op.getRemove().toString());
        }
        {
            // Illegal remove without documentid attribute
            try {
                VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
                parser.read(op);
                fail();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    @Test
    public void testFieldPathUpdates() throws Exception {
        // Adding a few new fields to a Document using different syntax
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/tests/vespaxml/fieldpathupdates.xml", manager);

        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        parser.read(op);

        assertEquals(VespaXMLFeedReader.OperationType.UPDATE, op.getType());

        DocumentUpdate docUpdate = op.getDocumentUpdate();

        assertEquals(20, docUpdate.getFieldPathUpdates().size());

        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(0);
            assertEquals("url", ass.getOriginalFieldPath());
            assertEquals(new StringFieldValue("assignUrl"), ass.getNewValue());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(1);
            assertEquals("title", ass.getOriginalFieldPath());
            assertEquals(new StringFieldValue("assignTitle"), ass.getNewValue());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(2);
            assertEquals("last_downloaded", ass.getOriginalFieldPath());
            assertEquals("1", ass.getExpression());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(3);
            assertEquals("value_long", ass.getOriginalFieldPath());
            assertEquals("2", ass.getExpression());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(5);
            assertEquals("stringarr", ass.getOriginalFieldPath());
            assertEquals("[assignString1, assignString2]", ass.getNewValue().toString());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(6);
            assertEquals("intarr", ass.getOriginalFieldPath());
            assertEquals("[3, 4]", ass.getNewValue().toString());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(7);
            assertEquals("longarr", ass.getOriginalFieldPath());
            assertEquals("[5, 6]", ass.getNewValue().toString());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(8);
            assertEquals("bytearr", ass.getOriginalFieldPath());
            assertEquals("[7, 8]", ass.getNewValue().toString());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(9);
            assertEquals("floatarr", ass.getOriginalFieldPath());
            assertEquals("[9.0, 10.0]", ass.getNewValue().toString());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(10);
            assertEquals("weightedsetint", ass.getOriginalFieldPath());
            WeightedSet set = (WeightedSet)ass.getNewValue();
            assertEquals(Integer.valueOf(11), set.get(new IntegerFieldValue(11)));
            assertEquals(Integer.valueOf(12), set.get(new IntegerFieldValue(12)));
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(11);
            assertEquals("weightedsetstring", ass.getOriginalFieldPath());
            WeightedSet set = (WeightedSet)ass.getNewValue();
            assertEquals(Integer.valueOf(13), set.get(new StringFieldValue("assign13")));
            assertEquals(Integer.valueOf(14), set.get(new StringFieldValue("assign14")));
        }
        {
            AddFieldPathUpdate ass = (AddFieldPathUpdate)docUpdate.getFieldPathUpdates().get(12);
            assertEquals("stringarr", ass.getOriginalFieldPath());
            assertEquals("[addString1, addString2]", ass.getNewValues().toString());
        }
        {
            AddFieldPathUpdate ass = (AddFieldPathUpdate)docUpdate.getFieldPathUpdates().get(13);
            assertEquals("longarr", ass.getOriginalFieldPath());
            assertEquals("[5]", ass.getNewValues().toString());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(14);
            assertEquals("weightedsetint{13}", ass.getOriginalFieldPath());
            assertEquals("13", ass.getExpression());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(15);
            assertEquals("weightedsetint{14}", ass.getOriginalFieldPath());
            assertEquals("14", ass.getExpression());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(16);
            assertEquals("weightedsetstring{add13}", ass.getOriginalFieldPath());
            assertEquals("1", ass.getExpression());
        }
        {
            AssignFieldPathUpdate ass = (AssignFieldPathUpdate)docUpdate.getFieldPathUpdates().get(17);
            assertEquals("weightedsetstring{assign13}", ass.getOriginalFieldPath());
            assertEquals("130", ass.getExpression());
        }
        {
            RemoveFieldPathUpdate ass = (RemoveFieldPathUpdate)docUpdate.getFieldPathUpdates().get(18);
            assertEquals("weightedsetstring{assign14}", ass.getOriginalFieldPath());
        }
        {
            RemoveFieldPathUpdate ass = (RemoveFieldPathUpdate)docUpdate.getFieldPathUpdates().get(19);
            assertEquals("bytearr", ass.getOriginalFieldPath());
        }
        Document doc = new Document(manager.getDocumentType("news"), new DocumentId("doc:test:test:test"));
        docUpdate.applyTo(doc);
    }

    @Test
    public void testDocInDoc() throws Exception {
        DocumentTypeManager m = new DocumentTypeManager();
        DocumentTypeManagerConfigurer
                .configure(m, "file:src/test/java/com/yahoo/document/documentmanager.docindoc.cfg");

        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/test_docindoc.xml", m);
        List<VespaXMLFeedReader.Operation> ops = parser.readAll();

        assertEquals(1, ops.size());
        VespaXMLFeedReader.Operation op = ops.get(0);
        System.err.println(op);

        assertEquals(VespaXMLFeedReader.OperationType.DOCUMENT, op.getType());
        assertNull(op.getRemove());
        assertNull(op.getDocumentUpdate());
        assertNull(op.getFeedOperation());
        assertNotNull(op.getDocument());

        Document doc = op.getDocument();

        assertEquals("outerdoc", doc.getDataType().getName());
        assertEquals("doc:outer:this:is:outer:doc", doc.getId().toString());
        assertEquals(1, doc.getFieldCount());

        Array lst = (Array)doc.getFieldValue("innerdocuments");
        assertNotNull(lst);
        assertEquals(3, lst.size());

        Document child = (Document)lst.get(0);
        assertEquals(2, child.getFieldCount());
        assertEquals("Peter Sellers", child.getFieldValue("name").toString());
        assertEquals("Comedian", child.getFieldValue("content").toString());

        child = (Document)lst.get(1);
        assertEquals(2, child.getFieldCount());
        assertEquals("Ole Olsen", child.getFieldValue("name").toString());
        assertEquals("Common man", child.getFieldValue("content").toString());

        child = (Document)lst.get(2);
        assertEquals(2, child.getFieldCount());
        assertEquals("Stein Nilsen", child.getFieldValue("name").toString());
        assertEquals("Worker", child.getFieldValue("content").toString());
    }

    @Test(expected = DeserializationException.class)
    public void testBinaryEncodingStrings() throws Exception {
        DocumentTypeManager dtm = new DocumentTypeManager();

        DocumentType type = new DocumentType("foo");
        type.addField(new Field("title", DataType.STRING));

        dtm.registerDocumentType(type);

        String input =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<vespafeed>\n" +
                        "    <document documenttype=\"foo\" documentid=\"doc:foo:bar:baz\"> \n" +
                        "        <title binaryencoding=\"base64\">testTitle</title>\n" +
                        "    </document>\n" +
                        "</vespafeed>\n";

        VespaXMLFeedReader parser = new VespaXMLFeedReader(new ByteArrayInputStream(Utf8.toBytes(input)), dtm);
        parser.readAll();
    }

    @Test(expected = DeserializationException.class)
    public void testIllegalCharacterInStrings() throws Exception {
        DocumentTypeManager dtm = new DocumentTypeManager();

        DocumentType type = new DocumentType("foo");
        type.addField(new Field("title", DataType.STRING));

        dtm.registerDocumentType(type);

        String input =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<vespafeed>\n" +
                        "    <document documenttype=\"foo\" documentid=\"doc:foo:bar:baz\"> \n" +
                        "        <title>test\uFDDFTitle</title>\n" +
                        "    </document>\n" +
                        "</vespafeed>\n";

        VespaXMLFeedReader parser = new VespaXMLFeedReader(new ByteArrayInputStream(Utf8.toBytes(input)), dtm);
        parser.readAll();
    }

    @Test
    public void testTestAndSetConditionAttribute() throws Exception {
        VespaXMLFeedReader parser = new VespaXMLFeedReader("src/test/vespaxmlparser/testandset.xml", manager);
        final int NUM_OPERATIONS_IN_FEED = 3;

        for (int i = 0; i < NUM_OPERATIONS_IN_FEED; i++) {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            parser.read(op);

            assertTrue("Missing test and set condition", op.getCondition().isPresent());
            assertEquals("Condition is not the same as in xml feed", "news.value_long == 1", op.getCondition().getSelection());
        }
    }

}
