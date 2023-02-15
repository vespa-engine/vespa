// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/bucket.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <tests/persistence/common/filestortestfixture.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/stllike/hash_set_insert.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".operationabortingtest");

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

namespace {

VESPA_THREAD_STACK_TAG(test_thread);

// Exploit the fact that PersistenceProviderWrapper already provides a forwarding
// implementation of all SPI calls, so we can selectively override.
class BlockingMockProvider : public PersistenceProviderWrapper
{
    vespalib::Barrier& _queueBarrier;
    vespalib::Barrier& _completionBarrier;
public:
    using UP = std::unique_ptr<BlockingMockProvider>;

    mutable std::atomic<uint32_t> _bucketInfoInvocations;
    std::atomic<uint32_t> _createBucketInvocations;
    std::atomic<uint32_t> _deleteBucketInvocations;

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

    void
    putAsync(const spi::Bucket&, spi::Timestamp, document::Document::SP, spi::OperationComplete::UP onComplete) override
    {
        _queueBarrier.await();
        // message abort stage with active opertion in disk queue
        std::this_thread::sleep_for(75ms);
        _completionBarrier.await();
        // test finished
        onComplete->onComplete(std::make_unique<spi::Result>());
    }

    spi::BucketInfoResult getBucketInfo(const spi::Bucket& bucket) const override {
        ++_bucketInfoInvocations;
        return PersistenceProviderWrapper::getBucketInfo(bucket);
    }

    void createBucketAsync(const spi::Bucket& bucket, spi::OperationComplete::UP onComplete) noexcept override {
        ++_createBucketInvocations;
        PersistenceProviderWrapper::createBucketAsync(bucket, std::move(onComplete));
    }

    void
    deleteBucketAsync(const spi::Bucket& bucket, spi::OperationComplete::UP onComplete) noexcept override {
        ++_deleteBucketInvocations;
        PersistenceProviderWrapper::deleteBucketAsync(bucket, std::move(onComplete));
    }
};

}

struct OperationAbortingTest : FileStorTestFixture {
    std::unique_ptr<spi::dummy::DummyPersistence> _dummyProvider;
    BlockingMockProvider * _blockingProvider;
    std::unique_ptr<vespalib::Barrier> _queueBarrier;
    std::unique_ptr<vespalib::Barrier> _completionBarrier;

    void setupProviderAndBarriers(uint32_t queueBarrierThreads) {
        FileStorTestFixture::setupPersistenceThreads(1);
        _dummyProvider = std::make_unique<spi::dummy::DummyPersistence>(_node->getTypeRepo());
        _dummyProvider->initialize();
        _queueBarrier = std::make_unique<vespalib::Barrier>(queueBarrierThreads);
        _completionBarrier = std::make_unique<vespalib::Barrier>(2);
        auto blockingProvider = std::make_unique<BlockingMockProvider>(*_dummyProvider, *_queueBarrier, *_completionBarrier);
        _blockingProvider = blockingProvider.get();
        _node->setPersistenceProvider(std::move(blockingProvider));
    }

    void validateReplies(DummyStorageLink& link, size_t repliesTotal,
                         const std::vector<document::BucketId>& okReplies,
                         const std::vector<document::BucketId>& abortedGetDiffs);

    void doTestSpecificOperationsNotAborted(const std::vector<api::StorageMessage::SP>& msgs,
                                            bool shouldCreateBucketInitially);

    api::BucketInfo getBucketInfoFromDB(const document::BucketId&) const;

    void SetUp() override;
};

namespace {

template <typename T, typename Collection>
bool
existsIn(const T& elem, const Collection& collection) {
    return (std::find(collection.begin(), collection.end(), elem) != collection.end());
}

}

void
OperationAbortingTest::SetUp()
{
}

void
OperationAbortingTest::validateReplies(DummyStorageLink& link, size_t repliesTotal,
                                       const std::vector<document::BucketId>& okReplies,
                                       const std::vector<document::BucketId>& abortedGetDiffs)
{
    link.waitForMessages(repliesTotal, MSG_WAIT_TIME);
    ASSERT_EQ(repliesTotal, link.getNumReplies());

    for (uint32_t i = 0; i < repliesTotal; ++i) {
        api::StorageReply& reply(dynamic_cast<api::StorageReply&>(*link.getReply(i)));
        LOG(debug, "Checking reply %s", reply.toString(true).c_str());
        switch (static_cast<uint32_t>(reply.getType().getId())) {
        case api::MessageType::PUT_REPLY_ID:
        case api::MessageType::CREATEBUCKET_REPLY_ID:
        case api::MessageType::DELETEBUCKET_REPLY_ID:
        case api::MessageType::GET_REPLY_ID:
            ASSERT_EQ(api::ReturnCode::OK, resultOf(reply));
            break;
        case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        {
            auto& gr = static_cast<api::GetBucketDiffReply&>(reply);
            if (existsIn(gr.getBucketId(), abortedGetDiffs)) {
                ASSERT_EQ(api::ReturnCode::ABORTED, resultOf(reply));
            } else {
                ASSERT_TRUE(existsIn(gr.getBucketId(), okReplies));
                ASSERT_EQ(api::ReturnCode::OK, resultOf(reply));
            }
            break;
        }
        case api::MessageType::INTERNAL_REPLY_ID:
            ASSERT_EQ(api::ReturnCode::OK, resultOf(reply));
            break;
        default:
            FAIL() << "got unknown reply type";
        }
    }
}

namespace {

class ExplicitBucketSetPredicate : public AbortBucketOperationsCommand::AbortPredicate {
    using BucketSet = vespalib::hash_set<document::BucketId, document::BucketId::hash>;
    BucketSet _bucketsToAbort;

    bool doShouldAbort(const document::Bucket &bucket) const override;
public:
    ~ExplicitBucketSetPredicate() override;

    template <typename Iterator>
    ExplicitBucketSetPredicate(Iterator first, Iterator last)
        : _bucketsToAbort(first, last)
    {}

    const BucketSet& getBucketsToAbort() const {
        return _bucketsToAbort;
    }
};

bool
ExplicitBucketSetPredicate::doShouldAbort(const document::Bucket &bucket) const {
    return _bucketsToAbort.find(bucket.getBucketId()) != _bucketsToAbort.end();
}

ExplicitBucketSetPredicate::~ExplicitBucketSetPredicate() = default;

template <typename Container>
AbortBucketOperationsCommand::SP
makeAbortCmd(const Container& buckets)
{
    auto pred = std::make_unique<ExplicitBucketSetPredicate>(buckets.begin(), buckets.end());
    return std::make_shared<AbortBucketOperationsCommand>(std::move(pred));
}

}

TEST_F(OperationAbortingTest, abort_message_clears_relevant_queued_operations) {
    setupProviderAndBarriers(2);
    TestFileStorComponents c(*this);
    document::BucketId bucket(16, 1);
    createBucket(bucket);
    LOG(debug, "Sending put to trigger thread barrier");
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));
    LOG(debug, "waiting for test and persistence thread to reach barriers");
    _queueBarrier->await();
    LOG(debug, "barrier passed");
    /*
     * All load we send down to filestor from now on wil be enqueued, as the
     * persistence thread is blocked.
     *
     * Cannot abort the bucket we're blocking the thread on since we'll
     * deadlock the test if we do.
     */
    std::vector<document::BucketId> bucketsToAbort = {
        document::BucketId(16, 3),
        document::BucketId(16, 5)
    };
    std::vector<document::BucketId> bucketsToKeep = {
        document::BucketId(16, 2),
        document::BucketId(16, 4)
    };

    for (uint32_t i = 0; i < bucketsToAbort.size(); ++i) {
        createBucket(bucketsToAbort[i]);
        c.sendDummyGetDiff(bucketsToAbort[i]);
    }
    for (uint32_t i = 0; i < bucketsToKeep.size(); ++i) {
        createBucket(bucketsToKeep[i]);
        c.sendDummyGetDiff(bucketsToKeep[i]);
    }

    auto abortCmd = makeAbortCmd(bucketsToAbort);
    c.top.sendDown(abortCmd);

    LOG(debug, "waiting on completion barrier");
    _completionBarrier->await();

    // put+abort+get replies
    size_t expectedMsgs(2 + bucketsToAbort.size() + bucketsToKeep.size());
    LOG(debug, "barrier passed, waiting for %zu replies", expectedMsgs);

    ASSERT_NO_FATAL_FAILURE(validateReplies(c.top, expectedMsgs, bucketsToKeep, bucketsToAbort));
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
TEST_F(OperationAbortingTest, wait_for_current_operation_completion_for_aborted_bucket) {
    setupProviderAndBarriers(3);
    TestFileStorComponents c(*this);

    document::BucketId bucket(16, 1);
    createBucket(bucket);
    LOG(debug, "Sending put to trigger thread barrier");
    c.sendPut(bucket, DocumentIndex(0), PutTimestamp(1000));

    std::vector<document::BucketId> abortSet { bucket };
    auto abortCmd = makeAbortCmd(abortSet);

    SendTask sendTask(abortCmd, *_queueBarrier, c.top);
    auto thread = vespalib::thread::start(sendTask, test_thread);

    LOG(debug, "waiting for threads to reach barriers");
    _queueBarrier->await();
    LOG(debug, "barrier passed");

    LOG(debug, "waiting on completion barrier");
    _completionBarrier->await();

    thread.join();

    // If waiting works, put reply shall always be ordered before the internal
    // reply, as it must finish processing fully before the abort returns.
    c.top.waitForMessages(2, MSG_WAIT_TIME);
    ASSERT_EQ(2, c.top.getNumReplies());
    EXPECT_EQ(api::MessageType::PUT_REPLY, c.top.getReply(0)->getType());
    EXPECT_EQ(api::MessageType::INTERNAL_REPLY, c.top.getReply(1)->getType());
}

TEST_F(OperationAbortingTest, do_not_abort_create_bucket_commands) {
    document::BucketId bucket(16, 1);
    std::vector<api::StorageMessage::SP> msgs;
    msgs.emplace_back(std::make_shared<api::CreateBucketCommand>(makeDocumentBucket(bucket)));

    bool shouldCreateBucketInitially = false;
    doTestSpecificOperationsNotAborted(msgs, shouldCreateBucketInitially);
}

TEST_F(OperationAbortingTest, do_not_abort_recheck_bucket_commands) {
    document::BucketId bucket(16, 1);
    std::vector<api::StorageMessage::SP> msgs;
    msgs.emplace_back(std::make_shared<RecheckBucketInfoCommand>(makeDocumentBucket(bucket)));

    bool shouldCreateBucketInitially = true;
    doTestSpecificOperationsNotAborted(msgs, shouldCreateBucketInitially);
}

api::BucketInfo
OperationAbortingTest::getBucketInfoFromDB(const document::BucketId& id) const
{
    StorBucketDatabase::WrappedEntry entry(
            _node->getStorageBucketDatabase().get(id, "foo", StorBucketDatabase::CREATE_IF_NONEXISTING));
    assert(entry.exist());
    return entry->info;
}

TEST_F(OperationAbortingTest, do_not_abort_delete_bucket_commands) {
    document::BucketId bucket(16, 1);
    std::vector<api::StorageMessage::SP> msgs;
    msgs.emplace_back(std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucket)));

    bool shouldCreateBucketInitially = true;
    doTestSpecificOperationsNotAborted(msgs, shouldCreateBucketInitially);
}

void
OperationAbortingTest::doTestSpecificOperationsNotAborted(const std::vector<api::StorageMessage::SP>& msgs,
                                                          bool shouldCreateBucketInitially)
{
    setupProviderAndBarriers(2);
    TestFileStorComponents c(*this);
    document::BucketId bucket(16, 1);
    document::BucketId blockerBucket(16, 2);    

    if (shouldCreateBucketInitially) {
        createBucket(bucket);
    }
    createBucket(blockerBucket);
    LOG(debug, "Sending put to trigger thread barrier");
    c.sendPut(blockerBucket, DocumentIndex(0), PutTimestamp(1000));
    LOG(debug, "waiting for test and persistence thread to reach barriers");
    _queueBarrier->await();
    LOG(debug, "barrier passed");

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
                auto& delCmd = dynamic_cast<api::DeleteBucketCommand&>(*msgs[i]);
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
            FAIL() << "unsupported message type";
        }
        c.top.sendDown(msgs[i]);
    }

    std::vector<document::BucketId> abortSet { bucket };
    AbortBucketOperationsCommand::SP abortCmd(makeAbortCmd(abortSet));
    c.top.sendDown(abortCmd);

    LOG(debug, "waiting on completion barrier");
    _completionBarrier->await();

    // At this point, the recheck command is still either enqueued, is processing
    // or has finished. Since it does not generate any replies, send a low priority
    // get which will wait until it has finished processing.
    c.sendDummyGet(blockerBucket);

    // put+abort+get + any other creates/deletes/rechecks
    size_t expectedMsgs(3 + expectedCreateBuckets + expectedDeleteBuckets + expectedRecheckReplies);
    LOG(debug, "barrier passed, waiting for %zu replies", expectedMsgs);

    std::vector<document::BucketId> okReplies;
    okReplies.push_back(bucket);
    okReplies.push_back(blockerBucket);
    std::vector<document::BucketId> abortedGetDiffs;
    validateReplies(c.top, expectedMsgs, okReplies, abortedGetDiffs);

    ASSERT_EQ(expectedBucketInfoInvocations, _blockingProvider->_bucketInfoInvocations);
    ASSERT_EQ(expectedCreateBuckets + (shouldCreateBucketInitially ? 2 : 1),
              _blockingProvider->_createBucketInvocations);
    ASSERT_EQ(expectedDeleteBuckets, _blockingProvider->_deleteBucketInvocations);
}
    
} // storage
