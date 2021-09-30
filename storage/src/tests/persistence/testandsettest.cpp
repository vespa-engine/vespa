// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#include <vespa/storage/persistence/persistencehandler.h>
#include <tests/persistence/persistencetestutils.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <functional>

using std::unique_ptr;
using std::shared_ptr;

using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct TestAndSetTest : SingleDiskPersistenceTestUtils {
    static constexpr int MIN_DOCUMENT_SIZE = 0;
    static constexpr int MAX_DOCUMENT_SIZE = 128;
    static constexpr int RANDOM_SEED = 1234;

    const document::BucketId BUCKET_ID{16, 4};
    const document::StringFieldValue MISMATCHING_HEADER{"Definitely nothing about loud canines"};
    const document::StringFieldValue MATCHING_HEADER{"Some string with woofy dog as a substring"};
    const document::StringFieldValue OLD_CONTENT{"Some old content"};
    const document::StringFieldValue NEW_CONTENT{"Freshly pressed and squeezed content"};
    const document::Bucket BUCKET = makeDocumentBucket(BUCKET_ID);

    unique_ptr<PersistenceHandler> persistenceHandler;
    const AsyncHandler * asyncHandler;
    shared_ptr<document::Document> testDoc;
    document::DocumentId testDocId;
    spi::Context context;

    TestAndSetTest()
        : persistenceHandler(),
          asyncHandler(nullptr),
          context(0, 0)
    {}

    void SetUp() override {
        SingleDiskPersistenceTestUtils::SetUp();

        createBucket(BUCKET_ID);
        getPersistenceProvider().createBucket(makeSpiBucket(BUCKET_ID),context);

        testDoc = createTestDocument();
        testDocId = testDoc->getId();
        asyncHandler = &_persistenceHandler->asyncHandler();
    }

    void TearDown() override {
        SingleDiskPersistenceTestUtils::TearDown();
    }

    std::shared_ptr<api::UpdateCommand> conditional_update_test(
        bool createIfMissing,
        api::Timestamp updateTimestamp);

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

TEST_F(TestAndSetTest, conditional_put_not_executed_on_condition_mismatch) {
    // Put document with mismatching header
    api::Timestamp timestampOne = 0;
    putTestDocument(false, timestampOne);

    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Conditionally replace document, but fail due to lack of woofy dog
    api::Timestamp timestampTwo = 1;
    auto putTwo = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestampTwo);
    setTestCondition(*putTwo);

    ASSERT_EQ(fetchResult(asyncHandler->handlePut(*putTwo, createTracker(putTwo, BUCKET))).getResult(),
              api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));
}

TEST_F(TestAndSetTest, conditional_put_executed_on_condition_match) {
    // Put document with matching header
    api::Timestamp timestampOne = 0;
    putTestDocument(true, timestampOne);

    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Update content of document
    testDoc->setValue(testDoc->getField("content"), NEW_CONTENT);

    // Conditionally replace document with updated version, succeed in doing so
    api::Timestamp timestampTwo = 1;
    auto putTwo = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestampTwo);
    setTestCondition(*putTwo);

    ASSERT_EQ(fetchResult(asyncHandler->handlePut(*putTwo, createTracker(putTwo, BUCKET))).getResult(), api::ReturnCode::Result::OK);
    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId) +
              expectedDocEntryString(timestampTwo, testDocId),
              dumpBucket(BUCKET_ID));

    assertTestDocumentFoundAndMatchesContent(NEW_CONTENT);
}

TEST_F(TestAndSetTest, conditional_remove_not_executed_on_condition_mismatch) {
    // Put document with mismatching header
    api::Timestamp timestampOne = 0;
    putTestDocument(false, timestampOne);

    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Conditionally remove document, fail in doing so
    api::Timestamp timestampTwo = 1;
    auto remove = std::make_shared<api::RemoveCommand>(BUCKET, testDocId, timestampTwo);
    setTestCondition(*remove);

    ASSERT_EQ(fetchResult(asyncHandler->handleRemove(*remove, createTracker(remove, BUCKET))).getResult(),
              api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Assert that the document is still there
    retrieveTestDocument();
}

TEST_F(TestAndSetTest, conditional_remove_executed_on_condition_match) {
    // Put document with matching header
    api::Timestamp timestampOne = 0;
    putTestDocument(true, timestampOne);

    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    // Conditionally remove document, succeed in doing so
    api::Timestamp timestampTwo = 1;
    auto remove = std::make_shared<api::RemoveCommand>(BUCKET, testDocId, timestampTwo);
    setTestCondition(*remove);

    ASSERT_EQ(fetchResult(asyncHandler->handleRemove(*remove, createTracker(remove, BUCKET))).getResult(), api::ReturnCode::Result::OK);
    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId) +
              expectedDocEntryString(timestampTwo, testDocId, spi::REMOVE_ENTRY),
              dumpBucket(BUCKET_ID));
}

std::shared_ptr<api::UpdateCommand>
TestAndSetTest::conditional_update_test(bool createIfMissing, api::Timestamp updateTimestamp)
{
    auto docUpdate = std::make_shared<document::DocumentUpdate>(_env->_testDocMan.getTypeRepo(), testDoc->getType(), testDocId);
    auto fieldUpdate = document::FieldUpdate(testDoc->getField("content"));
    fieldUpdate.addUpdate(document::AssignValueUpdate(NEW_CONTENT));
    docUpdate->addUpdate(fieldUpdate);
    docUpdate->setCreateIfNonExistent(createIfMissing);

    auto updateUp = std::make_unique<api::UpdateCommand>(BUCKET, docUpdate, updateTimestamp);
    setTestCondition(*updateUp);
    return updateUp;
}

TEST_F(TestAndSetTest, conditional_update_not_executed_on_condition_mismatch) {
    api::Timestamp timestampOne = 0;
    api::Timestamp timestampTwo = 1;
    putTestDocument(false, timestampOne);
    auto updateUp = conditional_update_test(false, timestampTwo);

    ASSERT_EQ(fetchResult(asyncHandler->handleUpdate(*updateUp, createTracker(updateUp, BUCKET))).getResult(),
              api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId), dumpBucket(BUCKET_ID));

    assertTestDocumentFoundAndMatchesContent(OLD_CONTENT);
}

TEST_F(TestAndSetTest, conditional_update_executed_on_condition_match) {
    api::Timestamp timestampOne = 0;
    api::Timestamp timestampTwo = 1;
    putTestDocument(true, timestampOne);
    auto updateUp = conditional_update_test(false, timestampTwo);

    ASSERT_EQ(fetchResult(asyncHandler->handleUpdate(*updateUp, createTracker(updateUp, BUCKET))).getResult(), api::ReturnCode::Result::OK);
    EXPECT_EQ(expectedDocEntryString(timestampOne, testDocId) +
              expectedDocEntryString(timestampTwo, testDocId),
              dumpBucket(BUCKET_ID));

    assertTestDocumentFoundAndMatchesContent(NEW_CONTENT);
}

TEST_F(TestAndSetTest, conditional_update_not_executed_when_no_document_and_no_auto_create) {
    api::Timestamp updateTimestamp = 200;
    auto updateUp = conditional_update_test(false, updateTimestamp);

    ASSERT_EQ(fetchResult(asyncHandler->handleUpdate(*updateUp, createTracker(updateUp, BUCKET))).getResult(),
              api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    EXPECT_EQ("", dumpBucket(BUCKET_ID));
}

TEST_F(TestAndSetTest, conditional_update_executed_when_no_document_but_auto_create_is_enabled) {
    api::Timestamp updateTimestamp = 200;
    auto updateUp = conditional_update_test(true, updateTimestamp);

    ASSERT_EQ(fetchResult(asyncHandler->handleUpdate(*updateUp, createTracker(updateUp, BUCKET))).getResult(), api::ReturnCode::Result::OK);
    EXPECT_EQ(expectedDocEntryString(updateTimestamp, testDocId), dumpBucket(BUCKET_ID));
    assertTestDocumentFoundAndMatchesContent(NEW_CONTENT);
}

TEST_F(TestAndSetTest, invalid_document_selection_should_fail) {
    // Conditionally replace nonexisting document
    // Fail early since document selection is invalid 
    api::Timestamp timestamp = 0;
    auto put = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestamp);
    put->setCondition(documentapi::TestAndSetCondition("bjarne"));

    ASSERT_EQ(fetchResult(asyncHandler->handlePut(*put, createTracker(put, BUCKET))).getResult(), api::ReturnCode::Result::ILLEGAL_PARAMETERS);
    EXPECT_EQ("", dumpBucket(BUCKET_ID));
}

TEST_F(TestAndSetTest, conditional_put_to_non_existing_document_should_fail) {
    // Conditionally replace nonexisting document
    // Fail since no document exists to match with test and set
    api::Timestamp timestamp = 0;
    auto put = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestamp);
    setTestCondition(*put);
    asyncHandler->handlePut(*put, createTracker(put, BUCKET));

    ASSERT_EQ(fetchResult(asyncHandler->handlePut(*put, createTracker(put, BUCKET))).getResult(),
              api::ReturnCode::Result::TEST_AND_SET_CONDITION_FAILED);
    EXPECT_EQ("", dumpBucket(BUCKET_ID));
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

document::Document::SP
TestAndSetTest::retrieveTestDocument()
{
    auto get = std::make_shared<api::GetCommand>(BUCKET, testDocId, document::AllFields::NAME);
    auto tracker = _persistenceHandler->simpleMessageHandler().handleGet(*get, createTracker(get, BUCKET));
    assert(tracker->getResult() == api::ReturnCode::Result::OK);

    auto & reply = static_cast<api::GetReply &>(tracker->getReply());
    assert(reply.wasFound());

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

    auto put = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestamp);
    fetchResult(asyncHandler->handlePut(*put, createTracker(put, BUCKET)));
}

void TestAndSetTest::assertTestDocumentFoundAndMatchesContent(const document::FieldValue & value)
{
    auto doc = retrieveTestDocument();
    auto & field = doc->getField("content");

    EXPECT_EQ(*doc->getValue(field), value);
}

std::string TestAndSetTest::expectedDocEntryString(
    api::Timestamp timestamp,
    const document::DocumentId & docId,
    spi::DocumentMetaFlags removeFlag)
{
    std::stringstream ss;

    ss << "DocEntry(" << timestamp << ", " << removeFlag << ", ";
    if (removeFlag == spi::REMOVE_ENTRY) {
        ss << docId << ")\n";
    } else {
       ss << "Doc(" << docId << "))\n";
    }

    return ss.str();
}

} // storage
