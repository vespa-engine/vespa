// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <tests/persistence/persistencetestutils.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/persistence/spi/test.h>
#include <functional>

using std::unique_ptr;
using std::shared_ptr;

using namespace std::string_literals;
using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;

namespace storage {

class TestAndSetTest : public SingleDiskPersistenceTestUtils
{
    static constexpr int MIN_DOCUMENT_SIZE = 0;
    static constexpr int MAX_DOCUMENT_SIZE = 128;
    static constexpr int RANDOM_SEED = 1234;

    const document::BucketId BUCKET_ID{16, 4};
    const document::StringFieldValue MISMATCHING_HEADER{"Definitely nothing about loud canines"};
    const document::StringFieldValue MATCHING_HEADER{"Some string with woofy dog as a substring"};
    const document::StringFieldValue OLD_CONTENT{"Some old content"};
    const document::StringFieldValue NEW_CONTENT{"Freshly pressed and squeezed content"};

    unique_ptr<PersistenceThread> thread;
    shared_ptr<document::Document> testDoc;
    document::DocumentId testDocId;

public:
    void setUp() override {
        SingleDiskPersistenceTestUtils::setUp();

        spi::Context context(
            spi::LoadType(0, "default"),
            spi::Priority(0),
            spi::Trace::TraceLevel(0));

        createBucket(BUCKET_ID);
        getPersistenceProvider().createBucket(
                makeSpiBucket(BUCKET_ID),
            context);

        thread = createPersistenceThread(0);
        testDoc = createTestDocument();
        testDocId = testDoc->getId();
    }

    void tearDown() override {
        thread.reset(nullptr);
        SingleDiskPersistenceTestUtils::tearDown();
    }

    void conditional_put_not_executed_on_condition_mismatch();
    void conditional_put_executed_on_condition_match();
    void conditional_remove_not_executed_on_condition_mismatch();
    void conditional_remove_executed_on_condition_match();
    void conditional_update_not_executed_on_condition_mismatch();
    void conditional_update_executed_on_condition_match();
    void invalid_document_selection_should_fail();
    void non_existing_document_should_fail();
    void document_with_no_type_should_fail();

    CPPUNIT_TEST_SUITE(TestAndSetTest);
    CPPUNIT_TEST(conditional_put_not_executed_on_condition_mismatch);
    CPPUNIT_TEST(conditional_put_executed_on_condition_match);
    CPPUNIT_TEST(conditional_remove_not_executed_on_condition_mismatch);
    CPPUNIT_TEST(conditional_remove_executed_on_condition_match);
    CPPUNIT_TEST(conditional_update_not_executed_on_condition_mismatch);
    CPPUNIT_TEST(conditional_update_executed_on_condition_match);
    CPPUNIT_TEST(invalid_document_selection_should_fail);
    CPPUNIT_TEST(non_existing_document_should_fail);
    CPPUNIT_TEST(document_with_no_type_should_fail);
    CPPUNIT_TEST_SUITE_END();

protected:
    std::unique_ptr<api::UpdateCommand> conditional_update_test(
        bool matchingHeader,
        api::Timestamp timestampOne,
        api::Timestamp timestampTwo);

    document::Document::SP createTestDocument();
    document::Document::SP retrieveTestDocument();
    void setTestCondition(api::TestAndSetCommand & command);
    void putTestDocument(bool matchingHeader, api::Timestamp timestamp);
    void assertTestDocumentFoundAndMatchesContent(const document::FieldValue & value);

    static std::string expectedDocEntryString(
        api::Timestamp timestamp,
        const document::DocumentId & testDocId,
        spi::DocumentMetaFlags removeFlag = spi::NONE);
};

CPPUNIT_TEST_SUITE_REGISTRATION(TestAndSetTest);

void TestAndSetTest::conditional_put_not_executed_on_condition_mismatch()
{
    // Put document with mismatching header
    api::Timestamp timestampOne = 0;
    putTestDocument(false, timestampOne);

    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Conditionally replace document, but fail due to lack of woofy dog
    api::Timestamp timestampTwo = 1;
    api::PutCommand putTwo(makeDocumentBucket(BUCKET_ID), testDoc, timestampTwo);
    setTestCondition(putTwo);

    CPPUNIT_ASSERT(thread->handlePut(putTwo)->getResult() == api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));
}

void TestAndSetTest::conditional_put_executed_on_condition_match()
{
    // Put document with matching header
    api::Timestamp timestampOne = 0;
    putTestDocument(true, timestampOne);

    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Update content of document
    testDoc->setValue(testDoc->getField("content"), NEW_CONTENT);

    // Conditionally replace document with updated version, succeed in doing so
    api::Timestamp timestampTwo = 1;
    api::PutCommand putTwo(makeDocumentBucket(BUCKET_ID), testDoc, timestampTwo);
    setTestCondition(putTwo);

    CPPUNIT_ASSERT(thread->handlePut(putTwo)->getResult() == api::ReturnCode::Result::OK);
    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId) +
                         expectedDocEntryString(timestampTwo, testDocId),
                         dumpBucket(BUCKET_ID));

    assertTestDocumentFoundAndMatchesContent(NEW_CONTENT);
}

void TestAndSetTest::conditional_remove_not_executed_on_condition_mismatch()
{
    // Put document with mismatching header
    api::Timestamp timestampOne = 0;
    putTestDocument(false, timestampOne);

    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Conditionally remove document, fail in doing so
    api::Timestamp timestampTwo = 1;
    api::RemoveCommand remove(makeDocumentBucket(BUCKET_ID), testDocId, timestampTwo);
    setTestCondition(remove);

    CPPUNIT_ASSERT(thread->handleRemove(remove)->getResult() == api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Assert that the document is still there
    retrieveTestDocument();
}

void TestAndSetTest::conditional_remove_executed_on_condition_match()
{
    // Put document with matching header
    api::Timestamp timestampOne = 0;
    putTestDocument(true, timestampOne);

    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Conditionally remove document, succeed in doing so
    api::Timestamp timestampTwo = 1;
    api::RemoveCommand remove(makeDocumentBucket(BUCKET_ID), testDocId, timestampTwo);
    setTestCondition(remove);

    CPPUNIT_ASSERT(thread->handleRemove(remove)->getResult() == api::ReturnCode::Result::OK);
    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId) +
                         expectedDocEntryString(timestampTwo, testDocId, spi::REMOVE_ENTRY),
                         dumpBucket(BUCKET_ID));
}

std::unique_ptr<api::UpdateCommand> TestAndSetTest::conditional_update_test(
    bool matchingHeader,
    api::Timestamp timestampOne,
    api::Timestamp timestampTwo)
{
    putTestDocument(matchingHeader, timestampOne);

    auto docUpdate = std::make_shared<document::DocumentUpdate>(_env->_testDocMan.getTypeRepo(), testDoc->getType(), testDocId);
    auto fieldUpdate = document::FieldUpdate(testDoc->getField("content"));
    fieldUpdate.addUpdate(document::AssignValueUpdate(NEW_CONTENT));
    docUpdate->addUpdate(fieldUpdate);

    auto updateUp = std::make_unique<api::UpdateCommand>(makeDocumentBucket(BUCKET_ID), docUpdate, timestampTwo);
    setTestCondition(*updateUp);
    return updateUp;
}

void TestAndSetTest::conditional_update_not_executed_on_condition_mismatch()
{
    api::Timestamp timestampOne = 0;
    api::Timestamp timestampTwo = 1;
    auto updateUp = conditional_update_test(false, timestampOne, timestampTwo);

    CPPUNIT_ASSERT(thread->handleUpdate(*updateUp)->getResult() == api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId),
                        dumpBucket(BUCKET_ID));

    assertTestDocumentFoundAndMatchesContent(OLD_CONTENT);
}

void TestAndSetTest::conditional_update_executed_on_condition_match()
{
    api::Timestamp timestampOne = 0;
    api::Timestamp timestampTwo = 1;
    auto updateUp = conditional_update_test(true, timestampOne, timestampTwo);

    CPPUNIT_ASSERT(thread->handleUpdate(*updateUp)->getResult() == api::ReturnCode::Result::OK);
    CPPUNIT_ASSERT_EQUAL(expectedDocEntryString(timestampOne, testDocId) +
                         expectedDocEntryString(timestampTwo, testDocId),
                         dumpBucket(BUCKET_ID));

    assertTestDocumentFoundAndMatchesContent(NEW_CONTENT);
}

void TestAndSetTest::invalid_document_selection_should_fail()
{
    // Conditionally replace nonexisting document
    // Fail early since document selection is invalid 
    api::Timestamp timestamp = 0;
    api::PutCommand put(makeDocumentBucket(BUCKET_ID), testDoc, timestamp);
    put.setCondition(documentapi::TestAndSetCondition("bjarne"));

    CPPUNIT_ASSERT(thread->handlePut(put)->getResult() == api::ReturnCode::Result::ILLEGAL_PARAMETERS);
    CPPUNIT_ASSERT_EQUAL(""s, dumpBucket(BUCKET_ID));
}

void TestAndSetTest::non_existing_document_should_fail()
{
    // Conditionally replace nonexisting document
    // Fail since no document exists to match with test and set
    api::Timestamp timestamp = 0;
    api::PutCommand put(makeDocumentBucket(BUCKET_ID), testDoc, timestamp);
    setTestCondition(put);
    thread->handlePut(put);

    CPPUNIT_ASSERT(thread->handlePut(put)->getResult() == api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    CPPUNIT_ASSERT_EQUAL(""s, dumpBucket(BUCKET_ID));
}

void TestAndSetTest::document_with_no_type_should_fail()
{
    // Conditionally replace nonexisting document
    // Fail since no document exists to match with test and set
    api::Timestamp timestamp = 0;
    document::DocumentId legacyDocId("doc:mail:3619.html");
    api::RemoveCommand remove(makeDocumentBucket(BUCKET_ID), legacyDocId, timestamp);
    setTestCondition(remove);

    auto code = thread->handleRemove(remove)->getResult();
    CPPUNIT_ASSERT(code == api::ReturnCode::Result::ILLEGAL_PARAMETERS);
    CPPUNIT_ASSERT(code.getMessage() == "Document id has no doctype");
    CPPUNIT_ASSERT_EQUAL(""s, dumpBucket(BUCKET_ID));
}

document::Document::SP
TestAndSetTest::createTestDocument()
{
    auto doc = document::Document::SP(
        createRandomDocumentAtLocation(
            BUCKET_ID.getId(),
            RANDOM_SEED,
            MIN_DOCUMENT_SIZE,
            MAX_DOCUMENT_SIZE));

    doc->setValue(doc->getField("content"), OLD_CONTENT);
    doc->setValue(doc->getField("hstringval"), MISMATCHING_HEADER);

    return doc;
}

document::Document::SP TestAndSetTest::retrieveTestDocument()
{
    api::GetCommand get(makeDocumentBucket(BUCKET_ID), testDocId, "[all]");
    auto tracker = thread->handleGet(get);
    CPPUNIT_ASSERT(tracker->getResult() == api::ReturnCode::Result::OK);

    auto & reply = static_cast<api::GetReply &>(*tracker->getReply());
    CPPUNIT_ASSERT(reply.wasFound());

    return reply.getDocument();
}

void TestAndSetTest::setTestCondition(api::TestAndSetCommand & command)
{
    command.setCondition(documentapi::TestAndSetCondition("testdoctype1.hstringval=\"*woofy dog*\""));
}

void TestAndSetTest::putTestDocument(bool matchingHeader, api::Timestamp timestamp) {
    if (matchingHeader) {
        testDoc->setValue(testDoc->getField("hstringval"), MATCHING_HEADER);
    }

    api::PutCommand put(makeDocumentBucket(BUCKET_ID), testDoc, timestamp);
    thread->handlePut(put);
}

void TestAndSetTest::assertTestDocumentFoundAndMatchesContent(const document::FieldValue & value)
{
    auto doc = retrieveTestDocument();
    auto & field = doc->getField("content");

    CPPUNIT_ASSERT_EQUAL(*doc->getValue(field), value);
}

std::string TestAndSetTest::expectedDocEntryString(
    api::Timestamp timestamp,
    const document::DocumentId & docId,
    spi::DocumentMetaFlags removeFlag)
{
    std::stringstream ss;

    ss << "DocEntry(" << timestamp << ", " << removeFlag << ", ";
    if (removeFlag == spi::REMOVE_ENTRY) {
        ss << docId.toString() << ")\n";
    } else {
       ss << "Doc(" << docId.toString() << "))\n";
    }

    return ss.str();
}

} // storage
