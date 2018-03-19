// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/persistence/messages.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/stllike/hash_set_insert.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".operationabortingtest");

using document::test::makeDocumentBucket;

namespace storage {

namespace {

// Exploit the fact that PersistenceProviderWrapper already provides a forwarding
// implementation of all SPI calls, so we can selectively override.
class BlockingMockProvider : public PersistenceProviderWrapper
{
    vespalib::Barrier& _queueBarrier;
    vespalib::Barrier& _completionBarrier;
public:
    typedef std::unique_ptr<BlockingMockProvider> UP;

    mutable uint32_t _bucketInfoInvocations;
    uint32_t _createBucketInvocations;
    uint32_t _deleteBucketInvocations;

    BlockingMockProvider(spi::PersistenceProvider& wrappedProvider,
                         vespalib::Barrier& queueBarrier,
                         vespalib::Barrier& completionBarrier)
        : PersistenceProviderWrapper(wrappedProvider),
          _queueBarrier(queueBarrier),
          _completionBarrier(completionBarrier),
          _bucketInfoInvocations(0),
          _createBucketInvocations(0),
          _deleteBucketInvocations(0)
    {}

    spi::Result put(const spi::Bucket& bucket, spi::Timestamp timestamp,
                    const document::Document::SP& doc, spi::Context& context) override
    {
        (void) bucket;
        (void) timestamp;
        (void) doc;
        (void) context;
        _queueBarrier.await();
        // message abort stage with active opertion in disk queue
        FastOS_Thread::Sleep(75);
        _completionBarrier.await();
        // test finished
        return spi::Result();
    }

    spi::BucketInfoResult getBucketInfo(const spi::Bucket& bucket) const override {
        ++_bucketInfoInvocations;
        return PersistenceProviderWrapper::getBucketInfo(bucket);
    }

    spi::Result createBucket(const spi::Bucket& bucket, spi::Context& ctx) override {
        ++_createBucketInvocations;
        return PersistenceProviderWrapper::createBucket(bucket, ctx);
    }

    spi::Result deleteBucket(const spi::Bucket& bucket, spi::Context& ctx) override {
        ++_deleteBucketInvocations;
        return PersistenceProviderWrapper::deleteBucket(bucket, ctx);
    }
};

spi::LoadType defaultLoadType(0, "default");

}

class OperationAbortingTest : public FileStorTestFixture
{
public:
    spi::PersistenceProvider::UP _dummyProvider;
    BlockingMockProvider* _blockingProvider;
    std::unique_ptr<vespalib::Barrier> _queueBarrier;
    std::unique_ptr<vespalib::Barrier> _completionBarrier;

    void setupDisks(uint32_t diskCount, uint32_t queueBarrierThreads) {
        FileStorTestFixture::setupDisks(diskCount);
        _dummyProvider.reset(new spi::dummy::DummyPersistence(_node->getTypeRepo(), diskCount));
        _queueBarrier.reset(new vespalib::Barrier(queueBarrierThreads));
        _completionBarrier.reset(new vespalib::Barrier(2));
        _blockingProvider = new BlockingMockProvider(*_dummyProvider, *_queueBarrier, *_completionBarrier);
        _node->setPersistenceProvider(spi::PersistenceProvider::UP(_blockingProvider));
    }

    void validateReplies(DummyStorageLink& link, size_t repliesTotal,
                         const std::vector<document::BucketId>& okReplies,
                         const std::vector<document::BucketId>& abortedGetDiffs);

    void doTestSpecificOperationsNotAborted(const char* testName,
                                            const std::vector<api::StorageMessage::SP>& msgs,
                                            bool shouldCreateBucketInitially);

    api::BucketInfo getBucketInfoFromDB(const document::BucketId&) const;

public:
    void testAbortMessageClearsRelevantQueuedOperations();
    void testWaitForCurrentOperationCompletionForAbortedBucket();
    void testDoNotAbortCreateBucketCommands();
    void testDoNotAbortRecheckBucketCommands();
    void testDoNotAbortDeleteBucketCommands();

    void setUp() override;

    CPPUNIT_TEST_SUITE(OperationAbortingTest);
    CPPUNIT_TEST(testAbortMessageClearsRelevantQueuedOperations);
    CPPUNIT_TEST(testWaitForCurrentOperationCompletionForAbortedBucket);
    CPPUNIT_TEST(testDoNotAbortCreateBucketCommands);
    CPPUNIT_TEST(testDoNotAbortRecheckBucketCommands);
    CPPUNIT_TEST(testDoNotAbortDeleteBucketCommands);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(OperationAbortingTest);

namespace {

template <typename T, typename Collection>
bool
existsIn(const T& elem, const Collection& collection) {
    return (std::find(collection.begin(), collection.end(), elem) != collection.end());
}

}

void
OperationAbortingTest::setUp()
{
}

void
OperationAbortingTest::validateReplies(DummyStorageLink& link, size_t repliesTotal,
                                       const std::vector<document::BucketId>& okReplies,
                                       const std::vector<document::BucketId>& abortedGetDiffs)
{
    link.waitForMessages(repliesTotal, MSG_WAIT_TIME);
    CPPUNIT_ASSERT_EQUAL(repliesTotal, link.getNumReplies());

    for (uint32_t i = 0; i < repliesTotal; ++i) {
        api::StorageReply& reply(dynamic_cast<api::StorageReply&>(*link.getReply(i)));
        LOG(info, "Checking reply %s", reply.toString(true).c_str());
        switch (static_cast<uint32_t>(reply.getType().getId())) {
        case api::MessageType::PUT_REPLY_ID:
        case api::MessageType::CREATEBUCKET_REPLY_ID:
        case api::MessageType::DELETEBUCKET_REPLY_ID:
        case api::MessageType::GET_REPLY_ID:
            CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, resultOf(reply));
            break;
        case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        {
            api::GetBucketDiffReply& gr(
                    static_cast<api::GetBucketDiffReply&>(reply));
            if (existsIn(gr.getBucketId(), abortedGetDiffs)) {
                CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED, resultOf(reply));
            } else {
                CPPUNIT_ASSERT(existsIn(gr.getBucketId(), okReplies));
                CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, resultOf(reply));
            }
            break;
        }
        case api::MessageType::INTERNAL_REPLY_ID:
            CPPUNIT_ASSERT_EQUAL(api::ReturnCode::OK, resultOf(reply));
            break;
        default:
            CPPUNIT_FAIL("got unknown reply type");
        }
    }
}

namespace {

class ExplicitBucketSetPredicate : public AbortBucketOperationsCommand::AbortPredicate {
    using BucketSet = vespalib::hash_set<document::BucketId, document::BucketId::hash>;
    BucketSet _bucketsToAbort;

    bool doShouldAbort(const document::Bucket &bucket) const override;
public:
    ~ExplicitBucketSetPredicate();

    template <typename Iterator>
    ExplicitBucketSetPredicate(Iterator first, Iterator last)
        : _bucketsToAbort(first, last)
    { }

    const BucketSet& getBucketsToAbort() const {
        return _bucketsToAbort;
    }
};

bool
ExplicitBucketSetPredicate::doShouldAbort(const document::Bucket &bucket) const {
    return _bucketsToAbort.find(bucket.getBucketId()) != _bucketsToAbort.end();
}

ExplicitBucketSetPredicate::~ExplicitBucketSetPredicate() { }

template <typename Container>
AbortBucketOperationsCommand::SP
makeAbortCmd(const Container& buckets)
{
    auto pred = std::make_unique<ExplicitBucketSetPredicate>(buckets.begin(), buckets.end());
    return std::make_shared<AbortBucketOperationsCommand>(std::move(pred));
}

}

void
OperationAbortingTest::testAbortMessageClearsRelevantQueuedOperations()
{
    setupDisks(1, 2);
    TestFileStorComponents c(*this, "testAbortMessageClearsRelevantQueuedOperations");
    document::BucketId bucket(16, 1);
    createBucket(bucket);
    LOG(info, "Sending put to trigger thread barrier");
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    LOG(info, "waiting for test and persistence thread to reach barriers");
    _queueBarrier->await();
    LOG(info, "barrier passed");
    /*
     * All load we send down to filestor from now on wil be enqueued, as the
     * persistence thread is blocked.
     *
     * Cannot abort the bucket we're blocking the thread on since we'll
     * deadlock the test if we do.
     */
    std::vector<document::BucketId> bucketsToAbort;
    bucketsToAbort.push_back(document::BucketId(16, 3));
    bucketsToAbort.push_back(document::BucketId(16, 5));
    std::vector<document::BucketId> bucketsToKeep;
    bucketsToKeep.push_back(document::BucketId(16, 2));
    bucketsToKeep.push_back(document::BucketId(16, 4));

    for (uint32_t i = 0; i < bucketsToAbort.size(); ++i) {
        createBucket(bucketsToAbort[i]);
        c.sendDummyGetDiff(bucketsToAbort[i]);
    }
    for (uint32_t i = 0; i < bucketsToKeep.size(); ++i) {
        createBucket(bucketsToKeep[i]);
        c.sendDummyGetDiff(bucketsToKeep[i]);
    }

    AbortBucketOperationsCommand::SP abortCmd(makeAbortCmd(bucketsToAbort));
    c.top.sendDown(abortCmd);

    LOG(info, "waiting on completion barrier");
    _completionBarrier->await();

    // put+abort+get replies
    size_t expectedMsgs(2 + bucketsToAbort.size() + bucketsToKeep.size());
    LOG(info, "barrier passed, waiting for %zu replies", expectedMsgs);

    validateReplies(c.top, expectedMsgs, bucketsToKeep, bucketsToAbort);
}

namespace {

/**
 * Sending an abort while we're processing a message for a bucket in its set
 * will block until the operation has completed. Therefore we logically cannot
 * do any operations to trigger the operation to complete after the send in
 * the same thread as we're sending in...
 */
class SendTask : public vespalib::Runnable
{
    AbortBucketOperationsCommand::SP _abortCmd;
    vespalib::Barrier& _queueBarrier;
    StorageLink& _downLink;
public:
    SendTask(const AbortBucketOperationsCommand::SP& abortCmd,
             vespalib::Barrier& queueBarrier,
             StorageLink& downLink)
        : _abortCmd(abortCmd),
          _queueBarrier(queueBarrier),
          _downLink(downLink)
    {}

    void run() override {
        // Best-effort synchronized starting
        _queueBarrier.await();
        _downLink.sendDown(_abortCmd);
    }
};

}

/**
 * This test basically is not fully deterministic in that it tests cross-thread
 * behavior on mutexes that are not visible to the thread itself and where there
 * are no available side-effects to consistently sync around. However, it should
 * impose sufficient ordering guarantees that it never provides false positives
 * as long as the tested functionality is in fact correct.
 */
void
OperationAbortingTest::testWaitForCurrentOperationCompletionForAbortedBucket()
{
    setupDisks(1, 3);
    TestFileStorComponents c(*this, "testWaitForCurrentOperationCompletionForAbortedBucket");

    document::BucketId bucket(16, 1);
    createBucket(bucket);
    LOG(info, "Sending put to trigger thread barrier");
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));

    std::vector<document::BucketId> abortSet { bucket };
    AbortBucketOperationsCommand::SP abortCmd(makeAbortCmd(abortSet));

    SendTask sendTask(abortCmd, *_queueBarrier, c.top);
    vespalib::Thread thread(sendTask);
    thread.start();

    LOG(info, "waiting for threads to reach barriers");
    _queueBarrier->await();
    LOG(info, "barrier passed");

    LOG(info, "waiting on completion barrier");
    _completionBarrier->await();

    thread.stop();
    thread.join();

    // If waiting works, put reply shall always be ordered before the internal
    // reply, as it must finish processing fully before the abort returns.
    c.top.waitForMessages(2, MSG_WAIT_TIME);
    CPPUNIT_ASSERT_EQUAL(size_t(2), c.top.getNumReplies());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::PUT_REPLY, c.top.getReply(0)->getType());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::INTERNAL_REPLY, c.top.getReply(1)->getType());
}

void
OperationAbortingTest::testDoNotAbortCreateBucketCommands()
{
    document::BucketId bucket(16, 1);
    std::vector<api::StorageMessage::SP> msgs;
    msgs.push_back(api::StorageMessage::SP(new api::CreateBucketCommand(makeDocumentBucket(bucket))));

    bool shouldCreateBucketInitially(false);
    doTestSpecificOperationsNotAborted("testDoNotAbortCreateBucketCommands", msgs, shouldCreateBucketInitially);
}

void
OperationAbortingTest::testDoNotAbortRecheckBucketCommands()
{
    document::BucketId bucket(16, 1);
    std::vector<api::StorageMessage::SP> msgs;
    msgs.push_back(api::StorageMessage::SP(new RecheckBucketInfoCommand(makeDocumentBucket(bucket))));

    bool shouldCreateBucketInitially(true);
    doTestSpecificOperationsNotAborted("testDoNotAbortRecheckBucketCommands", msgs, shouldCreateBucketInitially);
}

api::BucketInfo
OperationAbortingTest::getBucketInfoFromDB(const document::BucketId& id) const
{
    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(id, "foo", StorBucketDatabase::CREATE_IF_NONEXISTING));
    CPPUNIT_ASSERT(entry.exist());
    return entry->info;
}

void
OperationAbortingTest::testDoNotAbortDeleteBucketCommands()
{
    document::BucketId bucket(16, 1);
    std::vector<api::StorageMessage::SP> msgs;
    api::DeleteBucketCommand::SP cmd(new api::DeleteBucketCommand(makeDocumentBucket(bucket)));
    msgs.push_back(cmd);

    bool shouldCreateBucketInitially(true);
    doTestSpecificOperationsNotAborted("testDoNotAbortRecheckBucketCommands", msgs, shouldCreateBucketInitially);
}

void
OperationAbortingTest::doTestSpecificOperationsNotAborted(const char* testName,
                                                          const std::vector<api::StorageMessage::SP>& msgs,
                                                          bool shouldCreateBucketInitially)
{
    setupDisks(1, 2);
    TestFileStorComponents c(*this, testName);
    document::BucketId bucket(16, 1);
    document::BucketId blockerBucket(16, 2);    

    if (shouldCreateBucketInitially) {
        createBucket(bucket);
    }
    createBucket(blockerBucket);
    LOG(info, "Sending put to trigger thread barrier");
    c.sendPut(blockerBucket, DocumentIndex(0), PutTimestamp(1000));
    LOG(info, "waiting for test and persistence thread to reach barriers");
    _queueBarrier->await();
    LOG(info, "barrier passed");

    uint32_t expectedCreateBuckets = 0;
    uint32_t expectedDeleteBuckets = 0;
    uint32_t expectedBucketInfoInvocations = 1; // from blocker put
    uint32_t expectedRecheckReplies = 0;

    for (uint32_t i = 0; i < msgs.size(); ++i) {
        switch (msgs[i]->getType().getId()) {
        case api::MessageType::CREATEBUCKET_ID:
            ++expectedCreateBuckets;
            break;
        case api::MessageType::DELETEBUCKET_ID:
            {
                api::DeleteBucketCommand& delCmd(dynamic_cast<api::DeleteBucketCommand&>(*msgs[i]));
                delCmd.setBucketInfo(getBucketInfoFromDB(delCmd.getBucketId()));
            }
            ++expectedDeleteBuckets;
            ++expectedBucketInfoInvocations;
            break;
        case api::MessageType::INTERNAL_ID:
            ++expectedRecheckReplies;
            ++expectedBucketInfoInvocations;
            break;
        default:
            CPPUNIT_FAIL("unsupported message type");
        }
        c.top.sendDown(msgs[i]);
    }

    std::vector<document::BucketId> abortSet { bucket };
    AbortBucketOperationsCommand::SP abortCmd(makeAbortCmd(abortSet));
    c.top.sendDown(abortCmd);

    LOG(info, "waiting on completion barrier");
    _completionBarrier->await();

    // At this point, the recheck command is still either enqueued, is processing
    // or has finished. Since it does not generate any replies, send a low priority
    // get which will wait until it has finished processing.
    c.sendDummyGet(blockerBucket);

    // put+abort+get + any other creates/deletes/rechecks
    size_t expectedMsgs(3 + expectedCreateBuckets + expectedDeleteBuckets + expectedRecheckReplies);
    LOG(info, "barrier passed, waiting for %zu replies", expectedMsgs);

    std::vector<document::BucketId> okReplies;
    okReplies.push_back(bucket);
    okReplies.push_back(blockerBucket);
    std::vector<document::BucketId> abortedGetDiffs;
    validateReplies(c.top, expectedMsgs, okReplies, abortedGetDiffs);

    CPPUNIT_ASSERT_EQUAL(expectedBucketInfoInvocations, _blockingProvider->_bucketInfoInvocations);
    CPPUNIT_ASSERT_EQUAL(expectedCreateBuckets + (shouldCreateBucketInitially ? 2 : 1),
                         _blockingProvider->_createBucketInvocations);
    CPPUNIT_ASSERT_EQUAL(expectedDeleteBuckets, _blockingProvider->_deleteBucketInvocations);
}
    
} // storage
