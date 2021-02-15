// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/storage/persistence/mergehandler.h>
#include <vespa/storage/persistence/filestorage/mergestatus.h>
#include <tests/persistence/persistencetestutils.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <tests/common/message_sender_stub.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <gmock/gmock.h>
#include <cmath>

#include <vespa/log/log.h>
LOG_SETUP(".test.persistence.handler.merge");

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage {

struct MergeHandlerTest : SingleDiskPersistenceTestUtils {
    uint32_t _location; // Location used for all merge tests
    document::Bucket _bucket; // Bucket used for all merge tests
    uint64_t _maxTimestamp;
    std::vector<api::MergeBucketCommand::Node> _nodes;
    std::unique_ptr<spi::Context> _context;

    // Fetch a single command or reply; doesn't care which.
    template <typename T>
    std::shared_ptr<T> fetchSingleMessage();

    void SetUp() override;

    enum ChainPos { FRONT, MIDDLE, BACK };
    void setUpChain(ChainPos);

    void testGetBucketDiffChain(bool midChain);
    void testApplyBucketDiffChain(bool midChain);

    // @TODO Add test to test that buildBucketInfo and mergeLists create minimal list (wrong sorting screws this up)

    void fillDummyApplyDiff(std::vector<api::ApplyBucketDiffCommand::Entry>& diff);
    std::shared_ptr<api::ApplyBucketDiffCommand> createDummyApplyDiff(
            int timestampOffset,
            uint16_t hasMask = 0x1,
            bool filled = true);

    std::shared_ptr<api::GetBucketDiffCommand>
    createDummyGetBucketDiff(int timestampOffset,
                             uint16_t hasMask);

    struct ExpectedExceptionSpec // Try saying this out loud 3 times in a row.
    {
        uint32_t mask;
        const char* expected;
    };

    class HandlerInvoker
    {
    public:
        virtual ~HandlerInvoker() = default;
        virtual void beforeInvoke(MergeHandlerTest&, MergeHandler&, spi::Context&) {}
        virtual void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) = 0;
        virtual std::string afterInvoke(MergeHandlerTest&, MergeHandler&) = 0;
    };
    friend class HandlerInvoker;

    class NoReplyHandlerInvoker
        : public HandlerInvoker
    {
    public:
        std::string afterInvoke(MergeHandlerTest&, MergeHandler&) override;
    };

    template <typename ExpectedMessage>
    std::string checkMessage(api::ReturnCode::Result expectedResult);

    class HandleMergeBucketInvoker
        : public NoReplyHandlerInvoker
    {
    public:
        void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
    };

    class HandleMergeBucketReplyInvoker
        : public NoReplyHandlerInvoker
    {
    public:
        void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
    };

    class HandleGetBucketDiffInvoker
        : public NoReplyHandlerInvoker
    {
    public:
        void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
    };

    class MultiPositionHandlerInvoker
        : public HandlerInvoker
    {
    public:
        MultiPositionHandlerInvoker()
            : _pos(FRONT)
        {
        }
        void setChainPos(ChainPos pos) { _pos = pos; }
        ChainPos getChainPos() const { return _pos; }
    private:
        ChainPos _pos;
    };

    class HandleGetBucketDiffReplyInvoker
        : public HandlerInvoker
    {
    public:
        HandleGetBucketDiffReplyInvoker();
        ~HandleGetBucketDiffReplyInvoker() override;
        void beforeInvoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
        void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
        std::string afterInvoke(MergeHandlerTest&, MergeHandler&) override;
    private:
        MessageSenderStub _stub;
        std::shared_ptr<api::GetBucketDiffCommand> _diffCmd;
    };

    class HandleApplyBucketDiffInvoker
        : public NoReplyHandlerInvoker
    {
    public:
        HandleApplyBucketDiffInvoker() : _counter(0) {}
        void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
    private:
        int _counter;
    };

    class HandleApplyBucketDiffReplyInvoker
        : public MultiPositionHandlerInvoker
    {
    public:
        HandleApplyBucketDiffReplyInvoker();
        ~HandleApplyBucketDiffReplyInvoker() override;
        void beforeInvoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
        void invoke(MergeHandlerTest&, MergeHandler&, spi::Context&) override;
        std::string afterInvoke(MergeHandlerTest&, MergeHandler&) override;
    private:
        int _counter;
        MessageSenderStub _stub;
        std::shared_ptr<api::ApplyBucketDiffCommand> _applyCmd;
    };

    std::string
    doTestSPIException(MergeHandler& handler,
                       PersistenceProviderWrapper& providerWrapper,
                       HandlerInvoker& invoker,
                       const ExpectedExceptionSpec& spec);

    MergeHandler createHandler(size_t maxChunkSize = 0x400000) {
        return MergeHandler(getEnv(), getPersistenceProvider(),
                            getEnv()._component.cluster_context(), getEnv()._component.getClock(), maxChunkSize);
    }
    MergeHandler createHandler(spi::PersistenceProvider & spi) {
        return MergeHandler(getEnv(), spi,
                            getEnv()._component.cluster_context(), getEnv()._component.getClock());
    }
};

MergeHandlerTest::HandleGetBucketDiffReplyInvoker::HandleGetBucketDiffReplyInvoker() = default;
MergeHandlerTest::HandleGetBucketDiffReplyInvoker::~HandleGetBucketDiffReplyInvoker() = default;
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::HandleApplyBucketDiffReplyInvoker()
    : _counter(0),
      _stub(),
      _applyCmd()
{}
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::~HandleApplyBucketDiffReplyInvoker() = default;

void
MergeHandlerTest::SetUp() {
    _context = std::make_unique<spi::Context>(0, 0);
    SingleDiskPersistenceTestUtils::SetUp();

    _location = 1234;
    _bucket = makeDocumentBucket(document::BucketId(16, _location));
    _maxTimestamp = 11501;

    LOG(debug, "Creating %s in bucket database", _bucket.toString().c_str());
    bucketdb::StorageBucketInfo bucketDBEntry;
    getEnv().getBucketDatabase(_bucket.getBucketSpace()).insert(_bucket.getBucketId(), bucketDBEntry, "mergetestsetup");

    LOG(debug, "Creating bucket to merge");
    createTestBucket(_bucket);

    setUpChain(FRONT);
}

void
MergeHandlerTest::setUpChain(ChainPos pos) {
    _nodes.clear();
    if (pos != FRONT) {
        _nodes.push_back(api::MergeBucketCommand::Node(2, false));
    }
    _nodes.push_back(api::MergeBucketCommand::Node(0, false));
    if (pos != BACK) {
        _nodes.push_back(api::MergeBucketCommand::Node(1, false));
    }
}

// Test a regular merge bucket command fetching data, including
// puts, removes, unrevertable removes & duplicates.
TEST_F(MergeHandlerTest, merge_bucket_command) {
    MergeHandler handler = createHandler();

    LOG(debug, "Handle a merge bucket command");
    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    cmd->setSourceIndex(1234);
    MessageTracker::UP tracker = handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));

    LOG(debug, "Check state");
    ASSERT_EQ(1, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::GETBUCKETDIFF, messageKeeper()._msgs[0]->getType());
    auto& cmd2 = dynamic_cast<api::GetBucketDiffCommand&>(*messageKeeper()._msgs[0]);
    EXPECT_THAT(_nodes, ContainerEq(cmd2.getNodes()));
    auto diff = cmd2.getDiff();
    EXPECT_EQ(17, diff.size());
    EXPECT_EQ(1, cmd2.getAddress()->getIndex());
    EXPECT_EQ(1234, cmd2.getSourceIndex());

    tracker->generateReply(*cmd);
    EXPECT_FALSE(tracker->hasReply());
}

void
MergeHandlerTest::testGetBucketDiffChain(bool midChain)
{
    setUpChain(midChain ? MIDDLE : BACK);
    MergeHandler handler = createHandler();

    LOG(debug, "Verifying that get bucket diff is sent on");
    auto cmd = std::make_shared<api::GetBucketDiffCommand>(_bucket, _nodes, _maxTimestamp);
    MessageTracker::UP tracker1 = handler.handleGetBucketDiff(*cmd, createTracker(cmd, _bucket));
    api::StorageMessage::SP replySent = std::move(*tracker1).stealReplySP();

    if (midChain) {
        LOG(debug, "Check state");
        ASSERT_EQ(1, messageKeeper()._msgs.size());
        ASSERT_EQ(api::MessageType::GETBUCKETDIFF, messageKeeper()._msgs[0]->getType());
        auto& cmd2 = dynamic_cast<api::GetBucketDiffCommand&>(*messageKeeper()._msgs[0]);
        EXPECT_THAT(_nodes, ContainerEq(cmd2.getNodes()));
        auto diff = cmd2.getDiff();
        EXPECT_EQ(17, diff.size());
        EXPECT_EQ(1, cmd2.getAddress()->getIndex());

        LOG(debug, "Verifying that replying the diff sends on back");
        auto reply = std::make_unique<api::GetBucketDiffReply>(cmd2);

        ASSERT_FALSE(replySent.get());

        MessageSenderStub stub;
        handler.handleGetBucketDiffReply(*reply, stub);
        ASSERT_EQ(1, stub.replies.size());
        replySent = stub.replies[0];
    }
    auto reply2 = std::dynamic_pointer_cast<api::GetBucketDiffReply>(replySent);
    ASSERT_TRUE(reply2.get());

    EXPECT_THAT(_nodes, ContainerEq(reply2->getNodes()));
    auto diff = reply2->getDiff();
    EXPECT_EQ(17, diff.size());
}

TEST_F(MergeHandlerTest, get_bucket_diff_mid_chain) {
    testGetBucketDiffChain(true);
}

TEST_F(MergeHandlerTest, get_bucket_diff_end_of_chain) {
    testGetBucketDiffChain(false);
}

// Test that a simplistic merge with 1 doc to actually merge,
// sends apply bucket diff through the entire chain of 3 nodes.
void
MergeHandlerTest::testApplyBucketDiffChain(bool midChain)
{
    setUpChain(midChain ? MIDDLE : BACK);
    MergeHandler handler = createHandler();

    LOG(debug, "Verifying that apply bucket diff is sent on");
    auto cmd = std::make_shared<api::ApplyBucketDiffCommand>(_bucket, _nodes);
    MessageTracker::UP tracker1 = handler.handleApplyBucketDiff(*cmd, createTracker(cmd, _bucket));
    api::StorageMessage::SP replySent = std::move(*tracker1).stealReplySP();

    if (midChain) {
        LOG(debug, "Check state");
        ASSERT_EQ(1, messageKeeper()._msgs.size());
        ASSERT_EQ(api::MessageType::APPLYBUCKETDIFF, messageKeeper()._msgs[0]->getType());
        auto& cmd2 = dynamic_cast<api::ApplyBucketDiffCommand&>(*messageKeeper()._msgs[0]);
        EXPECT_THAT(_nodes, ContainerEq(cmd2.getNodes()));
        auto diff = cmd2.getDiff();
        EXPECT_EQ(0, diff.size());
        EXPECT_EQ(1, cmd2.getAddress()->getIndex());

        EXPECT_FALSE(replySent.get());

        LOG(debug, "Verifying that replying the diff sends on back");
        auto reply = std::make_unique<api::ApplyBucketDiffReply>(cmd2);

        MessageSenderStub stub;
        handler.handleApplyBucketDiffReply(*reply, stub);
        ASSERT_EQ(1, stub.replies.size());
        replySent = stub.replies[0];
    }

    auto reply2 = std::dynamic_pointer_cast<api::ApplyBucketDiffReply>(replySent);
    ASSERT_TRUE(reply2.get());

    EXPECT_THAT(_nodes, ContainerEq(reply2->getNodes()));
    auto diff = reply2->getDiff();
    EXPECT_EQ(0, diff.size());
}

TEST_F(MergeHandlerTest, apply_bucket_diff_mid_chain) {
    testApplyBucketDiffChain(true);
}

TEST_F(MergeHandlerTest, apply_bucket_diff_end_of_chain) {
    testApplyBucketDiffChain(false);
}

// Test that a simplistic merge with one thing to actually merge,
// sends correct commands and finish.
TEST_F(MergeHandlerTest, master_message_flow) {
    MergeHandler handler = createHandler();

    LOG(debug, "Handle a merge bucket command");
    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);

    handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));
    LOG(debug, "Check state");
    ASSERT_EQ(1, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::GETBUCKETDIFF, messageKeeper()._msgs[0]->getType());
    auto& cmd2 = dynamic_cast<api::GetBucketDiffCommand&>(*messageKeeper()._msgs[0]);

    auto reply = std::make_unique<api::GetBucketDiffReply>(cmd2);
    // End of chain can remove entries all have. This should end up with
    // one entry master node has other node don't have
    reply->getDiff().resize(1);

    handler.handleGetBucketDiffReply(*reply, messageKeeper());

    LOG(debug, "Check state");
    ASSERT_EQ(2, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::APPLYBUCKETDIFF, messageKeeper()._msgs[1]->getType());
    auto& cmd3 = dynamic_cast<api::ApplyBucketDiffCommand&>(*messageKeeper()._msgs[1]);
    auto reply2 = std::make_unique<api::ApplyBucketDiffReply>(cmd3);
    ASSERT_EQ(1, reply2->getDiff().size());
    reply2->getDiff()[0]._entry._hasMask |= 2u;

    MessageSenderStub stub;
    handler.handleApplyBucketDiffReply(*reply2, stub);

    ASSERT_EQ(1, stub.replies.size());

    auto reply3 = std::dynamic_pointer_cast<api::MergeBucketReply>(stub.replies[0]);
    ASSERT_TRUE(reply3.get());

    EXPECT_THAT(_nodes, ContainerEq(reply3->getNodes()));
    EXPECT_TRUE(reply3->getResult().success());
    EXPECT_FALSE(fsHandler().isMerging(_bucket));
}

template <typename T>
std::shared_ptr<T>
MergeHandlerTest::fetchSingleMessage()
{
    std::vector<api::StorageMessage::SP>& msgs(messageKeeper()._msgs);
    if (msgs.empty()) {
        std::ostringstream oss;
        oss << "No messages available to fetch (expected type "
            << typeid(T).name()
            << ")";
        throw std::runtime_error(oss.str());
    }
    std::shared_ptr<T> ret(std::dynamic_pointer_cast<T>(
                                     messageKeeper()._msgs.back()));
    if (!ret) {
        std::ostringstream oss;
        oss << "Expected message of type "
            << typeid(T).name()
            << ", but got "
            << messageKeeper()._msgs[0]->toString();
        throw std::runtime_error(oss.str());
    }
    messageKeeper()._msgs.pop_back();

    return ret;
}

namespace {

size_t
getFilledCount(const std::vector<api::ApplyBucketDiffCommand::Entry>& diff)
{
    size_t filledCount = 0;
    for (size_t i=0; i<diff.size(); ++i) {
        if (diff[i].filled()) {
            ++filledCount;
        }
    }
    return filledCount;
}

size_t
getFilledDataSize(const std::vector<api::ApplyBucketDiffCommand::Entry>& diff)
{
    size_t filledSize = 0;
    for (size_t i=0; i<diff.size(); ++i) {
        filledSize += diff[i]._headerBlob.size();
        filledSize += diff[i]._bodyBlob.size();
    }
    return filledSize;
}

}

TEST_F(MergeHandlerTest, chunked_apply_bucket_diff) {
    uint32_t docSize = 1024;
    uint32_t docCount = 10;
    uint32_t maxChunkSize = docSize * 3;
    for (uint32_t i = 0; i < docCount; ++i) {
        doPut(1234, spi::Timestamp(4000 + i), docSize, docSize);
    }

    MergeHandler handler = createHandler(maxChunkSize);

    LOG(debug, "Handle a merge bucket command");
    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));

    auto getBucketDiffCmd = fetchSingleMessage<api::GetBucketDiffCommand>();
    auto getBucketDiffReply = std::make_unique<api::GetBucketDiffReply>(*getBucketDiffCmd);

    handler.handleGetBucketDiffReply(*getBucketDiffReply, messageKeeper());

    uint32_t totalDiffs = getBucketDiffCmd->getDiff().size();
    std::set<spi::Timestamp> seen;

    api::MergeBucketReply::SP reply;
    while (seen.size() != totalDiffs) {
        auto applyBucketDiffCmd = fetchSingleMessage<api::ApplyBucketDiffCommand>();

        LOG(debug, "Test that we get chunked diffs in ApplyBucketDiff");
        auto& diff = applyBucketDiffCmd->getDiff();
        ASSERT_LT(getFilledCount(diff), totalDiffs);
        ASSERT_LE(getFilledDataSize(diff), maxChunkSize);

        // Include node 1 in hasmask for all diffs to indicate it's done
        // Also remember the diffs we've seen thus far to ensure chunking
        // does not send duplicates.
        for (size_t i = 0; i < diff.size(); ++i) {
            if (!diff[i].filled()) {
                continue;
            }
            diff[i]._entry._hasMask |= 2u;
            auto inserted = seen.emplace(spi::Timestamp(diff[i]._entry._timestamp));
            if (!inserted.second) {
                FAIL() << "Diff for " << diff[i]
                       << " has already been seen in another ApplyBucketDiff";
            }
        }

        auto applyBucketDiffReply = std::make_unique<api::ApplyBucketDiffReply>(*applyBucketDiffCmd);
        {
            handler.handleApplyBucketDiffReply(*applyBucketDiffReply, messageKeeper());

            if (!messageKeeper()._msgs.empty()) {
                ASSERT_FALSE(reply.get());
                reply = std::dynamic_pointer_cast<api::MergeBucketReply>(
                        messageKeeper()._msgs[messageKeeper()._msgs.size() - 1]);
            }
        }
    }
    LOG(debug, "Done with applying diff");

    ASSERT_TRUE(reply.get());
    EXPECT_THAT(_nodes, ContainerEq(reply->getNodes()));
    EXPECT_TRUE(reply->getResult().success());
}

TEST_F(MergeHandlerTest, chunk_limit_partially_filled_diff) {
    setUpChain(FRONT);

    uint32_t docSize = 1024;
    uint32_t docCount = 3;
    uint32_t maxChunkSize = 1024 + 1024 + 512;

    for (uint32_t i = 0; i < docCount; ++i) {
        doPut(1234, spi::Timestamp(4000 + i), docSize, docSize);
    }

    std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff;
    for (uint32_t i = 0; i < docCount; ++i) {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = 4000 + i;
        if (i == 0) {
            e._headerBlob.resize(docSize);
        }
        e._entry._hasMask = 0x3;
        e._entry._flags = MergeHandler::IN_USE;
        applyDiff.push_back(e);
    }

    setUpChain(MIDDLE);
    auto applyBucketDiffCmd = std::make_shared<api::ApplyBucketDiffCommand>(_bucket, _nodes);
    applyBucketDiffCmd->getDiff() = applyDiff;

    MergeHandler handler = createHandler(maxChunkSize);
    handler.handleApplyBucketDiff(*applyBucketDiffCmd, createTracker(applyBucketDiffCmd, _bucket));

    auto fwdDiffCmd = fetchSingleMessage<api::ApplyBucketDiffCommand>();
    // Should not fill up more than chunk size allows for
    EXPECT_EQ(2, getFilledCount(fwdDiffCmd->getDiff()));
    EXPECT_LE(getFilledDataSize(fwdDiffCmd->getDiff()), maxChunkSize);
}

TEST_F(MergeHandlerTest, max_timestamp) {
    doPut(1234, spi::Timestamp(_maxTimestamp + 10), 1024, 1024);

    MergeHandler handler = createHandler();

    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));

    auto getCmd = fetchSingleMessage<api::GetBucketDiffCommand>();

    ASSERT_FALSE(getCmd->getDiff().empty());
    EXPECT_LE(getCmd->getDiff().back()._timestamp, _maxTimestamp);
}

void
MergeHandlerTest::fillDummyApplyDiff(
        std::vector<api::ApplyBucketDiffCommand::Entry>& diff)
{
    document::TestDocMan docMan;
    document::Document::SP doc(docMan.createRandomDocumentAtLocation(_location));
    std::vector<char> headerBlob;
    {
        vespalib::nbostream stream;
        doc->serializeHeader(stream);
        headerBlob.resize(stream.size());
        memcpy(&headerBlob[0], stream.peek(), stream.size());
    }

    assert(diff.size() == 3);
    diff[0]._headerBlob = headerBlob;
    diff[1]._docName = doc->getId().toString();
    diff[2]._docName = doc->getId().toString();
}

std::shared_ptr<api::ApplyBucketDiffCommand>
MergeHandlerTest::createDummyApplyDiff(int timestampOffset,
                                       uint16_t hasMask,
                                       bool filled)
{

    std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff;
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = timestampOffset;
        e._entry._hasMask = hasMask;
        e._entry._flags = MergeHandler::IN_USE;
        applyDiff.push_back(e);
    }
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = timestampOffset + 1;
        e._entry._hasMask = hasMask;
        e._entry._flags = MergeHandler::IN_USE | MergeHandler::DELETED;
        applyDiff.push_back(e);
    }
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = timestampOffset + 2;
        e._entry._hasMask = hasMask;
        e._entry._flags = MergeHandler::IN_USE |
                          MergeHandler::DELETED |
                          MergeHandler::DELETED_IN_PLACE;
        applyDiff.push_back(e);
    }

    if (filled) {
        fillDummyApplyDiff(applyDiff);
    }

    auto applyBucketDiffCmd = std::make_shared<api::ApplyBucketDiffCommand>(_bucket, _nodes);
    applyBucketDiffCmd->getDiff() = applyDiff;
    return applyBucketDiffCmd;
}

// Must match up with diff used in createDummyApplyDiff
std::shared_ptr<api::GetBucketDiffCommand>
MergeHandlerTest::createDummyGetBucketDiff(int timestampOffset, uint16_t hasMask)
{
    std::vector<api::GetBucketDiffCommand::Entry> diff;
    {
        api::GetBucketDiffCommand::Entry e;
        e._timestamp = timestampOffset;
        e._hasMask = hasMask;
        e._flags = MergeHandler::IN_USE;
        diff.push_back(e);
    }
    {
        api::GetBucketDiffCommand::Entry e;
        e._timestamp = timestampOffset + 1;
        e._hasMask = hasMask;
        e._flags = MergeHandler::IN_USE | MergeHandler::DELETED;
        diff.push_back(e);
    }
    {
        api::GetBucketDiffCommand::Entry e;
        e._timestamp = timestampOffset + 2;
        e._hasMask = hasMask;
        e._flags = MergeHandler::IN_USE |
                   MergeHandler::DELETED |
                   MergeHandler::DELETED_IN_PLACE;
        diff.push_back(e);
    }

    auto getBucketDiffCmd = std::make_shared<api::GetBucketDiffCommand>(_bucket, _nodes, 1_Mi);
    getBucketDiffCmd->getDiff() = diff;
    return getBucketDiffCmd;
}

TEST_F(MergeHandlerTest, spi_flush_guard) {
    PersistenceProviderWrapper providerWrapper(getPersistenceProvider());
    MergeHandler handler = createHandler(providerWrapper);

    providerWrapper.setResult(
            spi::Result(spi::Result::ErrorType::PERMANENT_ERROR, "who you gonna call?"));

    setUpChain(MIDDLE);
    // Fail applying unrevertable remove
    providerWrapper.setFailureMask(PersistenceProviderWrapper::FAIL_REMOVE);
    providerWrapper.clearOperationLog();

    try {
        auto cmd = createDummyApplyDiff(6000);
        handler.handleApplyBucketDiff(*cmd, createTracker(cmd, _bucket));
        FAIL() << "No exception thrown on failing in-place remove";
    } catch (const std::runtime_error& e) {
        EXPECT_TRUE(std::string(e.what()).find("Failed remove") != std::string::npos);
    }
}

TEST_F(MergeHandlerTest, bucket_not_found_in_db) {
    MergeHandler handler = createHandler();
    // Send merge for unknown bucket
    auto cmd = std::make_shared<api::MergeBucketCommand>(makeDocumentBucket(document::BucketId(16, 6789)), _nodes, _maxTimestamp);
    MessageTracker::UP tracker = handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));
    EXPECT_TRUE(tracker->getResult().isBucketDisappearance());
}

TEST_F(MergeHandlerTest, merge_progress_safe_guard) {
    MergeHandler handler = createHandler();
    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));

    auto getBucketDiffCmd = fetchSingleMessage<api::GetBucketDiffCommand>();
    auto getBucketDiffReply = std::make_unique<api::GetBucketDiffReply>(*getBucketDiffCmd);

    handler.handleGetBucketDiffReply(*getBucketDiffReply, messageKeeper());

    auto applyBucketDiffCmd = fetchSingleMessage<api::ApplyBucketDiffCommand>();
    auto applyBucketDiffReply = std::make_unique<api::ApplyBucketDiffReply>(*applyBucketDiffCmd);

    MessageSenderStub stub;
    handler.handleApplyBucketDiffReply(*applyBucketDiffReply, stub);

    ASSERT_EQ(1, stub.replies.size());

    auto mergeReply = std::dynamic_pointer_cast<api::MergeBucketReply>(stub.replies[0]);
    ASSERT_TRUE(mergeReply.get());
    EXPECT_EQ(mergeReply->getResult().getResult(), api::ReturnCode::INTERNAL_FAILURE);
}

TEST_F(MergeHandlerTest, safe_guard_not_invoked_when_has_mask_changes) {
    MergeHandler handler = createHandler();
    _nodes.clear();
    _nodes.emplace_back(0, false);
    _nodes.emplace_back(1, false);
    _nodes.emplace_back(2, false);
    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));

    auto getBucketDiffCmd = fetchSingleMessage<api::GetBucketDiffCommand>();
    auto getBucketDiffReply = std::make_unique<api::GetBucketDiffReply>(*getBucketDiffCmd);

    handler.handleGetBucketDiffReply(*getBucketDiffReply, messageKeeper());

    auto applyBucketDiffCmd = fetchSingleMessage<api::ApplyBucketDiffCommand>();
    auto applyBucketDiffReply = std::make_unique<api::ApplyBucketDiffReply>(*applyBucketDiffCmd);
    ASSERT_FALSE(applyBucketDiffReply->getDiff().empty());
    // Change a hasMask to indicate something changed during merging.
    applyBucketDiffReply->getDiff()[0]._entry._hasMask = 0x5;

    MessageSenderStub stub;
    LOG(debug, "sending apply bucket diff reply");
    handler.handleApplyBucketDiffReply(*applyBucketDiffReply, stub);

    ASSERT_EQ(1, stub.commands.size());

    auto applyBucketDiffCmd2 = std::dynamic_pointer_cast<api::ApplyBucketDiffCommand>(stub.commands[0]);
    ASSERT_TRUE(applyBucketDiffCmd2.get());
    ASSERT_EQ(applyBucketDiffCmd->getDiff().size(), applyBucketDiffCmd2->getDiff().size());
    EXPECT_EQ(0x5, applyBucketDiffCmd2->getDiff()[0]._entry._hasMask);
}

TEST_F(MergeHandlerTest, entry_removed_after_get_bucket_diff) {
    MergeHandler handler = createHandler();
    std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff;
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = 13001; // Removed in persistence
        e._entry._hasMask = 0x2;
        e._entry._flags = MergeHandler::IN_USE;
        applyDiff.push_back(e);
    }
    setUpChain(BACK);
    auto applyBucketDiffCmd = std::make_shared<api::ApplyBucketDiffCommand>(_bucket, _nodes);
    applyBucketDiffCmd->getDiff() = applyDiff;

    auto tracker = handler.handleApplyBucketDiff(*applyBucketDiffCmd, createTracker(applyBucketDiffCmd, _bucket));

    auto applyBucketDiffReply = std::dynamic_pointer_cast<api::ApplyBucketDiffReply>(std::move(*tracker).stealReplySP());
    ASSERT_TRUE(applyBucketDiffReply.get());

    auto& diff = applyBucketDiffReply->getDiff();
    ASSERT_EQ(1, diff.size());
    EXPECT_FALSE(diff[0].filled());
    EXPECT_EQ(0x0, diff[0]._entry._hasMask);
}

std::string
MergeHandlerTest::doTestSPIException(MergeHandler& handler,
                                     PersistenceProviderWrapper& providerWrapper,
                                     HandlerInvoker& invoker,
                                     const ExpectedExceptionSpec& spec)
{
    providerWrapper.setFailureMask(0);
    invoker.beforeInvoke(*this, handler, *_context); // Do any setup stuff first

    uint32_t failureMask = spec.mask;
    const char* expectedSubstring = spec.expected;
    providerWrapper.setFailureMask(failureMask);
    try {
        invoker.invoke(*this, handler, *_context);
        if (failureMask != 0) {
            return (std::string("No exception was thrown during handler "
                                "invocation. Expected exception containing '")
                    + expectedSubstring + "'");
        }
    } catch (const std::runtime_error& e) {
        if (std::string(e.what()).find(expectedSubstring)
            == std::string::npos)
        {
            return (std::string("Expected exception to contain substring '")
                    + expectedSubstring + "', but message was: " + e.what());
        }
    }
    if (fsHandler().isMerging(_bucket)) {
        return (std::string("After operation with expected exception '")
                + expectedSubstring + "', merge state was not cleared");
    }
    // Postcondition check.
    std::string check = invoker.afterInvoke(*this, handler);
    if (!check.empty()) {
        return (std::string("Postcondition validation failed for operation "
                            "with expected exception '")
                + expectedSubstring + "': " + check);
    }
    return "";
}

std::string
MergeHandlerTest::NoReplyHandlerInvoker::afterInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler)
{
    (void) handler;
    if (!test.messageKeeper()._msgs.empty()) {
        std::ostringstream ss;
        ss << "Expected 0 explicit replies, got "
           << test.messageKeeper()._msgs.size();
        return ss.str();
    }
    return "";
}

template <typename ExpectedMessage>
std::string
MergeHandlerTest::checkMessage(api::ReturnCode::Result expectedResult)
{
    try {
        std::shared_ptr<ExpectedMessage> msg(
                fetchSingleMessage<ExpectedMessage>());
        if (msg->getResult().getResult() != expectedResult) {
            return "Got unexpected result: " + msg->getResult().toString();
        }
    } catch (std::exception& e) {
        return e.what();
    }
    return "";
}

void
MergeHandlerTest::HandleMergeBucketInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context&)
{
    auto cmd = std::make_shared<api::MergeBucketCommand>(test._bucket, test._nodes, test._maxTimestamp);
    handler.handleMergeBucket(*cmd, test.createTracker(cmd, test._bucket));
}

TEST_F(MergeHandlerTest, merge_bucket_spi_failures) {
    PersistenceProviderWrapper providerWrapper(getPersistenceProvider());
    MergeHandler handler = createHandler(providerWrapper);
    providerWrapper.setResult(
            spi::Result(spi::Result::ErrorType::PERMANENT_ERROR, "who you gonna call?"));
    setUpChain(MIDDLE);

    ExpectedExceptionSpec exceptions[] = {
        { PersistenceProviderWrapper::FAIL_CREATE_BUCKET, "create bucket" },
        { PersistenceProviderWrapper::FAIL_BUCKET_INFO, "get bucket info" },
        { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
        { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
    };
    typedef ExpectedExceptionSpec* ExceptionIterator;
    ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

    for (ExceptionIterator it = exceptions; it != last; ++it) {
        HandleMergeBucketInvoker invoker;
        EXPECT_EQ("", doTestSPIException(handler, providerWrapper, invoker, *it));
    }
}

void
MergeHandlerTest::HandleGetBucketDiffInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context& )
{
    auto cmd = std::make_shared<api::GetBucketDiffCommand>(test._bucket, test._nodes, test._maxTimestamp);
    handler.handleGetBucketDiff(*cmd, test.createTracker(cmd, test._bucket));
}

TEST_F(MergeHandlerTest, get_bucket_diff_spi_failures) {
    PersistenceProviderWrapper providerWrapper(getPersistenceProvider());
    MergeHandler handler = createHandler(providerWrapper);
    providerWrapper.setResult(spi::Result(spi::Result::ErrorType::PERMANENT_ERROR, "who you gonna call?"));
    setUpChain(MIDDLE);

    ExpectedExceptionSpec exceptions[] = {
        { PersistenceProviderWrapper::FAIL_CREATE_BUCKET, "create bucket" },
        { PersistenceProviderWrapper::FAIL_BUCKET_INFO, "get bucket info" },
        { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
        { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
    };

    typedef ExpectedExceptionSpec* ExceptionIterator;
    ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

    for (ExceptionIterator it = exceptions; it != last; ++it) {
        HandleGetBucketDiffInvoker invoker;
        EXPECT_EQ("", doTestSPIException(handler, providerWrapper, invoker, *it));
    }
}

void
MergeHandlerTest::HandleApplyBucketDiffInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context&)
{
    ++_counter;
    auto cmd = test.createDummyApplyDiff(100000 * _counter);
    handler.handleApplyBucketDiff(*cmd, test.createTracker(cmd, test._bucket));
}

TEST_F(MergeHandlerTest, apply_bucket_diff_spi_failures) {
    PersistenceProviderWrapper providerWrapper(getPersistenceProvider());
    MergeHandler handler = createHandler(providerWrapper);
    providerWrapper.setResult(
            spi::Result(spi::Result::ErrorType::PERMANENT_ERROR, "who you gonna call?"));
    setUpChain(MIDDLE);

    ExpectedExceptionSpec exceptions[] = {
        { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
        { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
        { PersistenceProviderWrapper::FAIL_PUT | PersistenceProviderWrapper::FAIL_REMOVE, "Failed put" },
        { PersistenceProviderWrapper::FAIL_REMOVE, "Failed remove" },
    };

    typedef ExpectedExceptionSpec* ExceptionIterator;
    ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

    for (ExceptionIterator it = exceptions; it != last; ++it) {
        HandleApplyBucketDiffInvoker invoker;
        EXPECT_EQ("", doTestSPIException(handler, providerWrapper, invoker, *it));
        // Casual, in-place testing of bug 6752085.
        // This will fail if we give NaN to the metric in question.
        EXPECT_TRUE(std::isfinite(getEnv()._metrics.merge_handler_metrics.mergeAverageDataReceivedNeeded.getLast()));
    }
}

void
MergeHandlerTest::HandleGetBucketDiffReplyInvoker::beforeInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context&)
{
    auto cmd = std::make_shared<api::MergeBucketCommand>(test._bucket, test._nodes, test._maxTimestamp);
    handler.handleMergeBucket(*cmd, test.createTracker(cmd, test._bucket));
    _diffCmd = test.fetchSingleMessage<api::GetBucketDiffCommand>();
}

void
MergeHandlerTest::HandleGetBucketDiffReplyInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context&)
{
    (void) test;
    api::GetBucketDiffReply reply(*_diffCmd);
    handler.handleGetBucketDiffReply(reply, _stub);
}

std::string
MergeHandlerTest::HandleGetBucketDiffReplyInvoker::afterInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler)
{
    (void) handler;
    if (!_stub.commands.empty()) {
        return "Unexpected commands in reply stub";
    }
    if (!_stub.replies.empty()) {
        return "Unexpected replies in reply stub";
    }
    // Initial merge bucket should have been replied to by clearMergeStatus.
    return test.checkMessage<api::MergeBucketReply>(
            api::ReturnCode::INTERNAL_FAILURE);
}

TEST_F(MergeHandlerTest, get_bucket_diff_reply_spi_failures) {
    PersistenceProviderWrapper providerWrapper(getPersistenceProvider());
    MergeHandler handler = createHandler(providerWrapper);
    providerWrapper.setResult(
            spi::Result(spi::Result::ErrorType::PERMANENT_ERROR, "who you gonna call?"));
    HandleGetBucketDiffReplyInvoker invoker;

    setUpChain(FRONT);

    ExpectedExceptionSpec exceptions[] = {
        { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
        { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
    };

    typedef ExpectedExceptionSpec* ExceptionIterator;
    ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

    for (ExceptionIterator it = exceptions; it != last; ++it) {
        EXPECT_EQ("", doTestSPIException(handler, providerWrapper, invoker, *it));
    }
}

void
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::beforeInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context&)
{
    ++_counter;
    _stub.clear();
    if (getChainPos() == FRONT) {
        auto cmd = std::make_shared<api::MergeBucketCommand>(test._bucket, test._nodes, test._maxTimestamp);
        handler.handleMergeBucket(*cmd, test.createTracker(cmd, test._bucket));
        auto diffCmd = test.fetchSingleMessage<api::GetBucketDiffCommand>();
        auto dummyDiff = test.createDummyGetBucketDiff(100000 * _counter, 0x2);
        diffCmd->getDiff() = dummyDiff->getDiff();

        api::GetBucketDiffReply diffReply(*diffCmd);
        handler.handleGetBucketDiffReply(diffReply, _stub);

        assert(_stub.commands.size() == 1);
        _applyCmd = std::dynamic_pointer_cast<api::ApplyBucketDiffCommand>(
                _stub.commands[0]);
    } else {
        // Pretend last node in chain has data and that it will be fetched when
        // chain is unwinded.
        auto cmd = test.createDummyApplyDiff(100000 * _counter, 0x4, false);
        handler.handleApplyBucketDiff(*cmd, test.createTracker(cmd, test._bucket));
        _applyCmd = test.fetchSingleMessage<api::ApplyBucketDiffCommand>();
    }
}

void
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context&)
{
    (void) test;
    api::ApplyBucketDiffReply reply(*_applyCmd);
    test.fillDummyApplyDiff(reply.getDiff());
    _stub.clear();
    handler.handleApplyBucketDiffReply(reply, _stub);
}

std::string
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::afterInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler)
{
    (void) handler;
    if (!_stub.commands.empty()) {
        return "Unexpected commands in reply stub";
    }
    if (!_stub.replies.empty()) {
        return "Unexpected replies in reply stub";
    }
    if (getChainPos() == FRONT) {
        return test.checkMessage<api::MergeBucketReply>(
                api::ReturnCode::INTERNAL_FAILURE);
    } else {
        return test.checkMessage<api::ApplyBucketDiffReply>(
                api::ReturnCode::INTERNAL_FAILURE);
    }
}

TEST_F(MergeHandlerTest, apply_bucket_diff_reply_spi_failures) {
    PersistenceProviderWrapper providerWrapper(getPersistenceProvider());
    HandleApplyBucketDiffReplyInvoker invoker;
    for (int i = 0; i < 2; ++i) {
        ChainPos pos(i == 0 ? FRONT : MIDDLE);
        setUpChain(pos);
        invoker.setChainPos(pos);
        MergeHandler handler = createHandler(providerWrapper);
        providerWrapper.setResult(
                spi::Result(spi::Result::ErrorType::PERMANENT_ERROR, "who you gonna call?"));

        ExpectedExceptionSpec exceptions[] = {
            { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
            { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
            { PersistenceProviderWrapper::FAIL_PUT, "Failed put" },
            { PersistenceProviderWrapper::FAIL_REMOVE, "Failed remove" },
        };

        typedef ExpectedExceptionSpec* ExceptionIterator;
        ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

        for (ExceptionIterator it = exceptions; it != last; ++it) {
            EXPECT_EQ("", doTestSPIException(handler, providerWrapper, invoker, *it));
        }
    }
}

TEST_F(MergeHandlerTest, remove_from_diff) {
    framework::defaultimplementation::FakeClock clock;
    MergeStatus status(clock, 0, 0);

    std::vector<api::GetBucketDiffCommand::Entry> diff(2);
    diff[0]._timestamp = 1234;
    diff[0]._flags = 0x1;
    diff[0]._hasMask = 0x2;

    diff[1]._timestamp = 5678;
    diff[1]._flags = 0x3;
    diff[1]._hasMask = 0x6;

    status.diff.insert(status.diff.end(), diff.begin(), diff.end());
    using NodeList = decltype(_nodes);
    status.nodeList = NodeList{{0, true}, {1, true}, {2, true}};

    {
        std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff(2);
        applyDiff[0]._entry._timestamp = 1234;
        applyDiff[0]._entry._flags = 0x1;
        applyDiff[0]._entry._hasMask = 0x0; // Removed during merging

        applyDiff[1]._entry._timestamp = 5678;
        applyDiff[1]._entry._flags = 0x3;
        applyDiff[1]._entry._hasMask = 0x7;

        EXPECT_TRUE(status.removeFromDiff(applyDiff, 0x7, status.nodeList));
        EXPECT_TRUE(status.diff.empty());
    }

    status.diff.insert(status.diff.end(), diff.begin(), diff.end());

    {
        std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff(2);
        applyDiff[0]._entry._timestamp = 1234;
        applyDiff[0]._entry._flags = 0x1;
        applyDiff[0]._entry._hasMask = 0x2;

        applyDiff[1]._entry._timestamp = 5678;
        applyDiff[1]._entry._flags = 0x3;
        applyDiff[1]._entry._hasMask = 0x6;

        EXPECT_FALSE(status.removeFromDiff(applyDiff, 0x7, status.nodeList));
        EXPECT_EQ(2, status.diff.size());
    }

    status.diff.clear();
    status.diff.insert(status.diff.end(), diff.begin(), diff.end());

    {
        // Hasmasks have changed but diff still remains the same size.
        std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff(2);
        applyDiff[0]._entry._timestamp = 1234;
        applyDiff[0]._entry._flags = 0x1;
        applyDiff[0]._entry._hasMask = 0x1;

        applyDiff[1]._entry._timestamp = 5678;
        applyDiff[1]._entry._flags = 0x3;
        applyDiff[1]._entry._hasMask = 0x5;

        EXPECT_TRUE(status.removeFromDiff(applyDiff, 0x7, status.nodeList));
        EXPECT_EQ(2, status.diff.size());
    }
}

TEST_F(MergeHandlerTest, remove_put_on_existing_timestamp) {
    setUpChain(BACK);

    document::TestDocMan docMan;
    document::Document::SP doc(docMan.createRandomDocumentAtLocation(_location));
    spi::Timestamp ts(10111);
    doPut(doc, ts);

    MergeHandler handler = createHandler();
    std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff;
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = ts;
        e._entry._hasMask = 0x1;
        e._docName = doc->getId().toString();
        e._entry._flags = MergeHandler::IN_USE | MergeHandler::DELETED;
        applyDiff.push_back(e);
    }

    auto applyBucketDiffCmd = std::make_shared<api::ApplyBucketDiffCommand>(_bucket, _nodes);
    applyBucketDiffCmd->getDiff() = applyDiff;

    auto tracker = handler.handleApplyBucketDiff(*applyBucketDiffCmd, createTracker(applyBucketDiffCmd, _bucket));

    auto applyBucketDiffReply = std::dynamic_pointer_cast<api::ApplyBucketDiffReply>(std::move(*tracker).stealReplySP());
    ASSERT_TRUE(applyBucketDiffReply.get());

    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));

    auto getBucketDiffCmd = fetchSingleMessage<api::GetBucketDiffCommand>();

    // Timestamp should now be a regular remove
    bool foundTimestamp = false;
    for (size_t i = 0; i < getBucketDiffCmd->getDiff().size(); ++i) {
        const api::GetBucketDiffCommand::Entry& e(getBucketDiffCmd->getDiff()[i]);
        if (e._timestamp == ts) {
            EXPECT_EQ(
                    uint16_t(MergeHandler::IN_USE | MergeHandler::DELETED),
                    e._flags);
            foundTimestamp = true;
            break;
        }
    }
    EXPECT_TRUE(foundTimestamp);
}

namespace {

storage::api::GetBucketDiffCommand::Entry
make_entry(uint64_t timestamp, uint16_t mask) {
    storage::api::GetBucketDiffCommand::Entry entry;
    entry._timestamp = timestamp;
    entry._gid = document::GlobalId();
    entry._headerSize = 0;
    entry._bodySize = 0;
    entry._flags = MergeHandler::StateFlag::IN_USE;
    entry._hasMask = mask;
    return entry;
}

void
fill_entry(storage::api::ApplyBucketDiffCommand::Entry &e, const document::Document& doc, const document::DocumentTypeRepo &repo)
{
    e._docName = doc.getId().toString();
    vespalib::nbostream stream;
    doc.serialize(stream);
    e._headerBlob.resize(stream.size());
    memcpy(&e._headerBlob[0], stream.peek(), stream.size());
    e._repo = &repo;
}

/*
 * Helper class to check both timestamp and mask at once.
 */
struct EntryCheck
{
    uint64_t _timestamp;
    uint16_t _hasMask;

    EntryCheck(uint64_t timestamp, uint16_t hasMask)
        : _timestamp(timestamp),
          _hasMask(hasMask)
    {
    }
    bool operator==(const api::GetBucketDiffCommand::Entry &rhs) const {
        return _timestamp == rhs._timestamp && _hasMask == rhs._hasMask;
    }
};

std::ostream &operator<<(std::ostream &os, const EntryCheck &entry)
{
    os << "EntryCheck(timestamp=" << entry._timestamp << ", hasMask=" << entry._hasMask << ")";
    return os;
}

}

namespace api {

std::ostream &operator<<(std::ostream &os, const MergeBucketCommand::Node &node)
{
    os << "Node(" << node.index << "," << (node.sourceOnly ? "true" : "false") << ")";
    return os;
}

std::ostream &operator<<(std::ostream &os, const GetBucketDiffCommand::Entry &entry)
{
    os << "Entry(timestamp=" << entry._timestamp << ", hasMask=" << entry._hasMask << ")";
    return os;
}

}

TEST_F(MergeHandlerTest, partially_filled_apply_bucket_diff_reply)
{
    using NodeList = decltype(_nodes);
    // Redundancy is 2 and source only nodes 3 and 4 have doc1 and doc2
    _nodes.clear();
    _nodes.emplace_back(0, false);
    _nodes.emplace_back(1, false);
    _nodes.emplace_back(2, true);
    _nodes.emplace_back(3, true);
    _nodes.emplace_back(4, true);
    _maxTimestamp = 30000;  // Extend timestamp range to include doc1 and doc2

    auto doc1 = _env->_testDocMan.createRandomDocumentAtLocation(_location, 1);
    auto doc2 = _env->_testDocMan.createRandomDocumentAtLocation(_location, 2);
    
    MergeHandler handler = createHandler();
    auto cmd = std::make_shared<api::MergeBucketCommand>(_bucket, _nodes, _maxTimestamp);
    cmd->setSourceIndex(1234);
    MessageTracker::UP tracker = handler.handleMergeBucket(*cmd, createTracker(cmd, _bucket));
    ASSERT_EQ(1u, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::GETBUCKETDIFF, messageKeeper()._msgs[0]->getType());
    size_t baseline_diff_size = 0;
    {
        LOG(debug, "checking GetBucketDiff command");
        auto& cmd2 = dynamic_cast<api::GetBucketDiffCommand&>(*messageKeeper()._msgs[0]);
        EXPECT_THAT(_nodes, ContainerEq(cmd2.getNodes()));
        EXPECT_EQ(1, cmd2.getAddress()->getIndex());
        EXPECT_EQ(1234, cmd2.getSourceIndex());
        EXPECT_TRUE(getEnv()._fileStorHandler.isMerging(_bucket));
        auto &s = getEnv()._fileStorHandler.editMergeStatus(_bucket);
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {2, true}, {3, true}, {4, true}}), s.nodeList);
        baseline_diff_size = cmd2.getDiff().size();
        auto reply = std::make_unique<api::GetBucketDiffReply>(cmd2);
        auto &diff = reply->getDiff();
        // doc1 and doc2 is present on nodes 3 and 4.
        diff.push_back(make_entry(20000, ((1 << 3) | (1 << 4))));
        diff.push_back(make_entry(20100, ((1 << 3) | (1 << 4))));
        EXPECT_EQ(baseline_diff_size + 2u, reply->getDiff().size());
        handler.handleGetBucketDiffReply(*reply, messageKeeper());
        LOG(debug, "sent handleGetBucketDiffReply");
    }
    ASSERT_EQ(2u, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::APPLYBUCKETDIFF, messageKeeper()._msgs[1]->getType());
    {
        LOG(debug, "checking first ApplyBucketDiff command");
        EXPECT_TRUE(getEnv()._fileStorHandler.isMerging(_bucket));
        auto &s = getEnv()._fileStorHandler.editMergeStatus(_bucket);
        // Node 4 has been eliminated before the first ApplyBucketDiff command
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {2, true}, {3, true}}), s.nodeList);
        EXPECT_EQ(baseline_diff_size + 2u, s.diff.size());
        EXPECT_EQ(EntryCheck(20000, 24u), s.diff[baseline_diff_size]);
        EXPECT_EQ(EntryCheck(20100, 24u), s.diff[baseline_diff_size + 1]);
        auto& cmd3 = dynamic_cast<api::ApplyBucketDiffCommand&>(*messageKeeper()._msgs[1]);
        // ApplyBucketDiffCommand has a shorter node list, node 2 is not present
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {3, true}}), cmd3.getNodes());
        auto reply = std::make_unique<api::ApplyBucketDiffReply>(cmd3);
        auto& diff = reply->getDiff();
        EXPECT_EQ(2u, diff.size());
        EXPECT_EQ(EntryCheck(20000u, 4u), diff[0]._entry);
        EXPECT_EQ(EntryCheck(20100u, 4u), diff[1]._entry);
        /*
         * Only fill first diff entry to simulate max chunk size being exceeded
         * when filling diff entries on source node (node 3).
         */
        fill_entry(diff[0], *doc1, getEnv().getDocumentTypeRepo());
        diff[0]._entry._hasMask |= 2u; // Simulate diff entry having been applied on node 1.
        handler.handleApplyBucketDiffReply(*reply, messageKeeper());
        LOG(debug, "handled first ApplyBucketDiffReply");
    }
    ASSERT_EQ(3u, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::APPLYBUCKETDIFF, messageKeeper()._msgs[2]->getType());
    {
        LOG(debug, "checking second ApplyBucketDiff command");
        EXPECT_TRUE(getEnv()._fileStorHandler.isMerging(_bucket));
        auto &s = getEnv()._fileStorHandler.editMergeStatus(_bucket);
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {2, true}, {3, true}}), s.nodeList);
        EXPECT_EQ(baseline_diff_size + 1u, s.diff.size());
        EXPECT_EQ(EntryCheck(20100, 24u), s.diff[baseline_diff_size]);
        auto& cmd4 = dynamic_cast<api::ApplyBucketDiffCommand&>(*messageKeeper()._msgs[2]);
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {3, true}}), cmd4.getNodes());
        auto reply = std::make_unique<api::ApplyBucketDiffReply>(cmd4);
        auto& diff = reply->getDiff();
        EXPECT_EQ(1u, diff.size());
        EXPECT_EQ(EntryCheck(20100u, 4u), diff[0]._entry);
        // Simulate that node 3 somehow lost doc2 when trying to fill diff entry.
        diff[0]._entry._hasMask &= ~4u;
        handler.handleApplyBucketDiffReply(*reply, messageKeeper());
        LOG(debug, "handled second ApplyBucketDiffReply");
    }
    ASSERT_EQ(4u, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::APPLYBUCKETDIFF, messageKeeper()._msgs[3]->getType());
    {
        LOG(debug, "checking third ApplyBucketDiff command");
        EXPECT_TRUE(getEnv()._fileStorHandler.isMerging(_bucket));
        auto &s = getEnv()._fileStorHandler.editMergeStatus(_bucket);
        // Nodes 3 and 2 have been eliminated before the third ApplyBucketDiff command
        EXPECT_EQ((NodeList{{0, false}, {1, false}}), s.nodeList);
        EXPECT_EQ(baseline_diff_size + 1u, s.diff.size());
        EXPECT_EQ(EntryCheck(20100, 16u), s.diff[baseline_diff_size]);
        auto& cmd5 = dynamic_cast<api::ApplyBucketDiffCommand&>(*messageKeeper()._msgs[3]);
        EXPECT_EQ((NodeList{{0, false}, {1, false}}), cmd5.getNodes());
        auto reply = std::make_unique<api::ApplyBucketDiffReply>(cmd5);
        auto& diff = reply->getDiff();
        EXPECT_EQ(baseline_diff_size, diff.size());
        for (auto& e : diff) {
            EXPECT_EQ(1u, e._entry._hasMask);
            e._entry._hasMask |= 2u;
        }
        handler.handleApplyBucketDiffReply(*reply, messageKeeper());
        LOG(debug, "handled third ApplyBucketDiffReply");
    }
    ASSERT_EQ(5u, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::APPLYBUCKETDIFF, messageKeeper()._msgs[4]->getType());
    {
        LOG(debug, "checking fourth ApplyBucketDiff command");
        EXPECT_TRUE(getEnv()._fileStorHandler.isMerging(_bucket));
        auto &s = getEnv()._fileStorHandler.editMergeStatus(_bucket);
        // All nodes in use again due to failure to fill diff entry for doc2
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {2, true}, {3, true}, {4, true}}), s.nodeList);
        EXPECT_EQ(1u, s.diff.size());
        EXPECT_EQ(EntryCheck(20100, 16u), s.diff[0]);
        auto& cmd6 = dynamic_cast<api::ApplyBucketDiffCommand&>(*messageKeeper()._msgs[4]);
        EXPECT_EQ((NodeList{{0, false}, {1, false}, {4, true}}), cmd6.getNodes());
        auto reply = std::make_unique<api::ApplyBucketDiffReply>(cmd6);
        auto& diff = reply->getDiff();
        EXPECT_EQ(1u, diff.size());
        fill_entry(diff[0], *doc2, getEnv().getDocumentTypeRepo());
        diff[0]._entry._hasMask |= 2u;
        handler.handleApplyBucketDiffReply(*reply, messageKeeper());
        LOG(debug, "handled fourth ApplyBucketDiffReply");
    }
    ASSERT_EQ(6u, messageKeeper()._msgs.size());
    ASSERT_EQ(api::MessageType::MERGEBUCKET_REPLY, messageKeeper()._msgs[5]->getType());
    LOG(debug, "got mergebucket reply");
}

} // storage
