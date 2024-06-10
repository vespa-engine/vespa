// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/vsm/common/docsum.h>
#include <vespa/vsm/common/storagedocument.h>
#include <vespa/vsm/vsm/flattendocsumwriter.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;

namespace vsm {

template <typename T>
class Vector : public std::vector<T>
{
public:
    Vector<T> & add(T v) { this->push_back(v); return *this; }
};

using StringList = Vector<std::string>;
using WeightedStringList = Vector<std::pair<std::string, int32_t> >;


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


class DocsumTest : public ::testing::Test
{
protected:
    ArrayFieldValue createFieldValue(const StringList & fv);
    WeightedSetFieldValue createFieldValue(const WeightedStringList & fv);

    void assertFlattenDocsumWriter(const FieldValue & fv, const std::string & exp, const std::string& label) {
        FlattenDocsumWriter fdw;
        assertFlattenDocsumWriter(fdw, fv, exp, label);
    }
    void assertFlattenDocsumWriter(FlattenDocsumWriter & fdw, const FieldValue & fv, const std::string & exp, const std::string& label);

    DocsumTest();
    ~DocsumTest() override;
};

DocsumTest::DocsumTest()
    : ::testing::Test()
{
}

DocsumTest::~DocsumTest() = default;

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
DocsumTest::assertFlattenDocsumWriter(FlattenDocsumWriter & fdw, const FieldValue & fv, const std::string & exp, const std::string& label)
{
    SCOPED_TRACE(label);
    FieldPath empty;
    fv.iterateNested(empty.getFullRange(), fdw);
    std::string actual(fdw.getResult().getBuffer(), fdw.getResult().getPos());
    EXPECT_EQ(exp, actual);
}

TEST_F(DocsumTest, flatten_docsum_writer_basic)
{
    assertFlattenDocsumWriter(StringFieldValue("foo bar"), "foo bar", "string foo bar");
    assertFlattenDocsumWriter(RawFieldValue("foo bar"), "foo bar", "raw foo bar");
    assertFlattenDocsumWriter(BoolFieldValue(true), "true", "bool true");
    assertFlattenDocsumWriter(BoolFieldValue(false), "false", "bool false");
    assertFlattenDocsumWriter(LongFieldValue(123456789), "123456789", "long");
    assertFlattenDocsumWriter(createFieldValue(StringList().add("foo bar").add("baz").add(" qux ")),
                                               "foo bar baz  qux ", "wset");
}

TEST_F(DocsumTest, flatten_docsum_writer_multiple_invocations)
{
    FlattenDocsumWriter fdw("#");
    assertFlattenDocsumWriter(fdw, StringFieldValue("foo"), "foo", "string foo");
    assertFlattenDocsumWriter(fdw, StringFieldValue("bar"), "foo#bar", "string bar");
    fdw.clear();
    assertFlattenDocsumWriter(fdw, StringFieldValue("baz"), "baz", "string baz");
    assertFlattenDocsumWriter(fdw, StringFieldValue("qux"), "baz qux", "string qux");
}

TEST_F(DocsumTest, flatten_docsum_writer_resizing)
{
    FlattenDocsumWriter fdw("#");
    EXPECT_EQ(fdw.getResult().getPos(), 0u);
    EXPECT_EQ(fdw.getResult().getLength(), 32u);
    assertFlattenDocsumWriter(fdw, StringFieldValue("aaaabbbbccccddddeeeeffffgggghhhh"),
                              "aaaabbbbccccddddeeeeffffgggghhhh",
                              "string long");
    EXPECT_EQ(fdw.getResult().getPos(), 32u);
    EXPECT_EQ(fdw.getResult().getLength(), 32u);
    assertFlattenDocsumWriter(fdw, StringFieldValue("aaaa"), "aaaabbbbccccddddeeeeffffgggghhhh#aaaa", "string second long");
    EXPECT_EQ(fdw.getResult().getPos(), 37u);
    EXPECT_TRUE(fdw.getResult().getLength() >= 37u);
    fdw.clear();
    EXPECT_EQ(fdw.getResult().getPos(), 0u);
    EXPECT_TRUE(fdw.getResult().getLength() >= 37u);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
