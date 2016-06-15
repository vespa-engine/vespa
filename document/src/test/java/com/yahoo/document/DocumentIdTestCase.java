// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.*;
import com.yahoo.document.idstring.*;

import java.math.BigInteger;

import java.io.*;
import java.util.regex.Pattern;
import java.util.Arrays;

public class DocumentIdTestCase extends junit.framework.TestCase {
    DocumentTypeManager manager = new DocumentTypeManager();

    public DocumentIdTestCase(String name) {
        super(name);
    }

    protected void setUp() {
        DocumentType testDocType = new DocumentType("testdoc");

        testDocType.addHeaderField("intattr", DataType.INT);
        testDocType.addField("rawattr", DataType.RAW);
        testDocType.addField("floatattr", DataType.FLOAT);
        testDocType.addHeaderField("stringattr", DataType.STRING);
        testDocType.addHeaderField("Minattr", DataType.INT);

        manager.registerDocumentType(testDocType);
    }

    public void testCompareTo() {
        DocumentId docId1 = new Document(manager.getDocumentType("testdoc"), new DocumentId("doc:testdoc:http://www.uio.no/")).getId();
        DocumentId docId2 = new Document(manager.getDocumentType("testdoc"), new DocumentId("doc:testdoc:http://www.uio.no/")).getId();
        DocumentId docId3 = new Document(manager.getDocumentType("testdoc"), new DocumentId("doc:testdoc:http://www.ntnu.no/")).getId();

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

    public void testValidInvalidUriSchemes() {
        try {
            //valid URIs
            new DocumentId("doc:blabla:something");
            new DocumentId("doc:doc:doc");
            new DocumentId("userdoc:bla:2387:");
            new DocumentId("userdoc:bar:0:");
            new DocumentId("userdoc:bar:18446744073709551615:");
            new DocumentId("userdoc:foo:15:bar");
            new DocumentId("id:namespace:type:n=42:whatever");
            new DocumentId("id:namespace:type::whatever");
        } catch (IllegalArgumentException iae) {
            fail(iae.getMessage());
        }

        checkInvalidUri("foobar:");
        checkInvalidUri("ballooo:blabla/something/");
        checkInvalidUri("doc:");
        checkInvalidUri("doc::");
        checkInvalidUri("doc:::");
        checkInvalidUri("doc::/");
        checkInvalidUri("doc");
        checkInvalidUri("userdoc:");
        checkInvalidUri("userdoc::");
        checkInvalidUri("userdoc:::");
        checkInvalidUri("userdoc:::/");
        checkInvalidUri("userdoc");
        checkInvalidUri("userdoc:-87987//");
        checkInvalidUri("userdoc:18446744073709551620/bar/");
        checkInvalidUri("id:namespace:type");
        checkInvalidUri("id:namespace:type:key-values");
        checkInvalidUri("id:namespace:type:n=0,n=1:foo");
        checkInvalidUri("id:namespace:type:g=foo,g=bar:foo");
        checkInvalidUri("id:namespace:type:n=0,g=foo:foo");
    }


    //Compares globalId with C++ implementation located in
    // ~document-HEAD/document/src/tests/cpp-globalidbucketids.txt
    public void testCalculateGlobalId() throws IOException{

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

    public void testGroupdoc() {
        try {
            //valid
            new DocumentId("groupdoc:blabla:something:jkl");
            new DocumentId("groupdoc:doc:doc:asd");
            new DocumentId("groupdoc:bar:0:a");
            new DocumentId("groupdoc:bar:18446744073709551615:");
            new DocumentId("groupdoc:foo:15:bar");
        } catch (IllegalArgumentException iae) {
            fail(iae.getMessage());
        }
    }

    public void testInvalidGroupdoc() {
        checkInvalidUri("grouppdoc:blabla:something");
        checkInvalidUri("groupdoc:blablasomething");
    }

    public void testUriNamespace() {
        DocumentId docId = new DocumentId("doc:bar:foo");
        assertEquals("doc:bar:foo", docId.toString());
        assertEquals("doc", docId.getScheme().getType().toString());
        assertEquals("bar", docId.getScheme().getNamespace());
        assertEquals("foo", docId.getScheme().getNamespaceSpecific());

        docId = new DocumentId("userdoc:ns:90:boo");
        assertEquals("userdoc:ns:90:boo", docId.toString());
        assertEquals("userdoc", docId.getScheme().getType().toString());
        assertEquals("ns", docId.getScheme().getNamespace());
        assertEquals("boo", docId.getScheme().getNamespaceSpecific());
        assertEquals(90l, ((UserDocIdString) docId.getScheme()).getUserId());

        docId = new DocumentId("userdoc:ns:18446744073709551615:boo");
        assertEquals("userdoc:ns:18446744073709551615:boo", docId.toString());
        assertEquals("userdoc", docId.getScheme().getType().toString());
        assertEquals("ns", docId.getScheme().getNamespace());
        assertEquals("boo", docId.getScheme().getNamespaceSpecific());
        assertEquals(new BigInteger("18446744073709551615").longValue(), ((UserDocIdString) docId.getScheme()).getUserId());

        docId = new DocumentId("userdoc:ns:9223372036854775808:boo");
        assertEquals("userdoc:ns:9223372036854775808:boo", docId.toString());
        assertEquals("userdoc", docId.getScheme().getType().toString());
        assertEquals("ns", docId.getScheme().getNamespace());
        assertEquals("boo", docId.getScheme().getNamespaceSpecific());
        assertEquals(new BigInteger("9223372036854775808").longValue(), ((UserDocIdString) docId.getScheme()).getUserId());

        BigInteger negativeUserId = new BigInteger("F00DCAFEDEADBABE", 16);
        assertEquals(0xF00DCAFEDEADBABEl, negativeUserId.longValue());
        docId = new DocumentId("userdoc:ns:"+negativeUserId+":bar");
        assertEquals("userdoc:ns:17297704939806374590:bar", docId.toString());
        assertEquals(negativeUserId.longValue(), ((UserDocIdString) docId.getScheme()).getUserId());

        docId = new DocumentId("orderdoc(31,19):ns2:1234:1268182861:foo");
        assertEquals("orderdoc(31,19):ns2:1234:1268182861:foo", docId.toString());
        assertEquals("orderdoc", docId.getScheme().getType().toString());
        assertEquals("ns2", docId.getScheme().getNamespace());
        assertEquals("foo", docId.getScheme().getNamespaceSpecific());
        assertEquals(31, ((OrderDocIdString)docId.getScheme()).getWidthBits());
        assertEquals(19, ((OrderDocIdString)docId.getScheme()).getDivisionBits());
        assertEquals("1234", ((OrderDocIdString)docId.getScheme()).getGroup());
        assertEquals(1234, ((OrderDocIdString)docId.getScheme()).getUserId());
        assertEquals(1268182861, ((OrderDocIdString)docId.getScheme()).getOrdering());
    }

    public void testIdStrings() {
        DocumentId docId;
        docId = new DocumentId(new DocIdString("test", "baaaa"));
        assertEquals("doc:test:baaaa", docId.toString());
        assertFalse(docId.hasDocType());

        docId = new DocumentId(new UserDocIdString("test", 54, "something"));
        assertEquals("userdoc:test:54:something", docId.toString());
        assertFalse(docId.hasDocType());

        docId = new DocumentId(new UserDocIdString("test", 0xFFFFFFFFFFFFFFFFl, "something"));
        assertEquals("userdoc:test:18446744073709551615:something", docId.toString());

        //sign flipped
        docId = new DocumentId(new UserDocIdString("test", -8193, "something"));
        assertEquals("userdoc:test:18446744073709543423:something", docId.toString());

        docId = new DocumentId(new IdIdString("namespace", "type", "g=group", "foobar"));
        assertEquals("id:namespace:type:g=group:foobar", docId.toString());
        assertTrue(docId.hasDocType());
        assertEquals("type", docId.getDocType());
    }

    public void testIdStringFeatures() {
        DocumentId none = new DocumentId("id:ns:type::foo");
        assertFalse(none.getScheme().hasGroup());
        assertFalse(none.getScheme().hasNumber());

        none = new DocumentId("doc:ns:foo");
        assertFalse(none.getScheme().hasGroup());
        assertFalse(none.getScheme().hasNumber());

        DocumentId user = new DocumentId("id:ns:type:n=42:foo");
        assertFalse(user.getScheme().hasGroup());
        assertTrue(user.getScheme().hasNumber());
        assertEquals(42, user.getScheme().getNumber());

        user = new DocumentId("userdoc:ns:42:foo");
        assertFalse(user.getScheme().hasGroup());
        assertTrue(user.getScheme().hasNumber());
        assertEquals(42, user.getScheme().getNumber());

        DocumentId group = new DocumentId("id:ns:type:g=mygroup:foo");
        assertTrue(group.getScheme().hasGroup());
        assertFalse(group.getScheme().hasNumber());
        assertEquals("mygroup", group.getScheme().getGroup());

        group = new DocumentId("groupdoc:ns:mygroup:foo");
        assertTrue(group.getScheme().hasGroup());
        assertFalse(group.getScheme().hasNumber());
        assertEquals("mygroup", group.getScheme().getGroup());

        DocumentId order = new DocumentId("orderdoc(5,2):ns:42:007:foo");
        assertTrue(order.getScheme().hasGroup());
        assertTrue(order.getScheme().hasNumber());
        assertEquals("42", order.getScheme().getGroup());
        assertEquals(42, order.getScheme().getNumber());
    }

    public void testHashCodeOfGids() {
        DocumentId docId0 = new DocumentId("doc:blabla:0");
        byte[] docId0Gid = docId0.getGlobalId();
        DocumentId docId0Copy = new DocumentId("doc:blabla:0");
        byte[] docId0CopyGid = docId0Copy.getGlobalId();


        //GIDs should be the same
        for (int i = 0; i < docId0Gid.length; i++) {
            assertEquals(docId0Gid[i], docId0CopyGid[i]);
        }

        //straight hashCode() of byte arrays won't be the same
        assertFalse(docId0Gid.hashCode() == docId0CopyGid.hashCode());

        //Arrays.hashCode() works better...
        assertEquals(Arrays.hashCode(docId0Gid), Arrays.hashCode(docId0CopyGid));
    }

}
