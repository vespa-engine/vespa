// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/vsm/flattendocsumwriter.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/data/slime/slime.h>

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
    bool setField(FieldIdT fId, document::FieldValue::UP fv) override {
        if (fId < _fields.size()) {
            _fields[fId].reset(fv.release());
            return true;
        }
        return false;
    }
    const document::FieldValue * getField(FieldIdT fId) const override {
        if (fId < _fields.size()) {
            return _fields[fId].get();
        }
        return nullptr;
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
    void testFlattenDocsumWriter();
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
    { // test mulitple invocations
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

int
DocsumTest::Main()
{
    TEST_INIT("docsum_test");

    TEST_DO(testFlattenDocsumWriter());

    TEST_DONE();
}

}

TEST_APPHOOK(vsm::DocsumTest);

