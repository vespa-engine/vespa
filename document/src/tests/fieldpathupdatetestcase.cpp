// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/select/node.h>
#include <vespa/document/base/exceptions.h>

#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/update/fieldpathupdates.h>
#include <vespa/document/update/documentupdate.h>

#include <vespa/document/repo/configbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <fcntl.h>

using vespalib::Identifiable;
using namespace document::config_builder;

namespace document {

using namespace fieldvalue;

struct FieldPathUpdateTestCase : public CppUnit::TestFixture {
    std::shared_ptr<const DocumentTypeRepo> _repo;
    DocumentType _foobar_type;

    void setUp() override;
    void tearDown() override;

    void testWhereClause();
    void testNoIterateMapValues();
    void testRemoveField();
    void testApplyRemoveEntireListField();
    void testApplyRemoveMultiList();
    void testApplyRemoveMultiWset();
    void testApplyAssignSingle();
    void testApplyAssignMath();
    void testApplyAssignMathDivZero();
    void testApplyAssignMathByteToZero();
    void testApplyAssignMathNotModifiedOnUnderflow();
    void testApplyAssignMathNotModifiedOnOverflow();
    void testApplyAssignFieldNotExistingInExpression();
    void testApplyAssignFieldNotExistingInPath();
    void testApplyAssignTargetNotExisting();
    void testAssignSimpleMapValueWithVariable();
    void testApplyAssignMathRemoveIfZero();
    void testApplyAssignMultiList();
    void testApplyAssignMultiWset();
    void testAssignWsetRemoveIfZero();
    void testApplyAddMultiList();
    void testAddAndAssignList();
    void testAssignMap();
    void testAssignMapStruct();
    void testAssignMapStructVariable();
    void testAssignMapNoExist();
    void testAssignMapNoExistNoCreate();
    void testQuotedStringKey();
    void testEqualityComparison();
    void testAffectsDocumentBody();
    void testIncompatibleDataTypeFails();
    void testSerializeAssign();
    void testSerializeAdd();
    void testSerializeRemove();
    void testSerializeAssignMath();
    void testReadSerializedFile();
    void testGenerateSerializedFile();

    CPPUNIT_TEST_SUITE(FieldPathUpdateTestCase);
    CPPUNIT_TEST(testWhereClause);
    CPPUNIT_TEST(testNoIterateMapValues);
    CPPUNIT_TEST(testRemoveField);
    CPPUNIT_TEST(testApplyRemoveEntireListField);
    CPPUNIT_TEST(testApplyRemoveMultiList);
    CPPUNIT_TEST(testApplyRemoveMultiWset);
    CPPUNIT_TEST(testApplyAssignSingle);
    CPPUNIT_TEST(testApplyAssignMath);
    CPPUNIT_TEST(testApplyAssignMathDivZero);
    CPPUNIT_TEST(testApplyAssignMathByteToZero);
    CPPUNIT_TEST(testApplyAssignMathNotModifiedOnUnderflow);
    CPPUNIT_TEST(testApplyAssignMathNotModifiedOnOverflow);
    CPPUNIT_TEST(testApplyAssignFieldNotExistingInExpression);
    CPPUNIT_TEST(testApplyAssignFieldNotExistingInPath);
    CPPUNIT_TEST(testApplyAssignTargetNotExisting);
    CPPUNIT_TEST(testAssignSimpleMapValueWithVariable);
    CPPUNIT_TEST(testApplyAssignMathRemoveIfZero);
    CPPUNIT_TEST(testApplyAssignMultiList);
    CPPUNIT_TEST(testApplyAssignMultiWset);
    CPPUNIT_TEST(testAssignWsetRemoveIfZero);
    CPPUNIT_TEST(testApplyAddMultiList);
    CPPUNIT_TEST(testAddAndAssignList);
    CPPUNIT_TEST(testAssignMap);
    CPPUNIT_TEST(testAssignMapStruct);
    CPPUNIT_TEST(testAssignMapStructVariable);
    CPPUNIT_TEST(testAssignMapNoExist);
    CPPUNIT_TEST(testAssignMapNoExistNoCreate);
    CPPUNIT_TEST(testQuotedStringKey);
    CPPUNIT_TEST(testEqualityComparison);
    CPPUNIT_TEST(testAffectsDocumentBody);
    CPPUNIT_TEST(testIncompatibleDataTypeFails);
    CPPUNIT_TEST(testSerializeAssign);
    CPPUNIT_TEST(testSerializeAdd);
    CPPUNIT_TEST(testSerializeRemove);
    CPPUNIT_TEST(testSerializeAssignMath);
    CPPUNIT_TEST(testReadSerializedFile);
    CPPUNIT_TEST(testGenerateSerializedFile);
    CPPUNIT_TEST_SUITE_END();
private:
    DocumentUpdate::UP
    createDocumentUpdateForSerialization(const DocumentTypeRepo& repo);
};

CPPUNIT_TEST_SUITE_REGISTRATION(FieldPathUpdateTestCase);

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
    Document::UP doc(new Document(*type, DocumentId("doc::testdoc")));
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

ByteBuffer::UP serializeHEAD(const DocumentUpdate & update)
{
    vespalib::nbostream stream;
    VespaDocumentSerializer serializer(stream);
    serializer.writeHEAD(update);
    ByteBuffer::UP retVal(new ByteBuffer(stream.size()));
    retVal->putBytes(stream.peek(), stream.size());
    return retVal;
}

void testSerialize(const DocumentTypeRepo& repo, const DocumentUpdate& a) {
    try{
        bool affectsBody = a.affectsDocumentBody();
        ByteBuffer::UP bb(serializeHEAD(a));
        bb->flip();
        DocumentUpdate::UP b(DocumentUpdate::createHEAD(repo, *bb));

        CPPUNIT_ASSERT_EQUAL(affectsBody, b->affectsDocumentBody());
        CPPUNIT_ASSERT_EQUAL(size_t(0), bb->getRemaining());
        CPPUNIT_ASSERT_EQUAL(a.getId().toString(), b->getId().toString());
        CPPUNIT_ASSERT_EQUAL(a.getUpdates().size(), b->getUpdates().size());
        for (size_t i(0); i < a.getUpdates().size(); i++) {
            const FieldUpdate & ua = a.getUpdates()[i];
            const FieldUpdate & ub = b->getUpdates()[i];

            CPPUNIT_ASSERT_EQUAL(&ua.getField(), &ub.getField());
            CPPUNIT_ASSERT_EQUAL(ua.getUpdates().size(),
                                 ub.getUpdates().size());
            for (size_t j(0); j < ua.getUpdates().size(); j++) {
                CPPUNIT_ASSERT_EQUAL(ua.getUpdates()[j]->getType(),
                                     ub.getUpdates()[j]->getType());
            }
        }
        CPPUNIT_ASSERT_EQUAL(a.getFieldPathUpdates().size(), b->getFieldPathUpdates().size());
        for (size_t i(0); i < a.getFieldPathUpdates().size(); i++) {
            const FieldPathUpdate::CP& ua = a.getFieldPathUpdates()[i];
            const FieldPathUpdate::CP& ub = b->getFieldPathUpdates()[i];

            CPPUNIT_ASSERT_EQUAL(*ua, *ub);
        }
        CPPUNIT_ASSERT_EQUAL(a, *b);
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

void
FieldPathUpdateTestCase::setUp()
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
FieldPathUpdateTestCase::tearDown()
{
}

void
FieldPathUpdateTestCase::testWhereClause()
{
    DocumentTypeRepo repo(getRepoConfig());
    Document::UP doc(createTestDocument(repo));
    std::string where = "test.l1s1.structmap.value.smap{$x} == \"dicaprio\"";
    TestFieldPathUpdate update("l1s1.structmap.value.smap{$x}", where);
    update.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(std::string("dicaprio"), update._str);
}

void
FieldPathUpdateTestCase::testNoIterateMapValues()
{
    DocumentTypeRepo repo(getRepoConfig());
    Document::UP doc(createTestDocument(repo));
    TestFieldPathUpdate update("l1s1.structwset.primitive1", "true");
    update.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(std::string("3;5"), update._str);
}

void
FieldPathUpdateTestCase::testRemoveField()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:things:thangs")));
    CPPUNIT_ASSERT(doc->hasValue("strfoo") == false);
    doc->setValue("strfoo", StringFieldValue("cocacola"));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("cocacola"), doc->getValue("strfoo")->getAsString());
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("strfoo")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT(doc->hasValue("strfoo") == false);
}

void
FieldPathUpdateTestCase::testApplyRemoveMultiList()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:things:thangs")));
    doc->setRepo(*_repo);
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);
    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("crouching tiger, hidden field"));
        strArray.add(StringFieldValue("remove val 1"));
        strArray.add(StringFieldValue("hello hello"));
        doc->setValue("strarray", strArray);
    }
    CPPUNIT_ASSERT(doc->hasValue("strarray"));
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new RemoveFieldPathUpdate("strarray[$x]", "foobar.strarray[$x] == \"remove val 1\"")));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray = doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        CPPUNIT_ASSERT_EQUAL(std::size_t(2), strArray->size());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("crouching tiger, hidden field"), (*strArray)[0].getAsString());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("hello hello"), (*strArray)[1].getAsString());
    }
}

void
FieldPathUpdateTestCase::testApplyRemoveEntireListField()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:things:thangs")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);
    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("this list"));
        strArray.add(StringFieldValue("should be"));
        strArray.add(StringFieldValue("totally removed"));
        doc->setValue("strarray", strArray);
    }
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("strarray", "")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT(!doc->hasValue("strarray"));
}

void
FieldPathUpdateTestCase::testApplyRemoveMultiWset()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:helan:halvan")));
    CPPUNIT_ASSERT(doc->hasValue("strwset") == false);
    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("hello hello"), 10);
        strWset.add(StringFieldValue("remove val 1"), 20);
        doc->setValue("strwset", strWset);
    }
    CPPUNIT_ASSERT(doc->hasValue("strwset"));
    //doc->print(std::cerr, true, "");
    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new RemoveFieldPathUpdate("strwset{remove val 1}")));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<WeightedSetFieldValue> strWset = doc->getAs<WeightedSetFieldValue>(doc->getField("strwset"));
        CPPUNIT_ASSERT_EQUAL(std::size_t(1), strWset->size());
        CPPUNIT_ASSERT_EQUAL(10, strWset->get(StringFieldValue("hello hello")));
    }
}

void
FieldPathUpdateTestCase::testApplyAssignSingle()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:drekka:karsk")));
    CPPUNIT_ASSERT(doc->hasValue("strfoo") == false);
    // Test assignment of non-existing
    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strfoo", std::string(), StringFieldValue("himert"))));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT(doc->hasValue("strfoo"));
    CPPUNIT_ASSERT_EQUAL(vespalib::string("himert"), doc->getValue("strfoo")->getAsString());
    // Test overwriting existing
    DocumentUpdate docUp2(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp2.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strfoo", std::string(), StringFieldValue("wunderbaum"))));
    docUp2.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("wunderbaum"), doc->getValue("strfoo")->getAsString());
}

void
FieldPathUpdateTestCase::testApplyAssignMath()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    doc->setValue("num", IntFieldValue(34));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "($value * 2) / $value")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(IntFieldValue(2)), *doc->getValue("num"));
}

void
FieldPathUpdateTestCase::testApplyAssignMathByteToZero()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    doc->setValue("byteval", ByteFieldValue(3));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("byteval", "", "$value - 3")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(ByteFieldValue(0)), *doc->getValue("byteval"));
}

void
FieldPathUpdateTestCase::testApplyAssignMathNotModifiedOnUnderflow()
{
    int low_value = -126;
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    doc->setValue("byteval", ByteFieldValue(low_value));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("byteval", "", "$value - 4")));
    docUp.applyTo(*doc);
    // Over/underflow will happen. You must have control of your data types.
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(ByteFieldValue((char)(low_value - 4))), *doc->getValue("byteval"));
}

void
FieldPathUpdateTestCase::testApplyAssignMathNotModifiedOnOverflow()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    doc->setValue("byteval", ByteFieldValue(127));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("byteval", "", "$value + 200")));
    docUp.applyTo(*doc);
    // Over/underflow will happen. You must have control of your data types.
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(ByteFieldValue(static_cast<char>(static_cast<int>(127+200)))), *doc->getValue("byteval"));
}

void
FieldPathUpdateTestCase::testApplyAssignMathDivZero()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    CPPUNIT_ASSERT(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(10));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "$value / ($value - 10)")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(IntFieldValue(10)), *doc->getValue("num"));
}

void
FieldPathUpdateTestCase::testApplyAssignFieldNotExistingInExpression()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    doc->setRepo(*_repo);
    CPPUNIT_ASSERT(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(10));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "foobar.num2 + $value")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(IntFieldValue(10)), *doc->getValue("num"));
}

void
FieldPathUpdateTestCase::testApplyAssignFieldNotExistingInPath()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    doc->setRepo(*_repo);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    try {
        docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("nosuchnum", "", "foobar.num + $value")));
        docUp.applyTo(*doc);
        CPPUNIT_ASSERT(false);
    } catch (const FieldNotFoundException&) {
    }
}

void
FieldPathUpdateTestCase::testApplyAssignTargetNotExisting()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    CPPUNIT_ASSERT(doc->hasValue("num") == false);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "$value + 5")));
    docUp.applyTo(*doc);
    CPPUNIT_ASSERT_EQUAL(static_cast<const FieldValue&>(IntFieldValue(5)), *doc->getValue("num"));
}

void
FieldPathUpdateTestCase::testAssignSimpleMapValueWithVariable()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bug:hunter")));
    doc->setRepo(*_repo);

    MapFieldValue mfv(doc->getType().getField("strmap").getDataType());
    mfv.put(StringFieldValue("foo"), StringFieldValue("bar"));
    mfv.put(StringFieldValue("baz"), StringFieldValue("bananas"));
    doc->setValue("strmap", mfv);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    // Select on value, not key
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(),
                                      "strmap{$x}", "foobar.strmap{$x} == \"bar\"", StringFieldValue("shinyvalue"))));
    docUp.applyTo(*doc);

    std::unique_ptr<MapFieldValue> valueNow(doc->getAs<MapFieldValue>(doc->getField("strmap")));

    CPPUNIT_ASSERT_EQUAL(std::size_t(2), valueNow->size());
    CPPUNIT_ASSERT_EQUAL(
            static_cast<const FieldValue&>(StringFieldValue("shinyvalue")),
            *valueNow->get(StringFieldValue("foo")));
    CPPUNIT_ASSERT_EQUAL(
            static_cast<const FieldValue&>(StringFieldValue("bananas")),
            *valueNow->get(StringFieldValue("baz")));
}

void
FieldPathUpdateTestCase::testApplyAssignMathRemoveIfZero()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    CPPUNIT_ASSERT(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(34));
    CPPUNIT_ASSERT(doc->hasValue("num") == true);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    FieldPathUpdate::CP up1(new AssignFieldPathUpdate("num", "", "($value * 2) / $value - 2"));
    static_cast<AssignFieldPathUpdate&>(*up1).setRemoveIfZero(true);
    docUp.addFieldPathUpdate(up1);

    docUp.applyTo(*doc);
    CPPUNIT_ASSERT(doc->hasValue("num") == false);
}

void
FieldPathUpdateTestCase::testApplyAssignMultiList()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:fest:skinnvest")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);

    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("hello hello"));
        strArray.add(StringFieldValue("blah blargh"));
        doc->setValue("strarray", strArray);
        CPPUNIT_ASSERT(doc->hasValue("strarray"));
    }

    ArrayFieldValue updateArray(doc->getType().getField("strarray").getDataType());
    updateArray.add(StringFieldValue("assigned val 0"));
    updateArray.add(StringFieldValue("assigned val 1"));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strarray", std::string(), updateArray)));
    docUp.applyTo(*doc);
    {
        std::unique_ptr<ArrayFieldValue> strArray =
            doc->getAs<ArrayFieldValue>(doc->getField("strarray"));
        CPPUNIT_ASSERT_EQUAL(std::size_t(2), strArray->size());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("assigned val 0"), (*strArray)[0].getAsString());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("assigned val 1"), (*strArray)[1].getAsString());
    }
}


void
FieldPathUpdateTestCase::testApplyAssignMultiWset()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:fest:skinnvest")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);

    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("hello gentlemen"), 10);
        strWset.add(StringFieldValue("what you say"), 20);
        doc->setValue("strwset", strWset);
        CPPUNIT_ASSERT(doc->hasValue("strwset"));
    }

    WeightedSetFieldValue assignWset(doc->getType().getField("strwset").getDataType());
    assignWset.add(StringFieldValue("assigned val 0"), 5);
    assignWset.add(StringFieldValue("assigned val 1"), 10);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "strwset", std::string(), assignWset)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");
    {
        std::unique_ptr<WeightedSetFieldValue> strWset = doc->getAs<WeightedSetFieldValue>(doc->getField("strwset"));
        CPPUNIT_ASSERT_EQUAL(std::size_t(2), strWset->size());
        CPPUNIT_ASSERT_EQUAL(5, strWset->get(StringFieldValue("assigned val 0")));
        CPPUNIT_ASSERT_EQUAL(10, strWset->get(StringFieldValue("assigned val 1")));
    }
}

void
FieldPathUpdateTestCase::testAssignWsetRemoveIfZero()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:tronder:bataljon")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);

    {
        WeightedSetFieldValue strWset(doc->getType().getField("strwset").getDataType());
        strWset.add(StringFieldValue("you say goodbye"), 164);
        strWset.add(StringFieldValue("but i say hello"), 243);
        doc->setValue("strwset", strWset);
        CPPUNIT_ASSERT(doc->hasValue("strwset"));
    }

    {
        DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
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
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), strWset->size());
            CPPUNIT_ASSERT_EQUAL(243, strWset->get(StringFieldValue("but i say hello")));
        }
    }
}

void
FieldPathUpdateTestCase::testApplyAddMultiList()
{
   Document::UP doc(new Document(_foobar_type, DocumentId("doc:george:costanza")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);

    ArrayFieldValue adds(doc->getType().getField("strarray").getDataType());
    adds.add(StringFieldValue("serenity now"));
    adds.add(StringFieldValue("a festivus for the rest of us"));
    adds.add(StringFieldValue("george is getting upset!"));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AddFieldPathUpdate(*doc->getDataType(), "strarray", std::string(), adds)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");
    CPPUNIT_ASSERT(doc->hasValue("strarray"));
}

void
FieldPathUpdateTestCase::testAddAndAssignList()
{
   Document::UP doc(new Document(_foobar_type, DocumentId("doc:fancy:pants")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);

    {
        ArrayFieldValue strArray(doc->getType().getField("strarray").getDataType());
        strArray.add(StringFieldValue("hello hello"));
        strArray.add(StringFieldValue("blah blargh"));
        doc->setValue("strarray", strArray);
        CPPUNIT_ASSERT(doc->hasValue("strarray"));
    }

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
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
        CPPUNIT_ASSERT_EQUAL(std::size_t(3), strArray->size());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("hello hello"), (*strArray)[0].getAsString());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("assigned val 1"), (*strArray)[1].getAsString());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("new value"), (*strArray)[2].getAsString());
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

Fixture::~Fixture() { }
Fixture::Fixture(const DocumentType &doc_type, const Keys &k)
    : doc(new Document(doc_type, DocumentId("doc:planet:express"))),
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

void
FieldPathUpdateTestCase::testAssignMap()
{
    Keys k;
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), "structmap{" + k.key2 + "}", std::string(), f.fv4)));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    CPPUNIT_ASSERT_EQUAL(std::size_t(3), valueNow->size());
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

void
FieldPathUpdateTestCase::testAssignMapStruct()
{
    Keys k;
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), "structmap{" + k.key2 + "}.rating",
                                      std::string(), IntFieldValue(48))));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    CPPUNIT_ASSERT_EQUAL(std::size_t(3), valueNow->size());
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

void
FieldPathUpdateTestCase::testAssignMapStructVariable()
{
    Keys k;
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), "structmap{$x}.rating",
                                      "foobar.structmap{$x}.title == \"farnsworth\"", IntFieldValue(48))));
    f.doc->setRepo(*_repo);
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    CPPUNIT_ASSERT_EQUAL(std::size_t(3), valueNow->size());
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

void
FieldPathUpdateTestCase::testAssignMapNoExist()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:planet:express")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv1(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv1.setValue("title", StringFieldValue("fry"));
    fv1.setValue("rating", IntFieldValue(30));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*doc->getDataType(), "structmap{foo}", std::string(), fv1)));
    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");

    std::unique_ptr<MapFieldValue> valueNow =
        doc->getAs<MapFieldValue>(doc->getField("structmap"));
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), valueNow->size());
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(fv1), *valueNow->get(StringFieldValue("foo")));
}

void
FieldPathUpdateTestCase::testAssignMapNoExistNoCreate()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:planet:express")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv1(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv1.setValue("title", StringFieldValue("fry"));
    fv1.setValue("rating", IntFieldValue(30));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    FieldPathUpdate::CP assignUpdate(
            new AssignFieldPathUpdate(*doc->getDataType(), "structmap{foo}", std::string(), fv1));
    static_cast<AssignFieldPathUpdate&>(*assignUpdate).setCreateMissingPath(false);
    docUp.addFieldPathUpdate(assignUpdate);

    //doc->print(std::cerr, true, "");
    docUp.applyTo(*doc);
    //doc->print(std::cerr, true, "");

    std::unique_ptr<MapFieldValue> valueNow = doc->getAs<MapFieldValue>(doc->getField("structmap"));
    CPPUNIT_ASSERT(valueNow.get() == 0);
}

void
FieldPathUpdateTestCase::testQuotedStringKey()
{
    Keys k;
    k.key2 = "here is a \"fancy\" 'map' :-} key :-{";
    const char field_path[] = "structmap{\"here is a \\\"fancy\\\" 'map' :-} key :-{\"}";
    Fixture f(_foobar_type, k);

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(
            new AssignFieldPathUpdate(*f.doc->getDataType(), field_path, std::string(), f.fv4)));
    docUp.applyTo(*f.doc);

    std::unique_ptr<MapFieldValue> valueNow = f.doc->getAs<MapFieldValue>(f.doc->getField("structmap"));
    CPPUNIT_ASSERT_EQUAL(std::size_t(3), valueNow->size());
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv1),
                         *valueNow->get(StringFieldValue(k.key1)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv4),
                         *valueNow->get(StringFieldValue(k.key2)));
    CPPUNIT_ASSERT_EQUAL(static_cast<FieldValue&>(f.fv3),
                         *valueNow->get(StringFieldValue(k.key3)));
}

void
FieldPathUpdateTestCase::testEqualityComparison()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:foo:zoo")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv4(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv4.setValue("title", StringFieldValue("tasty cake"));
    fv4.setValue("rating", IntFieldValue(95));

    {
        DocumentUpdate docUp1(_foobar_type, DocumentId("doc:barbar:foofoo"));
        DocumentUpdate docUp2(_foobar_type, DocumentId("doc:barbar:foofoo"));
        CPPUNIT_ASSERT(docUp1 == docUp2);

        FieldPathUpdate::CP assignUp1(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", std::string(), fv4));
        docUp1.addFieldPathUpdate(assignUp1);
        CPPUNIT_ASSERT(docUp1 != docUp2);
        docUp2.addFieldPathUpdate(assignUp1);
        CPPUNIT_ASSERT(docUp1 == docUp2);
    }
    {
        DocumentUpdate docUp1(_foobar_type, DocumentId("doc:barbar:foofoo"));
        DocumentUpdate docUp2(_foobar_type, DocumentId("doc:barbar:foofoo"));
        // where-clause diff
        FieldPathUpdate::CP assignUp1(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", std::string(), fv4));
        FieldPathUpdate::CP assignUp2(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", "false", fv4));
        docUp1.addFieldPathUpdate(assignUp1);
        docUp2.addFieldPathUpdate(assignUp2);
        CPPUNIT_ASSERT(docUp1 != docUp2);
    }
    {
        DocumentUpdate docUp1(_foobar_type, DocumentId("doc:barbar:foofoo"));
        DocumentUpdate docUp2(_foobar_type, DocumentId("doc:barbar:foofoo"));
        // fieldpath diff
        FieldPathUpdate::CP assignUp1(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be dragons}", std::string(), fv4));
        FieldPathUpdate::CP assignUp2(new AssignFieldPathUpdate(*doc->getDataType(),
                                              "structmap{here be kittens}", std::string(), fv4));
        docUp1.addFieldPathUpdate(assignUp1);
        docUp2.addFieldPathUpdate(assignUp2);
        CPPUNIT_ASSERT(docUp1 != docUp2);
    }

}

void
FieldPathUpdateTestCase::testAffectsDocumentBody()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:things:stuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue fv4(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    fv4.setValue("title", StringFieldValue("scruffy"));
    fv4.setValue("rating", IntFieldValue(90));

    // structmap is body field
    {
        DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
        CPPUNIT_ASSERT(!docUp.affectsDocumentBody());

        FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(),
                                                              "structmap{janitor}", std::string(), fv4));
        static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
        docUp.addFieldPathUpdate(update1);
        CPPUNIT_ASSERT(docUp.affectsDocumentBody());
    }

    // strfoo is header field
    {
        DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
        FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(),
                                            "strfoo", std::string(), StringFieldValue("helloworld")));
        static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
        docUp.addFieldPathUpdate(update1);
        CPPUNIT_ASSERT(!docUp.affectsDocumentBody());
    }

}

void
FieldPathUpdateTestCase::testIncompatibleDataTypeFails()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:things:stuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));

    try {
        FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(), "structmap{foo}",
                                                              std::string(), StringFieldValue("bad things")));
        CPPUNIT_ASSERT(false);
    } catch (const vespalib::IllegalArgumentException& e) {
        // OK
    }
}

void
FieldPathUpdateTestCase::testSerializeAssign()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:weloveto:serializestuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    StructFieldValue val(dynamic_cast<const MapDataType&>(*mfv.getDataType()).getValueType());
    val.setValue("title", StringFieldValue("cool frog"));
    val.setValue("rating", IntFieldValue(100));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    CPPUNIT_ASSERT(!docUp.affectsDocumentBody());

    FieldPathUpdate::CP update1(new AssignFieldPathUpdate(*doc->getDataType(), "structmap{ribbit}", "true", val));
    static_cast<AssignFieldPathUpdate&>(*update1).setCreateMissingPath(true);
    docUp.addFieldPathUpdate(update1);

    testSerialize(*_repo, docUp);
}

void
FieldPathUpdateTestCase::testSerializeAdd()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:george:costanza")));
    CPPUNIT_ASSERT(doc->hasValue("strarray") == false);

    ArrayFieldValue adds(doc->getType().getField("strarray").getDataType());
    adds.add(StringFieldValue("serenity now"));
    adds.add(StringFieldValue("a festivus for the rest of us"));
    adds.add(StringFieldValue("george is getting upset!"));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    CPPUNIT_ASSERT(!docUp.affectsDocumentBody());

    FieldPathUpdate::CP update1(new AddFieldPathUpdate(*doc->getDataType(), "strarray", std::string(), adds));
    docUp.addFieldPathUpdate(update1);

    testSerialize(*_repo, docUp);
}

void
FieldPathUpdateTestCase::testSerializeRemove()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:weloveto:serializestuff")));
    MapFieldValue mfv(doc->getType().getField("structmap").getDataType());

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    CPPUNIT_ASSERT(!docUp.affectsDocumentBody());

    FieldPathUpdate::CP update1(new RemoveFieldPathUpdate("structmap{ribbit}", std::string()));
    docUp.addFieldPathUpdate(update1);

    testSerialize(*_repo, docUp);
}

void
FieldPathUpdateTestCase::testSerializeAssignMath()
{
    Document::UP doc(new Document(_foobar_type, DocumentId("doc:bat:man")));
    CPPUNIT_ASSERT(doc->hasValue("num") == false);
    doc->setValue("num", IntFieldValue(34));

    DocumentUpdate docUp(_foobar_type, DocumentId("doc:barbar:foofoo"));
    docUp.addFieldPathUpdate(FieldPathUpdate::CP(new AssignFieldPathUpdate("num", "", "($value * 2) / $value")));
    testSerialize(*_repo, docUp);
}

DocumentUpdate::UP
FieldPathUpdateTestCase::createDocumentUpdateForSerialization(const DocumentTypeRepo& repo)
{
    const DocumentType *docType(repo.getDocumentType("serializetest"));
    DocumentUpdate::UP docUp(new DocumentUpdate(*docType, DocumentId("doc:serialization:xlanguage")));

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

void
FieldPathUpdateTestCase::testReadSerializedFile()
{
    // Reads a file serialized from java
    const std::string cfg_file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(cfg_file_name));

    int fd = open(TEST_PATH("data/serialize-fieldpathupdate-java.dat").c_str(), O_RDONLY);

    int len = lseek(fd,0,SEEK_END);
    ByteBuffer buf(len);
    lseek(fd,0,SEEK_SET);
    if (read(fd, buf.getBuffer(), len) != len) {
        throw vespalib::Exception("read failed");
    }
    close(fd);

    DocumentUpdate::UP updp(DocumentUpdate::createHEAD(repo, buf));
    DocumentUpdate& upd(*updp);

    DocumentUpdate::UP compare(createDocumentUpdateForSerialization(repo));
    CPPUNIT_ASSERT_EQUAL(*compare, upd);
}

void
FieldPathUpdateTestCase::testGenerateSerializedFile()
{
    const std::string cfg_file_name = TEST_PATH("data/crossplatform-java-cpp-doctypes.cfg");
    DocumentTypeRepo repo(readDocumenttypesConfig(cfg_file_name));
    // Tests nothing, only generates a file for java test
    DocumentUpdate::UP upd(createDocumentUpdateForSerialization(repo));

    ByteBuffer::UP buf(serializeHEAD(*upd));

    int fd = open(TEST_PATH("data/serialize-fieldpathupdate-cpp.dat").c_str(),
                  O_WRONLY | O_TRUNC | O_CREAT, 0644);
    if (write(fd, buf->getBuffer(), buf->getPos()) != (ssize_t)buf->getPos()) {
    	throw vespalib::Exception("write failed");
    }
    close(fd);
}

}
