// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/fieldvalue_helpers.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/numericdatatype.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <fcntl.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using vespalib::nbostream;
using namespace ::testing;

using namespace document::config_builder;

namespace document {

using namespace fieldvalue;

TEST(DocumentTest, testSizeOf)
{
    EXPECT_EQ(24u, sizeof(std::vector<char>));
    EXPECT_EQ(24u, sizeof(vespalib::alloc::Alloc));
    EXPECT_EQ(24u, sizeof(ByteBuffer));
    EXPECT_EQ(32u, sizeof(vespalib::GrowableByteBuffer));
    EXPECT_EQ(88ul, sizeof(IdString));
    EXPECT_EQ(104ul, sizeof(DocumentId));
    EXPECT_EQ(256ul, sizeof(Document));
    EXPECT_EQ(80ul, sizeof(NumericDataType));
    EXPECT_EQ(24ul, sizeof(LongFieldValue));
    EXPECT_EQ(104ul, sizeof(StructFieldValue));
    EXPECT_EQ(24ul, sizeof(StructuredFieldValue));
    EXPECT_EQ(56ul, sizeof(SerializableArray));
}

TEST(DocumentTest, testFieldPath)
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
        EXPECT_EQ(testValues[i+1], key);
        EXPECT_EQ(testValues[i+2], vespalib::string(tmp));
    }
}

class Handler : public fieldvalue::IteratorHandler {
public:
    Handler();
    ~Handler() override;
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


TEST(DocumentTest, testTraversing)
{
    Field primitive1("primitive1", 1, *DataType::INT);
    Field primitive2("primitive2", 2, *DataType::INT);
    StructDataType struct1("struct1");
    struct1.addField(primitive1);
    struct1.addField(primitive2);

    ArrayDataType iarr(*DataType::INT);
    ArrayDataType sarr(struct1);
    Field iarrF("iarray", 21, iarr);
    Field sarrF("sarray", 22, sarr);

    StructDataType struct2("struct2");
    struct2.addField(primitive1);
    struct2.addField(primitive2);
    struct2.addField(iarrF);
    struct2.addField(sarrF);
    Field s2("ss", 12, struct2);

    StructDataType struct3("struct3");
    struct3.addField(primitive1);
    struct3.addField(s2);

    Field structl1s1("l1s1", 11, struct3);

    DocumentType type("test");
    type.addField(primitive1);
    type.addField(structl1s1);

    Document doc(type, DocumentId("id:ns:test::1"));
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
    EXPECT_EQ(fullTraverser.getResult(),
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

TEST(DocumentTest, testVariables)
{
    ArrayDataType iarr(*DataType::INT);
    ArrayDataType iiarr(static_cast<DataType &>(iarr));
    ArrayDataType iiiarr(static_cast<DataType &>(iiarr));

    Field iiiarrF("iiiarray", 1, iiiarr);
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

    Document doc(type, DocumentId("id:ns:test::1"));
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

        EXPECT_EQ(fasit, handler.retVal);
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

TEST(DocumentTest, testModifyDocument)
{
    // Create test document type and content
    Field primitive1("primitive1", 1, *DataType::INT);
    Field primitive2("primitive2", 2, *DataType::INT);
    StructDataType struct1("struct1");
    struct1.addField(primitive1);
    struct1.addField(primitive2);

    ArrayDataType iarr(*DataType::INT);
    ArrayDataType sarr(struct1);
    Field iarrF("iarray", 21, iarr);
    Field sarrF("sarray", 22, sarr);

    MapDataType smap(*DataType::STRING, *DataType::STRING);
    Field smapF("smap", 23, smap);

    StructDataType struct2("struct2");
    struct2.addField(primitive1);
    struct2.addField(primitive2);
    struct2.addField(iarrF);
    struct2.addField(sarrF);
    struct2.addField(smapF);
    Field s2("ss", 12, struct2);

    MapDataType structmap(*DataType::STRING, struct2);
    Field structmapF("structmap", 24, structmap);

    WeightedSetDataType wset(*DataType::STRING, false, false);
    Field wsetF("wset", 25, wset);

    WeightedSetDataType structwset(struct2, false, false);
    Field structwsetF("structwset", 26, structwset);

    StructDataType struct3("struct3");
    struct3.addField(primitive1);
    struct3.addField(s2);
    struct3.addField(structmapF);
    struct3.addField(wsetF);
    struct3.addField(structwsetF);

    Field structl1s1("l1s1", 11, struct3);

    DocumentType type("test");
    type.addField(primitive1);
    type.addField(structl1s1);

    Document::UP doc(new Document(type, DocumentId("id:ns:test::1")));
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

    WeightedSetFieldValue wwset1(wset);
    WSetHelper wset1(wwset1);
    wset1.add("foo");
    wset1.add("bar");
    wset1.add("zoo");
    l1s1.setValue(wsetF, wwset1);

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

TEST(DocumentTest, testSimpleUsage)
{
    DocumentType::SP type(new DocumentType("test"));
    Field intF("int", 1, *DataType::INT);
    Field longF("long", 2, *DataType::LONG);
    Field strF("content", 4, *DataType::STRING);

    type->addField(intF);
    type->addField(longF);
    type->addField(strF);

    DocumentTypeRepo repo(*type);
    Document value(*repo.getDocumentType("test"), DocumentId("id:ns:test::1"));

        // Initially empty
    EXPECT_EQ(size_t(0), value.getSetFieldCount());
    EXPECT_TRUE(!value.hasValue(intF));

    value.setValue(intF, IntFieldValue(1));

        // Not empty
    EXPECT_EQ(size_t(1), value.getSetFieldCount());
    EXPECT_TRUE(value.hasValue(intF));

        // Adding some more
    value.setValue(longF, LongFieldValue(2));

        // Not empty
    EXPECT_EQ(size_t(2), value.getSetFieldCount());
    EXPECT_EQ(1, value.getValue(intF)->getAsInt());
    EXPECT_EQ(2, value.getValue(longF)->getAsInt());

    // Serialize & equality
    nbostream buffer;
    value.serialize(buffer);
    Document value2(*repo.getDocumentType("test"),
                    DocumentId("id::test:n=3:foo"));
    EXPECT_TRUE(value != value2);
    value2.deserialize(repo, buffer);
    EXPECT_TRUE(value2.hasValue(intF));
    EXPECT_EQ(value, value2);
    EXPECT_EQ(DocumentId("id:ns:test::1"), value2.getId());

        // Various ways of removing
    {
            // By value
        buffer.rp(0);
        value2.deserialize(repo, buffer);
        value2.remove(intF);
        EXPECT_TRUE(!value2.hasValue(intF));
        EXPECT_EQ(size_t(1), value2.getSetFieldCount());

            // Clearing all
        buffer.rp(0);
        value2.deserialize(repo, buffer);
        value2.clear();
        EXPECT_TRUE(!value2.hasValue(intF));
        EXPECT_EQ(size_t(0), value2.getSetFieldCount());
    }

        // Updating
    value2 = value;
    EXPECT_EQ(value, value2);
    value2.setValue(strF, StringFieldValue("foo"));
    EXPECT_TRUE(value2.hasValue(strF));
    EXPECT_EQ(vespalib::string("foo"),
                         value2.getValue(strF)->getAsString());
    EXPECT_TRUE(value != value2);
    value2.assign(value);
    EXPECT_EQ(value, value2);
    Document::UP valuePtr(value2.clone());
    EXPECT_EQ(value, *valuePtr);

        // Iterating
    const Document& constVal(value);
    for(Document::const_iterator it = constVal.begin();
        it != constVal.end(); ++it)
    {
        constVal.getValue(it.field());
    }

        // Comparison
    value2 = value;
    EXPECT_EQ(0, value.compare(value2));
    value2.remove(intF);
    EXPECT_TRUE(value.compare(value2) < 0);
    EXPECT_TRUE(value2.compare(value) > 0);
    value2 = value;
    value2.setValue(intF, IntFieldValue(5));
    EXPECT_TRUE(value.compare(value2) < 0);
    EXPECT_TRUE(value2.compare(value) > 0);

        // Output
    EXPECT_EQ(
            std::string("Document(id:ns:test::1, DocumentType(test))"),
            value.toString(false));
    EXPECT_EQ(
            std::string(
"  Document(id:ns:test::1\n"
"    DocumentType(test, id -877171244)\n"
"        : DocumentType(document) {\n"
"      StructDataType(test.header, id 306916075) {\n"
"        Field(content, id 4, PrimitiveDataType(String, id 2))\n"
"        Field(int, id 1, NumericDataType(Int, id 0))\n"
"        Field(long, id 2, NumericDataType(Long, id 4))\n"
"      }\n"
"    }\n"
"    int: 1\n"
"    long: 2\n"
"  )"),
            "  " + value.toString(true, "  "));
    EXPECT_EQ(
            std::string(
                "<document documenttype=\"test\" documentid=\"id:ns:test::1\">\n"
                "  <int>1</int>\n"
                "  <long>2</long>\n"
                "</document>"),
            value.toXml("  "));

        // Failure situations.

        // Fetch a field not existing in type
        // (Would be nice if this failed, but whole idea to fetch by field
        // objects is to improve performance.)
    Field anotherIntF("int", 17, *DataType::INT);
    EXPECT_TRUE(!value.hasValue(anotherIntF));
    EXPECT_TRUE(!value.getValue(anotherIntF));

        // Refuse to accept non-document types
    try{
        StructDataType otherType("foo", 4);
        Document value6(otherType, DocumentId("id:ns:foo::1"));
        FAIL() << "Didn't complain about non-document type";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot generate a document with non-document type"));
    }

        // Refuse to set wrong types
    try{
        value2.setValue(intF, StringFieldValue("bar"));
        FAIL() << "Failed to check type equality in setValue";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot assign value of type"));
    }
}

void verifyJavaDocument(Document& doc)
{
    IntFieldValue intVal;
    EXPECT_TRUE(doc.getValue(doc.getField("intfield"), intVal));
    EXPECT_EQ(5, intVal.getAsInt());

    FloatFieldValue floatVal;
    EXPECT_TRUE(doc.getValue(doc.getField("floatfield"), floatVal));
    EXPECT_TRUE(floatVal.getAsFloat() == (float) -9.23);

    StringFieldValue stringVal("");
    EXPECT_TRUE(doc.getValue(doc.getField("stringfield"), stringVal));
    EXPECT_EQ(vespalib::string("This is a string."),
                         stringVal.getAsString());

    LongFieldValue longVal;
    EXPECT_TRUE(doc.getValue(doc.getField("longfield"), longVal));
    EXPECT_EQ((int64_t)398420092938472983LL, longVal.getAsLong());

    DoubleFieldValue doubleVal;
    EXPECT_TRUE(doc.getValue(doc.getField("doublefield"), doubleVal));
    EXPECT_EQ(doubleVal.getAsDouble(), 98374532.398820);

    ByteFieldValue byteVal;
    EXPECT_TRUE(doc.getValue(doc.getField("bytefield"), byteVal));
    EXPECT_EQ(-2, byteVal.getAsInt());

    RawFieldValue rawVal;
    EXPECT_TRUE(doc.getValue(doc.getField("rawfield"), rawVal));
    EXPECT_TRUE(memcmp(rawVal.getAsRaw().first, "RAW DATA", 8) == 0);

    Document embedDocVal;
    EXPECT_TRUE(doc.getValue(doc.getField("docfield"), embedDocVal));

    ArrayFieldValue array(doc.getField("arrayoffloatfield").getDataType());
    EXPECT_TRUE(doc.getValue(doc.getField("arrayoffloatfield"), array));
    EXPECT_EQ((float)1.0, array[0].getAsFloat());
    EXPECT_EQ((float)2.0, array[1].getAsFloat());

    WeightedSetFieldValue wset(doc.getField("wsfield").getDataType());
    EXPECT_TRUE(doc.getValue(doc.getField("wsfield"), wset));
    EXPECT_EQ(50, wset.get(StringFieldValue("Weighted 0")));
    EXPECT_EQ(199, wset.get(StringFieldValue("Weighted 1")));

    MapFieldValue map(doc.getField("mapfield").getDataType());
    EXPECT_TRUE(doc.getValue(doc.getField("mapfield"), map));
    EXPECT_TRUE(map.get(StringFieldValue("foo1")).get());
    EXPECT_TRUE(map.get(StringFieldValue("foo2")).get());
    EXPECT_EQ(StringFieldValue("bar1"), dynamic_cast<StringFieldValue&>(*map.get(StringFieldValue("foo1"))));
    EXPECT_EQ(StringFieldValue("bar2"), dynamic_cast<StringFieldValue&>(*map.get(StringFieldValue("foo2"))));
}

TEST(DocumentTest, testReadSerializedFile)
{
    // Reads a file serialized from java
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    int fd = open(TEST_PATH("data/serializejava.dat").c_str(), O_RDONLY);

    size_t len = lseek(fd,0,SEEK_END);
    vespalib::alloc::Alloc buf = vespalib::alloc::Alloc::alloc(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.get(), len) != (ssize_t)len) {
        throw vespalib::Exception("read failed");
    }
    close(fd);

    nbostream stream(buf.get(), len);
    Document doc(repo, stream);
    verifyJavaDocument(doc);

    nbostream buf2 = doc.serialize();

    Document doc2(repo, buf2);
    verifyJavaDocument(doc2);

    EXPECT_TRUE(buf2.empty());
    buf2.rp(0);
    EXPECT_EQ(len, buf2.size());

    doc2.setValue("stringfield", StringFieldValue("hei"));

    nbostream buf3 = doc2.serialize();
    EXPECT_TRUE(len != buf3.size());
}

TEST(DocumentTest, testReadSerializedFileCompressed)
{
    // Reads a file serialized from java
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));

    int fd = open(TEST_PATH("data/serializejava-compressed.dat").c_str(), O_RDONLY);

    int len = lseek(fd,0,SEEK_END);
    vespalib::alloc::Alloc buf = vespalib::alloc::Alloc::alloc(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.get(), len) != len) {
        throw vespalib::Exception("read failed");
    }
    close(fd);

    nbostream stream(buf.get(), len);
    Document doc(repo, stream);
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

TEST(DocumentTest,testReadSerializedAllVersions)
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
        Document doc(*docType, DocumentId("id:ns:serializetest::http://test.doc.id/"));
        doc.setValue("intfield", IntFieldValue::make(5));
        doc.setValue("floatfield", FloatFieldValue::make(-9.23));
        doc.setValue("stringfield", StringFieldValue::make("This is a string."));
        doc.setValue("longfield", LongFieldValue::make(static_cast<int64_t>(398420092938472983LL)));
        doc.setValue("doublefield", DoubleFieldValue::make(98374532.398820));
        doc.setValue("bytefield", ByteFieldValue::make(-2));
        doc.setValue("rawfield", std::make_unique<RawFieldValue>("RAW DATA", 8));
        Document docInDoc(*docInDocType, DocumentId("id:ns:docindoc::http://doc.in.doc/"));
        docInDoc.setValue("stringindocfield", StringFieldValue::make("Elvis is dead"));
        doc.setValue("docfield", docInDoc);
        ArrayFieldValue floatArray(*arrayOfFloatDataType);
        CollectionHelper(floatArray).add(1.0);
        CollectionHelper(floatArray).add(2.0);
        doc.setValue("arrayoffloatfield", floatArray);
        WeightedSetFieldValue weightedSet(*weightedSetDataType);
        weightedSet.add(StringFieldValue("Weighted 0"), 50);
        weightedSet.add(StringFieldValue("Weighted 1"), 199);
        doc.setValue("wsfield", weightedSet);

        // Write document to disk, (when you bump version and alter stuff,
        // you can copy this current to new test for new version)
        {
            nbostream buf = doc.serialize();
            int fd = open(TEST_PATH("data/document-cpp-currentversion-uncompressed.dat").c_str(),
                          O_WRONLY | O_CREAT | O_TRUNC, 0644);
            EXPECT_TRUE(fd > 0);
            size_t len = write(fd, buf.peek(), buf.size());
            EXPECT_EQ(buf.size(), len);
            close(fd);
        }
    }

    std::string jpath = TEST_PATH("../test/serializeddocuments/");

    std::vector<TestDoc> tests;
    tests.push_back(TestDoc(TEST_PATH("data/document-cpp-v8-uncompressed.dat"), 8));
    tests.push_back(TestDoc(jpath + "document-java-v8-uncompressed.dat", 8));
    for (uint32_t i=0; i<tests.size(); ++i) {
        int version = tests[i]._createdVersion;
        std::string name = tests[i]._dataFile;
        std::cerr << name << std::endl;
        if (!vespalib::fileExists(name)) {
            FAIL() << "File " << name << " does not exist.";
        }
        int fd = open(tests[i]._dataFile.c_str(), O_RDONLY);
        int len = lseek(fd,0,SEEK_END);
        vespalib::alloc::Alloc buf = vespalib::alloc::Alloc::alloc(len);
        lseek(fd,0,SEEK_SET);
        if (read(fd, buf.get(), len) != len) {
                throw vespalib::Exception("read failed");
        }
        close(fd);

        nbostream stream(buf.get(), len);
        Document doc(repo, stream);

        IntFieldValue intVal;
        EXPECT_TRUE(doc.getValue(doc.getField("intfield"), intVal));
        EXPECT_EQ(5, intVal.getAsInt());

        FloatFieldValue floatVal;
        EXPECT_TRUE(doc.getValue(doc.getField("floatfield"), floatVal));
        EXPECT_TRUE(floatVal.getAsFloat() == (float) -9.23);

        StringFieldValue stringVal("");
        EXPECT_TRUE(doc.getValue(doc.getField("stringfield"), stringVal));
        EXPECT_EQ(vespalib::string("This is a string."),
                             stringVal.getAsString());

        LongFieldValue longVal;
        EXPECT_TRUE(doc.getValue(doc.getField("longfield"), longVal));
        EXPECT_EQ(static_cast<int64_t>(398420092938472983LL),
                             longVal.getAsLong());

        DoubleFieldValue doubleVal;
        EXPECT_TRUE(doc.getValue(doc.getField("doublefield"), doubleVal));
        EXPECT_EQ(doubleVal.getAsDouble(), 98374532.398820);

        ByteFieldValue byteVal;
        EXPECT_TRUE(doc.getValue(doc.getField("bytefield"), byteVal));
        EXPECT_EQ(-2, byteVal.getAsInt());

        RawFieldValue rawVal;
        EXPECT_TRUE(doc.getValue(doc.getField("rawfield"), rawVal));
        EXPECT_TRUE(memcmp(rawVal.getAsRaw().first, "RAW DATA", 8) == 0);

        if (version > 6) {
            Document docInDoc;
            EXPECT_TRUE(doc.getValue(doc.getField("docfield"), docInDoc));

            EXPECT_TRUE(docInDoc.getValue(
                        docInDoc.getField("stringindocfield"), stringVal));
            EXPECT_EQ(vespalib::string("Elvis is dead"),
                                 stringVal.getAsString());
        }

        ArrayFieldValue array(doc.getField("arrayoffloatfield").getDataType());
        EXPECT_TRUE(doc.getValue(doc.getField("arrayoffloatfield"), array));
        EXPECT_EQ((float)1.0, array[0].getAsFloat());
        EXPECT_EQ((float)2.0, array[1].getAsFloat());

        WeightedSetFieldValue wset(doc.getField("wsfield").getDataType());
        EXPECT_TRUE(doc.getValue(doc.getField("wsfield"), wset));
        EXPECT_EQ(50, wset.get(StringFieldValue("Weighted 0")));
        EXPECT_EQ(199, wset.get(StringFieldValue("Weighted 1")));

        // Check that serialization doesn't cause any problems.
        nbostream buf2 = doc.serialize();

        Document doc2(repo, buf2);
    }
}

size_t getSerializedSize(const Document &doc) {
    return doc.serialize().size();
}

TEST(DocumentTest, testGenerateSerializedFile)
{
    const std::string file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(file_name));
    Document doc(*repo.getDocumentType("serializetest"), DocumentId("id:ns:serializetest::http://test.doc.id/"));

    doc.setValue("intfield", IntFieldValue::make(5));
    doc.setValue("floatfield", FloatFieldValue::make(-9.23));
    doc.setValue("stringfield", StringFieldValue::make("This is a string."));
    doc.setValue("longfield", LongFieldValue::make((int64_t) 398420092938472983ll));
    doc.setValue("doublefield", DoubleFieldValue::make(98374532.398820));
    doc.setValue("urifield", StringFieldValue::make("http://this.is.a.test/"));
    doc.setValue("bytefield", ByteFieldValue::make(-2));
    doc.setValue("rawfield", std::make_unique<RawFieldValue>("RAW DATA"));

    const DocumentType *docindoc_type = repo.getDocumentType("docindoc");
    EXPECT_TRUE(docindoc_type);
    Document embedDoc(*docindoc_type, DocumentId("id:ns:docindoc::http://embedded"));

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

    nbostream buf = doc.serialize();

    const std::string serializedDir = TEST_PATH("../test/document/");
    int fd = open((serializedDir + "/serializecpp.dat").c_str(),
                  O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, buf.peek(), buf.size()) != (ssize_t)buf.size()) {
        throw vespalib::Exception("write failed");
    }
    close(fd);

    vespalib::nbostream hBuf;
    doc.serializeHeader(hBuf);
    fd = open((serializedDir + "/serializecppsplit_header.dat").c_str(),
              O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, hBuf.peek(), hBuf.size()) != (ssize_t)hBuf.size()) {
        throw vespalib::Exception("write failed");
    }
    close(fd);
}

TEST(DocumentTest, testBogusserialize)
{
    TestDocRepo test_repo;
    try {
        nbostream stream("aoifjweprjwoejr203r+2+4r823++!",100);
        Document doc(test_repo.getTypeRepo(), stream);
        FAIL() << "Failed to throw exception deserializing bogus data";
    } catch (DeserializeException& e) {
        EXPECT_THAT(e.what(), HasSubstr("Unrecognized serialization version"));
    }

    try {
        nbostream stream("",0);
        Document doc(test_repo.getTypeRepo(), stream);
        FAIL() << "Failed to throw exception deserializing empty buffer";
    } catch (DeserializeException& e) {
        EXPECT_THAT(e.what(), HasSubstr("Buffer out of bounds"));
    }
}

TEST(DocumentTest, testCRC32)
{
    TestDocRepo test_repo;
    Document doc(*test_repo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::crawler:http://www.ntnu.no/"));

    doc.setValue(doc.getField("hstringval"), StringFieldValue("bla bla bla bla bla"));

    uint32_t crc = doc.calculateChecksum();
    EXPECT_EQ(3987392271u, crc);

    nbostream buf = doc.serialize();

    int pos = 30;

    // Corrupt serialization.
    const_cast<char *>(buf.peek())[pos] ^= 72;
        // Create document. Byte corrupted above is in data area and
        // shouldn't fail deserialization.
    try {
        Document doc2(test_repo.getTypeRepo(), buf);
        buf.rp(0);
        EXPECT_TRUE(crc != doc2.calculateChecksum());
    } catch (document::DeserializeException& e) {
        EXPECT_TRUE(false);
    }
        // Return original value and retry
    const_cast<char *>(buf.peek())[pos] ^= 72;

    /// \todo TODO (was warning):  Cannot test for in memory representation altered, as there is no syntax for getting internal refs to data from document. Add test when this is added.
}

TEST(DocumentTest, testSliceSerialize)
{
        // Test that document doesn't need its own bytebuffer, such that we
        // can serialize multiple documents after each other in the same
        // bytebuffer.
    TestDocMan testDocMan;
    Document::UP doc = testDocMan.createDocument();
    Document::UP doc2 = testDocMan.createDocument("Some other content", "id:ns:testdoctype1::anotherdoc");

    ArrayFieldValue val(doc2->getField("rawarray").getDataType());
    val.add(RawFieldValue("hei", 3));
    val.add(RawFieldValue("hallo", 5));
    val.add(RawFieldValue("hei der", 7));
    doc2->setValue(doc2->getField("rawarray"), val);

    nbostream buf = doc->serialize();
    EXPECT_EQ(getSerializedSize(*doc), buf.size());
    doc2->serialize(buf);
    EXPECT_EQ(getSerializedSize(*doc) + getSerializedSize(*doc2), buf.size());

    Document doc3(testDocMan.getTypeRepo(), buf);
    EXPECT_EQ(getSerializedSize(*doc), buf.rp());
    Document doc4(testDocMan.getTypeRepo(), buf);
    EXPECT_EQ(getSerializedSize(*doc) + getSerializedSize(*doc2), buf.rp());

    EXPECT_EQ(*doc, doc3);
    EXPECT_EQ(*doc2, doc4);
}

TEST(DocumentTest, testUnknownEntries)
{
    // We should be able to deserialize a document with unknown values in it.
    DocumentType type1("test", 0);
    DocumentType type2("test", 0);
    Field field1("int1", *DataType::INT);
    Field field2("int2", *DataType::INT);
    Field field3("int3", *DataType::INT);
    Field field4("int4", *DataType::INT);

    type1.addField(field1);
    type1.addField(field2);
    type1.addField(field3);
    type1.addField(field4);

    type2.addField(field3);
    type2.addField(field4);

    DocumentTypeRepo repo(type2);

    Document doc1(type1, DocumentId("id:ns:test::1"));
    doc1.setValue(field1, IntFieldValue(1));
    doc1.setValue(field2, IntFieldValue(2));
    doc1.setValue(field3, IntFieldValue(3));
    doc1.setValue(field4, IntFieldValue(4));

    vespalib::nbostream os;
    doc1.serialize(os);

    Document doc2;
    doc2.deserialize(repo, os);

    EXPECT_EQ(std::string(
        "<document documenttype=\"test\" documentid=\"id:ns:test::1\">\n"
        "<int3>3</int3>\n"
        "<int4>4</int4>\n"
        "</document>"), doc2.toXml());

    EXPECT_EQ(3, doc2.getValue(field3)->getAsInt());
    EXPECT_EQ(4, doc2.getValue(field4)->getAsInt());

    // The fields are actually accessible as long as you ask with field of
    // correct type.

    EXPECT_TRUE(doc2.hasValue(field1));
    EXPECT_TRUE(doc2.hasValue(field2));

    EXPECT_EQ(1, doc2.getValue(field1)->getAsInt());
    EXPECT_EQ(2, doc2.getValue(field2)->getAsInt());

    EXPECT_EQ(size_t(2), doc2.getSetFieldCount());
}

TEST(DocumentTest, testAnnotationDeserialization)
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
    vespalib::alloc::Alloc buf = vespalib::alloc::Alloc::alloc(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.get(), len) != len) {
        throw vespalib::Exception("read failed");
    }
    close(fd);

    nbostream stream1(buf.get(), len);
    Document doc(repo, stream1);
    StringFieldValue strVal;
    EXPECT_TRUE(doc.getValue(doc.getField("story"), strVal));

    vespalib::nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.write(strVal);
    
    FixedTypeRepo fixedRepo(repo, doc.getType());
    VespaDocumentDeserializer deserializer(fixedRepo, stream, 8);
    StringFieldValue strVal2;
    deserializer.read(strVal2);
    EXPECT_EQ(strVal.toString(), strVal2.toString());
    EXPECT_EQ(strVal.toString(true), strVal2.toString(true));

    EXPECT_EQ(vespalib::string("help me help me i'm stuck inside a computer!"),
                         strVal.getAsString());
    StringFieldValue::SpanTrees trees = strVal.getSpanTrees();
    const SpanTree *span_tree = StringFieldValue::findTree(trees, "fruits");
    EXPECT_TRUE(span_tree);
    EXPECT_EQ(size_t(8), span_tree->numAnnotations());
    span_tree = StringFieldValue::findTree(trees, "ballooo");
    EXPECT_TRUE(span_tree);
    EXPECT_EQ(size_t(8), span_tree->numAnnotations());


    ByteFieldValue byteVal;
    EXPECT_TRUE(doc.getValue(doc.getField("age"), byteVal));
    EXPECT_EQ( 123, byteVal.getAsInt());

    IntFieldValue intVal;
    EXPECT_TRUE(doc.getValue(doc.getField("date"), intVal));
    EXPECT_EQ(13829297, intVal.getAsInt());

    LongFieldValue longVal;
    EXPECT_TRUE(doc.getValue(doc.getField("friend"), longVal));
    EXPECT_EQ((int64_t)2384LL, longVal.getAsLong());
}

TEST(DocumentTest, testDeserializeMultiple)
{
    TestDocRepo testDocRepo;
    const DocumentTypeRepo& repo(testDocRepo.getTypeRepo());
    const DocumentType* docTypePtr(repo.getDocumentType("testdoctype1"));
    EXPECT_TRUE(docTypePtr != nullptr);
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
    EXPECT_EQ(correct, sv3);
}

}
