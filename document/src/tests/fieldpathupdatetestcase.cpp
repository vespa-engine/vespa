// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/select/node.h>
#include <vespa/document/base/exceptions.h>

#include <vespa/vespalib/io/fileutil.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/update/documentupdate.h>

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
    DocumentType _foobar_type;
    ~FieldPathUpdateTestCase();

    void SetUp() override;
    void TearDown() override;

    DocumentUpdate::UP
    createDocumentUpdateForSerialization(const DocumentTypeRepo& repo);
};

namespace {

document::DocumenttypesConfig getRepoConfig() {
    const int struct2_id = 64;
    DocumenttypesConfigBuilderHelper builder;
    builder.document(
            42, "test",
            Struct("test.header")
            .addField("primitive1", DataType::T_INT)
            .addField("l1s1", Struct("struct3")
                      .addField("primitive1", DataType::T_INT)
                      .addField("ss", Struct("struct2")
                                .setId(struct2_id)
                                .addField("primitive1", DataType::T_INT)
                                .addField("primitive2", DataType::T_INT)
                                .addField("iarray", Array(DataType::T_INT))
                                .addField("sarray", Array(
                                                Struct("struct1")
                                                .addField("primitive1",
                                                        DataType::T_INT)
                                                .addField("primitive2",
                                                        DataType::T_INT)))
                                .addField("smap", Map(DataType::T_STRING,
                                                DataType::T_STRING)))
                      .addField("structmap",
                                Map(DataType::T_STRING, struct2_id))
                      .addField("wset", Wset(DataType::T_STRING))
                      .addField("structwset", Wset(struct2_id))),
            Struct("test.body"));
    return builder.config();
}

Document::UP
createTestDocument(const DocumentTypeRepo &repo)
{
    const DocumentType* type(repo.getDocumentType("test"));
    const DataType* struct3(repo.getDataType(*type, "struct3"));
    const DataType* struct2(repo.getDataType(*type, "struct2"));
    const DataType* iarr(repo.getDataType(*type, "Array<Int>"));
    const DataType* sarr(repo.getDataType(*type, "Array<struct1>"));
    const DataType* struct1(repo.getDataType(*type, "struct1"));
    const DataType* smap(repo.getDataType(*type, "Map<String,String>"));
    const DataType* structmap(repo.getDataType(*type, "Map<String,struct2>"));
    const DataType* wset(repo.getDataType(*type, "WeightedSet<String>"));
    const DataType* structwset(repo.getDataType(*type, "WeightedSet<struct2>"));
    Document::UP doc(new Document(*type, DocumentId("id:ns:test::1")));
    doc->setRepo(repo);
    doc->setValue("primitive1", IntFieldValue(1));
    StructFieldValue l1s1(*struct3);
    l1s1.setValue("primitive1", IntFieldValue(2));

    StructFieldValue l2s1(*struct2);
    l2s1.setValue("primitive1", IntFieldValue(3));
    l2s1.setValue("primitive2", IntFieldValue(4));
    StructFieldValue l2s2(*struct2);
    l2s2.setValue("primitive1", IntFieldValue(5));
    l2s2.setValue("primitive2", IntFieldValue(6));
    ArrayFieldValue iarr1(*iarr);
    iarr1.add(IntFieldValue(11));
    iarr1.add(IntFieldValue(12));
    iarr1.add(IntFieldValue(13));
    ArrayFieldValue sarr1(*sarr);
    StructFieldValue l3s1(*struct1);
    l3s1.setValue("primitive1", IntFieldValue(1));
    l3s1.setValue("primitive2", IntFieldValue(2));
    sarr1.add(l3s1);
    sarr1.add(l3s1);
    MapFieldValue smap1(*smap);
    smap1.put(StringFieldValue("leonardo"), StringFieldValue("dicaprio"));
    smap1.put(StringFieldValue("ellen"), StringFieldValue("page"));
    smap1.put(StringFieldValue("joseph"), StringFieldValue("gordon-levitt"));
    l2s1.setValue("smap", smap1);
    l2s1.setValue("iarray", iarr1);
    l2s1.setValue("sarray", sarr1);

    l1s1.setValue("ss", l2s1);
    MapFieldValue structmap1(*structmap);
    structmap1.put(StringFieldValue("test"), l2s1);
    l1s1.setValue("structmap", structmap1);

    WeightedSetFieldValue wset1(*wset);
    wset1.add("foo");
    wset1.add("bar");
    wset1.add("zoo");
    l1s1.setValue("wset", wset1);

    WeightedSetFieldValue wset2(*structwset);
    wset2.add(l2s1);
    wset2.add(l2s2);
    l1s1.setValue("structwset", wset2);

    doc->setValue("l1s1", l1s1);
    return doc;
}

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
            const FieldPathUpdate::CP& ua = a.getFieldPathUpdates()[i];
            const FieldPathUpdate::CP& ub = b->getFieldPathUpdates()[i];

            EXPECT_EQ(*ua, *ub);
        }
        EXPECT_EQ(a, *b);
    } catch (std::exception& e) {
        std::cerr << "Failed while testing document field path update:\n"
                  << a.toString(true) << "\n";
        throw;
    }
}

} // anon ns

struct TestFieldPathUpdate : FieldPathUpdate
{
    struct TestIteratorHandler : fieldvalue::IteratorHandler
    {
        TestIteratorHandler(std::string& str)
            : _str(str) {}

        ModificationStatus doModify(FieldValue& value) override
        {
            std::ostringstream ss;
            value.print(ss, false, "");
            if (!_str.empty()) {
                _str += ';';
            }
            _str += ss.str();
            return ModificationStatus::NOT_MODIFIED;
        }

        bool onComplex(const Content&) override { return false; }

        std::string& _str;
    };

    mutable std::string _str;

    ~TestFieldPathUpdate();
    TestFieldPathUpdate(const std::string& fieldPath, const std::string& whereClause);

    TestFieldPathUpdate(const TestFieldPathUpdate& other);

    std::unique_ptr<IteratorHandler> getIteratorHandler(Document&, const DocumentTypeRepo &) const override {
        return std::unique_ptr<IteratorHandler>(new TestIteratorHandler(_str));
    }

    TestFieldPathUpdate* clone() const override { return new TestFieldPathUpdate(*this); }

    void print(std::ostream& out, bool, const std::string&) const override {
        out << "TestFieldPathUpdate()";
    }

    void accept(UpdateVisitor & visitor) const override { (void) visitor; }
    uint8_t getSerializedType() const override { assert(false); return 7; }
};

TestFieldPathUpdate::~TestFieldPathUpdate() { }
TestFieldPathUpdate::TestFieldPathUpdate(const std::string& fieldPath, const std::string& whereClause)
    : FieldPathUpdate(fieldPath, whereClause)
{
}

TestFieldPathUpdate::TestFieldPathUpdate(const TestFieldPathUpdate& other)
    : FieldPathUpdate(other)
{
}

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

    _foobar_type = *_repo->getDocumentType("foobar");
}

void
FieldPathUpdateTestCase::TearDown()
{
}

TEST_F(FieldPathUpdateTestCase, testWhereClause)
{
    DocumentTypeRepo repo(getRepoConfig());
    Document::UP doc(createTestDocument(repo));
    std::string where = "test.l1s1.structmap.value.smap{$x} == \"dicaprio\"";
    TestFieldPathUpdate update("l1s1.structmap.value.smap{$x}", where);
    update.applyTo(*doc);
    EXPECT_EQ(std::string("dicaprio"), update._str);
}

TEST_F(FieldPathUpdateTestCase, testBrokenWhereClause)
{
    DocumentTypeRepo repo(getRepoConfig());
    Document::UP doc(createTestDocument(repo));
    std::string where = "l1s1.structmap.value.smap{$x} == \"dicaprio\"";
    TestFieldPathUpdate update("l1s1.structmap.value.smap{$x}", where);
    update.applyTo(*doc);
    EXPECT_EQ(std::string(""), update._str);
}

TEST_F(FieldPathUpdateTestCase, testNoIterateMapValues)
{
    DocumentTypeRepo repo(getRepoConfig());
    Document::UP doc(createTestDocument(repo));
    TestFieldPathUpdate update("l1s1.structwset.primitive1", "true");
    update.applyTo(*doc);
    EXPECT_EQ(std::string("3;5"), update._str);
}

TEST_F(FieldPathUpdateTestCase, testRemoveField)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::things:thangs")));
    EXPECT_TRUE(doc->hasValue("strfoo") == false);
    doc->setValue("strfoo", StringFieldValue("cocacola"));
    EXPECT_EQ(vespalib::string("cocacola"), doc->getValue("strfoo")->getAsString());
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("strfoo")));
    docUp.applyTo(*doc);
    EXPECT_TRUE(doc->hasValue("strfoo") == false);
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveMultiList)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::things:thangs")));
    doc->setRepo(*_repo);
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
    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new RemoveFieldPathUpdate("strarray[$x]", "foobar.strarray[$x] == \"remove val 1\"")));
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
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::things:thangs")));
    doc->setRepo(*_repo);
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
    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new RemoveFieldPathUpdate("strarray[$x]", "foobar.strarray[$x] == \"remove val 1\"")));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray = doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        ASSERT_EQ(std::size_t(1), strArray->size());
        EXPECT_EQ(vespalib::string("hello hello"), (*strArray)[0].getAsString());
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveEntireListField)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::things:thangs")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);
    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("this list"));
        strArray.add(StringFieldValue("should be"));
        strArray.add(StringFieldValue("totally removed"));
        doc->setValue("strarray", strArray);
    }
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("strarray", "")));
    docUp.applyTo(*doc);
    EXPECT_TRUE(!doc->hasValue("strarray"));
}

TEST_F(FieldPathUpdateTestCase, testApplyRemoveMultiWset)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::helan:halvan")));
    EXPECT_TRUE(doc->hasValue("strwset") == false);
    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("hello hello"), 10);
        strWset.add(StringFieldValue("remove val 1"), 20);
        doc->setValue("strwset", strWset);
    }
    EXPECT_TRUE(doc->hasValue("strwset"));
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("strwset{remove val 1}")));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<WeightedSetFieldValue> strWset = doc->getAs<WeightedSetFieldValue>(doc->getField("strwset"));
        ASSERT_EQ(std::size_t(1), strWset->size());
        EXPECT_EQ(10, strWset->get(StringFieldValue("hello hello")));
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignSingle)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::drekka:karsk")));
    EXPECT_TRUE(doc->hasValue("strfoo") == false);
    // Test assignment of non-existing
    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strfoo", std::string(), StringFieldValue("himert"))));
    docUp.applyTo(*doc);
    EXPECT_TRUE(doc->hasValue("strfoo"));
    EXPECT_EQ(vespalib::string("himert"), doc->getValue("strfoo")->getAsString());
    // Test overwriting existing
    DocumentUpdate docUp2(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp2.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strfoo", std::string(), StringFieldValue("wunderbaum"))));
    docUp2.applyTo(*doc);
    EXPECT_EQ(vespalib::string("wunderbaum"), doc->getValue("strfoo")->getAsString());
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMath)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    doc->setValue("num", IntFieldValue(34));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "($value * 2) / $value")));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(2)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathByteToZero)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    doc->setValue("byteval", ByteFieldValue(3));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("byteval", "", "$value - 3")));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(ByteFieldValue(0)), *doc->getValue("byteval"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathNotModifiedOnUnderflow)
{
    int low_value = -126;
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    doc->setValue("byteval", ByteFieldValue(low_value));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("byteval", "", "$value - 4")));
    docUp.applyTo(*doc);
    // Over/underflow will happen. You must have control of your data types.
    EXPECT_EQ(static_cast<const FieldValue&>(ByteFieldValue((char)(low_value - 4))), *doc->getValue("byteval"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathNotModifiedOnOverflow)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    doc->setValue("byteval", ByteFieldValue(127));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("byteval", "", "$value + 200")));
    docUp.applyTo(*doc);
    // Over/underflow will happen. You must have control of your data types.
    EXPECT_EQ(static_cast<const FieldValue&>(ByteFieldValue(static_cast<char>(static_cast<int>(127+200)))), *doc->getValue("byteval"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMathDivZero)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(10));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "$value / ($value - 10)")));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(10)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignFieldNotExistingInExpression)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    doc->setRepo(*_repo);
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(10));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "foobar.num2 + $value")));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(10)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignFieldNotExistingInPath)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    doc->setRepo(*_repo);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    try {
        docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("nosuchnum", "", "foobar.num + $value")));
        docUp.applyTo(*doc);
        EXPECT_TRUE(false);
    } catch (const FieldNotFoundException&) {
    }
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignTargetNotExisting)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    EXPECT_TRUE(doc->hasValue("num") == false);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "$value + 5")));
    docUp.applyTo(*doc);
    EXPECT_EQ(static_cast<const FieldValue&>(IntFieldValue(5)), *doc->getValue("num"));
}

TEST_F(FieldPathUpdateTestCase, testAssignSimpleMapValueWithVariable)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bug:hunter")));
    doc->setRepo(*_repo);

    MapFieldValue mfv(doc->getType().getField("strmap").getDataType());
    mfv.put(StringFieldValue("foo"), StringFieldValue("bar"));
    mfv.put(StringFieldValue("baz"), StringFieldValue("bananas"));
    doc->setValue("strmap", mfv);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    // Select on value, not key
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(),
                                      "strmap{$x}", "foobar.strmap{$x} == \"bar\"", StringFieldValue("shinyvalue"))));
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
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(34));
    EXPECT_TRUE(doc->hasValue("num") == true);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    FieldPathUpdate::CP up1(new AssignFieldPathUpdate("num", "", "($value * 2) / $value - 2"));
    static_cast<AssignFieldPathUpdate&>(*up1).setRemoveIfZero(true);
    docUp.addFieldPathUpdate(up1);

    docUp.applyTo(*doc);
    EXPECT_TRUE(doc->hasValue("num") == false);
}

TEST_F(FieldPathUpdateTestCase, testApplyAssignMultiList)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::fest:skinnvest")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("hello hello"));
        strArray.add(StringFieldValue("blah blargh"));
        doc->setValue("strarray", strArray);
        EXPECT_TRUE(doc->hasValue("strarray"));
    }

    ArrayFieldValue updateArray(doc->getType().getField("strarray").getDataType());
    updateArray.add(StringFieldValue("assigned val 0"));
    updateArray.add(StringFieldValue("assigned val 1"));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strarray", std::string(), updateArray)));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray =
            doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        ASSERT_EQ(std::size_t(2), strArray->size());
        EXPECT_EQ(vespalib::string("assigned val 0"), (*strArray)[0].getAsString());
        EXPECT_EQ(vespalib::string("assigned val 1"), (*strArray)[1].getAsString());
    }
}


TEST_F(FieldPathUpdateTestCase, testApplyAssignMultiWset)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::fest:skinnvest")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("hello gentlemen"), 10);
        strWset.add(StringFieldValue("what you say"), 20);
        doc->setValue("strwset", strWset);
        EXPECT_TRUE(doc->hasValue("strwset"));
    }

    WeightedSetFieldValue assignWset(doc->getType().getField("strwset").getDataType());
    assignWset.add(StringFieldValue("assigned val 0"), 5);
    assignWset.add(StringFieldValue("assigned val 1"), 10);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strwset", std::string(), assignWset)));
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
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::tronder:bataljon")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("you say goodbye"), 164);
        strWset.add(StringFieldValue("but i say hello"), 243);
        doc->setValue("strwset", strWset);
        EXPECT_TRUE(doc->hasValue("strwset"));
    }

    {
        DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        IntFieldValue zeroWeight(0);
        FieldPathUpdate::CP assignUpdate(
                new AssignFieldPathUpdate(*doc->getDataType(), "strwset{you say goodbye}", std::string(), zeroWeight));
        static_cast<AssignFieldPathUpdate&>(*assignUpdate).setRemoveIfZero(true);
        docUp.addFieldPathUpdate(assignUpdate);
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
   Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::george:costanza")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    ArrayFieldValue adds(doc->getType().getField("strarray").getDataType());
    adds.add(StringFieldValue("serenity now"));
    adds.add(StringFieldValue("a festivus for the rest of us"));
    adds.add(StringFieldValue("george is getting upset!"));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AddFieldPathUpdate(*doc->getDataType(), "strarray", std::string(), adds)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");
    EXPECT_TRUE(doc->hasValue("strarray"));
}

TEST_F(FieldPathUpdateTestCase, testAddAndAssignList)
{
   Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::fancy:pants")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("hello hello"));
        strArray.add(StringFieldValue("blah blargh"));
        doc->setValue("strarray", strArray);
        EXPECT_TRUE(doc->hasValue("strarray"));
    }

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(),
                                      "strarray[1]", std::string(), StringFieldValue("assigned val 1"))));

    ArrayFieldValue adds(doc->getType().getField("strarray").getDataType());
    adds.add(StringFieldValue("new value"));

    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AddFieldPathUpdate(*doc->getDataType(), "strarray",
                                                                        std::string(), adds)));
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
    Document::UP doc;
    MapFieldValue mfv;
    StructFieldValue fv1, fv2, fv3, fv4;

    const MapDataType &getMapType(const DocumentType &doc_type) {
        return static_cast<const MapDataType &>(doc_type.getField("structmap").getDataType());
    }

    ~Fixture();
    Fixture(const DocumentType &doc_type, const Keys &k);
};

Fixture::~Fixture() = default;
Fixture::Fixture(const DocumentType &doc_type, const Keys &k)
    : doc(new Document(doc_type, DocumentId("id:ns:" + doc_type.getName() + "::planet:express"))),
      mfv(getMapType(doc_type)),
      fv1(getMapType(doc_type).getValueType()),
      fv2(getMapType(doc_type).getValueType()),
      fv3(getMapType(doc_type).getValueType()),
      fv4(getMapType(doc_type).getValueType())
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

    fv4.setValue("title", StringFieldValue("farnsworth"));
    fv4.setValue("rating", IntFieldValue(48));
}

}  // namespace

TEST_F(FieldPathUpdateTestCase, testAssignMap)
{
    Keys k;
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), "structmap{" + k.key2 + "}", std::string(), f.fv4)));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapStruct)
{
    Keys k;
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), "structmap{" + k.key2 + "}.rating",
                                      std::string(), IntFieldValue(48))));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapStructVariable)
{
    Keys k;
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), "structmap{$x}.rating",
                                      "foobar.structmap{$x}.title == \"farnsworth\"", IntFieldValue(48))));
    f.doc->setRepo(*_repo);
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapNoExist)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::planet:express")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv1(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv1.setValue("title", StringFieldValue("fry"));
    fv1.setValue("rating", IntFieldValue(30));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "structmap{foo}", std::string(), fv1)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");

    std::unique_ptr<MapFieldValue> valueNow =
        doc->getAs<MapFieldValue>(doc->getField("structmap"));
    ASSERT_EQ(std::size_t(1), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(fv1), *valueNow->get(StringFieldValue("foo")));
}

TEST_F(FieldPathUpdateTestCase, testAssignMapNoExistNoCreate)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::planet:express")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv1(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv1.setValue("title", StringFieldValue("fry"));
    fv1.setValue("rating", IntFieldValue(30));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    FieldPathUpdate::CP assignUpdate(
            new AssignFieldPathUpdate(*doc->getDataType(), "structmap{foo}", std::string(), fv1));
    static_cast<AssignFieldPathUpdate&>(*assignUpdate).setCreateMissingPath(false);
    docUp.addFieldPathUpdate(assignUpdate);

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
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), field_path, std::string(), f.fv4)));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    ASSERT_EQ(std::size_t(3), valueNow->size());
    EXPECT_EQ(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    EXPECT_EQ(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

TEST_F(FieldPathUpdateTestCase, testEqualityComparison)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::foo:zoo")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv4(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv4.setValue("title", StringFieldValue("tasty cake"));
    fv4.setValue("rating", IntFieldValue(95));

    {
        DocumentUpdate docUp1(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        DocumentUpdate docUp2(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        EXPECT_TRUE(docUp1 == docUp2);

        FieldPathUpdate::CP assignUp1(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", std::string(), fv4));
        docUp1.addFieldPathUpdate(assignUp1);
        EXPECT_TRUE(docUp1 != docUp2);
        docUp2.addFieldPathUpdate(assignUp1);
        EXPECT_TRUE(docUp1 == docUp2);
    }
    {
        DocumentUpdate docUp1(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        DocumentUpdate docUp2(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        // where-clause diff
        FieldPathUpdate::CP assignUp1(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", std::string(), fv4));
        FieldPathUpdate::CP assignUp2(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", "false", fv4));
        docUp1.addFieldPathUpdate(assignUp1);
        docUp2.addFieldPathUpdate(assignUp2);
        EXPECT_TRUE(docUp1 != docUp2);
    }
    {
        DocumentUpdate docUp1(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        DocumentUpdate docUp2(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        // fieldpath diff
        FieldPathUpdate::CP assignUp1(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", std::string(), fv4));
        FieldPathUpdate::CP assignUp2(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be kittens}", std::string(), fv4));
        docUp1.addFieldPathUpdate(assignUp1);
        docUp2.addFieldPathUpdate(assignUp2);
        EXPECT_TRUE(docUp1 != docUp2);
    }

}

TEST_F(FieldPathUpdateTestCase, testAffectsDocumentBody)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::things:stuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv4(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv4.setValue("title", StringFieldValue("scruffy"));
    fv4.setValue("rating", IntFieldValue(90));

    // structmap is body field
    {
        DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

        FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(),
                                                              "structmap{janitor}", std::string(), fv4));
        static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
        docUp.addFieldPathUpdate(update1);
    }

    // strfoo is header field
    {
        DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
        FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(),
                                            "strfoo", std::string(), StringFieldValue("helloworld")));
        static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
        docUp.addFieldPathUpdate(update1);
    }

}

TEST_F(FieldPathUpdateTestCase, testIncompatibleDataTypeFails)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::things:stuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    try {
        FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(), "structmap{foo}",
                                                              std::string(), StringFieldValue("bad things")));
        EXPECT_TRUE(false);
    } catch (const vespalib::IllegalArgumentException& e) {
        // OK
    }
}

TEST_F(FieldPathUpdateTestCase, testSerializeAssign)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::weloveto:serializestuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue val(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    val.setValue("title", StringFieldValue("cool frog"));
    val.setValue("rating", IntFieldValue(100));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(), "structmap{ribbit}", "true", val));
    static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
    docUp.addFieldPathUpdate(update1);

    testSerialize(*_repo, docUp);
}

TEST_F(FieldPathUpdateTestCase, testSerializeAdd)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::george:costanza")));
    EXPECT_TRUE(doc->hasValue("strarray") == false);

    ArrayFieldValue adds(doc->getType().getField("strarray").getDataType());
    adds.add(StringFieldValue("serenity now"));
    adds.add(StringFieldValue("a festivus for the rest of us"));
    adds.add(StringFieldValue("george is getting upset!"));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    FieldPathUpdate::CP update1(new AddFieldPathUpdate(*doc->getDataType(), "strarray", std::string(), adds));
    docUp.addFieldPathUpdate(update1);

    testSerialize(*_repo, docUp);
}

TEST_F(FieldPathUpdateTestCase, testSerializeRemove)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::weloveto:serializestuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));

    FieldPathUpdate::CP update1(new RemoveFieldPathUpdate("structmap{ribbit}", std::string()));
    docUp.addFieldPathUpdate(update1);

    testSerialize(*_repo, docUp);
}

TEST_F(FieldPathUpdateTestCase, testSerializeAssignMath)
{
    Document::UP doc(new Document(_foobar_type, DocumentId("id:ns:foobar::bat:man")));
    EXPECT_TRUE(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(34));

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id:ns:foobar::barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "($value * 2) / $value")));
    testSerialize(*_repo, docUp);
}

DocumentUpdate::UP
FieldPathUpdateTestCase::createDocumentUpdateForSerialization(const DocumentTypeRepo& repo)
{
    const DocumentType *docType(repo.getDocumentType("serializetest"));
    auto docUp = std::make_unique<DocumentUpdate>(repo, *docType, DocumentId("id:ns:serializetest::xlanguage"));

    FieldPathUpdate::CP assign(new AssignFieldPathUpdate("intfield", "", "3"));
    static_cast<AssignFieldPathUpdate&>(*assign).setRemoveIfZero(true);
    static_cast<AssignFieldPathUpdate&>(*assign).setCreateMissingPath(false);
    docUp->addFieldPathUpdate(assign);

    ArrayFieldValue fArray(docType->getField("arrayoffloatfield").getDataType());
    fArray.add(FloatFieldValue(12.0));
    fArray.add(FloatFieldValue(5.0));

    docUp->addFieldPathUpdate(FieldPathUpdate::CP(new AddFieldPathUpdate(*docType, "arrayoffloatfield", "", fArray)));
    docUp->addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("intfield", "serializetest.intfield > 0")));

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
    auto doc = std::make_unique<Document>(_foobar_type, DocumentId("id::foobar::1"));
    doc->setRepo(*_repo);
    auto& field = doc->getType().getField("strarray");

    ArrayFieldValue str_array(field.getDataType());
    str_array.add(StringFieldValue("jerry"));
    doc->setValue("strarray", str_array);

    DocumentUpdate docUp(*_repo, _foobar_type, DocumentId("id::foobar::1"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strarray[1]", "", StringFieldValue("george"))));
    docUp.applyTo(*doc);

    // Doc is unmodified.
    auto new_arr = doc->getAs<ArrayFieldValue>(field);
    EXPECT_EQ(str_array, *new_arr);
}

TEST_F(FieldPathUpdateTestCase, update_can_have_removes_for_both_existent_and_nonexistent_keys) {
    DocumentId doc_id("id:ns:foobar::george:costanza");
    auto doc = std::make_unique<Document>(_foobar_type, doc_id);
    auto& map_type = dynamic_cast<const MapDataType&>(doc->getType().getField("structmap").getDataType());
    auto& struct_type = map_type.getValueType();
    MapFieldValue mfv(map_type);

    StructFieldValue mystruct(struct_type);
    mystruct.setValue("title", StringFieldValue("sharknado in space, part deux"));
    mystruct.setValue("rating", IntFieldValue(90));
    mfv.put(StringFieldValue("coolmovie"), mystruct);
    doc->setValue("structmap", mfv);

    DocumentUpdate update(*_repo, _foobar_type, doc_id);
    auto update1 = std::make_unique<RemoveFieldPathUpdate>("structmap{coolmovie}", "");
    auto update2 = std::make_unique<RemoveFieldPathUpdate>("structmap{no such key}", "");
    update.addFieldPathUpdate(FieldPathUpdate::CP(std::move(update1)));
    update.addFieldPathUpdate(FieldPathUpdate::CP(std::move(update2)));
    update.applyTo(*doc);

    auto new_value = doc->getValue("structmap");
    auto& map_value = dynamic_cast<MapFieldValue&>(*new_value);
    EXPECT_EQ(map_value.size(), 0);
}

}
