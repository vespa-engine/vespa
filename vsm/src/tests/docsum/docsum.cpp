// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/vsm/flattendocsumwriter.h>
#include <vespa/vsm/vsm/slimefieldwriter.h>

using namespace document;

namespace vsm {

template <typename T>
class Vector : public std::vector<T>
{
public:
    Vector<T> & add(T v) { this->push_back(v); return *this; }
};

typedef Vector<std::string>  StringList;
typedef Vector<std::pair<std::string, int32_t> > WeightedStringList;


class TestDocument : public vsm::Document
{
private:
    std::vector<FieldValueContainer> _fields;

public:
    TestDocument(const search::DocumentIdT & docId, size_t numFields) : vsm::Document(docId, numFields), _fields(numFields) {}
    virtual bool setField(FieldIdT fId, document::FieldValue::UP fv) override {
        if (fId < _fields.size()) {
            _fields[fId].reset(fv.release());
            return true;
        }
        return false;
    }
    virtual const document::FieldValue * getField(FieldIdT fId) const override {
        if (fId < _fields.size()) {
            return _fields[fId].get();
        }
        return NULL;
    }
};


class DocsumTest : public vespalib::TestApp
{
private:
    ArrayFieldValue createFieldValue(const StringList & fv);
    WeightedSetFieldValue createFieldValue(const WeightedStringList & fv);

    void assertFlattenDocsumWriter(const FieldValue & fv, const std::string & exp) {
        FlattenDocsumWriter fdw;
        assertFlattenDocsumWriter(fdw, fv, exp);
    }
    void assertFlattenDocsumWriter(FlattenDocsumWriter & fdw, const FieldValue & fv, const std::string & exp);
    void assertSlimeFieldWriter(const FieldValue & fv, const std::string & exp) {
        SlimeFieldWriter sfw;
        TEST_DO(assertSlimeFieldWriter(sfw, fv, exp));
    }
    void assertSlimeFieldWriter(SlimeFieldWriter & sfw, const FieldValue & fv, const std::string & exp);

    void testFlattenDocsumWriter();
    void testSlimeFieldWriter();
    void requireThatSlimeFieldWriterHandlesMap();
    void testDocSumCache();

public:
    int Main() override;
};

ArrayFieldValue
DocsumTest::createFieldValue(const StringList & fv)
{

    static ArrayDataType type(*DataType::STRING);
    ArrayFieldValue afv(type);
    for (size_t i = 0; i < fv.size(); ++i) {
        afv.add(StringFieldValue(fv[i]));
    }
    return afv;
}

WeightedSetFieldValue
DocsumTest::createFieldValue(const WeightedStringList & fv)
{
    static WeightedSetDataType type(*DataType::STRING, false, false);
    WeightedSetFieldValue wsfv(type);
    for (size_t i = 0; i < fv.size(); ++i) {
        wsfv.add(StringFieldValue(fv[i].first), fv[i].second);
    }
    return wsfv;
}

void
DocsumTest::assertFlattenDocsumWriter(FlattenDocsumWriter & fdw, const FieldValue & fv, const std::string & exp)
{
    FieldPath empty;
    fv.iterateNested(empty.getFullRange(), fdw);
    std::string actual(fdw.getResult().getBuffer(), fdw.getResult().getPos());
    EXPECT_EQUAL(actual, exp);
}

void
DocsumTest::assertSlimeFieldWriter(SlimeFieldWriter & sfw, const FieldValue & fv, const std::string & exp)
{
    sfw.convert(fv);

    vespalib::Slime gotSlime;
    vespalib::Memory serialized(sfw.out());
    size_t decodeRes = vespalib::slime::BinaryFormat::decode(serialized, gotSlime);
    ASSERT_EQUAL(decodeRes, serialized.size);

    vespalib::Slime expSlime;
    size_t used = vespalib::slime::JsonFormat::decode(exp, expSlime);
    EXPECT_TRUE(used > 0);
    EXPECT_EQUAL(expSlime, gotSlime);
}

void
DocsumTest::testFlattenDocsumWriter()
{
    { // basic tests
        TEST_DO(assertFlattenDocsumWriter(StringFieldValue("foo bar"), "foo bar"));
        TEST_DO(assertFlattenDocsumWriter(RawFieldValue("foo bar"), "foo bar"));
        TEST_DO(assertFlattenDocsumWriter(BoolFieldValue(true), "true"));
        TEST_DO(assertFlattenDocsumWriter(BoolFieldValue(false), "false"));
        TEST_DO(assertFlattenDocsumWriter(LongFieldValue(123456789), "123456789"));
        TEST_DO(assertFlattenDocsumWriter(createFieldValue(StringList().add("foo bar").add("baz").add(" qux ")),
                                  "foo bar baz  qux "));
    }
    { // test mulitple invokations
        FlattenDocsumWriter fdw("#");
        TEST_DO(assertFlattenDocsumWriter(fdw, StringFieldValue("foo"), "foo"));
        TEST_DO(assertFlattenDocsumWriter(fdw, StringFieldValue("bar"), "foo#bar"));
        fdw.clear();
        TEST_DO(assertFlattenDocsumWriter(fdw, StringFieldValue("baz"), "baz"));
        TEST_DO(assertFlattenDocsumWriter(fdw, StringFieldValue("qux"), "baz qux"));
    }
    { // test resizing
        FlattenDocsumWriter fdw("#");
        EXPECT_EQUAL(fdw.getResult().getPos(), 0u);
        EXPECT_EQUAL(fdw.getResult().getLength(), 32u);
        TEST_DO(assertFlattenDocsumWriter(fdw, StringFieldValue("aaaabbbbccccddddeeeeffffgggghhhh"),
                                          "aaaabbbbccccddddeeeeffffgggghhhh"));
        EXPECT_EQUAL(fdw.getResult().getPos(), 32u);
        EXPECT_EQUAL(fdw.getResult().getLength(), 32u);
        TEST_DO(assertFlattenDocsumWriter(fdw, StringFieldValue("aaaa"), "aaaabbbbccccddddeeeeffffgggghhhh#aaaa"));
        EXPECT_EQUAL(fdw.getResult().getPos(), 37u);
        EXPECT_TRUE(fdw.getResult().getLength() >= 37u);
        fdw.clear();
        EXPECT_EQUAL(fdw.getResult().getPos(), 0u);
        EXPECT_TRUE(fdw.getResult().getLength() >= 37u);
    }
}

void
DocsumTest::testSlimeFieldWriter()
{
    { // basic types
        assertSlimeFieldWriter(LongFieldValue(123456789), "123456789");
        assertSlimeFieldWriter(BoolFieldValue(true), "true");
        assertSlimeFieldWriter(BoolFieldValue(false), "false");
        assertSlimeFieldWriter(DoubleFieldValue(12.34), "12.34");
        assertSlimeFieldWriter(StringFieldValue("foo bar"), "\"foo bar\"");
    }
    { // collection field values
        assertSlimeFieldWriter(createFieldValue(StringList().add("foo").add("bar").add("baz")),
                               "[\"foo\",\"bar\",\"baz\"]");
        assertSlimeFieldWriter(createFieldValue(WeightedStringList().add(std::make_pair("bar", 20)).
                                                                     add(std::make_pair("baz", 30)).
                                                                     add(std::make_pair("foo", 10))),
                               "[{item:\"bar\",weight:20},{item:\"baz\",weight:30},{item:\"foo\",weight:10}]");
    }
    { // struct field value
        StructDataType subType("substruct");
        Field fd("d", 0, *DataType::STRING);
        Field fe("e", 1, *DataType::STRING);
        subType.addField(fd);
        subType.addField(fe);
        StructFieldValue subValue(subType);
        subValue.setValue(fd, StringFieldValue("baz"));
        subValue.setValue(fe, StringFieldValue("qux"));

        StructDataType type("struct");
        Field fa("a", 0, *DataType::STRING);
        Field fb("b", 1, *DataType::STRING);
        Field fc("c", 2, subType);
        type.addField(fa);
        type.addField(fb);
        type.addField(fc);
        StructFieldValue value(type);
        value.setValue(fa, StringFieldValue("foo"));
        value.setValue(fb, StringFieldValue("bar"));
        value.setValue(fc, subValue);


        { // select a subset and then all
            SlimeFieldWriter sfw;
            DocsumFieldSpec::FieldIdentifierVector fields;
            {
                FieldPath path;
                type.buildFieldPath(path, "a");
                fields.push_back(DocsumFieldSpec::FieldIdentifier(0, path));
            }
            {
                FieldPath path;
                type.buildFieldPath(path, "c.e");
                fields.push_back(DocsumFieldSpec::FieldIdentifier(0, path));
            }
            sfw.setInputFields(fields);
            TEST_DO(assertSlimeFieldWriter(sfw, value, "{\"a\":\"foo\",\"c\":{\"e\":\"qux\"}}"));
            sfw.clear();
            TEST_DO(assertSlimeFieldWriter(sfw, value, "{\"a\":\"foo\",\"b\":\"bar\",\"c\":{\"d\":\"baz\",\"e\":\"qux\"}}"));
        }

    { // multiple invocations
        SlimeFieldWriter sfw;
        TEST_DO(assertSlimeFieldWriter(sfw, StringFieldValue("foo"), "\"foo\""));
        sfw.clear();
        TEST_DO(assertSlimeFieldWriter(sfw, StringFieldValue("bar"), "\"bar\""));
        sfw.clear();
        TEST_DO(assertSlimeFieldWriter(sfw, StringFieldValue("baz"), "\"baz\""));
    }

    }
}

void
DocsumTest::requireThatSlimeFieldWriterHandlesMap()
{
    { // map<string, string>
        MapDataType mapType(*DataType::STRING, *DataType::STRING);
        MapFieldValue mapfv(mapType);
        EXPECT_TRUE(mapfv.put(StringFieldValue("k1"), StringFieldValue("v1")));
        EXPECT_TRUE(mapfv.put(StringFieldValue("k2"), StringFieldValue("v2")));
        assertSlimeFieldWriter(mapfv, "[{\"key\":\"k1\",\"value\":\"v1\"},{\"key\":\"k2\",\"value\":\"v2\"}]");
    }
    { // map<string, struct>
        StructDataType structType("struct");
        Field fa("a", 0, *DataType::STRING);
        Field fb("b", 1, *DataType::STRING);
        structType.addField(fa);
        structType.addField(fb);
        StructFieldValue structValue(structType);
        structValue.setValue(fa, StringFieldValue("foo"));
        structValue.setValue(fb, StringFieldValue("bar"));
        MapDataType mapType(*DataType::STRING, structType);
        MapFieldValue mapfv(mapType);
        EXPECT_TRUE(mapfv.put(StringFieldValue("k1"), structValue));
        { // select a subset and then all
            SlimeFieldWriter sfw;
            DocsumFieldSpec::FieldIdentifierVector fields;
            {
                FieldPath path;
                mapType.buildFieldPath(path, "value.b");
                fields.push_back(DocsumFieldSpec::FieldIdentifier(0, path));
            }
            sfw.setInputFields(fields);
            TEST_DO(assertSlimeFieldWriter(sfw, mapfv, "[{\"key\":\"k1\",\"value\":{\"b\":\"bar\"}}]"));
            {
                FieldPath path;
                mapType.buildFieldPath(path, "{k1}.a");
                fields[0] = DocsumFieldSpec::FieldIdentifier(0, path);
            }
            sfw.clear();
            sfw.setInputFields(fields);
            TEST_DO(assertSlimeFieldWriter(sfw, mapfv, "[{\"key\":\"k1\",\"value\":{\"a\":\"foo\"}}]"));
            sfw.clear(); // all fields implicit
            TEST_DO(assertSlimeFieldWriter(sfw, mapfv, "[{\"key\":\"k1\",\"value\":{\"a\":\"foo\",\"b\":\"bar\"}}]"));
        }
    }
}

int
DocsumTest::Main()
{
    TEST_INIT("docsum_test");

    TEST_DO(testFlattenDocsumWriter());
    TEST_DO(testSlimeFieldWriter());
    TEST_DO(requireThatSlimeFieldWriterHandlesMap());

    TEST_DONE();
}

}

TEST_APPHOOK(vsm::DocsumTest);

