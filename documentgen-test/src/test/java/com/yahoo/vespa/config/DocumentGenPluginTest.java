// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.docproc.proxy.ProxyDocument;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.Generated;
import com.yahoo.document.MapDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.ReferenceFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.schema.derived.Deriver;
import com.yahoo.tensor.Tensor;
import com.yahoo.vespa.document.NodeImpl;
import com.yahoo.vespa.document.dom.DocumentImpl;
import com.yahoo.vespa.documentgen.test.Book;
import com.yahoo.vespa.documentgen.test.Book.Ss0;
import com.yahoo.vespa.documentgen.test.Book.Ss1;
import com.yahoo.vespa.documentgen.test.Common;
import com.yahoo.vespa.documentgen.test.ConcreteDocumentFactory;
import com.yahoo.vespa.documentgen.test.Music;
import com.yahoo.vespa.documentgen.test.Music3;
import com.yahoo.vespa.documentgen.test.Music4;
import com.yahoo.vespa.documentgen.test.Parent;
import com.yahoo.vespa.documentgen.test.annotation.Artist;
import com.yahoo.vespa.documentgen.test.annotation.Date;
import com.yahoo.vespa.documentgen.test.annotation.Emptyannotation;
import com.yahoo.vespa.documentgen.test.annotation.Person;

import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests vespa-documentgen-plugin
 *
 * @author vegardh
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class DocumentGenPluginTest {

    // NOTE: Most assertEquals in this use the wrong argument order

    private static final int NUM_BOOKS = 10000;

    @Test
    public void testRealBasic() {
        Music music = getMusicBasic();
        assertEquals(7, music.getFieldCount());
        assertEquals("Astroburger", music.getArtist());
        assertEquals(10.654f, music.getWeight_src(), 0);
        assertEquals((Integer)2005, music.getYear());
        assertEquals("http://astro.burger", music.getUri());
        assertFalse(music.getEitheror());
        music.setUri(null);
        assertNull(music.getUri());
        music.setUri("https://astro.burger");
        assertEquals("https://astro.burger", music.getUri());
        music.setYear(2006);
        assertEquals((Integer)2006, music.getYear());
        music.setEitheror(true);
        assertTrue(music.getEitheror());
    }

    @Test
    public void testClear() {
        Book book = getBook();
        assertTrue(Struct.class.isInstance(book.getMystruct()));
        assertTrue(List.class.isInstance(book.getMyarrayint()));
        assertEquals(book.getAuthor(), "Herman Melville");
        book.clear();
        assertNull(book.getMystruct());
        assertNull(book.getAuthor());
        assertNull(book.getMyarrayint());
        assertNull(book.getFieldValue("author"));
        assertNull(book.getFieldValue("mystruct"));
        assertNull(book.getFieldValue("myarrayint"));
    }
    
    @Test
    public void testBasicDoc() {
        Music music = getMusicBasic();
        Field artist = music.getField("artist");
        assertEquals(artist, new Field("artist", DataType.STRING));
        assertEquals(artist.getDataType(), DataType.STRING);
        StringFieldValue astroburger = new StringFieldValue("Astroburger");
        assertEquals(music.getFieldValue(artist).getWrappedValue(), astroburger.getString());
        assertEquals(music.getFieldValue("year").getWrappedValue(), 2005);
        assertEquals(music.getFieldValue("artist"), astroburger);
        assertEquals(music.getFieldValue(artist), astroburger);
        assertEquals(music.getFieldValue("artist"), astroburger);

        music.setYear(2006);
        assertEquals(music.getFieldValue("year").getWrappedValue(), 2006);
        music.setArtist("Don Bingo's Astroburger");
        assertEquals(artist, new Field("artist", DataType.STRING));
        assertEquals(artist.getDataType(), DataType.STRING);
        assertEquals(music.getFieldValue(artist).getWrappedValue(), "Don Bingo's Astroburger");
        StringFieldValue bingoAstroburger = new StringFieldValue("Don Bingo's Astroburger");
        assertEquals(music.getFieldValue("artist"), bingoAstroburger);
        assertEquals(music.getFieldValue(artist), bingoAstroburger);
        assertEquals(music.getFieldValue("artist"), bingoAstroburger);

        assertNull(music.getFieldValue(new Field("nonexisting")));
        assertNull(music.getFieldValue("nono"));
        assertNull(music.getField("nope"));
        assertNull(music.getFieldValue(new Field("nada")));
        assertNull(music.getFieldValue("zilch"));
        assertNull(music.getFieldValue("zero"));

        assertNull(music.removeFieldValue("nothere"));
        assertNull(music.removeFieldValue(new Field("nothereno")));
        assertNull(music.removeFieldValue(new Field("invalid")));
        assertNull(music.removeFieldValue("goner"));
        assertNull(music.removeFieldValue("absent"));
    }

    @Test
    public void testSet() {
        Music music = getMusicBasic();
        Field artist = music.getField("artist");
        music.setFieldValue("artist", new StringFieldValue("Don Bingo's Astroburger 1"));
        assertEquals(music.getArtist(), "Don Bingo's Astroburger 1");
        music.setFieldValue(artist, new StringFieldValue("Don Bingo's Astroburger 2"));
        assertEquals(music.getArtist(), "Don Bingo's Astroburger 2");
        music.setFieldValue(artist, new StringFieldValue("Don Bingo's Astroburger 3"));
        assertEquals(music.getArtist(), "Don Bingo's Astroburger 3");
        music.setFieldValue("artist", new StringFieldValue("Don Bingo's Astroburger 4"));
        assertEquals(music.getArtist(), "Don Bingo's Astroburger 4");
        music.setFieldValue("artist", new StringFieldValue("Don Bingo's Astroburger 5"));
        assertEquals(music.getArtist(), "Don Bingo's Astroburger 5");
    }

    @Test
    public void testSetString() {
        Book book = new Book(new DocumentId("id:book:book::0"));
        book.setFieldValue("author", "Herman Melville");
        assertNotEquals(null, book.authorSpanTrees());
    }

    @Test
    public void testRemoveFieldValue() {
        Book book = getBook();
        book.setAuthor(null);
        Field a = new Field("author", DataType.STRING);
        assertNull(book.getFieldValue("author"));
        assertNull(book.getFieldValue(a));
        assertEquals(book.getField("author"), a);
        assertNull(book.getFieldValue(a));
        assertNull(book.getFieldValue("author"));
        assertNull(book.getFieldValue("author"));

        book.removeFieldValue("isbn");
        book.removeFieldValue(new Field("year", DataType.INT));
        book.removeFieldValue(new Field("description", DataType.STRING));
        Array old = (Array) book.removeFieldValue("myarrayint");
        assertEquals(old.get(0), new IntegerFieldValue(10));
        book.removeFieldValue("stringmap");
        book.removeFieldValue("mywsinteger");
        assertNull(book.getIsbn());
        assertNull(book.getYear());
        assertNull(book.getDescription());
        assertNull(book.getStringmap());
        assertNull(book.getMyarrayint());
        assertNull(book.getMywsinteger());

        Music music = getMusicBasic();
        Field artist = music.getField("artist");
        Field year = music.getField("year");
        music.removeFieldValue(artist);
        assertNull(music.getArtist());
        music.removeFieldValue("disp_song");
        assertNull(music.getDisp_song());
        music.removeFieldValue(year);
        assertNull(music.getYear());
        music.removeFieldValue("uri");
        assertNull(music.getUri());
        music.removeFieldValue("weight_src");
        assertNull(music.getWeight_src());
    }

    @Test
    public void testStructs() {
        Book book = getBook();
        assertBook(book);
    }

    private void assertBook(Book book) {
        assertTrue(Struct.class.isInstance(book.getMystruct()));
        assertEquals(-238472634.78, book.getMystruct().getSs01().getD0(), 0);
        assertEquals((Integer)999, book.getMystruct().getI1());
        assertEquals(book.getAuthor(), "Herman Melville");
        book.getMystruct().getSs01().setD0(4d);
        assertEquals(book.getMystruct().getSs01().getD0(), 4.0, 1E-6);
        book.getMystruct().setS1("new s1");
        assertEquals(book.getMystruct().getS1(), "new s1");
        assertEquals(((StructuredFieldValue)book.getFieldValue("mystruct")).getField("s1").getDataType(), DataType.STRING);
        assertSame(((StructuredFieldValue)book.getFieldValue("mystruct")).getField("s1").getDataType(), ((StructuredFieldValue)book.getFieldValue("mystruct")).getField("s1").getDataType());
        FieldValue old = book.getMystruct().setFieldValue(((StructuredFieldValue)book.getFieldValue("mystruct")).getField("s1"), new StringFieldValue("TJO"));
        assertEquals(old.getWrappedValue(), "new s1");
        assertEquals(book.getMystruct().getS1(), "TJO");
        assertEquals(book.getMystruct().getFieldValue("s1").getWrappedValue(), "TJO");
    }

    @Test
    public void testArrays() {
        Book book = getBook();
        assertTrue(book.getField("myarrayint").getDataType() instanceof ArrayDataType);
        assertEquals(book.getMyarrayint().size(), 3);
        assertEquals(book.getMyarrayint().get(0), (Integer)10);
        assertEquals(book.getMyarrayint().get(1), (Integer)20);
        assertEquals(book.getMyarrayint().get(2), (Integer)30);
        Array<IntegerFieldValue> arrInt = (Array<IntegerFieldValue>) book.getFieldValue("myarrayint");
        for (Iterator<IntegerFieldValue> i = arrInt.fieldValueIterator() ; i.hasNext() ; ) {
            assertEquals(i.next().getWrappedValue(), 10);
            assertEquals(i.next().getWrappedValue(), 20);
            assertEquals(i.next().getWrappedValue(), 30);
            assertFalse(i.hasNext());
        }
        arrInt.set(1, new IntegerFieldValue(22));
        assertEquals(book.getMyarrayint().get(1), (Integer) 22);

        assertEquals(book.getMytriplearray().get(0).get(0).get(0), (Integer)1);
        assertEquals(book.getMytriplearray().get(0).get(0).get(1), (Integer)2);
        assertEquals(book.getMytriplearray().get(0).get(0).get(2), (Integer)3);
        assertEquals(book.getMytriplearray().get(0).get(1).get(0), (Integer)9);
        assertEquals(book.getMytriplearray().get(0).get(1).get(1), (Integer)10);
        assertEquals(book.getMytriplearray().get(0).get(1).get(2), (Integer)11);

        book.getMytriplearray().get(0).get(1).add(12);
        Array myTripleArray = (Array) book.getFieldValue("mytriplearray");
        IntegerFieldValue twelveFv = (IntegerFieldValue) ((Array)((Array) myTripleArray.getFieldValue(0)).getFieldValue(1)).getFieldValue(3);
        assertEquals(twelveFv.getWrappedValue(), 12);
        IntegerFieldValue twelve = (IntegerFieldValue) ((Array)((Array) myTripleArray.get(0)).get(1)).get(3);
        assertEquals(twelve, new IntegerFieldValue(12));
    }

    private void verifyArrayOfStruct(Book book) {
        assertEquals(book.getMysinglestructarray().get(0).getS1(), "YEPS");
        assertEquals(book.getMysinglestructarray().get(1).getI1(), (Integer)456);
        Struct s1 = (Struct) ((Array)book.getFieldValue("mysinglestructarray")).get(0);
        Struct s2 = (Struct) ((Array)book.getFieldValue("mysinglestructarray")).get(1);
        StringFieldValue sfv1 = (StringFieldValue) s1.getFieldValue("s1");
        IntegerFieldValue ifv1 = (IntegerFieldValue) s1.getFieldValue("i1");
        StringFieldValue sfv2 = (StringFieldValue) s2.getFieldValue("s1");
        IntegerFieldValue ifv2 = (IntegerFieldValue) s2.getFieldValue("i1");
        assertEquals(sfv1.getString(), "YEPS");
        assertEquals(sfv2.getString(), "JA");
        assertEquals(ifv1.getInteger(), 789);
        assertEquals(ifv2.getInteger(), 456);
        s2.setFieldValue("i1", new IntegerFieldValue(123));
        assertEquals(book.getMysinglestructarray().get(1).getI1(), (Integer)123);
        Book.Ss1 prev = book.getMysinglestructarray().remove(0);
        assertEquals(book.getMysinglestructarray().get(0).getI1(), (Integer)123);
        book.getMysinglestructarray().add(0, prev);
        assertEquals(book.getMysinglestructarray().get(1).getI1(), (Integer)123);
        s2.setFieldValue("i1", new IntegerFieldValue(456));
    }

    private static Document copyBySerialization(Document orig) {
        return roundtripSerialize(orig, typeManagerForBookType());
    }
    private Book toBook(Document doc) {
        return (Book) new ConcreteDocumentFactory().getDocumentCopy(doc.getDataType().getName(), doc, doc.getId());
    }

    @Test
    public void testArrayOfStruct() {
        Book book = getBook();
        verifyArrayOfStruct(book);
        verifyArrayOfStruct(toBook(copyBySerialization(book)));
    }

    private void verifyMaps(Book book) {
        assertTrue(book.getField("stringmap").getDataType() instanceof MapDataType);
        MapFieldValue mfv = (MapFieldValue) book.getFieldValue("stringmap");
        assertEquals(mfv.get(new StringFieldValue("Melville")), new StringFieldValue("Moby Dick"));
        book.getStringmap().put("Melville", "Moby Dick Or The Whale");
        assertEquals(mfv.get(new StringFieldValue("Melville")), new StringFieldValue("Moby Dick Or The Whale"));
        book.getStringmap().remove("Melville");
        assertEquals(mfv.keySet().size(), 2);
        book.getStringmap().put("Melville", "MD");
        assertEquals(mfv.keySet().size(), 3);
        book.getStringmap().put("Melville", "Moby Dick");
        assertEquals(mfv.keySet().size(), 3);

        assertEquals(book.getStructmap().get(50).getS1(), "test s1");
        MapFieldValue mfv2 = (MapFieldValue) book.getFieldValue("structmap");
        Struct fifty = (Struct)(mfv2.get(new IntegerFieldValue(50)));
        assertEquals(fifty.getFieldValue("s1").getWrappedValue(), "test s1");
        assertEquals(((Ss1)mfv2.get(new IntegerFieldValue(50))).getS1(), "test s1");
    }

    @Test
    public void testMaps() {
        Book book = getBook();
        verifyMaps(book);
        verifyMaps(toBook(copyBySerialization(book)));
    }

    @Test
    public void testWeightedSets() {
        Book book = getBook();
        assertTrue(book.getField("mywsinteger").getDataType() instanceof WeightedSetDataType);
        Field ws = book.getField("mywsinteger");
        assertEquals(book.getMywsinteger().get(2), (Integer)200);
        WeightedSet integerWs = (WeightedSet) book.getFieldValue("mywsinteger");
        assertEquals(integerWs.get(new IntegerFieldValue(2)), (Integer)200);
        integerWs.remove(new IntegerFieldValue(2));
        assertNull(book.getMywsinteger().get(2));
        assertEquals(((WeightedSet)book.getFieldValue(ws)).get(new IntegerFieldValue(1)), (Integer)100);

        Map<Integer, Integer> ws2 = new HashMap<>();
        ws2.put(1, 1000);
        ws2.put(2, 2000);
        ws2.put(3, 3000);
        book.setMywsinteger(ws2);
        assertEquals(book.getMywsinteger().get(2), (Integer)2000);
        integerWs = (WeightedSet) book.getFieldValue("mywsinteger");
        assertEquals(integerWs.get(new IntegerFieldValue(2)), (Integer)2000);
        assertEquals(integerWs.size(), 3);
        ws2.put(4, 4000);
        assertEquals(book.getMywsinteger().get(4), (Integer)4000);
        book.getMywsinteger().remove(4);
        assertNull(book.getMywsinteger().get(4));
        assertNull(ws2.get(4));
        assertEquals(((WeightedSet)book.getFieldValue(ws)).get(new IntegerFieldValue(1)), (Integer)1000);
    }

    @Test
    public void testBaseAnnotations() {
        Book book = getBook();
        SpanTree authorTree = new SpanTree();
        Person p = new Person();
        p.setName("Melville");
        authorTree.annotate(p);
        StringFieldValue sfv =  ((StringFieldValue) book.getFieldValue("author"));
        sfv.setSpanTree(authorTree);
        book.setFieldValue("author", sfv);
        assertEquals(book.authorSpanTrees().values().iterator().next().iterator().next(), p);

        final SpanTree descTree = new SpanTree();
        Person p2 = new Person();
        p2.setName("H. Melville");
        descTree.annotate(p2);
        book.setDescriptionSpanTrees(new HashMap<>(){{ put(descTree.getName(), descTree); }});
        assertEquals(((Person) ((StringFieldValue) book.getFieldValue(book.getField("description"))).getSpanTrees().iterator().next().iterator().next()).getName(),
        "H. Melville");
        assertEquals(((Person) ((StringFieldValue) book.removeFieldValue("description")).getSpanTrees().iterator().next().iterator().next()).getName(), "H. Melville");
        assertNull(book.descriptionSpanTrees());
        assertNull((book.getFieldValue("description")));
        Artist a = new Artist();
        assertTrue(Person.class.isInstance(a));
        assertEquals(((StructDataType) a.getType().getDataType()).getField("name").getDataType(), DataType.STRING);
        assertEquals(((StructDataType) a.getType().getDataType()).getField("instrument").getDataType(), DataType.INT);
        assertEquals(((Struct) a.getFieldValue()).getField("name").getDataType(), DataType.STRING);
    }

    @Test
    public void concrete_reference_id_is_initially_null() {
        final Book book = getBook();
        assertNull(book.getRef());
    }

    @Test
    public void can_set_and_get_concrete_reference_document_id() {
        final Book book = getBook();
        final DocumentId docId = new DocumentId("id:ns:parent::foo");
        book.setRef(docId); // TODO currently no validation of ID upon setRef time
        assertEquals(docId, book.getRef());
    }

    @Test
    public void clearing_document_nulls_out_reference_id() {
        final Book book = getBook();
        book.setRef(new DocumentId("id:ns:parent::foo"));
        book.clear();

        assertNull(book.getRef());
    }

    @Test
    public void reference_field_has_correct_reference_data_type() {
        final Book book = getBook();
        final Field field = book.getField("ref");
        assertEquals(ReferenceDataType.createWithInferredId(Parent.type), field.getDataType());
    }

    @Test
    public void concrete_reference_id_can_be_transparently_converted_to_field_value() {
        final Book book = getBook();
        final DocumentId docId = new DocumentId("id:ns:parent::bar");
        book.setRef(docId);

        final Field field = book.getField("ref");
        final FieldValue value = book.getFieldValue(field);
        assertTrue(value instanceof ReferenceFieldValue);
        final ReferenceFieldValue refValue = (ReferenceFieldValue)value;
        assertEquals(field.getDataType(), refValue.getDataType());
        assertTrue(refValue.getDocumentId().isPresent());
        assertEquals(docId, refValue.getDocumentId().get());
    }

    @Test
    public void reference_field_value_can_be_transparently_converted_to_concrete_reference_id() {
        final Book book = getBook();
        final DocumentId docId = new DocumentId("id:ns:parent::bar");
        final Field field = book.getField("ref");
        book.setFieldValue(field, new ReferenceFieldValue((ReferenceDataType)field.getDataType(), docId));

        assertEquals(docId, book.getRef());
    }

    @Test
    public void testPackDocFromGenericDoc() {
        DocumentType bookGeneric = new DocumentType("book");
        DocumentType somethingElse = new DocumentType("somethingElse");
        bookGeneric.addField("author", DataType.STRING);
        bookGeneric.addField("title", DataType.STRING);
        Document genBook = new Document(bookGeneric, new DocumentId("id:book:book::0"));
        genBook.setFieldValue("author", new StringFieldValue("Melville"));
        genBook.setFieldValue("title", new StringFieldValue("Moby Dick"));
        Document notBook = new Document(somethingElse, new DocumentId("id:notbook:somethingElse::0"));

        assertNull(pack(notBook));
        Book book = pack(genBook);
        assertEquals(book.getTitle(), "Moby Dick");
        assertEquals(book.getAuthor(), "Melville");
    }

    @Test
    public void testConcreteProxyDoc() {
        Book book = getBook();
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("t", "title");
        fieldMap.put("a", "author");
        fieldMap.put("i", "isbn");
        fieldMap.put("y", "year");
        DocumentProcessor dp = new BookProcessor();
        ProxyDocument proxiedBook = new ProxyDocument(dp, book, fieldMap);
        dp.process(Processing.of(new DocumentPut(proxiedBook)));
        assertEquals(proxiedBook.getFieldValue("title").getWrappedValue(), "The T");
        assertEquals(book.getFieldValue("title").getWrappedValue(), "The T");
        assertEquals(book.getTitle(), "The T");
        assertNull(book.getAuthor());
        assertNull(book.getFieldValue("author"));
        assertEquals(book.getYear(), (Integer)2011);
        assertEquals(book.getFieldValue("year").getWrappedValue(), 2011);
        assertEquals(book.getIsbn(), "ISBN YEP");
        assertEquals(book.getFieldValue("isbn"), new StringFieldValue("ISBN YEP"));
    }

    public static class BookProcessor extends DocumentProcessor {

        public Progress process(Processing processing) {
            DocumentPut put = (DocumentPut)processing.getDocumentOperations().get(0);
            Document document = put.getDocument();
            document.setFieldValue("t", new StringFieldValue("The T"));
            document.removeFieldValue("a");
            document.setFieldValue("y", new IntegerFieldValue(2011));
            document.setFieldValue("i", new StringFieldValue("ISBN YEP"));
            return Progress.DONE;
        }
    }

    private static DocumentTypeManager typeManagerFromSDs(String... files) {
        var cfg = getDocumentConfig(Arrays.asList(files));
        return new DocumentTypeManager(cfg);
    }

    private static DocumentTypeManager typeManagerForBookType() {
        return typeManagerFromSDs("etc/complex/common.sd", "etc/complex/parent.sd", "etc/complex/book.sd");
    }

    @Test
    @Ignore // Just to test memory usage
    public void testMemUseGeneric() {
        final DocumentTypeManager mgr = typeManagerForBookType();
        DocumentType bookT=mgr.getDocumentType("book");
        List<Document> manyGenericBooks = new ArrayList<>();
        for (int i = 0; i < NUM_BOOKS; i++) {
            manyGenericBooks.add(newBookGeneric(bookT, i, mgr));
        }
        assertEquals(NUM_BOOKS, manyGenericBooks.size());
    }

    private static DocumentmanagerConfig getDocumentConfig(List<String> sds) {
        return new DocumentmanagerConfig(Deriver.getDocumentManagerConfig(sds));
    }

    @Test
    @Ignore // Just to test memory usage
    public void testMemUseConcrete() {
        final DocumentTypeManager mgr = typeManagerForBookType();
        List<Book> manyConcreteBooks = new ArrayList<>();
        for (int i = 0; i < NUM_BOOKS; i++) {
            manyConcreteBooks.add(newBookConcrete(i));
        }
        assertEquals(NUM_BOOKS, manyConcreteBooks.size());
    }

    private Book newBookConcrete(int i) {
        Book book = new Book(new DocumentId("id:book:book::"+i));
        book.setAuthor("Melville");
        Date date = new Date().setExacttime(99L);
        book.setTitleSpanTrees(new HashMap<>());
        SpanTree t = new SpanTree().annotate(date);
        book.titleSpanTrees().put(t.getName(), t);
        book.setTitle("Moby Dick");
        book.setYear(1851);
        book.setMystruct(new Ss1().setSs01(new Ss0().setS0("My s0").setD0(99d)).setS1("My s1").setL1(89L));//.setAl1(myAs1));
        Map<Integer, Integer> wsInteger = new HashMap<>();
        wsInteger.put(56, 55);
        wsInteger.put(57, 54);
        book.setMywsinteger(wsInteger);

        Array<IntegerFieldValue> intArr1 = new Array<>(DataType.getArray(DataType.INT));
        intArr1.add(new IntegerFieldValue(1));
        intArr1.add(new IntegerFieldValue(2));
        intArr1.add(new IntegerFieldValue(3));
        Array intArr1Arr = new Array(DataType.getArray(intArr1.getDataType()));
        intArr1Arr.add(intArr1);
        Array intArr1ArrArr = new Array(DataType.getArray(intArr1Arr.getDataType()));
        intArr1ArrArr.add(intArr1Arr);
        book.setMytriplearray(intArr1ArrArr);

        return book;
    }

    private Document newBookGeneric(DocumentType bookT, int i, DocumentTypeManager mgr) {
        Document bookGeneric = new Document(bookT, new DocumentId("id:book:book::"+i));
        bookGeneric.setFieldValue("author", new StringFieldValue("Melville"));
        StringFieldValue title = new StringFieldValue("Moby Dick");
        SpanTree titleTree = new SpanTree();
        title.setSpanTree(titleTree);

        AnnotationType dateType = mgr.getAnnotationTypeRegistry().getType("date");
        Struct dateStruct = new Struct(mgr.getAnnotationTypeRegistry().getType("date").getDataType());
        dateStruct.setFieldValue("exacttime", new LongFieldValue(99L));
        Annotation date = new Annotation(dateType);
        date.setFieldValue(dateStruct);
        titleTree.annotate(date);
        bookGeneric.setFieldValue("title", title);

        bookGeneric.setFieldValue("year", new IntegerFieldValue(1851));
        Struct myS0 = new Struct(bookT.getStructType("ss0"));
        myS0.setFieldValue("s0", new StringFieldValue("My s0"));
        myS0.setFieldValue("d0", new DoubleFieldValue(99));
        Struct myS1 = new Struct(bookT.getStructType("ss1"));
        myS1.setFieldValue("s1", new StringFieldValue("My s1"));
        myS1.setFieldValue("l1", new LongFieldValue(89));
        Array<StringFieldValue> myAs1 = new Array<>(DataType.getArray(DataType.STRING));
        myAs1.add(new StringFieldValue("as1_1"));
        myAs1.add(new StringFieldValue("as1_2"));
        myS1.setFieldValue("as1", myAs1);
        myS1.setFieldValue("ss01", myS0);
        bookGeneric.setFieldValue("mystruct", myS1);

        WeightedSet<FloatFieldValue> wsInteger = new WeightedSet<>(DataType.getWeightedSet(DataType.FLOAT));
        wsInteger.put(new FloatFieldValue(56), 55);
        wsInteger.put(new FloatFieldValue(57), 54);
        bookGeneric.setFieldValue("mywsinteger", wsInteger);

        Array<IntegerFieldValue> intArr1 = new Array<>(DataType.getArray(DataType.INT));
        intArr1.add(new IntegerFieldValue(1));
        intArr1.add(new IntegerFieldValue(2));
        intArr1.add(new IntegerFieldValue(3));
        Array<Array<IntegerFieldValue>> intArr1Arr = new Array<>(DataType.getArray(intArr1.getDataType()));
        intArr1Arr.add(intArr1);
        Array<Array<Array<IntegerFieldValue>>> intArr1ArrArr = new Array<>(DataType.getArray(intArr1Arr.getDataType()));
        intArr1ArrArr.add(intArr1Arr);
        bookGeneric.setFieldValue("mytriplearray", intArr1ArrArr);

        return bookGeneric;
    }

    @Test
    public void testPackComplex() {
        final DocumentTypeManager mgr = typeManagerForBookType();
        DocumentType bookT = mgr.getDocumentType("book");
        Document bookGeneric = new Document(bookT, new DocumentId("id:book:book::0"));
        bookGeneric.setFieldValue("author", new StringFieldValue("Melville"));
        StringFieldValue title = new StringFieldValue("Moby Dick");
        SpanTree titleTree = new SpanTree();
        title.setSpanTree(titleTree);

        AnnotationType dateType = mgr.getAnnotationTypeRegistry().getType("date");
        Struct dateStruct = new Struct(mgr.getAnnotationTypeRegistry().getType("date").getDataType());
        dateStruct.setFieldValue("exacttime", new LongFieldValue(99L));
        Annotation date = new Annotation(dateType);
        date.setFieldValue(dateStruct);
        titleTree.annotate(date);
        bookGeneric.setFieldValue("title", title);

        StringFieldValue titleCheck=(StringFieldValue) bookGeneric.getFieldValue("title");
        assertEquals(titleCheck.getWrappedValue(), "Moby Dick");
        SpanTree treeCheck = titleCheck.getSpanTrees().iterator().next();
        Annotation titleAnnCheck = treeCheck.iterator().next();
        assertEquals(((StructuredFieldValue) titleAnnCheck.getFieldValue()).getFieldValue("exacttime").getWrappedValue(), 99L);

        bookGeneric.setFieldValue("year", new IntegerFieldValue(1851));
        Struct myS0 = new Struct(bookT.getStructType("ss0"));
        myS0.setFieldValue("s0", new StringFieldValue("My s0"));
        myS0.setFieldValue("d0", new DoubleFieldValue(99));
        Struct myS1 = new Struct(bookT.getStructType("ss1"));
        myS1.setFieldValue("s1", new StringFieldValue("My s1"));
        myS1.setFieldValue("l1", new LongFieldValue(89));
        Array<StringFieldValue> myAs1 = new Array<>(DataType.getArray(DataType.STRING));
        myAs1.add(new StringFieldValue("as1_1"));
        myAs1.add(new StringFieldValue("as1_2"));
        myS1.setFieldValue("as1", myAs1);
        myS1.setFieldValue("ss01", myS0);
        bookGeneric.setFieldValue("mystruct", myS1);
        assertEquals(((StructuredFieldValue) bookGeneric.getFieldValue("mystruct")).getFieldValue("s1").getWrappedValue(), "My s1");
        WeightedSet<IntegerFieldValue> wsInteger = new WeightedSet<>(DataType.getWeightedSet(DataType.INT));
        wsInteger.put(new IntegerFieldValue(56), 55);
        wsInteger.put(new IntegerFieldValue(57), 54);
        bookGeneric.setFieldValue("mywsinteger", wsInteger);
        Array<IntegerFieldValue> intArr1 = new Array<>(DataType.getArray(DataType.INT));
        intArr1.add(new IntegerFieldValue(1));
        intArr1.add(new IntegerFieldValue(2));
        intArr1.add(new IntegerFieldValue(3));
        Array<Array<IntegerFieldValue>> intArr1Arr = new Array<>(DataType.getArray(intArr1.getDataType()));
        intArr1Arr.add(intArr1);
        Array<Array<Array<IntegerFieldValue>>> intArr1ArrArr = new Array<>(DataType.getArray(intArr1Arr.getDataType()));
        intArr1ArrArr.add(intArr1Arr);
        bookGeneric.setFieldValue("mytriplearray", intArr1ArrArr);

        Book book = new Book(bookGeneric, bookGeneric.getId());

        assertEquals(book.getAuthor(), "Melville");
        assertEquals(book.getMystruct().getS1(), "My s1");
        assertEquals(book.getMystruct().getSs01().getS0(), "My s0");
        assertEquals(book.getMytriplearray().get(0).get(0).get(0), (Integer)1);
        assertEquals(book.getMytriplearray().get(0).get(0).get(1), (Integer)2);
        assertEquals(book.getMytriplearray().get(0).get(0).get(2), (Integer)3);
        assertEquals(book.getMywsinteger().get(57), (Integer)54);
        assertEquals(book.getMystruct().getAs1().get(1), "as1_2");
        treeCheck = book.titleSpanTrees().values().iterator().next();
        titleAnnCheck = treeCheck.iterator().next();
        assertEquals(((StructuredFieldValue) titleAnnCheck.getFieldValue()).getFieldValue("exacttime").getWrappedValue(), 99L);

        Book book2 = new Book(book, book.getId());
        assertEquals(book2.getId(), bookGeneric.getId());

        assertEquals(book2.getAuthor(), "Melville");
        assertEquals(book2.getMystruct().getS1(), "My s1");
        assertEquals(book2.getMystruct().getSs01().getS0(), "My s0");
        assertEquals(book2.getMytriplearray().get(0).get(0).get(0), (Integer)1);
        assertEquals(book2.getMytriplearray().get(0).get(0).get(1), (Integer)2);
        assertEquals(book2.getMytriplearray().get(0).get(0).get(2), (Integer)3);
        assertEquals(book2.getMywsinteger().get(57), (Integer)54);
        assertEquals(book2.getMystruct().getAs1().get(1), "as1_2");
        treeCheck = book2.titleSpanTrees().values().iterator().next();
        titleAnnCheck = treeCheck.iterator().next();
        assertEquals(((StructuredFieldValue) titleAnnCheck.getFieldValue()).getFieldValue("exacttime").getWrappedValue(), 99L);
    }

    @Test
    public void testFactory() {
        Book b = (Book) ConcreteDocumentFactory.getDocument("book", new DocumentId("id:book:book::10"));
        b.setAuthor("Per Ulv");
        final Date d = (Date) ConcreteDocumentFactory.getAnnotation("date");
        d.setExacttime(79L);
        b.setAuthorSpanTrees(new HashMap<>() {{ put("root", new SpanTree("root").annotate(d));  }});
        StringFieldValue authorCheck=(StringFieldValue) b.getFieldValue("author");
        assertEquals(authorCheck.getWrappedValue(), "Per Ulv");
        SpanTree treeCheck = authorCheck.getSpanTrees().iterator().next();
        Annotation authorAnnCheck = treeCheck.iterator().next();
        assertEquals(((Struct) authorAnnCheck.getFieldValue()).getFieldValue("exacttime").getWrappedValue(), 79L);

        b.setMystruct(((Ss1) ConcreteDocumentFactory.getStruct("ss1")).setS1("Test s1!"));
        assertEquals(((Struct) b.getFieldValue("mystruct")).getFieldValue("s1").getWrappedValue(), "Test s1!");

        Ss1 fss1=(Ss1)ConcreteDocumentFactory.getStruct("ss1");
        fss1.setD1(678d);
        b.setMystruct(fss1);
        assertEquals(b.getMystruct().getFieldValue("d1").getWrappedValue(), 678d);
        assertEquals(b.getMystruct().getD1(), (Double)678d);

        assertEquals(ConcreteDocumentFactory.documentTypeObjects.size(), 10);
        assertEquals(ConcreteDocumentFactory.documentTypeObjects.get("music"), Music.type);
        assertEquals(ConcreteDocumentFactory.documentTypeObjects.get("parent"), Parent.type);
        assertEquals(ConcreteDocumentFactory.documentTypeObjects.get("common"), Common.type);
    }

    /**
     * Packs the given doc to Book
     * @param d doc, never null
     * @return a Book object or null if input doc isn't a Book
     */
    private Book pack(Document d) {
        if (!Book.type.getName().equals(d.getDataType().getName())) return null;
        String dataType = d.getDataType().getName();
        Class generated;
        try {
            generated = Class.forName("com.yahoo.vespa.documentgen.test."+className(dataType));
        } catch (ClassNotFoundException e) {
            return null;
        }
        if (generated.getAnnotation(com.yahoo.document.Generated.class)==null) return null;
        Book book = new Book(d.getId());
        for (Iterator<Map.Entry<Field, FieldValue>> i = d.iterator(); i.hasNext() ; ) {
            Map.Entry<Field, FieldValue> e = i.next();
            Field f = e.getKey();
            FieldValue fv = e.getValue();
            book.setFieldValue(f, fv);
        }
        return book;
    }

    private String className(String s) {
        return s.substring(0, 1).toUpperCase()+s.substring(1);
    }

    private Music getMusicBasic() {
        Music music = new Music(new DocumentId("id:music:music::0"));
        music.setArtist("Astroburger");
        music.setDisp_song("disp");
        music.setSong("Calling the sun");
        music.setYear(2005);
        music.setUri("http://astro.burger");
        music.setWeight_src(10.654f);
        music.setEitheror(false);
        return music;
    }

    private Book getBook() {
        Book book = new Book(new DocumentId("id:book:book::0"));
        book.setAuthor("Herman Melville");
        book.setTitle("Moby Dick - Or The Whale");
        book.setIsbn("234-33");
        book.setYear(1815).
          setDescription("A great novel about whaling.");

        Ss1 ss1 = new Book.Ss1();
        Ss0 ss0 = new Book.Ss0();
        ss0.setD0(-238472634.78);
        ss1.setS1("test s1").
          setI1(999).
          setD1(56.777).
          setSs01(ss0);
        book.setMystruct(ss1);

        List<Integer> myArrInt = new ArrayList<>();
        myArrInt.add(10);
        myArrInt.add(20);
        myArrInt.add(30);
        book.setMyarrayint(myArrInt);

        List<Integer> intL = new ArrayList<>(){{add(1);add(2);add(3);}};
        List<Integer> intL2 = new ArrayList<>(){{add(9);add(10);add(11);}};
        List<List<Integer>> doubleIntL = new ArrayList<>();
        doubleIntL.add(intL);
        doubleIntL.add(intL2);
        List<List<List<Integer>>> tripleIntL = new ArrayList<>();
        tripleIntL.add(doubleIntL);
        book.setMytriplearray(tripleIntL);

        Map<String, String> sMap = new HashMap<>();
        sMap.put("Melville", "Moby Dick");
        sMap.put("Bulgakov", "The Master and Margarita");
        sMap.put("Black Debbath", "Tung tung politisk rock");
        book.setStringmap(sMap);

        Map<Integer, Ss1> structMap = new HashMap<>();
        structMap.put(50, ss1);
        structMap.put(60, ss1);
        book.setStructmap(structMap);

        Map<Integer, Integer> ws = new HashMap<>();
        ws.put(1, 100);
        ws.put(2, 200);
        ws.put(3, 300);
        book.setMywsinteger(ws);

        Ss1 arrayedStruct1 = new Ss1().setS1("YEPS").setI1(789);
        Ss1 arrayedStruct2 = new Ss1().setS1("JA").setI1(456);
        List<Ss1> structArray = new ArrayList<>();
        structArray.add(arrayedStruct1);
        structArray.add(arrayedStruct2);
        book.setMysinglestructarray(structArray);
        book.setContent(ByteBuffer.allocate(3).put(new byte[]{3,4,5}));
        book.getContent().position(0);
        return book;
    }

    @Test
    public void testProvided() {
        assertTrue(ConcreteDocumentFactory.getAnnotation("NodeImpl") instanceof NodeImpl);
        assertTrue(ConcreteDocumentFactory.getAnnotation("DocumentImpl") instanceof DocumentImpl);
        assertNotNull(ConcreteDocumentFactory.getAnnotation("artist").getClass().getAnnotation(Generated.class));
        assertNull(ConcreteDocumentFactory.getAnnotation("NodeImpl").getClass().getAnnotation(Generated.class));
        assertNull(ConcreteDocumentFactory.getAnnotation("DocumentImpl").getClass().getAnnotation(Generated.class));
        assertNotNull(ConcreteDocumentFactory.getAnnotation("NodeImplSub").getClass().getAnnotation(Generated.class));
        assertNotNull(ConcreteDocumentFactory.getAnnotation("DocumentImplSub").getClass().getAnnotation(Generated.class));
    }

    @Test
    public void testAbstract() {
        assertTrue(Modifier.isAbstract(Emptyannotation.class.getModifiers()));
    }

    private static Document roundtripSerialize(Document docToSerialize, DocumentTypeManager mgr) {
        final GrowableByteBuffer outputBuffer = new GrowableByteBuffer();
        final DocumentSerializer serializer = DocumentSerializerFactory.createHead(outputBuffer);
        serializer.write(docToSerialize);
        outputBuffer.flip();
        return new Document(DocumentDeserializerFactory.createHead(mgr, outputBuffer));
    }

    @Test
    public void testSerialization() {
        final Book book = getBook();
        assertEquals(book.getMystruct().getD1(), (Double)56.777);
        assertEquals(book.getFieldCount(), 13);
        assertEquals(book.getMystruct().getFieldCount(), 4);
        assertEquals(book.getContent().get(0), 3);
        assertEquals(book.getContent().get(1), 4);
        assertEquals(book.getContent().get(2), 5);
        final Document des = roundtripSerialize(book, typeManagerForBookType());
        assertEquals(des.getFieldCount(), 13);
        assertEquals(des.getDataType().getName(), "book");
        assertEquals(((Raw) des.getFieldValue("content")).getByteBuffer().get(0), 3);
        assertEquals(((Raw) des.getFieldValue("content")).getByteBuffer().get(1), 4);
        assertEquals(((Raw) des.getFieldValue("content")).getByteBuffer().get(2), 5);
        assertEquals(des.getFieldValue("author").toString(), "Herman Melville");
        assertEquals(des.getFieldValue("title").toString(), "Moby Dick - Or The Whale");
        assertEquals(des.getFieldValue("title").toString(), "Moby Dick - Or The Whale");
        assertEquals(des.getFieldValue("author").toString(), "Herman Melville");

        Struct mystruct = (Struct)des.getFieldValue("mystruct");
        FieldValue d1 = mystruct.getFieldValue("d1");
        assertEquals(d1.getWrappedValue(), 56.777d);

        Struct ss01 = (Struct) mystruct.getFieldValue("ss01");
        DoubleFieldValue ss01d0 = (DoubleFieldValue) ss01.getFieldValue("d0");
        assertEquals(ss01d0.getWrappedValue(), -238472634.78d);

        Array<IntegerFieldValue> a = (Array<IntegerFieldValue>) des.getFieldValue("myarrayint");
        assertEquals(a.size(), 3);
        assertEquals(a.get(0).getInteger(), 10);
        assertEquals(a.get(1).getInteger(), 20);
        assertEquals(a.get(2).getInteger(), 30);

        WeightedSet<IntegerFieldValue> ws = (WeightedSet<IntegerFieldValue>) des.getFieldValue("mywsinteger");
        assertEquals(ws.size(), 3);
        assertEquals(ws.get(new IntegerFieldValue(1)), (Integer)100);
        assertEquals(ws.get(new IntegerFieldValue(2)), (Integer)200);
        assertEquals(ws.get(new IntegerFieldValue(3)), (Integer)300);

        Array<Struct> sstrctArr = (Array<Struct>) des.getFieldValue("mysinglestructarray");
        assertEquals(sstrctArr.size(), 2);
        assertEquals(sstrctArr.get(0).getFieldValue("s1").getWrappedValue().toString(), "YEPS");
        assertEquals(sstrctArr.get(1).getFieldValue("s1").getWrappedValue().toString(), "JA");
    }

    @Test
    public void concrete_reference_fields_can_be_roundtrip_serialized() {
        final Book book = getBook();
        final DocumentId id = new DocumentId("id:ns:parent::baz");
        book.setRef(id);

        final Document doc = roundtripSerialize(book, typeManagerForBookType());
        final ReferenceFieldValue refValue = (ReferenceFieldValue) doc.getFieldValue(doc.getField("ref"));
        assertTrue(refValue.getDocumentId().isPresent());
        assertEquals(id, refValue.getDocumentId().get());

        final Book bookCopy = (Book)new ConcreteDocumentFactory().getDocumentCopy(
                "book", doc, new DocumentId("id:ns:book::helloworld"));
        assertEquals(id, bookCopy.getRef());
    }

    @Test
    public void testInheritanceOfGeneratedTypes() {
        assertEquals(Music3.class.getSuperclass(), Document.class);
        assertEquals(Music4.class.getSuperclass(), Document.class);
        assertEquals(Music.class.getSuperclass(), Common.class);
        assertEquals(Book.class.getSuperclass(), Common.class);
    }

    @Test
    public void testEquals() {
        Book b1 = getBook();
        Book b2 = getBook();
        Book b3 = null;
        assertTrue(b1.equals(b1));
        assertFalse(b1.equals(b3));
        assertTrue(b1.equals(b2));
        assertTrue(b2.equals(b1));
        b2.setAuthor("foo");
        assertFalse(b1.equals(b2));
        b1.setAuthor("foo");
        assertTrue(b1.equals(b2));
        b1.getMyarrayint().set(0, 65);
        assertFalse(b1.equals(b2));
        assertFalse(b2.equals(b1));
        b2.getMyarrayint().set(0, 65);
        assertTrue(b1.equals(b2));
        assertTrue(b2.equals(b1));
        Ss1 arrayedStruct1 = new Ss1().setS1("YEPPETI").setI1(789);
        Ss1 arrayedStruct2 = new Ss1().setS1("JADDA").setI1(456);
        List<Ss1> structArray = new ArrayList<>();
        structArray.add(arrayedStruct1);
        structArray.add(arrayedStruct2);
        b1.setMysinglestructarray(structArray);
        assertFalse(b1.equals(b2));
        assertFalse(b2.equals(b1));
        arrayedStruct1 = new Ss1().setS1("YEPPETI").setI1(789);
        arrayedStruct2 = new Ss1().setS1("JADDA").setI1(456);
        structArray = new ArrayList<>();
        structArray.add(arrayedStruct1);
        structArray.add(arrayedStruct2);
        b2.setMysinglestructarray(structArray);
        assertTrue(b1.equals(b2));
        assertTrue(b2.equals(b1));
    }
    
    @Test
    public void testHashCode() {
        Book book1 = new Book(new DocumentId("id:book:book::0"));
        Book book2 = new Book(new DocumentId("id:book:book::0"));
        assertNull(book1.getAuthor());
        assertEquals(book1.hashCode(), book2.hashCode());
        book2.setAuthor("Bill");
        assertNotSame(book1.hashCode(), book2.hashCode());
        
    }
    
    @Test
    public void testFunnyDocName() {
        com.yahoo.vespa.documentgen.test.Class c = new com.yahoo.vespa.documentgen.test.Class(new DocumentId("id:class:class::0"));
        c.setClassf("foo");
    }

    @Test
    @Ignore
    public void testAllMethodsOverridden() {
        List <Method> unmasked = com.yahoo.protect.ClassValidator.unmaskedMethodsFromSuperclass(Common.class);
        System.out.println(unmasked);
        assertEquals(unmasked.size(), 0); // probably not needed
    }

    @Test
    public void testTensorType() {
        Book book = new Book(new DocumentId("id:book:book::0"));
        assertNull(book.getVector());
        book.setVector(Tensor.from("{{x:0}:1.0, {x:1}:2.0, {x:2}:3.0}"));
        assertEquals("tensor(x{}):{0:1.0, 1:2.0, 2:3.0}", book.getVector().toString());
    }

    @Test
    public void testPositionType() {
        Music4 book = new Music4(new DocumentId("id:music4:music4::0"));
        book.setPos(new Music4.Position().setX(7).setY(8));
        assertEquals(new Music4.Position().setX(7).setY(8), book.getPos());
        assertEquals(1, book.getFieldCount());
        int numIteratedValues = 0;
        for (Iterator<Map.Entry<Field, FieldValue>> it = book.iterator(); it.hasNext(); numIteratedValues++) {
            Map.Entry<Field, FieldValue> entry = it.next();
        }
        assertEquals(book.getFieldCount(), numIteratedValues);
        Field posZcurve = book.getField(PositionDataType.getZCurveFieldName("pos"));
        assertNotNull(posZcurve);
        assertNotEquals(book.getDataType().fieldSet(), book.getDataType().fieldSetAll());
        assertFalse(book.getDataType().fieldSet().contains(posZcurve));
        assertTrue(book.getDataType().fieldSetAll().contains(posZcurve));
        assertTrue(book.getDataType().getFields().contains(posZcurve));
    }

    @Test
    public void imported_fields_are_enumerated_in_document_type() {
        var docType = getBook().getDataType();
        assertEquals(2, docType.getImportedFieldNames().size());
        assertTrue(docType.hasImportedField("my_dummy"));
        assertTrue(docType.hasImportedField("my_foo"));
        assertFalse(docType.hasImportedField("some_field_that_does_not_exist"));
    }
    
}
