// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vdstestlib/cppunit/macros.h>

#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <fcntl.h>

using vespalib::nbostream;
using vespalib::compression::CompressionConfig;

using namespace document::config_builder;

namespace document {

using namespace fieldvalue;

struct DocumentTest : public CppUnit::TestFixture {
    void testTraversing();
    void testFieldPath();
    void testModifyDocument();
    void testVariables();
    void testSimpleUsage();
    void testReadSerializedFile();
    void testReadSerializedFileCompressed();
    void testReadSerializedAllVersions();
    void testGenerateSerializedFile();
    void testGetURIFromSerialized();
    void testBogusserialize();
    void testCRC32();
    void testHasChanged();
    void testSplitSerialization();
    void testSliceSerialize();
    void testCompression();
    void testCompressionConfigured();
    void testUnknownEntries();
    void testAnnotationDeserialization();
    void testGetSerializedSize();
    void testDeserializeMultiple();
    void testSizeOf();

    CPPUNIT_TEST_SUITE(DocumentTest);
    CPPUNIT_TEST(testFieldPath);
    CPPUNIT_TEST(testTraversing);
    CPPUNIT_TEST(testModifyDocument);
    CPPUNIT_TEST(testVariables);
    CPPUNIT_TEST(testSimpleUsage);
    CPPUNIT_TEST(testReadSerializedFile);
    CPPUNIT_TEST(testReadSerializedFileCompressed);
    CPPUNIT_TEST(testReadSerializedAllVersions);
    CPPUNIT_TEST(testGenerateSerializedFile);
    CPPUNIT_TEST(testGetURIFromSerialized);
    CPPUNIT_TEST(testBogusserialize);
    CPPUNIT_TEST(testCRC32);
    CPPUNIT_TEST(testHasChanged);
    CPPUNIT_TEST(testSplitSerialization);
    CPPUNIT_TEST(testSliceSerialize);
    CPPUNIT_TEST(testCompression);
    CPPUNIT_TEST(testCompressionConfigured);
    CPPUNIT_TEST(testUnknownEntries);
    CPPUNIT_TEST(testAnnotationDeserialization);
    CPPUNIT_TEST(testGetSerializedSize);
    CPPUNIT_TEST(testDeserializeMultiple);
    CPPUNIT_TEST(testSizeOf);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentTest);

void DocumentTest::testSizeOf()
{
    CPPUNIT_ASSERT_EQUAL(136ul, sizeof(Document));
    CPPUNIT_ASSERT_EQUAL(72ul, sizeof(StructFieldValue));
    CPPUNIT_ASSERT_EQUAL(24ul, sizeof(StructuredFieldValue));
    CPPUNIT_ASSERT_EQUAL(64ul, sizeof(SerializableArray));
}

void DocumentTest::testFieldPath()
{
    const vespalib::string testValues[] = { "{}", "", "",
                                       "{}r", "", "r",
                                       "{{}}", "{", "}",
                                       "{{}}r", "{", "}r",
                                       "{\"{}\"}", "{}", "",
                                       "{\"{}\"}r", "{}", "r",
                                       "{\"{\\a}\"}r", "{a}", "r",
                                       "{\"{\\\"}\"}r", "{\"}", "r",
                                       "{\"{\\\\}\"}r", "{\\}", "r",
                                       "{$x}", "$x", "",
                                       "{$x}[$y]", "$x", "[$y]",
                                       "{$x}.ss", "$x", ".ss",
                                       "{\"\"}", "", ""
                                     };
    for (size_t i(0); i < sizeof(testValues)/sizeof(testValues[0]); i+=3) {
        vespalib::stringref tmp = testValues[i];
        vespalib::string key = FieldPathEntry::parseKey(tmp);
        CPPUNIT_ASSERT_EQUAL(testValues[i+1], key);
        CPPUNIT_ASSERT_EQUAL(testValues[i+2], vespalib::string(tmp));
    }
}

class Handler : public fieldvalue::IteratorHandler {
public:
    Handler();
    ~Handler();
    const std::string & getResult() const { return _result; }
private:
    void onPrimitive(uint32_t, const Content&) override {
        std::ostringstream os; os << "P-" << getArrayIndex();
        _result += os.str();
    }
    void onCollectionStart(const Content&)     override { _result += '['; }
    void onCollectionEnd(const Content&)       override { _result += ']'; }
    void onStructStart(const Content&)         override {
        std::ostringstream os; os << "<" << getArrayIndex() << ":";
        _result += os.str();
    }
    void onStructEnd(const Content&)           override { _result += '>'; }
    std::string _result;
};

Handler::Handler() = default;
Handler::~Handler() = default;


void DocumentTest::testTraversing()
{
    Field primitive1("primitive1", 1, *DataType::INT, true);
    Field primitive2("primitive2", 2, *DataType::INT, true);
    StructDataType struct1("struct1");
    struct1.addField(primitive1);
    struct1.addField(primitive2);

    ArrayDataType iarr(*DataType::INT);
    ArrayDataType sarr(struct1);
    Field iarrF("iarray", 21, iarr, true);
    Field sarrF("sarray", 22, sarr, true);

    StructDataType struct2("struct2");
    struct2.addField(primitive1);
    struct2.addField(primitive2);
    struct2.addField(iarrF);
    struct2.addField(sarrF);
    Field s2("ss", 12, struct2, true);

    StructDataType struct3("struct3");
    struct3.addField(primitive1);
    struct3.addField(s2);

    Field structl1s1("l1s1", 11, struct3, true);

    DocumentType type("test");
    type.addField(primitive1);
    type.addField(structl1s1);

    Document doc(type, DocumentId("doc::testdoc"));
    doc.setValue(primitive1, IntFieldValue(1));

    StructFieldValue l1s1(struct3);
    l1s1.setValue(primitive1, IntFieldValue(2));

    StructFieldValue l2s1(struct2);
    l2s1.setValue(primitive1, IntFieldValue(3));
    l2s1.setValue(primitive2, IntFieldValue(4));
    ArrayFieldValue iarr1(iarr);
    iarr1.add(IntFieldValue(11));
    iarr1.add(IntFieldValue(12));
    iarr1.add(IntFieldValue(13));
    ArrayFieldValue sarr1(sarr);
    StructFieldValue l3s1(struct1);
    l3s1.setValue(primitive1, IntFieldValue(1));
    l3s1.setValue(primitive2, IntFieldValue(2));
    sarr1.add(l3s1);
    sarr1.add(l3s1);
    l2s1.setValue(iarrF, iarr1);
    l2s1.setValue(sarrF, sarr1);

    l1s1.setValue(s2, l2s1);
    doc.setValue(structl1s1, l1s1);

    Handler fullTraverser;
    FieldPath empty;
    doc.iterateNested(empty.getFullRange(), fullTraverser);
    CPPUNIT_ASSERT_EQUAL(fullTraverser.getResult(),
                         std::string("<0:P-0<0:P-0<0:P-0P-0[P-0P-1P-2][<0:P-0P-0><1:P-1P-1>]>>>"));
}

class VariableIteratorHandler : public IteratorHandler {
public:
    VariableIteratorHandler();
    ~VariableIteratorHandler();
    std::string retVal;

    void onPrimitive(uint32_t, const Content & fv) override {
        for (VariableMap::iterator iter = getVariables().begin(); iter != getVariables().end(); iter++) {
            retVal += vespalib::make_string("%s: %s,", iter->first.c_str(), iter->second.toString().c_str());
        }
        retVal += " - " + fv.getValue().toString() + "\n";
    };
};

VariableIteratorHandler::VariableIteratorHandler() {}
VariableIteratorHandler::~VariableIteratorHandler() {}

void
DocumentTest::testVariables()
{
    ArrayDataType iarr(*DataType::INT);
    ArrayDataType iiarr(static_cast<DataType &>(iarr));
    ArrayDataType iiiarr(static_cast<DataType &>(iiarr));

    Field iiiarrF("iiiarray", 1, iiiarr, true);
    DocumentType type("test");
    type.addField(iiiarrF);

    ArrayFieldValue iiiaV(iiiarr);
    for (int i = 1; i < 4; i++) {
        ArrayFieldValue iiaV(iiarr);
        for (int j = 1; j < 4; j++) {
            ArrayFieldValue iaV(iarr);
            for (int k = 1; k < 4; k++) {
                iaV.add(IntFieldValue(i * j * k));
            }
            iiaV.add(iaV);
        }
        iiiaV.add(iiaV);
    }

    Document doc(type, DocumentId("doc::testdoc"));
    doc.setValue(iiiarrF, iiiaV);

    {
        VariableIteratorHandler handler;
        FieldPath path;
        type.buildFieldPath(path, "iiiarray[$x][$y][$z]");
        doc.iterateNested(path.getFullRange(), handler);

        std::string fasit =
            "x: 0,y: 0,z: 0, - 1\n"
            "x: 0,y: 0,z: 1, - 2\n"
            "x: 0,y: 0,z: 2, - 3\n"
            "x: 0,y: 1,z: 0, - 2\n"
            "x: 0,y: 1,z: 1, - 4\n"
            "x: 0,y: 1,z: 2, - 6\n"
            "x: 0,y: 2,z: 0, - 3\n"
            "x: 0,y: 2,z: 1, - 6\n"
            "x: 0,y: 2,z: 2, - 9\n"
            "x: 1,y: 0,z: 0, - 2\n"
            "x: 1,y: 0,z: 1, - 4\n"
            "x: 1,y: 0,z: 2, - 6\n"
            "x: 1,y: 1,z: 0, - 4\n"
            "x: 1,y: 1,z: 1, - 8\n"
            "x: 1,y: 1,z: 2, - 12\n"
            "x: 1,y: 2,z: 0, - 6\n"
            "x: 1,y: 2,z: 1, - 12\n"
            "x: 1,y: 2,z: 2, - 18\n"
            "x: 2,y: 0,z: 0, - 3\n"
            "x: 2,y: 0,z: 1, - 6\n"
            "x: 2,y: 0,z: 2, - 9\n"
            "x: 2,y: 1,z: 0, - 6\n"
            "x: 2,y: 1,z: 1, - 12\n"
            "x: 2,y: 1,z: 2, - 18\n"
            "x: 2,y: 2,z: 0, - 9\n"
            "x: 2,y: 2,z: 1, - 18\n"
            "x: 2,y: 2,z: 2, - 27\n";

        CPPUNIT_ASSERT_EQUAL(fasit, handler.retVal);
    }

}

class ModifyIteratorHandler : public IteratorHandler {
public:
    ModificationStatus doModify(FieldValue& fv) override {
        StringFieldValue* sfv = dynamic_cast<StringFieldValue*>(&fv);
        if (sfv != NULL) {
            *sfv = std::string("newvalue");
            return ModificationStatus::MODIFIED;
        }

        return ModificationStatus::NOT_MODIFIED;
    };

    bool onComplex(const Content&) override {
        return false;
    }
};

void
DocumentTest::testModifyDocument()
{
    // Create test document type and content
    Field primitive1("primitive1", 1, *DataType::INT, true);
    Field primitive2("primitive2", 2, *DataType::INT, true);
    StructDataType struct1("struct1");
    struct1.addField(primitive1);
    struct1.addField(primitive2);

    ArrayDataType iarr(*DataType::INT);
    ArrayDataType sarr(struct1);
    Field iarrF("iarray", 21, iarr, true);
    Field sarrF("sarray", 22, sarr, true);

    MapDataType smap(*DataType::STRING, *DataType::STRING);
    Field smapF("smap", 23, smap, true);

    StructDataType struct2("struct2");
    struct2.addField(primitive1);
    struct2.addField(primitive2);
    struct2.addField(iarrF);
    struct2.addField(sarrF);
    struct2.addField(smapF);
    Field s2("ss", 12, struct2, true);

    MapDataType structmap(*DataType::STRING, struct2);
    Field structmapF("structmap", 24, structmap, true);

    WeightedSetDataType wset(*DataType::STRING, false, false);
    Field wsetF("wset", 25, wset, true);

    WeightedSetDataType structwset(struct2, false, false);
    Field structwsetF("structwset", 26, structwset, true);

    StructDataType struct3("struct3");
    struct3.addField(primitive1);
    struct3.addField(s2);
    struct3.addField(structmapF);
    struct3.addField(wsetF);
    struct3.addField(structwsetF);

    Field structl1s1("l1s1", 11, struct3, true);

    DocumentType type("test");
    type.addField(primitive1);
    type.addField(structl1s1);

    Document::UP doc(new Document(type, DocumentId("doc::testdoc")));
    doc->setValue(primitive1, IntFieldValue(1));

    StructFieldValue l1s1(struct3);
    l1s1.setValue(primitive1, IntFieldValue(2));

    StructFieldValue l2s1(struct2);
    l2s1.setValue(primitive1, IntFieldValue(3));
    l2s1.setValue(primitive2, IntFieldValue(4));
    StructFieldValue l2s2(struct2);
    l2s2.setValue(primitive1, IntFieldValue(5));
    l2s2.setValue(primitive2, IntFieldValue(6));
    ArrayFieldValue iarr1(iarr);
    iarr1.add(IntFieldValue(11));
    iarr1.add(IntFieldValue(12));
    iarr1.add(IntFieldValue(13));
    ArrayFieldValue sarr1(sarr);
    StructFieldValue l3s1(struct1);
    l3s1.setValue(primitive1, IntFieldValue(1));
    l3s1.setValue(primitive2, IntFieldValue(2));
    sarr1.add(l3s1);
    sarr1.add(l3s1);
    MapFieldValue smap1(smap);
    smap1.put(StringFieldValue("leonardo"), StringFieldValue("dicaprio"));
    smap1.put(StringFieldValue("ellen"), StringFieldValue("page"));
    smap1.put(StringFieldValue("joseph"), StringFieldValue("gordon-levitt"));
    l2s1.setValue(smapF, smap1);
    l2s1.setValue(iarrF, iarr1);
    l2s1.setValue(sarrF, sarr1);

    l1s1.setValue(s2, l2s1);
    MapFieldValue structmap1(structmap);
    structmap1.put(StringFieldValue("test"), l2s1);
    l1s1.setValue(structmapF, structmap1);

    WeightedSetFieldValue wset1(wset);
    wset1.add("foo");
    wset1.add("bar");
    wset1.add("zoo");
    l1s1.setValue(wsetF, wset1);

    WeightedSetFieldValue wset2(structwset);
    wset2.add(l2s1);
    wset2.add(l2s2);
    l1s1.setValue(structwsetF, wset2);

    doc->setValue(structl1s1, l1s1);

    // Begin test proper

    doc->print(std::cerr, true, "");

    ModifyIteratorHandler handler;

    FieldPath path;
    doc->getDataType()->buildFieldPath(path, "l1s1.structmap.value.smap{leonardo}");
    doc->iterateNested(path.getFullRange(), handler);

    doc->print(std::cerr, true, "");
}

void DocumentTest::testSimpleUsage()
{
    DocumentType::SP type(new DocumentType("test"));
    Field intF("int", 1, *DataType::INT, true);
    Field longF("long", 2, *DataType::LONG, true);
    Field strF("content", 4, *DataType::STRING, false);

    type->addField(intF);
    type->addField(longF);
    type->addField(strF);

    DocumentTypeRepo repo(*type);
    Document value(*repo.getDocumentType("test"), DocumentId("doc::testdoc"));

        // Initially empty
    CPPUNIT_ASSERT_EQUAL(size_t(0), value.getSetFieldCount());
    CPPUNIT_ASSERT(!value.hasValue(intF));

    value.setValue(intF, IntFieldValue(1));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(1), value.getSetFieldCount());
    CPPUNIT_ASSERT(value.hasValue(intF));

        // Adding some more
    value.setValue(longF, LongFieldValue(2));

        // Not empty
    CPPUNIT_ASSERT_EQUAL(size_t(2), value.getSetFieldCount());
    CPPUNIT_ASSERT_EQUAL(1, value.getValue(intF)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(2, value.getValue(longF)->getAsInt());

        // Serialize & equality
    std::unique_ptr<ByteBuffer> buffer(value.serialize());
    buffer->flip();
    Document value2(*repo.getDocumentType("test"),
                    DocumentId("userdoc::3:foo"));
    CPPUNIT_ASSERT(value != value2);
    value2.deserialize(repo, *buffer);
    CPPUNIT_ASSERT(value2.hasValue(intF));
    CPPUNIT_ASSERT_EQUAL(value, value2);
    CPPUNIT_ASSERT_EQUAL(DocumentId("doc::testdoc"), value2.getId());

        // Various ways of removing
    {
            // By value
        buffer->setPos(0);
        value2.deserialize(repo, *buffer);
        value2.remove(intF);
        CPPUNIT_ASSERT(!value2.hasValue(intF));
        CPPUNIT_ASSERT_EQUAL(size_t(1), value2.getSetFieldCount());

            // Clearing all
        buffer->setPos(0);
        value2.deserialize(repo, *buffer);
        value2.clear();
        CPPUNIT_ASSERT(!value2.hasValue(intF));
        CPPUNIT_ASSERT_EQUAL(size_t(0), value2.getSetFieldCount());
    }

        // Updating
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(value, value2);
    value2.setValue(strF, StringFieldValue("foo"));
    CPPUNIT_ASSERT(value2.hasValue(strF));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo"),
                         value2.getValue(strF)->getAsString());
    CPPUNIT_ASSERT(value != value2);
    value2.assign(value);
    CPPUNIT_ASSERT_EQUAL(value, value2);
    Document::UP valuePtr(value2.clone());
    CPPUNIT_ASSERT_EQUAL(value, *valuePtr);

        // Iterating
    const Document& constVal(value);
    for(Document::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        constVal.getValue(it.field());
    }

        // Comparison
    value2 = value;
    CPPUNIT_ASSERT_EQUAL(0, value.compare(value2));
    value2.remove(intF);
    CPPUNIT_ASSERT(value.compare(value2) < 0);
    CPPUNIT_ASSERT(value2.compare(value) > 0);
    value2 = value;
    value2.setValue(intF, IntFieldValue(5));
    CPPUNIT_ASSERT(value.compare(value2) < 0);
    CPPUNIT_ASSERT(value2.compare(value) > 0);

        // Output
    CPPUNIT_ASSERT_EQUAL(
            std::string("Document(doc::testdoc, DocumentType(test))"),
            value.toString(false));
    CPPUNIT_ASSERT_EQUAL(
            std::string(
"  Document(doc::testdoc\n"
"    DocumentType(test, id -877171244)\n"
"        : DocumentType(document) {\n"
"      StructDataType(test.header, id 306916075) {\n"
"        Field(content, id 4, PrimitiveDataType(String, id 2), body)\n"
"        Field(int, id 1, NumericDataType(Int, id 0), header)\n"
"        Field(long, id 2, NumericDataType(Long, id 4), header)\n"
"      }\n"
"    }\n"
"    int: 1\n"
"    long: 2\n"
"  )"),
            "  " + value.toString(true, "  "));
    CPPUNIT_ASSERT_EQUAL(
            std::string(
                "<document documenttype=\"test\" documentid=\"doc::testdoc\">\n"
                "  <int>1</int>\n"
                "  <long>2</long>\n"
                "</document>"),
            value.toXml("  "));

        // Failure situations.

        // Fetch a field not existing in type
        // (Would be nice if this failed, but whole idea to fetch by field
        // objects is to improve performance.)
    Field anotherIntF("int", 17, *DataType::INT, true);
    CPPUNIT_ASSERT(!value.hasValue(anotherIntF));
    CPPUNIT_ASSERT(value.getValue(anotherIntF).get() == 0);

        // Refuse to accept non-document types
    try{
        StructDataType otherType("foo", 4);
        Document value6(otherType, DocumentId("doc::"));
        CPPUNIT_FAIL("Didn't complain about non-document type");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot generate a document with "
                               "non-document type", e.what());
    }

        // Refuse to set wrong types
    try{
        value2.setValue(intF, StringFieldValue("bar"));
        CPPUNIT_FAIL("Failed to check type equality in setValue");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot assign value of type", e.what());
    }
}

void verifyJavaDocument(Document& doc)
{
    IntFieldValue intVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("intfield"), intVal));
    CPPUNIT_ASSERT_EQUAL(5, intVal.getAsInt());

    FloatFieldValue floatVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("floatfield"), floatVal));
    CPPUNIT_ASSERT(floatVal.getAsFloat() == (float) -9.23);

    StringFieldValue stringVal("");
    CPPUNIT_ASSERT(doc.getValue(doc.getField("stringfield"), stringVal));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("This is a string."),
                         stringVal.getAsString());

    LongFieldValue longVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("longfield"), longVal));
    CPPUNIT_ASSERT_EQUAL((int64_t)398420092938472983LL, longVal.getAsLong());

    DoubleFieldValue doubleVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("doublefield"), doubleVal));
    CPPUNIT_ASSERT_EQUAL(doubleVal.getAsDouble(), 98374532.398820);

    ByteFieldValue byteVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("bytefield"), byteVal));
    CPPUNIT_ASSERT_EQUAL(-2, byteVal.getAsInt());

    RawFieldValue rawVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("rawfield"), rawVal));
    CPPUNIT_ASSERT(memcmp(rawVal.getAsRaw().first, "RAW DATA", 8) == 0);

    Document embedDocVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("docfield"), embedDocVal));

    ArrayFieldValue array(doc.getField("arrayoffloatfield").getDataType());
    CPPUNIT_ASSERT(doc.getValue(doc.getField("arrayoffloatfield"), array));
    CPPUNIT_ASSERT_EQUAL((float)1.0, array[0].getAsFloat());
    CPPUNIT_ASSERT_EQUAL((float)2.0, array[1].getAsFloat());

    WeightedSetFieldValue wset(doc.getField("wsfield").getDataType());
    CPPUNIT_ASSERT(doc.getValue(doc.getField("wsfield"), wset));
    CPPUNIT_ASSERT_EQUAL(50, wset.get(StringFieldValue("Weighted 0")));
    CPPUNIT_ASSERT_EQUAL(199, wset.get(StringFieldValue("Weighted 1")));

    MapFieldValue map(doc.getField("mapfield").getDataType());
    CPPUNIT_ASSERT(doc.getValue(doc.getField("mapfield"), map));
    CPPUNIT_ASSERT(map.get(StringFieldValue("foo1")).get());
    CPPUNIT_ASSERT(map.get(StringFieldValue("foo2")).get());
    CPPUNIT_ASSERT_EQUAL(StringFieldValue("bar1"), dynamic_cast<StringFieldValue&>(*map.get(StringFieldValue("foo1"))));
    CPPUNIT_ASSERT_EQUAL(StringFieldValue("bar2"), dynamic_cast<StringFieldValue&>(*map.get(StringFieldValue("foo2"))));
}

void DocumentTest::testReadSerializedFile()
{
    // Reads a file serialized from java
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    int fd = open(TEST_PATH("data/serializejava.dat").c_str(), O_RDONLY);

    size_t len = lseek(fd,0,SEEK_END);
    ByteBuffer buf(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.getBuffer(), len) != (ssize_t)len) {
	throw vespalib::Exception("read failed");
    }
    close(fd);

    Document doc(repo, buf);
    verifyJavaDocument(doc);

    std::unique_ptr<ByteBuffer> buf2 = doc.serialize();
    buf2->flip();

    Document doc2(repo, *buf2);
    verifyJavaDocument(doc2);

    CPPUNIT_ASSERT_EQUAL(len, buf2->getPos());
    CPPUNIT_ASSERT(memcmp(buf2->getBuffer(), buf.getBuffer(), buf2->getPos()) == 0);

    doc2.setValue("stringfield", StringFieldValue("hei"));

    std::unique_ptr<ByteBuffer> buf3 = doc2.serialize();
    CPPUNIT_ASSERT(len != buf3->getPos());
}

void DocumentTest::testReadSerializedFileCompressed()
{
    // Reads a file serialized from java
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    int fd = open(TEST_PATH("data/serializejava-compressed.dat").c_str(), O_RDONLY);

    int len = lseek(fd,0,SEEK_END);
    ByteBuffer buf(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.getBuffer(), len) != len) {
	throw vespalib::Exception("read failed");
    }
    close(fd);

    Document doc(repo, buf);
    verifyJavaDocument(doc);
}

namespace {
    struct TestDoc {
        std::string _dataFile;
        /**
         * We may add or remove types as we create new versions. If we do so,
         * we can use the created version to know what types we no longer
         * should check, or what fields these old documents does not contain.
         */
        uint32_t _createdVersion;

        TestDoc(const std::string& dataFile, uint32_t version)
            : _dataFile(dataFile),
              _createdVersion(version)
        {
        }
    };
}

/**
 * Tests serialization of all versions.
 *
 * This test tests serialization and deserialization of documents of all
 * supported types.
 *
 * Serialization is only supported in newest format. Deserialization should work
 * for all formats supported, but only the part that makes sense in the new
 * format. Thus, if new format deprecates a datatype, that datatype, when
 * serializing old versions, must either just be dropped or converted.
 *
 * Thus, we create document type programmatically, because all old versions need
 * to make sense with current config.
 *
 * When we create a document programmatically. This is serialized into current
 * version files. When altering the format, after the alteration, copy the
 * current version files to a specific version file and add those to list of
 * files this test checks.
 *
 * When adding new fields to the documents, use the version tagged with each
 * file to ignore these field for old types.
 */
void DocumentTest::testReadSerializedAllVersions()
{
    const int array_id = 1650586661;
    const int wset_id = 1328286588;
        // Create the datatype used for serialization test
    DocumenttypesConfigBuilderHelper builder;
    builder.document(1306012852, "serializetest",
                     Struct("serializetest.header")
                     .addField("floatfield", DataType::T_FLOAT)
                     .addField("stringfield", DataType::T_STRING)
                     .addField("longfield", DataType::T_LONG)
                     .addField("urifield", DataType::T_URI),
                     Struct("serializetest.body")
                     .addField("intfield", DataType::T_INT)
                     .addField("rawfield", DataType::T_RAW)
                     .addField("doublefield", DataType::T_DOUBLE)
                     .addField("bytefield", DataType::T_BYTE)
                     .addField("arrayoffloatfield",
                               Array(DataType::T_FLOAT).setId(array_id))
                     .addField("docfield", DataType::T_DOCUMENT)
                     .addField("wsfield",
                               Wset(DataType::T_STRING).setId(wset_id)));
    builder.document(1447635645, "docindoc", Struct("docindoc.header"),
                     Struct("docindoc.body")
                     .addField("stringindocfield", DataType::T_STRING));
    DocumentTypeRepo repo(builder.config());

    const DocumentType* docType(repo.getDocumentType("serializetest"));
    const DocumentType* docInDocType(repo.getDocumentType("docindoc"));
    const DataType* arrayOfFloatDataType(repo.getDataType(*docType, array_id));
    const DataType* weightedSetDataType(repo.getDataType(*docType, wset_id));

        // Create a memory instance of document
    {
        Document doc(*docType,
                     DocumentId("doc:serializetest:http://test.doc.id/"));
        doc.set("intfield", 5);
        doc.set("floatfield", -9.23);
        doc.set("stringfield", "This is a string.");
        doc.set("longfield", static_cast<int64_t>(398420092938472983LL));
        doc.set("doublefield", 98374532.398820);
        doc.set("bytefield", -2);
        doc.setValue("rawfield", RawFieldValue("RAW DATA", 8));
        Document docInDoc(*docInDocType,
                          DocumentId("doc:serializetest:http://doc.in.doc/"));
        docInDoc.set("stringindocfield", "Elvis is dead");
        //docInDoc.setCompression(CompressionConfig(CompressionConfig::NONE, 0, 0));
        doc.setValue("docfield", docInDoc);
        ArrayFieldValue floatArray(*arrayOfFloatDataType);
        floatArray.add(1.0);
        floatArray.add(2.0);
        doc.setValue("arrayoffloatfield", floatArray);
        WeightedSetFieldValue weightedSet(*weightedSetDataType);
        weightedSet.add(StringFieldValue("Weighted 0"), 50);
        weightedSet.add(StringFieldValue("Weighted 1"), 199);
        doc.setValue("wsfield", weightedSet);

        // Write document to disk, (when you bump version and alter stuff,
        // you can copy this current to new test for new version)
        {
            //doc.setCompression(CompressionConfig(CompressionConfig::NONE, 0, 0));
            std::unique_ptr<ByteBuffer> buf = doc.serialize();
            CPPUNIT_ASSERT_EQUAL(buf->getLength(), buf->getPos());
            int fd = open(TEST_PATH("data/document-cpp-currentversion-uncompressed.dat").c_str(),
                          O_WRONLY | O_CREAT | O_TRUNC, 0644);
            CPPUNIT_ASSERT(fd > 0);
            size_t len = write(fd, buf->getBuffer(), buf->getPos());
            CPPUNIT_ASSERT_EQUAL(buf->getPos(), len);
            close(fd);
        }
        {
            CompressionConfig oldCfg(doc.getType().getFieldsType().getCompressionConfig());
            CompressionConfig newCfg(CompressionConfig::LZ4, 9, 95);
            const_cast<StructDataType &>(doc.getType().getFieldsType()).setCompressionConfig(newCfg);
            std::unique_ptr<ByteBuffer> buf = doc.serialize();
            CPPUNIT_ASSERT(buf->getPos() <= buf->getLength());
            int fd = open(TEST_PATH("data/document-cpp-currentversion-lz4-9.dat").c_str(),
                          O_WRONLY | O_CREAT | O_TRUNC, 0644);
            CPPUNIT_ASSERT(fd > 0);
            size_t len = write(fd, buf->getBuffer(), buf->getPos());
            CPPUNIT_ASSERT_EQUAL(buf->getPos(), len);
            close(fd);
            const_cast<StructDataType &>(doc.getType().getFieldsType()).setCompressionConfig(oldCfg);
        }
    }

    std::string jpath = TEST_PATH("../test/serializeddocuments/");

    std::vector<TestDoc> tests;
    tests.push_back(TestDoc(TEST_PATH("data/document-cpp-v8-uncompressed.dat"), 8));
    tests.push_back(TestDoc(TEST_PATH("data/document-cpp-v7-uncompressed.dat"), 7));
    tests.push_back(TestDoc(jpath + "document-java-v8-uncompressed.dat", 8));
    for (uint32_t i=0; i<tests.size(); ++i) {
        int version = tests[i]._createdVersion;
        std::string name = tests[i]._dataFile;
        std::cerr << name << std::endl;
        if (!vespalib::fileExists(name)) {
            CPPUNIT_FAIL("File " + name + " does not exist.");
        }
        int fd = open(tests[i]._dataFile.c_str(), O_RDONLY);
        int len = lseek(fd,0,SEEK_END);
        ByteBuffer buf(len);
        lseek(fd,0,SEEK_SET);
	if (read(fd, buf.getBuffer(), len) != len) {
	    throw vespalib::Exception("read failed");
	}
        close(fd);

        Document doc(repo, buf);

        IntFieldValue intVal;
        CPPUNIT_ASSERT(doc.getValue(doc.getField("intfield"), intVal));
        CPPUNIT_ASSERT_EQUAL(5, intVal.getAsInt());

        FloatFieldValue floatVal;
        CPPUNIT_ASSERT(doc.getValue(doc.getField("floatfield"), floatVal));
        CPPUNIT_ASSERT(floatVal.getAsFloat() == (float) -9.23);

        StringFieldValue stringVal("");
        CPPUNIT_ASSERT(doc.getValue(doc.getField("stringfield"), stringVal));
        CPPUNIT_ASSERT_EQUAL(vespalib::string("This is a string."),
                             stringVal.getAsString());

        LongFieldValue longVal;
        CPPUNIT_ASSERT(doc.getValue(doc.getField("longfield"), longVal));
        CPPUNIT_ASSERT_EQUAL(static_cast<int64_t>(398420092938472983LL),
                             longVal.getAsLong());

        DoubleFieldValue doubleVal;
        CPPUNIT_ASSERT(doc.getValue(doc.getField("doublefield"), doubleVal));
        CPPUNIT_ASSERT_EQUAL(doubleVal.getAsDouble(), 98374532.398820);

        ByteFieldValue byteVal;
        CPPUNIT_ASSERT(doc.getValue(doc.getField("bytefield"), byteVal));
        CPPUNIT_ASSERT_EQUAL(-2, byteVal.getAsInt());

        RawFieldValue rawVal;
        CPPUNIT_ASSERT(doc.getValue(doc.getField("rawfield"), rawVal));
        CPPUNIT_ASSERT(memcmp(rawVal.getAsRaw().first, "RAW DATA", 8) == 0);

        if (version > 6) {
            Document docInDoc;
            CPPUNIT_ASSERT(doc.getValue(doc.getField("docfield"), docInDoc));

            CPPUNIT_ASSERT(docInDoc.getValue(
                        docInDoc.getField("stringindocfield"), stringVal));
            CPPUNIT_ASSERT_EQUAL(vespalib::string("Elvis is dead"),
                                 stringVal.getAsString());
        }

        ArrayFieldValue array(doc.getField("arrayoffloatfield").getDataType());
        CPPUNIT_ASSERT(doc.getValue(doc.getField("arrayoffloatfield"), array));
        CPPUNIT_ASSERT_EQUAL((float)1.0, array[0].getAsFloat());
        CPPUNIT_ASSERT_EQUAL((float)2.0, array[1].getAsFloat());

        WeightedSetFieldValue wset(doc.getField("wsfield").getDataType());
        CPPUNIT_ASSERT(doc.getValue(doc.getField("wsfield"), wset));
        CPPUNIT_ASSERT_EQUAL(50, wset.get(StringFieldValue("Weighted 0")));
        CPPUNIT_ASSERT_EQUAL(199, wset.get(StringFieldValue("Weighted 1")));

        // Check that serialization doesn't cause any problems.
        std::unique_ptr<ByteBuffer> buf2 = doc.serialize();
        buf2->flip();

        Document doc2(repo, *buf2);
    }
}

size_t getSerializedSize(const Document &doc) {
    return doc.serialize()->getLength();
}

size_t getSerializedSizeHeader(const Document &doc) {
    nbostream stream;
    doc.serializeHeader(stream);
    return stream.size();
}

size_t getSerializedSizeBody(const Document &doc) {
    nbostream stream;
    doc.serializeBody(stream);
    return stream.size();
}

void DocumentTest::testGenerateSerializedFile()
{
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));
    Document doc(*repo.getDocumentType("serializetest"),
                 DocumentId(DocIdString("serializetest",
                                        "http://test.doc.id/")));

    doc.set("intfield", 5);
    doc.set("floatfield", -9.23);
    doc.set("stringfield", "This is a string.");
    doc.set("longfield", (int64_t) 398420092938472983ll);
    doc.set("doublefield", 98374532.398820);
    doc.set("urifield", "http://this.is.a.test/");
    doc.set("bytefield", -2);
    doc.set("rawfield", "RAW DATA");

    const DocumentType *docindoc_type = repo.getDocumentType("docindoc");
    CPPUNIT_ASSERT(docindoc_type);
    Document embedDoc(*docindoc_type,
                      DocumentId(DocIdString("docindoc", "http://embedded")));

    doc.setValue("docfield", embedDoc);

    WeightedSetFieldValue wset(doc.getField("wsfield").getDataType());
    wset.add(StringFieldValue("Weighted 0"), 50);
    wset.add(StringFieldValue("Weighted 1"), 199);
    doc.setValue("wsfield", wset);

    ArrayFieldValue array(doc.getField("arrayoffloatfield").getDataType());
    array.add(FloatFieldValue(1.0));
    array.add(FloatFieldValue(2.0));
    doc.setValue("arrayoffloatfield", array);

    MapFieldValue map(doc.getField("mapfield").getDataType());
    map.put(StringFieldValue("foo1"), StringFieldValue("bar1"));
    map.put(StringFieldValue("foo2"), StringFieldValue("bar2"));
    doc.setValue("mapfield", map);

    std::unique_ptr<ByteBuffer> buf = doc.serialize();

    const std::string serializedDir = TEST_PATH("../test/document/");
    int fd = open((serializedDir + "/serializecpp.dat").c_str(),
                  O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, buf->getBuffer(), buf->getPos()) != (ssize_t)buf->getPos()) {
	throw vespalib::Exception("write failed");
    }
    close(fd);

    ByteBuffer hBuf(getSerializedSizeHeader(doc));
    doc.serializeHeader(hBuf);
    fd = open((serializedDir + "/serializecppsplit_header.dat").c_str(),
              O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, hBuf.getBuffer(), hBuf.getPos()) != (ssize_t)hBuf.getPos()) {
	throw vespalib::Exception("write failed");
    }
    close(fd);

    ByteBuffer bBuf(getSerializedSizeBody(doc));
    doc.serializeBody(bBuf);
    fd = open(TEST_PATH("/serializecppsplit_body.dat").c_str(),
              O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, bBuf.getBuffer(), bBuf.getPos()) != (ssize_t)bBuf.getPos()) {
	throw vespalib::Exception("write failed");
    }
    close(fd);

    CompressionConfig newCfg(CompressionConfig::LZ4, 9, 95);
    const_cast<StructDataType &>(doc.getType().getFieldsType()).setCompressionConfig(newCfg);

    ByteBuffer lz4buf(getSerializedSize(doc));

    doc.serialize(lz4buf);
    lz4buf.flip();

    fd = open(TEST_PATH("/serializecpp-lz4-level9.dat").c_str(),
              O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, lz4buf.getBufferAtPos(), lz4buf.getRemaining()) != (ssize_t)lz4buf.getRemaining()) {
	throw vespalib::Exception("write failed");
    }
    close(fd);
}
void DocumentTest::testGetURIFromSerialized()
{
    TestDocRepo test_repo;
    Document doc(*test_repo.getDocumentType("testdoctype1"),
                 DocumentId("doc:ns:testdoc"));

    {
        std::unique_ptr<ByteBuffer> serialized = doc.serialize();
        serialized->flip();

        CPPUNIT_ASSERT_EQUAL(
                vespalib::string(DocIdString("ns", "testdoc").toString()),
                Document::getIdFromSerialized(*serialized).toString());

        CPPUNIT_ASSERT_EQUAL(vespalib::string("testdoctype1"),
                             Document::getDocTypeFromSerialized(
                                     test_repo.getTypeRepo(),
                                     *serialized)->getName());
    }

    {
        std::unique_ptr<ByteBuffer> serialized = doc.serialize();
        serialized->flip();

        Document doc2(test_repo.getTypeRepo(), *serialized, false, NULL);
        CPPUNIT_ASSERT_EQUAL(
                vespalib::string(DocIdString("ns", "testdoc").toString()), doc2.getId().toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("testdoctype1"), doc2.getType().getName());
    }
};

void DocumentTest::testBogusserialize()
{
    TestDocRepo test_repo;
    try {
        std::unique_ptr<ByteBuffer> buf(
                new ByteBuffer("aoifjweprjwoejr203r+2+4r823++!",100));
        Document doc(test_repo.getTypeRepo(), *buf);
        CPPUNIT_ASSERT(false);
    } catch (DeserializeException& e) {
        CPPUNIT_ASSERT_CONTAIN("Unrecognized serialization version", e.what());
    }

    try {
        std::unique_ptr<ByteBuffer> buf(new ByteBuffer("",0));
        Document doc(test_repo.getTypeRepo(), *buf);
        CPPUNIT_ASSERT(false);
    } catch (DeserializeException& e) {
        CPPUNIT_ASSERT_CONTAIN("Buffer out of bounds", e.what());
    }
}

void DocumentTest::testCRC32()
{
    TestDocRepo test_repo;
    Document doc(*test_repo.getDocumentType("testdoctype1"),
                 DocumentId(DocIdString("crawler", "http://www.ntnu.no/")));

    doc.setValue(doc.getField("hstringval"),
                 StringFieldValue("bla bla bla bla bla"));

    uint32_t crc = doc.calculateChecksum();
    CPPUNIT_ASSERT_EQUAL(277496115u, crc);

    std::unique_ptr<ByteBuffer> buf = doc.serialize();
    buf->flip();

    int pos = 20;

    // Corrupt serialization.
    buf->getBuffer()[pos] ^= 72;
        // Create document. Byte corrupted above is in data area and
        // shouldn't fail deserialization.
    try {
        Document doc2(test_repo.getTypeRepo(), *buf);
        buf->setPos(0);
        CPPUNIT_ASSERT(crc != doc2.calculateChecksum());
    } catch (document::DeserializeException& e) {
        CPPUNIT_ASSERT(false);
    }
        // Return original value and retry
    buf->getBuffer()[pos] ^= 72;

    /// \todo TODO (was warning):  Cannot test for in memory representation altered, as there is no syntax for getting internal refs to data from document. Add test when this is added.
}

void
DocumentTest::testHasChanged()
{
    TestDocRepo test_repo;
    Document doc(*test_repo.getDocumentType("testdoctype1"),
                 DocumentId(DocIdString("crawler", "http://www.ntnu.no/")));
        // Before deserialization we are changed.
    CPPUNIT_ASSERT(doc.hasChanged());

    doc.setValue(doc.getField("hstringval"),
                 StringFieldValue("bla bla bla bla bla"));
        // Still changed after setting a value of course.
    CPPUNIT_ASSERT(doc.hasChanged());

    std::unique_ptr<ByteBuffer> buf = doc.serialize();
    buf->flip();

        // Setting a value in doc tags us changed.
    {
        buf->setPos(0);
        Document doc2(test_repo.getTypeRepo(), *buf);
        CPPUNIT_ASSERT(!doc2.hasChanged());

        doc2.set("headerval", 13);
        CPPUNIT_ASSERT(doc2.hasChanged());
    }
        // Overwriting a value in doc tags us changed.
    {
        buf->setPos(0);
        Document doc2(test_repo.getTypeRepo(), *buf);

        doc2.set("hstringval", "bla bla bla bla bla");
        CPPUNIT_ASSERT(doc2.hasChanged());
    }
        // Clearing value tags us changed.
    {
        buf->setPos(0);
        Document doc2(test_repo.getTypeRepo(), *buf);

        doc2.clear();
        CPPUNIT_ASSERT(doc2.hasChanged());
    }
        // Add more tests here when we allow non-const refs to internals
}

void
DocumentTest::testSplitSerialization()
{
    TestDocMan testDocMan;
    Document::UP doc = testDocMan.createDocument();
    doc->set("headerval", 50);

    ByteBuffer buf(getSerializedSizeHeader(*doc));
    doc->serializeHeader(buf);
    buf.flip();

    ByteBuffer buf2(getSerializedSizeBody(*doc));
    doc->serializeBody(buf2);
    buf2.flip();

    CPPUNIT_ASSERT_EQUAL(size_t(65), buf.getLength());
    CPPUNIT_ASSERT_EQUAL(size_t(73), buf2.getLength());

    Document headerDoc(testDocMan.getTypeRepo(), buf);
    CPPUNIT_ASSERT(headerDoc.hasValue("headerval"));
    CPPUNIT_ASSERT(!headerDoc.hasValue("content"));

    buf.setPos(0);
    Document fullDoc(testDocMan.getTypeRepo(), buf, buf2);
    CPPUNIT_ASSERT(fullDoc.hasValue("headerval"));
    CPPUNIT_ASSERT(fullDoc.hasValue("content"));

    CPPUNIT_ASSERT_EQUAL(*doc, fullDoc);
}

void DocumentTest::testSliceSerialize()
{
        // Test that document doesn't need its own bytebuffer, such that we
        // can serialize multiple documents after each other in the same
        // bytebuffer.
    TestDocMan testDocMan;
    Document::UP doc = testDocMan.createDocument();
    Document::UP doc2 = testDocMan.createDocument(
            "Some other content", "doc:test:anotherdoc");

    ArrayFieldValue val(doc2->getField("rawarray").getDataType());
    val.add(RawFieldValue("hei", 3));
    val.add(RawFieldValue("hallo", 5));
    val.add(RawFieldValue("hei der", 7));
    doc2->setValue(doc2->getField("rawarray"), val);

    ByteBuffer buf(getSerializedSize(*doc) + getSerializedSize(*doc2));
    doc->serialize(buf);
    CPPUNIT_ASSERT_EQUAL(getSerializedSize(*doc), buf.getPos());
    doc2->serialize(buf);
    CPPUNIT_ASSERT_EQUAL(getSerializedSize(*doc) + getSerializedSize(*doc2),
                         buf.getPos());
    buf.flip();

    Document doc3(testDocMan.getTypeRepo(), buf);
    CPPUNIT_ASSERT_EQUAL(getSerializedSize(*doc), buf.getPos());
    Document doc4(testDocMan.getTypeRepo(), buf);
    CPPUNIT_ASSERT_EQUAL(getSerializedSize(*doc) + getSerializedSize(*doc2),
                         buf.getPos());

    CPPUNIT_ASSERT_EQUAL(*doc, doc3);
    CPPUNIT_ASSERT_EQUAL(*doc2, doc4);
}

void DocumentTest::testCompression()
{
    TestDocMan testDocMan;
    Document::UP doc = testDocMan.createDocument();

    std::string bigString("compress me");
    for (int i = 0; i < 8; ++i) { bigString += bigString; }

    doc->setValue("hstringval", StringFieldValue(bigString));

    std::unique_ptr<ByteBuffer> buf_uncompressed = doc->serialize();
    buf_uncompressed->flip();

    CompressionConfig oldCfg(doc->getType().getFieldsType().getCompressionConfig());

    CompressionConfig newCfg(CompressionConfig::LZ4, 9, 95);
    const_cast<StructDataType &>(doc->getType().getFieldsType()).setCompressionConfig(newCfg);
    std::unique_ptr<ByteBuffer> buf_lz4 = doc->serialize();
    buf_lz4->flip();

    const_cast<StructDataType &>(doc->getType().getFieldsType()).setCompressionConfig(oldCfg);

    CPPUNIT_ASSERT(buf_lz4->getRemaining() < buf_uncompressed->getRemaining());

    Document doc_lz4(testDocMan.getTypeRepo(), *buf_lz4);

    CPPUNIT_ASSERT_EQUAL(*doc, doc_lz4);
}

void DocumentTest::testCompressionConfigured()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(43, "serializetest",
                     Struct("serializetest.header").setId(44),
                     Struct("serializetest.body").setId(45)
                     .addField("stringfield", DataType::T_STRING));
    DocumentTypeRepo repo(builder.config());
    Document doc_uncompressed(
            *repo.getDocumentType("serializetest"),
            DocumentId("doc:test:test"));

    std::string bigString("compress me");
    for (int i = 0; i < 8; ++i) { bigString += bigString; }

    doc_uncompressed.setValue("stringfield", StringFieldValue(bigString));
    std::unique_ptr<ByteBuffer> buf_uncompressed = doc_uncompressed.serialize();
    buf_uncompressed->flip();

    size_t uncompressedSize = buf_uncompressed->getRemaining();

    DocumenttypesConfigBuilderHelper builder2;
    builder2.document(43, "serializetest",
                      Struct("serializetest.header").setId(44),
                      Struct("serializetest.body").setId(45)
                      .addField("stringfield", DataType::T_STRING)
                      .setCompression(DocumenttypesConfig::Documenttype::
                                      Datatype::Sstruct::Compression::LZ4,
                                      9, 99, 0));
    DocumentTypeRepo repo2(builder2.config());

    Document doc(repo2, *buf_uncompressed);

    std::unique_ptr<ByteBuffer> buf_compressed = doc.serialize();
    buf_compressed->flip();
    size_t compressedSize = buf_compressed->getRemaining();

    CPPUNIT_ASSERT(compressedSize < uncompressedSize);

    Document doc2(repo2, *buf_compressed);

    std::unique_ptr<ByteBuffer> buf_compressed2 = doc2.serialize();
    buf_compressed2->flip();

    CPPUNIT_ASSERT_EQUAL(compressedSize, buf_compressed2->getRemaining());

    Document doc3(repo2, *buf_compressed2);

    CPPUNIT_ASSERT_EQUAL(doc2, doc_uncompressed);
    CPPUNIT_ASSERT_EQUAL(doc2, doc3);
}

void
DocumentTest::testUnknownEntries()
{
    // We should be able to deserialize a document with unknown values in it.
    DocumentType type1("test", 0);
    DocumentType type2("test", 0);
    Field field1("int1", *DataType::INT, true);
    Field field2("int2", *DataType::INT, true);
    Field field3("int3", *DataType::INT, false);
    Field field4("int4", *DataType::INT, false);

    type1.addField(field1);
    type1.addField(field2);
    type1.addField(field3);
    type1.addField(field4);

    type2.addField(field3);
    type2.addField(field4);

    DocumentTypeRepo repo(type2);

    Document doc1(type1, DocumentId("doc::testdoc"));
    doc1.setValue(field1, IntFieldValue(1));
    doc1.setValue(field2, IntFieldValue(2));
    doc1.setValue(field3, IntFieldValue(3));
    doc1.setValue(field4, IntFieldValue(4));

    uint32_t headerLen = getSerializedSizeHeader(doc1);
    document::ByteBuffer header(headerLen);
    doc1.serializeHeader(header);
    header.flip();

    uint32_t bodyLen = getSerializedSizeBody(doc1);
    document::ByteBuffer body(bodyLen);
    doc1.serializeBody(body);
    body.flip();

    uint32_t totalLen = getSerializedSize(doc1);
    document::ByteBuffer total(totalLen);
    doc1.serialize(total);
    total.flip();

    Document doc2;
    doc2.deserialize(repo, total);

    Document doc3;
    doc3.deserializeHeader(repo, header);
    doc3.deserializeBody(repo, body);

    CPPUNIT_ASSERT_EQUAL(std::string(
        "<document documenttype=\"test\" documentid=\"doc::testdoc\">\n"
        "<int3>3</int3>\n"
        "<int4>4</int4>\n"
        "</document>"), doc2.toXml());
    CPPUNIT_ASSERT_EQUAL(std::string(
        "<document documenttype=\"test\" documentid=\"doc::testdoc\">\n"
        "<int3>3</int3>\n"
        "<int4>4</int4>\n"
        "</document>"), doc3.toXml());

    CPPUNIT_ASSERT_EQUAL(3, doc2.getValue(field3)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(4, doc2.getValue(field4)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(3, doc3.getValue(field3)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(4, doc3.getValue(field4)->getAsInt());

    // The fields are actually accessible as long as you ask with field of
    // correct type.

    CPPUNIT_ASSERT(doc2.hasValue(field1));
    CPPUNIT_ASSERT(doc2.hasValue(field2));
    CPPUNIT_ASSERT(doc3.hasValue(field1));
    CPPUNIT_ASSERT(doc3.hasValue(field2));

    CPPUNIT_ASSERT_EQUAL(1, doc2.getValue(field1)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(2, doc2.getValue(field2)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(1, doc3.getValue(field1)->getAsInt());
    CPPUNIT_ASSERT_EQUAL(2, doc3.getValue(field2)->getAsInt());

    CPPUNIT_ASSERT_EQUAL(size_t(2), doc2.getSetFieldCount());
    CPPUNIT_ASSERT_EQUAL(size_t(2), doc3.getSetFieldCount());
}

void DocumentTest::testAnnotationDeserialization()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(-1326249427, "dokk", Struct("dokk.header"),
                     Struct("dokk.body")
                     .addField("age", DataType::T_BYTE)
                     .addField("story", DataType::T_STRING)
                     .addField("date", DataType::T_INT)
                     .addField("friend", DataType::T_LONG))
        .annotationType(609952424, "person", Struct("person")
                        .addField("firstname", DataType::T_STRING)
                        .addField("lastname", DataType::T_STRING)
                        .addField("birthyear", DataType::T_INT)
                        .setId(443162583))
        .annotationType(-1695443536, "dummy", 0)
        .annotationType(-427420193, "number", DataType::T_INT)
        .annotationType(1616020615, "relative", Struct("relative")
                        .addField("title", DataType::T_STRING)
                        .addField("related", AnnotationRef(609952424))
                        .setId(-236946034))
        .annotationType(-269517759, "banana", 0)
        .annotationType(-513687143, "grape", 0)
        .annotationType(1730712959, "apple", 0);
    DocumentTypeRepo repo(builder.config());

    int fd = open(TEST_PATH("data/serializejavawithannotations.dat").c_str(), O_RDONLY);
    int len = lseek(fd,0,SEEK_END);
    ByteBuffer buf(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.getBuffer(), len) != len) {
	throw vespalib::Exception("read failed");
    }
    close(fd);

    Document doc(repo, buf);
    StringFieldValue strVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("story"), strVal));

    vespalib::nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(strVal);
    
    FixedTypeRepo fixedRepo(repo, doc.getType());
    VespaDocumentDeserializer deserializer(fixedRepo, stream, 8);
    StringFieldValue strVal2;
    deserializer.read(strVal2);
    CPPUNIT_ASSERT_EQUAL(strVal.toString(), strVal2.toString());
    CPPUNIT_ASSERT_EQUAL(strVal.toString(true), strVal2.toString(true));

    CPPUNIT_ASSERT_EQUAL(vespalib::string("help me help me i'm stuck inside a computer!"),
                         strVal.getAsString());
    StringFieldValue::SpanTrees trees = strVal.getSpanTrees();
    const SpanTree *span_tree = StringFieldValue::findTree(trees, "fruits");
    CPPUNIT_ASSERT(span_tree);
    CPPUNIT_ASSERT_EQUAL(size_t(8), span_tree->numAnnotations());
    span_tree = StringFieldValue::findTree(trees, "ballooo");
    CPPUNIT_ASSERT(span_tree);
    CPPUNIT_ASSERT_EQUAL(size_t(8), span_tree->numAnnotations());


    ByteFieldValue byteVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("age"), byteVal));
    CPPUNIT_ASSERT_EQUAL( 123, byteVal.getAsInt());

    IntFieldValue intVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("date"), intVal));
    CPPUNIT_ASSERT_EQUAL(13829297, intVal.getAsInt());

    LongFieldValue longVal;
    CPPUNIT_ASSERT(doc.getValue(doc.getField("friend"), longVal));
    CPPUNIT_ASSERT_EQUAL((int64_t)2384LL, longVal.getAsLong());
}

void
DocumentTest::testGetSerializedSize()
{
    TestDocMan testDocMan;
    Document::UP doc = testDocMan.createDocument();

    CPPUNIT_ASSERT_EQUAL(getSerializedSize(*doc), doc->getSerializedSize());
}

void
DocumentTest::testDeserializeMultiple()
{
    TestDocRepo testDocRepo;
    const DocumentTypeRepo& repo(testDocRepo.getTypeRepo());
    const DocumentType* docTypePtr(repo.getDocumentType("testdoctype1"));
    CPPUNIT_ASSERT(docTypePtr != 0);
    const DocumentType & docType = *docTypePtr;

    StructFieldValue sv1(docType.getField("mystruct").getDataType());

    StructFieldValue sv2(docType.getField("mystruct").getDataType());

    sv1.setValue(sv1.getField("key"), IntFieldValue(1234));
    sv2.setValue(sv2.getField("value"), StringFieldValue("badger"));

    StructFieldValue sv3(docType.getField("mystruct").getDataType());

    vespalib::nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(sv1);
    serializer.write(sv2);

    VespaDocumentDeserializer deserializer(repo, stream, 8);
    deserializer.readStructNoReset(sv3);
    deserializer.readStructNoReset(sv3);

    StructFieldValue correct(docType.getField("mystruct").getDataType());

    correct.setValue(correct.getField("key"), IntFieldValue(1234));
    correct.setValue(correct.getField("value"), StringFieldValue("badger"));
    CPPUNIT_ASSERT_EQUAL(correct, sv3);
}

} // document
