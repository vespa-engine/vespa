// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/fieldvalue_helpers.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>

#include <vespa/document/base/exceptions.h>

#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>

#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/util/bytebuffer.h>

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <fcntl.h>
#include <gtest/gtest.h>

using vespalib::Identifiable;
using vespalib::nbostream;
using namespace document::config_builder;

namespace document {

using namespace fieldvalue;

class FieldPathUpdateTestCase : public ::testing::Test {
protected:
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType *_foobar_type;
    FieldPathUpdateTestCase();
    ~FieldPathUpdateTestCase();

    void SetUp() override;
    void TearDown() override;

    DocumentUpdate::UP
    createDocumentUpdateForSerialization(const DocumentTypeRepo& repo);
};

namespace {

nbostream
serializeHEAD(const DocumentUpdate & update)
{
    vespalib::nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.writeHEAD(update);
    return stream;
}

void testSerialize(const DocumentTypeRepo& repo, const DocumentUpdate& a) {
    try{
        auto bb(serializeHEAD(a));
        DocumentUpdate::UP b(DocumentUpdate::createHEAD(repo, bb));

        EXPECT_EQ(size_t(0), bb.size());
        EXPECT_EQ(a.getId().toString(), b->getId().toString());
        EXPECT_EQ(a.getUpdates().size(), b->getUpdates().size());
        for (size_t i(0); i < a.getUpdates().size(); i++) {
            const FieldUpdate & ua = a.getUpdates()[i];
            const FieldUpdate & ub = b->getUpdates()[i];

            EXPECT_EQ(&ua.getField(), &ub.getField());
            EXPECT_EQ(ua.getUpdates().size(), ub.getUpdates().size());
            for (size_t j(0); j < ua.getUpdates().size(); j++) {
                EXPECT_EQ(ua.getUpdates()[j]->getType(), ub.getUpdates()[j]->getType());
            }
        }
        EXPECT_EQ(a.getFieldPathUpdates().size(), b->getFieldPathUpdates().size());
        for (size_t i(0); i < a.getFieldPathUpdates().size(); i++) {
            const auto & ua = a.getFieldPathUpdates()[i];
            const auto & ub = b->getFieldPathUpdates()[i];

            EXPECT_EQ(*ua, *ub);
        }
        EXPECT_EQ(a, *b);
    } catch (std::exception& e) {
        std::cerr << "Failed while testing document field path update:\n";
        a.print(std::cerr, true, "");
        std::cerr << std::endl;
        throw;
    }
}

} // anon ns

FieldPathUpdateTestCase::FieldPathUpdateTestCase()
    : _foobar_type(nullptr)
{}

FieldPathUpdateTestCase::~FieldPathUpdateTestCase() = default;

void
FieldPathUpdateTestCase::SetUp()
{
    DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "foobar",
                     Struct("foobar.header")
                     .addField("num", DataType::T_INT)
                     .addField("byteval", DataType::T_BYTE)
                     .addField("strfoo", DataType::T_STRING)
                     .addField("strarray", Array(DataType::T_STRING)),
                     Struct("foobar.body")
                     .addField("strwset", Wset(DataType::T_STRING))
                     .addField("structmap",
                               Map(DataType::T_STRING, Struct("mystruct")
                                       .addField("title", DataType::T_STRING)
                                       .addField("rating", DataType::T_INT)))
                     .addField("strmap",
                               Map(DataType::T_STRING, DataType::T_STRING)));
    _repo.reset(new DocumentTypeRepo(builder.config()));

    _foobar_type = _repo->getDocumentType("foobar");
}

void
FieldPathUpdateTestCase::TearDown()
{
}

TEST_F(FieldPathUpdateTestCase, testRemoveField)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::things:thangs"));
    EXPECT_TRUE(doc->hasValue("strfoo") == false);
    doc->setValue("strfoo", StringFieldValue("cocacola"));
    EXPECT_EQ(vespalib::string("cocacola"), doc->getValue("strfoo")->getAsString());
    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("strfoo"));
    docUp.applyTo(*doc);
    EXPECT_TRUE(doc->hasValue("strfoo") == false);
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveMultiList)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::things:thangs"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);
    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("crouching tiger, hidden field"));
        strArray.add(StringFieldValue("remove val 1"));
        strArray.add(StringFieldValue("hello hello"));
        doc->setValue("strarray", strArray);
    }
    EXPECT_TRUE(doc->hasValue("strarray"));
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("strarray[$x]", "foobar.strarray[$x] == \"remove val 1\""));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray = doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        ASSERT_EQ(std::size_t(2), strArray->size());
        EXPECT_EQ(vespalib::string("crouching tiger, hidden field"), (*strArray)[0].getAsString());
        EXPECT_EQ(vespalib::string("hello hello"), (*strArray)[1].getAsString());
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveMultiList2)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::things:thangs"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);
    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("remove val 1"));
        strArray.add(StringFieldValue("remove val 1"));
        strArray.add(StringFieldValue("hello hello"));
        doc->setValue("strarray", strArray);
    }
    EXPECT_TRUE(doc->hasValue("strarray"));
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("strarray[$x]", "foobar.strarray[$x] == \"remove val 1\""));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray = doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        ASSERT_EQ(std::size_t(1), strArray->size());
        EXPECT_EQ(vespalib::string("hello hello"), (*strArray)[0].getAsString());
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveEntireListField)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::things:thangs"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);
    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("this list"));
        strArray.add(StringFieldValue("should be"));
        strArray.add(StringFieldValue("totally removed"));
        doc->setValue("strarray", strArray);
    }
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("strarray", ""));
    docUp.applyTo(*doc);
    EXPECT_TRUE(!doc->hasValue("strarray"));
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveMultiWset)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::helan:halvan"));
    EXPECT_TRUE(doc->hasValue("strwset") == false);
    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("hello hello"), 10);
        strWset.add(StringFieldValue("remove val 1"), 20);
        doc->setValue("strwset", strWset);
    }
    EXPECT_TRUE(doc->hasValue("strwset"));
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("strwset{remove val 1}"));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<WeightedSetFieldValue> strWset = doc->getAs<WeightedSetFieldValue>(doc->getField("strwset"));
        ASSERT_EQ(std::size_t(1), strWset->size());
        EXPECT_EQ(10, strWset->get(StringFieldValue("hello hello")));
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignSingle)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::drekka:karsk"));
    EXPECT_TRUE(doc->hasValue("strfoo") == false);
    // Test assignment of non-existing
    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strfoo", std::string(), StringFieldValue::make("himert")));
    docUp.applyTo(*doc);
    EXPECT_TRUE(doc->hasValue("strfoo"));
    EXPECT_EQ(vespalib::string("himert"), doc->getValue("strfoo")->getAsString());
    // Test overwriting existing
    DocumentUpdate docUp2(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp2.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strfoo", std::string(), StringFieldValue::make("wunderbaum")));
    docUp2.applyTo(*doc);
    EXPECT_EQ(vespalib::string("wunderbaum"), doc->getValue("strfoo")->getAsString());
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMath)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    doc->setValue("num", IntFieldValue(34));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("num", "", "($value * 2) / $value"));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(2)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathByteToZero)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    doc->setValue("byteval", ByteFieldValue(3));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("byteval", "", "$value - 3"));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(ByteFieldValue(0)), *doc->getValue("byteval"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathNotModifiedOnUnderflow)
{
    int low_value = -126;
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    doc->setValue("byteval", ByteFieldValue(low_value));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("byteval", "", "$value - 4"));
    docUp.applyTo(*doc);
    // Over/underflow will happen. You must have control of your data types.
    EXPECT_EQ(static_cast<const FieldValue&>(ByteFieldValue((char)(low_value - 4))), *doc->getValue("byteval"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathNotModifiedOnOverflow)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    doc->setValue("byteval", ByteFieldValue(127));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("byteval", "", "$value + 200"));
    docUp.applyTo(*doc);
    // Over/underflow will happen. You must have control of your data types.
    EXPECT_EQ(static_cast<const FieldValue&>(ByteFieldValue(static_cast<char>(static_cast<int>(127+200)))), *doc->getValue("byteval"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathDivZero)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(10));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("num", "", "$value / ($value - 10)"));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(10)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignFieldNotExistingInExpression)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(10));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("num", "", "foobar.num2 + $value"));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(10)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignFieldNotExistingInPath)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    try {
        docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("nosuchnum", "", "foobar.num + $value"));
        docUp.applyTo(*doc);
        EXPECT_TRUE(false);
    } catch (const FieldNotFoundException&) {
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignTargetNotExisting)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    EXPECT_TRUE(doc->hasValue("num") == false);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("num", "", "$value + 5"));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(5)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testAssignSimpleMapValueWithVariable)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bug:hunter"));

    MapFieldValue mfv(doc->getType().getField("strmap").getDataType());
    mfv.put(StringFieldValue("foo"), StringFieldValue("bar"));
    mfv.put(StringFieldValue("baz"), StringFieldValue("bananas"));
    doc->setValue("strmap", mfv);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    // Select on value, not key
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(),
                                      "strmap{$x}", "foobar.strmap{$x} == \"bar\"", StringFieldValue::make("shinyvalue")));
    docUp.applyTo(*doc);

    std::unique_ptr<MapFieldValue> valueNow(doc->getAs<MapFieldValue>(doc->getField("strmap")));

    ASSERT_EQ(std::size_t(2), valueNow->size());
    EXPECT_EQ(
            static_cast<const FieldValue&>(StringFieldValue("shinyvalue")),
            *valueNow->get(StringFieldValue("foo")));
    EXPECT_EQ(
            static_cast<const FieldValue&>(StringFieldValue("bananas")),
            *valueNow->get(StringFieldValue("baz")));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathRemoveIfZero)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(34));
    EXPECT_TRUE(doc->hasValue("num") == true);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    auto up1 = std::make_unique<AssignFieldPathUpdate>("num", "", "($value * 2) / $value - 2");
    static_cast<AssignFieldPathUpdate&>(*up1).setRemoveIfZero(true);
    docUp.addFieldPathUpdate(std::move(up1));

    docUp.applyTo(*doc);
    EXPECT_TRUE(doc->hasValue("num") == false);
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMultiList)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::fest:skinnvest"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("hello hello"));
        strArray.add(StringFieldValue("blah blargh"));
        doc->setValue("strarray", strArray);
        EXPECT_TRUE(doc->hasValue("strarray"));
    }

    auto updateArray = std::make_unique<ArrayFieldValue>(doc->getType().getField("strarray").getDataType());
    updateArray->add(StringFieldValue("assigned val 0"));
    updateArray->add(StringFieldValue("assigned val 1"));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strarray", std::string(), std::move(updateArray)));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray = doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        ASSERT_EQ(std::size_t(2), strArray->size());
        EXPECT_EQ(vespalib::string("assigned val 0"), (*strArray)[0].getAsString());
        EXPECT_EQ(vespalib::string("assigned val 1"), (*strArray)[1].getAsString());
    }
}


TEST_F(FieldPathUpdateTestCase, testApplyAssignMultiWset)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::fest:skinnvest"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("hello gentlemen"), 10);
        strWset.add(StringFieldValue("what you say"), 20);
        doc->setValue("strwset", strWset);
        EXPECT_TRUE(doc->hasValue("strwset"));
    }

    auto assignWset = std::make_unique<WeightedSetFieldValue>(doc->getType().getField("strwset").getDataType());
    assignWset->add(StringFieldValue("assigned val 0"), 5);
    assignWset->add(StringFieldValue("assigned val 1"), 10);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strwset", std::string(), std::move(assignWset)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");
    {
        std::unique_ptr<WeightedSetFieldValue> strWset = doc->getAs<WeightedSetFieldValue>(doc->getField("strwset"));
        ASSERT_EQ(std::size_t(2), strWset->size());
        EXPECT_EQ(5, strWset->get(StringFieldValue("assigned val 0")));
        EXPECT_EQ(10, strWset->get(StringFieldValue("assigned val 1")));
    }
}

TEST_F(FieldPathUpdateTestCase, testAssignWsetRemoveIfZero)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::tronder:bataljon"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("you say goodbye"), 164);
        strWset.add(StringFieldValue("but i say hello"), 243);
        doc->setValue("strwset", strWset);
        EXPECT_TRUE(doc->hasValue("strwset"));
    }

    {
        DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        auto assignUpdate = std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strwset{you say goodbye}", std::string(), IntFieldValue::make(0));
        static_cast<AssignFieldPathUpdate&>(*assignUpdate).setRemoveIfZero(true);
        docUp.addFieldPathUpdate(std::move(assignUpdate));
        //doc->print(std::cerr, true, "");
        docUp.applyTo(*doc);
        //doc->print(std::cerr, true, "");
        {
            std::unique_ptr<WeightedSetFieldValue> strWset = doc->getAs<WeightedSetFieldValue>(doc->getField("strwset"));
            ASSERT_EQ(std::size_t(1), strWset->size());
            EXPECT_EQ(243, strWset->get(StringFieldValue("but i say hello")));
        }
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyAddMultiList)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::george:costanza"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    auto adds = std::make_unique<ArrayFieldValue>(doc->getType().getField("strarray").getDataType());
    adds->add(StringFieldValue("serenity now"));
    adds->add(StringFieldValue("a festivus for the rest of us"));
    adds->add(StringFieldValue("george is getting upset!"));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AddFieldPathUpdate>(*doc->getDataType(), "strarray", std::string(), std::move(adds)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");
    EXPECT_TRUE(doc->hasValue("strarray"));
}

TEST_F(FieldPathUpdateTestCase, testAddAndAssignList)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::fancy:pants"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("hello hello"));
        strArray.add(StringFieldValue("blah blargh"));
        doc->setValue("strarray", strArray);
        EXPECT_TRUE(doc->hasValue("strarray"));
    }

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(),
                                      "strarray[1]", std::string(), StringFieldValue::make("assigned val 1")));

    auto adds = std::make_unique<ArrayFieldValue>(doc->getType().getField("strarray").getDataType());
    adds->add(StringFieldValue("new value"));

    docUp.addFieldPathUpdate(std::make_unique<AddFieldPathUpdate>(*doc->getDataType(), "strarray", std::string(), std::move(adds)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");
    {
        std::unique_ptr<ArrayFieldValue> strArray = doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        ASSERT_EQ(std::size_t(3), strArray->size());
        EXPECT_EQ(vespalib::string("hello hello"), (*strArray)[0].getAsString());
        EXPECT_EQ(vespalib::string("assigned val 1"), (*strArray)[1].getAsString());
        EXPECT_EQ(vespalib::string("new value"), (*strArray)[2].getAsString());
    }
}

namespace {
struct Keys {
    vespalib::string key1;
    vespalib::string key2;
    vespalib::string key3;
    Keys();
    ~Keys();
};

Keys::Keys() : key1("foo"), key2("bar"), key3("zoo") {}
Keys::~Keys() {}

struct Fixture {
    const DocumentType * _doc_type;
    Document::UP doc;
    MapFieldValue mfv;
    StructFieldValue fv1, fv2, fv3;

    static const MapDataType &
    getMapType(const DocumentType &doc_type) {
        return static_cast<const MapDataType &>(doc_type.getField("structmap").getDataType());
    }

    std::unique_ptr<FieldValue> fv4() const {
        auto sval = std::make_unique<StructFieldValue>(getMapType(*_doc_type).getValueType());
        sval->setValue("title", StringFieldValue("farnsworth"));
        sval->setValue("rating", IntFieldValue(48));
        return sval;
    }
    ~Fixture();
    Fixture(const DocumentTypeRepo &repo, const DocumentType &doc_type, const Keys &k);
};

Fixture::~Fixture() = default;
Fixture::Fixture(const DocumentTypeRepo &repo, const DocumentType &doc_type, const Keys &k)
    : _doc_type(&doc_type),
      doc(new Document(repo, doc_type, DocumentId("id:ns:" + doc_type.getName() + "::planet:express"))),
      mfv(getMapType(doc_type)),
      fv1(getMapType(doc_type).getValueType()),
      fv2(getMapType(doc_type).getValueType()),
      fv3(getMapType(doc_type).getValueType())
{
    fv1.setValue("title", StringFieldValue("fry"));
    fv1.setValue("rating", IntFieldValue(30));
    mfv.put(StringFieldValue(k.key1), fv1);

    fv2.setValue("title", StringFieldValue("farnsworth"));
    fv2.setValue("rating", IntFieldValue(60));
    mfv.put(StringFieldValue(k.key2), fv2);

    fv3.setValue("title", StringFieldValue("zoidberg"));
    fv3.setValue("rating", IntFieldValue(-20));
    mfv.put(StringFieldValue(k.key3), fv3);

    doc->setValue("structmap", mfv);
}

}  // namespace

TEST_F(FieldPathUpdateTestCase, testAssignMap)
{
    Keys k;
    Fixture f(*_repo, *_foobar_type, k);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*f.doc->getDataType(), "structmap{" + k.key2 + "}", std::string(), f.fv4()));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1), *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(*f.fv4(), *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3), *valueNow->get(StringFieldValue(k.key3)));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapStruct)
{
    Keys k;
    Fixture f(*_repo, *_foobar_type, k);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*f.doc->getDataType(), "structmap{" + k.key2 + "}.rating",
                                      std::string(), IntFieldValue::make(48)));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1), *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(*f.fv4(), *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3), *valueNow->get(StringFieldValue(k.key3)));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapStructVariable)
{
    Keys k;
    Fixture f(*_repo, *_foobar_type, k);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*f.doc->getDataType(), "structmap{$x}.rating",
                                      "foobar.structmap{$x}.title == \"farnsworth\"", IntFieldValue::make(48)));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1), *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(*f.fv4(), *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3), *valueNow->get(StringFieldValue(k.key3)));
}

std::unique_ptr<FieldValue>
createFry(const DataType & type) {
    auto fv = std::make_unique<StructFieldValue>(type);
    fv->setValue("title", StringFieldValue("fry"));
    fv->setValue("rating", IntFieldValue(30));
    return fv;
}
TEST_F(FieldPathUpdateTestCase, testAssignMapNoExist)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::planet:express"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{foo}", std::string(),
                                                                     createFry(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");

    std::unique_ptr<MapFieldValue> valueNow = doc->getAs<MapFieldValue>(doc->getField("structmap"));
    ASSERT_EQ(std::size_t(1), valueNow->size());
    EXPECT_EQ(*createFry(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType()), *valueNow->get(StringFieldValue("foo")));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapNoExistNoCreate)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::planet:express"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    auto assignUpdate = std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{foo}", std::string(),
                                                                createFry(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType()));
    static_cast<AssignFieldPathUpdate&>(*assignUpdate).setCreateMissingPath(false);
    docUp.addFieldPathUpdate(std::move(assignUpdate));

    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");

    std::unique_ptr<MapFieldValue> valueNow = doc->getAs<MapFieldValue>(doc->getField("structmap"));
    EXPECT_TRUE(valueNow.get() == 0);
}

TEST_F(FieldPathUpdateTestCase, testQuotedStringKey)
{
    Keys k;
    k.key2 = "here is a \"fancy\" 'map' :-} key :-{";
    const char field_path[] = "structmap{\"here is a \\\"fancy\\\" 'map' :-} key :-{\"}";
    Fixture f(*_repo, *_foobar_type, k);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*f.doc->getDataType(), field_path, std::string(), f.fv4()));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1), *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(*f.fv4(), *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3), *valueNow->get(StringFieldValue(k.key3)));
}

namespace {
std::unique_ptr<FieldValue>
createTastyCake(const DataType &type) {
    auto fv = std::make_unique<StructFieldValue>(type);
    fv->setValue("title", StringFieldValue("tasty cake"));
    fv->setValue("rating", IntFieldValue(95));
    return fv;
}
}
TEST_F(FieldPathUpdateTestCase, testEqualityComparison)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::foo:zoo"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    {
        DocumentUpdate docUp1(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        DocumentUpdate docUp2(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        EXPECT_TRUE(docUp1 == docUp2);

        docUp1.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{here be dragons}", std::string(),
                                                                          createTastyCake(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
        EXPECT_TRUE(docUp1 != docUp2);
        docUp2.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{here be dragons}", std::string(),
                                                                          createTastyCake(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
        EXPECT_TRUE(docUp1 == docUp2);
    }
    {
        DocumentUpdate docUp1(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        DocumentUpdate docUp2(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        // where-clause diff
        docUp1.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{here be dragons}", std::string(),
                                                                          createTastyCake(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
        docUp2.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{here be dragons}", "false",
                                                                          createTastyCake(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
        EXPECT_TRUE(docUp1 != docUp2);
    }
    {
        DocumentUpdate docUp1(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        DocumentUpdate docUp2(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        // fieldpath diff

        docUp1.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(),"structmap{here be dragons}", std::string(),
                                                                          createTastyCake(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
        docUp2.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{here be kittens}", std::string(),
                                                                          createTastyCake(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType())));
        EXPECT_TRUE(docUp1 != docUp2);
    }

}

TEST_F(FieldPathUpdateTestCase, testAffectsDocumentBody)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::things:stuff"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    auto fv4 = std::make_unique<StructFieldValue>(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv4->setValue("title", StringFieldValue("scruffy"));
    fv4->setValue("rating", IntFieldValue(90));

    // structmap is body field
    {
        DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

        auto update1 = std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{janitor}", std::string(), std::move(fv4));
        static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
        docUp.addFieldPathUpdate(std::move(update1));
    }

    // strfoo is header field
    {
        DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        auto update1 = std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strfoo", std::string(), StringFieldValue::make("helloworld"));
        static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
        docUp.addFieldPathUpdate(std::move(update1));
    }

}

TEST_F(FieldPathUpdateTestCase, testIncompatibleDataTypeFails)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::things:stuff"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    try {
        auto update1 = std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{foo}", std::string(), StringFieldValue::make("bad things"));
        EXPECT_TRUE(false);
    } catch (const vespalib::IllegalArgumentException& e) {
        // OK
    }
}

TEST_F(FieldPathUpdateTestCase, testSerializeAssign)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::weloveto:serializestuff"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    auto val = std::make_unique<StructFieldValue>(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    val->setValue("title", StringFieldValue("cool frog"));
    val->setValue("rating", IntFieldValue(100));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    auto update1 = std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "structmap{ribbit}", "true", std::move(val));
    static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
    docUp.addFieldPathUpdate(std::move(update1));

    testSerialize(*_repo, docUp);
}

TEST_F(FieldPathUpdateTestCase, testSerializeAdd)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::george:costanza"));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    auto adds = std::make_unique<ArrayFieldValue>(doc->getType().getField("strarray").getDataType());
    adds->add(StringFieldValue("serenity now"));
    adds->add(StringFieldValue("a festivus for the rest of us"));
    adds->add(StringFieldValue("george is getting upset!"));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    docUp.addFieldPathUpdate(std::make_unique<AddFieldPathUpdate>(*doc->getDataType(), "strarray", std::string(), std::move(adds)));

    testSerialize(*_repo, docUp);
}

TEST_F(FieldPathUpdateTestCase, testSerializeRemove)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::weloveto:serializestuff"));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    docUp.addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("structmap{ribbit}", std::string()));

    testSerialize(*_repo, docUp);
}

TEST_F(FieldPathUpdateTestCase, testSerializeAssignMath)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id:ns:foobar::bat:man"));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(34));

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>("num", "", "($value * 2) / $value"));
    testSerialize(*_repo, docUp);
}

DocumentUpdate::UP
FieldPathUpdateTestCase::createDocumentUpdateForSerialization(const DocumentTypeRepo& repo)
{
    const DocumentType *docType(repo.getDocumentType("serializetest"));
    auto docUp = std::make_unique<DocumentUpdate>(repo, *docType, DocumentId("id:ns:serializetest::xlanguage"));

    auto assign = std::make_unique<AssignFieldPathUpdate>("intfield", "", "3");
    static_cast<AssignFieldPathUpdate&>(*assign).setRemoveIfZero(true);
    static_cast<AssignFieldPathUpdate&>(*assign).setCreateMissingPath(false);
    docUp->addFieldPathUpdate(std::move(assign));

    auto fArray = std::make_unique<ArrayFieldValue>(docType->getField("arrayoffloatfield").getDataType());
    fArray->add(FloatFieldValue(12.0));
    fArray->add(FloatFieldValue(5.0));

    docUp->addFieldPathUpdate(std::make_unique<AddFieldPathUpdate>(*docType, "arrayoffloatfield", "", std::move(fArray)));
    docUp->addFieldPathUpdate(std::make_unique<RemoveFieldPathUpdate>("intfield", "serializetest.intfield > 0"));

    return docUp;
}

TEST_F(FieldPathUpdateTestCase, testReadSerializedFile)
{
    // Reads a file serialized from java
    const std::string cfg_file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(cfg_file_name));

    int fd = open(TEST_PATH("data/serialize-fieldpathupdate-java.dat").c_str(), O_RDONLY);

    int len = lseek(fd,0,SEEK_END);
    vespalib::alloc::Alloc buf = vespalib::alloc::Alloc::alloc(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.get(), len) != len) {
        throw vespalib::Exception("read failed");
    }
    close(fd);

    DocumentUpdate::UP updp(DocumentUpdate::createHEAD(repo, nbostream(std::move(buf), len)));
    DocumentUpdate& upd(*updp);

    DocumentUpdate::UP compare(createDocumentUpdateForSerialization(repo));
    EXPECT_EQ(*compare, upd);
}

TEST_F(FieldPathUpdateTestCase, testGenerateSerializedFile)
{
    const std::string cfg_file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(cfg_file_name));
    // Tests nothing, only generates a file for java test
    DocumentUpdate::UP upd(createDocumentUpdateForSerialization(repo));

    nbostream buf(serializeHEAD(*upd));

    int fd = open(TEST_PATH("data/serialize-fieldpathupdate-cpp.dat").c_str(),
                  O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, buf.data(), buf.size()) != (ssize_t)buf.size()) {
    	throw vespalib::Exception("write failed");
    }
    close(fd);
}

TEST_F(FieldPathUpdateTestCase, array_element_update_for_invalid_index_is_ignored)
{
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, DocumentId("id::foobar::1"));
    auto& field = doc->getType().getField("strarray");

    ArrayFieldValue str_array(field.getDataType());
    str_array.add(StringFieldValue("jerry"));
    doc->setValue("strarray", str_array);

    DocumentUpdate docUp(*_repo, *_foobar_type, DocumentId("id::foobar::1"));
    docUp.addFieldPathUpdate(std::make_unique<AssignFieldPathUpdate>(*doc->getDataType(), "strarray[1]", "", StringFieldValue::make("george")));
    docUp.applyTo(*doc);

    // Doc is unmodified.
    auto new_arr = doc->getAs<ArrayFieldValue>(field);
    EXPECT_EQ(str_array, *new_arr);
}

TEST_F(FieldPathUpdateTestCase, update_can_have_removes_for_both_existent_and_nonexistent_keys) {
    DocumentId doc_id("id:ns:foobar::george:costanza");
    auto doc = std::make_unique<Document>(*_repo, *_foobar_type, doc_id);
    auto& map_type = dynamic_cast<const MapDataType&>(doc->getType().getField("structmap").getDataType());
    auto& struct_type = map_type.getValueType();
    MapFieldValue mfv(map_type);

    StructFieldValue mystruct(struct_type);
    mystruct.setValue("title", StringFieldValue("sharknado in space, part deux"));
    mystruct.setValue("rating", IntFieldValue(90));
    mfv.put(StringFieldValue("coolmovie"), mystruct);
    doc->setValue("structmap", mfv);

    DocumentUpdate update(*_repo, *_foobar_type, doc_id);
    auto update1 = std::make_unique<RemoveFieldPathUpdate>("structmap{coolmovie}", "");
    auto update2 = std::make_unique<RemoveFieldPathUpdate>("structmap{no such key}", "");
    update.addFieldPathUpdate(std::move(update1));
    update.addFieldPathUpdate(std::move(update2));
    update.applyTo(*doc);

    auto new_value = doc->getValue("structmap");
    auto& map_value = dynamic_cast<MapFieldValue&>(*new_value);
    EXPECT_EQ(map_value.size(), 0);
}

}
