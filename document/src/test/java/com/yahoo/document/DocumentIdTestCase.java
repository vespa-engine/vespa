// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.idstring.IdIdString;
import com.yahoo.document.idstring.IdString;
import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Before;
import org.junit.Test;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DocumentIdTestCase {

    DocumentTypeManager manager = new DocumentTypeManager();

    @Before
    public void setUp() {
        DocumentType testDocType = new DocumentType("testdoc");

        testDocType.addField("intattr", DataType.INT);
        testDocType.addField("rawattr", DataType.RAW);
        testDocType.addField("floatattr", DataType.FLOAT);
        testDocType.addField("stringattr", DataType.STRING);
        testDocType.addField("Minattr", DataType.INT);

        manager.registerDocumentType(testDocType);
    }

    @Test
    public void testCompareTo() {
        DocumentId docId1 = new Document(manager.getDocumentType("testdoc"), new DocumentId("id:ns:testdoc::http://www.uio.no/")).getId();
        DocumentId docId2 = new Document(manager.getDocumentType("testdoc"), new DocumentId("id:ns:testdoc::http://www.uio.no/")).getId();
        DocumentId docId3 = new Document(manager.getDocumentType("testdoc"), new DocumentId("id:ns:testdoc::http://www.ntnu.no/")).getId();

        assertTrue(docId1.equals(docId2));
        assertTrue(!docId1.equals(docId3));
        assertTrue(docId1.compareTo(docId3) > 0);
        assertTrue(docId3.compareTo(docId1) < 0);

        assertEquals(docId1.hashCode(), docId2.hashCode());

    }

    private void checkInvalidUri(String uri) {
        try {
            //invalid URI
            new DocumentId(uri);
            fail();
        } catch (IllegalArgumentException iae) {
        }
    }

    @Test
    public void testValidInvalidUriSchemes() {
        try {
            //valid URIs
            new DocumentId("id:namespace:type:n=42:whatever");
            new DocumentId("id:namespace:type::whatever");
        } catch (IllegalArgumentException iae) {
            fail(iae.getMessage());
        }

        checkInvalidUri("foobar:");
        checkInvalidUri("ballooo:blabla/something/");
        checkInvalidUri("id:namespace:type");
        checkInvalidUri("id:namespace:type:key-values");
        checkInvalidUri("id:namespace:type:n=0,n=1:foo");
        checkInvalidUri("id:namespace:type:g=foo,g=bar:foo");
        checkInvalidUri("id:namespace:type:n=0,g=foo:foo");
    }

    @Test
    public void empty_user_location_value_throws_exception() {
        try {
            new DocumentId("id:namespace:type:n=:foo");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("ID location value for 'n=' key is empty", e.getMessage());
        }
    }

    @Test
    public void empty_group_location_value_throws_exception() {
        try {
            new DocumentId("id:namespace:type:g=:foo");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("ID location value for 'g=' key is empty", e.getMessage());
        }
    }

    //Compares globalId with C++ implementation located in
    // ~document-HEAD/document/src/tests/cpp-globalidbucketids.txt
    @Test
    public void testCalculateGlobalId() throws IOException {

        String file = "src/tests/cpp-globalidbucketids.txt";
        BufferedReader fr = new BufferedReader(new FileReader(file));
        String line;
        String[] split_line;
        String[] split_gid;
        byte[] b;

        // reads from file
        while ((line = fr.readLine()) != null) {
            split_line = line.split(" - ");
            DocumentId mydoc = new DocumentId(split_line[0]);
            b = mydoc.getGlobalId();
            split_gid = Pattern.compile("\\(|\\)").split(split_line[1]);
            compareStringByte(split_gid[1],b);
        }
        fr.close();
    }

    private void compareStringByte(String s, byte[] b){
        /*
          System.out.println("-- "+s+" --");
          System.out.print("++ 0x");
          for (int i=0; i<b.length; ++i) {
          int nr = b[i] & 0xFF;
          System.out.print(Integer.toHexString(nr / 16) + Integer.toHexString(nr % 16));
          }
          System.out.println(" ++");
        */
        s = s.substring(2);
        assertEquals(s.length()/2, b.length);
        for(int i=0; i<b.length;i++){
            String ss = s.substring(2*i,2*i+2);
            assertEquals(Integer.valueOf(ss, 16).intValue(),(((int)b[i])+256)%256);
        }       
    }

    //Compares bucketId with C++ implementation located in
    // ~document-HEAD/document/src/tests/cpp-globalidbucketids.txt
    @Test
    public void testGetBucketId() throws IOException{
        String file = "src/tests/cpp-globalidbucketids.txt";
        BufferedReader fr = new BufferedReader(new FileReader(file));
        String line;
        String[] split_line;
        BucketId bid;

        // reads from file
        while ((line = fr.readLine()) != null) {
            split_line = line.split(" - ");
            DocumentId mydoc = new DocumentId(split_line[0]);
            BucketIdFactory factory = new BucketIdFactory(32, 26, 6);
            bid = new BucketId(factory.getBucketId(mydoc).getId());
            assertEquals(split_line[2], bid.toString());
        }
        fr.close();
    }

    @Test
    public void testIdStrings() {
        DocumentId docId = new DocumentId(new IdIdString("namespace", "type", "g=group", "foobar"));
        assertEquals("id:namespace:type:g=group:foobar", docId.toString());
        assertTrue(docId.hasDocType());
        assertEquals("type", docId.getDocType());
    }

    @Test
    public void testIdStringFeatures() {
        DocumentId none = new DocumentId("id:ns:type::foo");
        assertFalse(none.getScheme().hasGroup());
        assertFalse(none.getScheme().hasNumber());

        DocumentId user = new DocumentId("id:ns:type:n=42:foo");
        assertFalse(user.getScheme().hasGroup());
        assertTrue(user.getScheme().hasNumber());
        assertEquals(42, user.getScheme().getNumber());

        DocumentId group = new DocumentId("id:ns:type:g=mygroup:foo");
        assertTrue(group.getScheme().hasGroup());
        assertFalse(group.getScheme().hasNumber());
        assertEquals("mygroup", group.getScheme().getGroup());

        group = new DocumentId("id:ns:type:g=mygroup:foo");
        assertTrue(group.getScheme().hasGroup());
        assertFalse(group.getScheme().hasNumber());
        assertEquals("mygroup", group.getScheme().getGroup());
    }

    @Test
    public void testHashCodeOfGids() {
        DocumentId docId0 = new DocumentId("id:blabla:type::0");
        byte[] docId0Gid = docId0.getGlobalId();
        DocumentId docId0Copy = new DocumentId("id:blabla:type::0");
        byte[] docId0CopyGid = docId0Copy.getGlobalId();

        assertArrayEquals(docId0Gid, docId0CopyGid);

        //straight hashCode() of byte arrays won't be the same
        assertNotEquals(docId0Gid.hashCode(), docId0CopyGid.hashCode());

        //Arrays.hashCode() works better...
        assertEquals(Arrays.hashCode(docId0Gid), Arrays.hashCode(docId0CopyGid));
    }

    @Test
    public void testDocumentIdCanOnlyContainTextCharacters() throws UnsupportedEncodingException {
        assertExceptionWhenConstructing(new byte[]{105, 100, 58, 97, 58, 98, 58, 58, 0, 99}, // "id:a:b::0x0c"
                                        "illegal code point 0x0");
        assertExceptionWhenConstructing(new byte[]{105, 100, 58, 97, 58, 98, 58, 58, 7, 99}, // "id:a:b::0x7c"
                                        "illegal code point 0x7");
    }

    private void assertExceptionWhenConstructing(byte[] rawId,
                                                 String exceptionMsg) throws UnsupportedEncodingException {
        String strId = new String(rawId, "UTF-8");
        try {
            new DocumentId(strId);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(exceptionMsg));
        }
    }

    @Test
    public void testSerializedDocumentIdCanContainNonTextCharacter() {
        String strId = new String(new byte[]{105, 100, 58, 97, 58, 98, 58, 58, 7, 99}); // "id:a:b::0x7c"
        DocumentId docId = DocumentId.createFromSerialized(strId);
        {
            assertEquals(strId, docId.toString());
        }
        {
            BufferSerializer buf = new BufferSerializer();
            docId.serialize(buf);
            buf.flip();
            DocumentId deserializedId = new DocumentId(buf);
            assertEquals(strId, deserializedId.toString());
        }
    }

    @Test
    public void testSerializedDocumentIdCannotContainZeroByte() {
        String strId = new String(new byte[]{105, 100, 58, 97, 58, 98, 58, 58, 0, 99}); // "id:a:b::0x0c"
        try {
            DocumentId.createFromSerialized(strId);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("illegal zero byte code point"));
        }
    }

    @Test
    public void testTooLongDocId() {
        StringBuilder sb = new StringBuilder("id:ns:type::");
        for(int i=0; i < 0x10000; i++) {
            sb.append('x');
        }
        try {
            new DocumentId(sb.toString());
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException ex) {
            assertEquals("Document id length 65548 is longer than max length of 65536", ex.getMessage());
        }
        assertEquals(65548, new DocumentId(IdString.createIdStringLessStrict(sb.toString())).toString().length());
    }

}
