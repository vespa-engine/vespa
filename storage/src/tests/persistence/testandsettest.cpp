// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#include <tests/persistence/persistencetestutils.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/storage/persistence/persistencehandler.h>
#include <functional>

using std::unique_ptr;
using std::shared_ptr;

using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;
using document::StringFieldValue;
using documentapi::TestAndSetCondition;
using namespace ::testing;

namespace storage {

struct TestAndSetTest : PersistenceTestUtils {
    static constexpr int MIN_DOCUMENT_SIZE = 0;
    static constexpr int MAX_DOCUMENT_SIZE = 128;
    static constexpr int RANDOM_SEED = 1234;

    const document::BucketId BUCKET_ID{16, 4};
    const StringFieldValue MISMATCHING_HEADER{"Definitely nothing about loud canines"};
    const StringFieldValue MATCHING_HEADER{"Some string with woofy dog as a substring"};
    const StringFieldValue OLD_CONTENT{"Some old content"};
    const StringFieldValue NEW_CONTENT{"Freshly pressed and squeezed content"};
    const document::Bucket BUCKET = makeDocumentBucket(BUCKET_ID);
    const TestAndSetCondition MATCHING_CONDITION{"testdoctype1.hstringval=\"*woofy dog*\""};

    unique_ptr<PersistenceHandler> persistenceHandler;
    const AsyncHandler * asyncHandler;
    const SimpleMessageHandler* simple_handler;
    shared_ptr<document::Document> testDoc;
    document::DocumentId testDocId;

    TestAndSetTest()
        : persistenceHandler(),
          asyncHandler(nullptr),
          simple_handler(nullptr)
    {}

    void SetUp() override {
        PersistenceTestUtils::SetUp();

        createBucket(BUCKET_ID);
        getPersistenceProvider().createBucket(makeSpiBucket(BUCKET_ID));

        testDoc = createTestDocument();
        testDocId = testDoc->getId();
        asyncHandler = &_persistenceHandler->asyncHandler();
        simple_handler = &_persistenceHandler->simpleMessageHandler();
    }

    void TearDown() override {
        PersistenceTestUtils::TearDown();
    }

    std::shared_ptr<api::UpdateCommand> conditional_update_test(
        bool createIfMissing,
        api::Timestamp updateTimestamp);

    document::Document::SP createTestDocument();
    document::Document::SP retrieveTestDocument();
    void setTestCondition(api::TestAndSetCommand & command);
    void putTestDocument(bool matchingHeader, api::Timestamp timestamp);
    std::shared_ptr<api::GetReply> invoke_conditional_get();
    void feed_remove_entry_with_timestamp(api::Timestamp timestamp);
    void assertTestDocumentFoundAndMatchesContent(const document::FieldValue & value);

    static std::string expectedDocEntryString(
        api::Timestamp timestamp,
        const document::DocumentId & testDocId,
        spi::DocumentMetaEnum removeFlag = spi::DocumentMetaEnum::NONE);
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
              expectedDocEntryString(timestampTwo, testDocId, spi::DocumentMetaEnum::REMOVE_ENTRY),
              dumpBucket(BUCKET_ID));
}

std::shared_ptr<api::UpdateCommand>
TestAndSetTest::conditional_update_test(bool createIfMissing, api::Timestamp updateTimestamp)
{
    auto docUpdate = std::make_shared<document::DocumentUpdate>(_env->_testDocMan.getTypeRepo(), testDoc->getType(), testDocId);
    docUpdate->addUpdate(document::FieldUpdate(testDoc->getField("content")).addUpdate(std::make_unique<document::AssignValueUpdate>(std::make_unique<StringFieldValue>(NEW_CONTENT))));
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

TEST_F(TestAndSetTest, document_selection_with_imported_field_should_fail_with_illegal_parameters) {
    api::Timestamp timestamp = 0;
    auto put = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestamp);
    put->setCondition(documentapi::TestAndSetCondition("testdoctype1.my_imported_field == null"));

    ASSERT_EQ(fetchResult(asyncHandler->handlePut(*put, createTracker(put, BUCKET))),
              api::ReturnCode(api::ReturnCode::Result::ILLEGAL_PARAMETERS,
                              "Condition field 'my_imported_field' could not be found, or is an imported field. "
                              "Imported fields are not supported in conditional mutations."));
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

TEST_F(TestAndSetTest, conditional_get_returns_doc_metadata_on_match) {
    const api::Timestamp timestamp = 12345;
    putTestDocument(true, timestamp);
    auto reply = invoke_conditional_get();

    ASSERT_EQ(reply->getResult(), api::ReturnCode());
    EXPECT_EQ(reply->getLastModifiedTimestamp(), timestamp);
    EXPECT_TRUE(reply->condition_matched());
    EXPECT_FALSE(reply->is_tombstone());
    // Checking reply->wasFound() is tempting but doesn't make sense here, as that checks for
    // the presence of a document object, which metadata-only gets by definition do not return.
}

TEST_F(TestAndSetTest, conditional_get_returns_doc_metadata_on_mismatch) {
    const api::Timestamp timestamp = 12345;
    putTestDocument(false, timestamp);
    auto reply = invoke_conditional_get();

    ASSERT_EQ(reply->getResult(), api::ReturnCode());
    EXPECT_EQ(reply->getLastModifiedTimestamp(), timestamp);
    EXPECT_FALSE(reply->condition_matched());
    EXPECT_FALSE(reply->is_tombstone());
}

TEST_F(TestAndSetTest, conditional_get_for_non_existing_document_returns_zero_timestamp) {
    auto reply = invoke_conditional_get();

    ASSERT_EQ(reply->getResult(), api::ReturnCode());
    EXPECT_EQ(reply->getLastModifiedTimestamp(), 0);
    EXPECT_FALSE(reply->condition_matched());
    EXPECT_FALSE(reply->is_tombstone());
}

TEST_F(TestAndSetTest, conditional_get_for_non_existing_document_with_explicit_tombstone_returns_tombstone_timestamp) {
    api::Timestamp timestamp = 56789;
    feed_remove_entry_with_timestamp(timestamp);
    auto reply = invoke_conditional_get();

    ASSERT_EQ(reply->getResult(), api::ReturnCode());
    EXPECT_EQ(reply->getLastModifiedTimestamp(), timestamp);
    EXPECT_FALSE(reply->condition_matched());
    EXPECT_TRUE(reply->is_tombstone());
}

TEST_F(TestAndSetTest, conditional_get_requires_metadata_only_fieldset) {
    auto get = std::make_shared<api::GetCommand>(BUCKET, testDocId, document::AllFields::NAME);
    get->set_condition(MATCHING_CONDITION);
    // Note: uses fetchResult instead of fetch_single_reply due to implicit failure signalling via tracker instance.
    auto result = fetchResult(simple_handler->handleGet(*get, createTracker(get, BUCKET)));
    ASSERT_EQ(result, api::ReturnCode(api::ReturnCode::ILLEGAL_PARAMETERS,
                                      "Conditional Get operations must be metadata-only"));
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

    auto& reply = dynamic_cast<api::GetReply&>(tracker->getReply());
    assert(reply.wasFound());

    return reply.getDocument();
}

void TestAndSetTest::setTestCondition(api::TestAndSetCommand & command)
{
    command.setCondition(MATCHING_CONDITION);
}

void TestAndSetTest::putTestDocument(bool matchingHeader, api::Timestamp timestamp) {
    if (matchingHeader) {
        testDoc->setValue(testDoc->getField("hstringval"), MATCHING_HEADER);
    }

    auto put = std::make_shared<api::PutCommand>(BUCKET, testDoc, timestamp);
    fetchResult(asyncHandler->handlePut(*put, createTracker(put, BUCKET)));
}

std::shared_ptr<api::GetReply> TestAndSetTest::invoke_conditional_get() {
    auto get = std::make_shared<api::GetCommand>(BUCKET, testDocId, document::NoFields::NAME);
    get->set_condition(MATCHING_CONDITION);
    return fetch_single_reply<api::GetReply>(simple_handler->handleGet(*get, createTracker(get, BUCKET)));
}

void TestAndSetTest::feed_remove_entry_with_timestamp(api::Timestamp timestamp) {
    auto remove = std::make_shared<api::RemoveCommand>(BUCKET, testDocId, timestamp);
    (void)fetchResult(asyncHandler->handleRemove(*remove, createTracker(remove, BUCKET)));
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
    spi::DocumentMetaEnum removeFlag)
{
    std::stringstream ss;

    ss << "DocEntry(" << timestamp << ", " << int(removeFlag) << ", ";
    if (removeFlag == spi::DocumentMetaEnum::REMOVE_ENTRY) {
        ss << docId << ")\n";
    } else {
       ss << "Doc(" << docId << "))\n";
    }

    return ss.str();
}

} // storage
