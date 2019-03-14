// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/storage/persistence/mergehandler.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/persistence/persistencetestutils.h>
#include <tests/persistence/common/persistenceproviderwrapper.h>
#include <tests/distributor/messagesenderstub.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <cmath>

#include <vespa/log/log.h>
LOG_SETUP(".test.persistence.handler.merge");

using document::test::makeDocumentBucket;

namespace storage {

struct MergeHandlerTest : public SingleDiskPersistenceTestUtils
{
    uint32_t _location; // Location used for all merge tests
    document::Bucket _bucket; // Bucket used for all merge tests
    uint64_t _maxTimestamp;
    std::vector<api::MergeBucketCommand::Node> _nodes;
    std::unique_ptr<spi::Context> _context;

    // Fetch a single command or reply; doesn't care which.
    template <typename T>
    std::shared_ptr<T> fetchSingleMessage();

    void setUp() override;

    enum ChainPos { FRONT, MIDDLE, BACK };
    void setUpChain(ChainPos);

        // Test a regular merge bucket command fetching data, including
        // puts, removes, unrevertable removes & duplicates.
    void testMergeBucketCommand();
        // Test that a simplistic merge with nothing to actually merge,
        // sends get bucket diff through the entire chain of 3 nodes.
    void testGetBucketDiffChain(bool midChain);
    void testGetBucketDiffMidChain() { testGetBucketDiffChain(true); }
    void testGetBucketDiffEndOfChain() { testGetBucketDiffChain(false); }
        // Test that a simplistic merge with nothing to actually merge,
        // sends apply bucket diff through the entire chain of 3 nodes.
    void testApplyBucketDiffChain(bool midChain);
    void testApplyBucketDiffMidChain() { testApplyBucketDiffChain(true); }
    void testApplyBucketDiffEndOfChain() { testApplyBucketDiffChain(false); }
        // Test that a simplistic merge with one thing to actually merge,
        // sends correct commands and finish.
    void testMasterMessageFlow();
        // Test that a simplistic merge with 1 doc to actually merge,
        // sends apply bucket diff through the entire chain of 3 nodes.
    void testApplyBucketDiffChain();
    void testMergeUnrevertableRemove();
    void testChunkedApplyBucketDiff();
    void testChunkLimitPartiallyFilledDiff();
    void testMaxTimestamp();
    void testSPIFlushGuard();
    void testBucketNotFoundInDb();
    void testMergeProgressSafeGuard();
    void testSafeGuardNotInvokedWhenHasMaskChanges();
    void testEntryRemovedAfterGetBucketDiff();

    void testMergeBucketSPIFailures();
    void testGetBucketDiffSPIFailures();
    void testApplyBucketDiffSPIFailures();
    void testGetBucketDiffReplySPIFailures();
    void testApplyBucketDiffReplySPIFailures();

    void testRemoveFromDiff();

    void testRemovePutOnExistingTimestamp();

    CPPUNIT_TEST_SUITE(MergeHandlerTest);
    CPPUNIT_TEST(testMergeBucketCommand);
    CPPUNIT_TEST(testGetBucketDiffMidChain);
    CPPUNIT_TEST(testGetBucketDiffEndOfChain);
    CPPUNIT_TEST(testApplyBucketDiffMidChain);
    CPPUNIT_TEST(testApplyBucketDiffEndOfChain);
    CPPUNIT_TEST(testMasterMessageFlow);
    CPPUNIT_TEST(testMergeUnrevertableRemove);
    CPPUNIT_TEST(testChunkedApplyBucketDiff);
    CPPUNIT_TEST(testChunkLimitPartiallyFilledDiff);
    CPPUNIT_TEST(testMaxTimestamp);
    CPPUNIT_TEST(testSPIFlushGuard);
    CPPUNIT_TEST(testBucketNotFoundInDb);
    CPPUNIT_TEST(testMergeProgressSafeGuard);
    CPPUNIT_TEST(testSafeGuardNotInvokedWhenHasMaskChanges);
    CPPUNIT_TEST(testEntryRemovedAfterGetBucketDiff);
    CPPUNIT_TEST(testMergeBucketSPIFailures);
    CPPUNIT_TEST(testGetBucketDiffSPIFailures);
    CPPUNIT_TEST(testApplyBucketDiffSPIFailures);
    CPPUNIT_TEST(testGetBucketDiffReplySPIFailures);
    CPPUNIT_TEST(testApplyBucketDiffReplySPIFailures);
    CPPUNIT_TEST(testRemoveFromDiff);
    CPPUNIT_TEST(testRemovePutOnExistingTimestamp);
    CPPUNIT_TEST_SUITE_END();

    // @TODO Add test to test that buildBucketInfo and mergeLists create minimal list (wrong sorting screws this up)
private:
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
        virtual ~HandlerInvoker() {}
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
        ~HandleGetBucketDiffReplyInvoker();
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
        ~HandleApplyBucketDiffReplyInvoker();
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
};

CPPUNIT_TEST_SUITE_REGISTRATION(MergeHandlerTest);


MergeHandlerTest::HandleGetBucketDiffReplyInvoker::HandleGetBucketDiffReplyInvoker() {}
MergeHandlerTest::HandleGetBucketDiffReplyInvoker::~HandleGetBucketDiffReplyInvoker() {}
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::HandleApplyBucketDiffReplyInvoker()
    : _counter(0),
      _stub(),
      _applyCmd()
{}
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::~HandleApplyBucketDiffReplyInvoker() {}

void
MergeHandlerTest::setUp() {
    _context.reset(new spi::Context(documentapi::LoadType::DEFAULT, 0, 0));
    SingleDiskPersistenceTestUtils::setUp();

    _location = 1234;
    _bucket = makeDocumentBucket(document::BucketId(16, _location));
    _maxTimestamp = 11501;

    LOG(info, "Creating %s in bucket database", _bucket.toString().c_str());
    bucketdb::StorageBucketInfo bucketDBEntry;
    bucketDBEntry.disk = 0;
    getEnv().getBucketDatabase(_bucket.getBucketSpace()).insert(_bucket.getBucketId(), bucketDBEntry, "mergetestsetup");

    LOG(info, "Creating bucket to merge");
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

void
MergeHandlerTest::testMergeBucketCommand()
{
    MergeHandler handler(getPersistenceProvider(), getEnv());

    LOG(info, "Handle a merge bucket command");
    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    cmd.setSourceIndex(1234);
    MessageTracker::UP tracker = handler.handleMergeBucket(cmd, *_context);

    LOG(info, "Check state");
    CPPUNIT_ASSERT_EQUAL(size_t(1), messageKeeper()._msgs.size());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::GETBUCKETDIFF,
                         messageKeeper()._msgs[0]->getType());
    api::GetBucketDiffCommand& cmd2(dynamic_cast<api::GetBucketDiffCommand&>(
                *messageKeeper()._msgs[0]));
    CPPUNIT_ASSERT_EQUAL(_nodes, cmd2.getNodes());
    std::vector<api::GetBucketDiffCommand::Entry> diff(cmd2.getDiff());
    CPPUNIT_ASSERT_EQUAL(size_t(17), diff.size());
    CPPUNIT_ASSERT_EQUAL(uint16_t(1), cmd2.getAddress()->getIndex());
    CPPUNIT_ASSERT_EQUAL(uint16_t(1234), cmd2.getSourceIndex());

    tracker->generateReply(cmd);
    CPPUNIT_ASSERT(!tracker->getReply().get());
}

void
MergeHandlerTest::testGetBucketDiffChain(bool midChain)
{
    setUpChain(midChain ? MIDDLE : BACK);
    MergeHandler handler(getPersistenceProvider(), getEnv());

    LOG(info, "Verifying that get bucket diff is sent on");
    api::GetBucketDiffCommand cmd(_bucket, _nodes, _maxTimestamp);
    MessageTracker::UP tracker1 = handler.handleGetBucketDiff(cmd, *_context);
    api::StorageMessage::SP replySent = tracker1->getReply();

    if (midChain) {
        LOG(info, "Check state");
        CPPUNIT_ASSERT_EQUAL(size_t(1), messageKeeper()._msgs.size());
        CPPUNIT_ASSERT_EQUAL(api::MessageType::GETBUCKETDIFF,
                             messageKeeper()._msgs[0]->getType());
        api::GetBucketDiffCommand& cmd2(
                dynamic_cast<api::GetBucketDiffCommand&>(
                    *messageKeeper()._msgs[0]));
        CPPUNIT_ASSERT_EQUAL(_nodes, cmd2.getNodes());
        std::vector<api::GetBucketDiffCommand::Entry> diff(cmd2.getDiff());
        CPPUNIT_ASSERT_EQUAL(size_t(17), diff.size());
        CPPUNIT_ASSERT_EQUAL(uint16_t(1), cmd2.getAddress()->getIndex());

        LOG(info, "Verifying that replying the diff sends on back");
        api::GetBucketDiffReply::UP reply(new api::GetBucketDiffReply(cmd2));

        CPPUNIT_ASSERT(!replySent.get());

        MessageSenderStub stub;
        handler.handleGetBucketDiffReply(*reply, stub);
        CPPUNIT_ASSERT_EQUAL(1, (int)stub.replies.size());
        replySent = stub.replies[0];
    }
    api::GetBucketDiffReply::SP reply2(
            std::dynamic_pointer_cast<api::GetBucketDiffReply>(
                    replySent));
    CPPUNIT_ASSERT(reply2.get());

    CPPUNIT_ASSERT_EQUAL(_nodes, reply2->getNodes());
    std::vector<api::GetBucketDiffCommand::Entry> diff(reply2->getDiff());
    CPPUNIT_ASSERT_EQUAL(size_t(17), diff.size());
}

void
MergeHandlerTest::testApplyBucketDiffChain(bool midChain)
{
    setUpChain(midChain ? MIDDLE : BACK);
    MergeHandler handler(getPersistenceProvider(), getEnv());

    LOG(info, "Verifying that apply bucket diff is sent on");
    api::ApplyBucketDiffCommand cmd(_bucket, _nodes, _maxTimestamp);
    MessageTracker::UP tracker1 = handler.handleApplyBucketDiff(cmd, *_context);
    api::StorageMessage::SP replySent = tracker1->getReply();

    if (midChain) {
        LOG(info, "Check state");
        CPPUNIT_ASSERT_EQUAL(size_t(1), messageKeeper()._msgs.size());
        CPPUNIT_ASSERT_EQUAL(api::MessageType::APPLYBUCKETDIFF,
                             messageKeeper()._msgs[0]->getType());
        api::ApplyBucketDiffCommand& cmd2(
                dynamic_cast<api::ApplyBucketDiffCommand&>(
                    *messageKeeper()._msgs[0]));
        CPPUNIT_ASSERT_EQUAL(_nodes, cmd2.getNodes());
        std::vector<api::ApplyBucketDiffCommand::Entry> diff(cmd2.getDiff());
        CPPUNIT_ASSERT_EQUAL(size_t(0), diff.size());
        CPPUNIT_ASSERT_EQUAL(uint16_t(1), cmd2.getAddress()->getIndex());

        CPPUNIT_ASSERT(!replySent.get());

        LOG(info, "Verifying that replying the diff sends on back");
        api::ApplyBucketDiffReply::UP reply(
                new api::ApplyBucketDiffReply(cmd2));

        MessageSenderStub stub;
        handler.handleApplyBucketDiffReply(*reply, stub);
        CPPUNIT_ASSERT_EQUAL(1, (int)stub.replies.size());
        replySent = stub.replies[0];
    }

    api::ApplyBucketDiffReply::SP reply2(
            std::dynamic_pointer_cast<api::ApplyBucketDiffReply>(replySent));
    CPPUNIT_ASSERT(reply2.get());

    CPPUNIT_ASSERT_EQUAL(_nodes, reply2->getNodes());
    std::vector<api::ApplyBucketDiffCommand::Entry> diff(reply2->getDiff());
    CPPUNIT_ASSERT_EQUAL(size_t(0), diff.size());
}

void
MergeHandlerTest::testMasterMessageFlow()
{
    MergeHandler handler(getPersistenceProvider(), getEnv());

    LOG(info, "Handle a merge bucket command");
    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);

    handler.handleMergeBucket(cmd, *_context);
    LOG(info, "Check state");
    CPPUNIT_ASSERT_EQUAL(size_t(1), messageKeeper()._msgs.size());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::GETBUCKETDIFF,
                         messageKeeper()._msgs[0]->getType());
    api::GetBucketDiffCommand& cmd2(dynamic_cast<api::GetBucketDiffCommand&>(
                *messageKeeper()._msgs[0]));

    api::GetBucketDiffReply::UP reply(new api::GetBucketDiffReply(cmd2));
        // End of chain can remove entries all have. This should end up with
        // one entry master node has other node don't have
    reply->getDiff().resize(1);

    handler.handleGetBucketDiffReply(*reply, messageKeeper());

    LOG(info, "Check state");
    CPPUNIT_ASSERT_EQUAL(size_t(2), messageKeeper()._msgs.size());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::APPLYBUCKETDIFF,
                         messageKeeper()._msgs[1]->getType());
    api::ApplyBucketDiffCommand& cmd3(
            dynamic_cast<api::ApplyBucketDiffCommand&>(
                *messageKeeper()._msgs[1]));
    api::ApplyBucketDiffReply::UP reply2(new api::ApplyBucketDiffReply(cmd3));
    CPPUNIT_ASSERT_EQUAL(size_t(1), reply2->getDiff().size());
    reply2->getDiff()[0]._entry._hasMask |= 2;

    MessageSenderStub stub;
    handler.handleApplyBucketDiffReply(*reply2, stub);

    CPPUNIT_ASSERT_EQUAL(1, (int)stub.replies.size());

    api::MergeBucketReply::SP reply3(
            std::dynamic_pointer_cast<api::MergeBucketReply>(stub.replies[0]));
    CPPUNIT_ASSERT(reply3.get());

    CPPUNIT_ASSERT_EQUAL(_nodes, reply3->getNodes());
    CPPUNIT_ASSERT(reply3->getResult().success());
    CPPUNIT_ASSERT(!fsHandler().isMerging(_bucket));
}

void
MergeHandlerTest::testMergeUnrevertableRemove()
{
/*
    MergeHandler handler(getPersistenceProvider(), getEnv());

    LOG(info, "Handle a merge bucket command");
    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    {
        MessageTracker tracker;
        handler.handleMergeBucket(cmd, tracker);
    }

    LOG(info, "Check state");
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), messageKeeper()._msgs.size());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::GETBUCKETDIFF,
                         messageKeeper()._msgs[0]->getType());
    api::GetBucketDiffCommand& cmd2(
            dynamic_cast<api::GetBucketDiffCommand&>(
                    *messageKeeper()._msgs[0]));

    api::GetBucketDiffReply::UP reply(new api::GetBucketDiffReply(cmd2));

    std::vector<Timestamp> docTimestamps;
    for (int i = 0; i < 4; ++i) {
        docTimestamps.push_back(Timestamp(reply->getDiff()[i]._timestamp));
    }
    CPPUNIT_ASSERT(reply->getDiff().size() >= 4);
    reply->getDiff().resize(4);
    // Add one non-unrevertable entry for existing timestamp which
    // should not be added
    reply->getDiff()[0]._flags |= Types::DELETED;
    reply->getDiff()[0]._bodySize = 0;
    reply->getDiff()[0]._hasMask = 2;
    // Add a unrevertable entry which should be modified
    reply->getDiff()[1]._flags |= Types::DELETED | Types::DELETED_IN_PLACE;
    reply->getDiff()[1]._bodySize = 0;
    reply->getDiff()[1]._hasMask = 2;
    // Add one non-unrevertable entry that is a duplicate put
    // which should not be added or fail the merge.
    LOG(info, "duplicate put has timestamp %zu and flags %u",
        reply->getDiff()[2]._timestamp,
        reply->getDiff()[2]._flags);
    reply->getDiff()[2]._hasMask = 2;
    // Add one unrevertable entry for a timestamp that does not exist
    reply->getDiff()[3]._flags |= Types::DELETED | Types::DELETED_IN_PLACE;
    reply->getDiff()[3]._timestamp = 12345678;
    reply->getDiff()[3]._bodySize = 0;
    reply->getDiff()[3]._hasMask = 2;
    {
        MessageTracker tracker;
        handler.handleGetBucketDiffReply(*reply, tracker);
    }

    LOG(info, "%s", reply->toString(true).c_str());

    LOG(info, "Create bucket diff reply");
    CPPUNIT_ASSERT_EQUAL(uint64_t(2), messageKeeper()._msgs.size());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::APPLYBUCKETDIFF,
                         messageKeeper()._msgs[1]->getType());
    api::ApplyBucketDiffCommand& cmd3(
            dynamic_cast<api::ApplyBucketDiffCommand&>(
                *messageKeeper()._msgs[1]));
    api::ApplyBucketDiffReply::UP reply2(
            new api::ApplyBucketDiffReply(cmd3));
    CPPUNIT_ASSERT_EQUAL(size_t(4), reply2->getDiff().size());

    memfile::DataLocation headerLocs[4];
    std::vector<DocumentId> documentIds;
    // So deserialization won't fail, we need some kind of header blob
    // for each entry

    for (int i = 0; i < 4; ++i) {
        api::ApplyBucketDiffReply::Entry& entry = reply2->getDiff()[i];
        CPPUNIT_ASSERT_EQUAL(uint16_t(2), entry._entry._hasMask);

        memfile::MemFilePtr file(getMemFile(_bucket));
        const memfile::MemSlot* slot = file->getSlotAtTime(docTimestamps[i]);
        CPPUNIT_ASSERT(slot != NULL);
        LOG(info, "Processing slot %s", slot->toString().c_str());
        CPPUNIT_ASSERT(slot->hasBodyContent());
        documentIds.push_back(file->getDocumentId(*slot));
        entry._docName = documentIds.back().toString();
        headerLocs[i] = slot->getLocation(HEADER);

        document::Document::UP doc(file->getDocument(*slot, ALL));
        {
            vespalib::nbostream stream;
            doc->serializeHeader(stream);
            std::vector<char> buf(
                    stream.peek(), stream.peek() + stream.size());
            entry._headerBlob.swap(buf);
        }
        // Put duplicate needs body blob as well
        if (i == 2) {
            vespalib::nbostream stream;
            doc->serializeBody(stream);
            std::vector<char> buf(
                    stream.peek(), stream.peek() + stream.size());
            entry._bodyBlob.swap(buf);
        }
    }

    LOG(info, "%s", reply2->toString(true).c_str());

    MessageTracker tracker;
    handler.handleApplyBucketDiffReply(*reply2, tracker);

    CPPUNIT_ASSERT(tracker._sendReply);
    api::MergeBucketReply::SP reply3(
            std::dynamic_pointer_cast<api::MergeBucketReply>(
                    tracker._reply));
    CPPUNIT_ASSERT(reply3.get());

    CPPUNIT_ASSERT_EQUAL(_nodes, reply3->getNodes());
    CPPUNIT_ASSERT(reply3->getResult().success());

    memfile::MemFilePtr file(getMemFile(_bucket));
    // Existing timestamp should not be modified by
    // non-unrevertable entry
    {
        const memfile::MemSlot* slot = file->getSlotAtTime(
                Timestamp(reply->getDiff()[0]._timestamp));
        CPPUNIT_ASSERT(slot != NULL);
        CPPUNIT_ASSERT(!slot->deleted());
    }
    // Ensure unrevertable remove for existing put was merged in OK
    {
        const memfile::MemSlot* slot = file->getSlotAtTime(
                Timestamp(reply->getDiff()[1]._timestamp));
        CPPUNIT_ASSERT(slot != NULL);
        CPPUNIT_ASSERT(slot->deleted());
        CPPUNIT_ASSERT(slot->deletedInPlace());
        CPPUNIT_ASSERT(!slot->hasBodyContent());
        // Header location should not have changed
        CPPUNIT_ASSERT_EQUAL(headerLocs[1], slot->getLocation(HEADER));
    }

    // Non-existing timestamp unrevertable remove should be added as
    // entry with doc id-only header
    {
        const memfile::MemSlot* slot = file->getSlotAtTime(
                Timestamp(reply->getDiff()[3]._timestamp));
        CPPUNIT_ASSERT(slot != NULL);
        CPPUNIT_ASSERT(slot->deleted());
        CPPUNIT_ASSERT(slot->deletedInPlace());
        CPPUNIT_ASSERT(!slot->hasBodyContent());
        CPPUNIT_ASSERT_EQUAL(documentIds[3], file->getDocumentId(*slot));
    }

*/
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

void
MergeHandlerTest::testChunkedApplyBucketDiff()
{
    uint32_t docSize = 1024;
    uint32_t docCount = 10;
    uint32_t maxChunkSize = docSize * 3;
    for (uint32_t i = 0; i < docCount; ++i) {
        doPut(1234, spi::Timestamp(4000 + i), docSize, docSize);
    }

    MergeHandler handler(getPersistenceProvider(), getEnv(), maxChunkSize);

    LOG(info, "Handle a merge bucket command");
    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(cmd, *_context);

    std::shared_ptr<api::GetBucketDiffCommand> getBucketDiffCmd(
            fetchSingleMessage<api::GetBucketDiffCommand>());
    api::GetBucketDiffReply::UP getBucketDiffReply(
            new api::GetBucketDiffReply(*getBucketDiffCmd));

    handler.handleGetBucketDiffReply(*getBucketDiffReply, messageKeeper());

    uint32_t totalDiffs = getBucketDiffCmd->getDiff().size();
    std::set<spi::Timestamp> seen;

    api::MergeBucketReply::SP reply;
    while (seen.size() != totalDiffs) {
        std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
                fetchSingleMessage<api::ApplyBucketDiffCommand>());

        LOG(info, "Test that we get chunked diffs in ApplyBucketDiff");
        std::vector<api::ApplyBucketDiffCommand::Entry>& diff(
                applyBucketDiffCmd->getDiff());
        CPPUNIT_ASSERT(getFilledCount(diff) < totalDiffs);
        CPPUNIT_ASSERT(getFilledDataSize(diff) <= maxChunkSize);

        // Include node 1 in hasmask for all diffs to indicate it's done
        // Also remember the diffs we've seen thus far to ensure chunking
        // does not send duplicates.
        for (size_t i = 0; i < diff.size(); ++i) {
            if (!diff[i].filled()) {
                continue;
            }
            diff[i]._entry._hasMask |= 2;
            std::pair<std::set<spi::Timestamp>::iterator, bool> inserted(
                    seen.insert(spi::Timestamp(diff[i]._entry._timestamp)));
            if (!inserted.second) {
                std::ostringstream ss;
                ss << "Diff for " << diff[i]
                   << " has already been seen in another ApplyBucketDiff";
                CPPUNIT_FAIL(ss.str());
            }
        }

        api::ApplyBucketDiffReply::UP applyBucketDiffReply(
                new api::ApplyBucketDiffReply(*applyBucketDiffCmd));
        {
            handler.handleApplyBucketDiffReply(*applyBucketDiffReply, messageKeeper());

            if (messageKeeper()._msgs.size()) {
                CPPUNIT_ASSERT(!reply.get());
                reply = std::dynamic_pointer_cast<api::MergeBucketReply>(
                        messageKeeper()._msgs[messageKeeper()._msgs.size() - 1]);
            }
        }
    }
    LOG(info, "Done with applying diff");

    CPPUNIT_ASSERT(reply.get());
    CPPUNIT_ASSERT_EQUAL(_nodes, reply->getNodes());
    CPPUNIT_ASSERT(reply->getResult().success());
}

void
MergeHandlerTest::testChunkLimitPartiallyFilledDiff()
{
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
    std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
            new api::ApplyBucketDiffCommand(_bucket, _nodes, maxChunkSize));
    applyBucketDiffCmd->getDiff() = applyDiff;

    MergeHandler handler(
            getPersistenceProvider(), getEnv(), maxChunkSize);
    handler.handleApplyBucketDiff(*applyBucketDiffCmd, *_context);

    std::shared_ptr<api::ApplyBucketDiffCommand> fwdDiffCmd(
            fetchSingleMessage<api::ApplyBucketDiffCommand>());
    // Should not fill up more than chunk size allows for
    CPPUNIT_ASSERT_EQUAL(size_t(2), getFilledCount(fwdDiffCmd->getDiff()));
    CPPUNIT_ASSERT(getFilledDataSize(fwdDiffCmd->getDiff()) <= maxChunkSize);
}

void
MergeHandlerTest::testMaxTimestamp()
{
    doPut(1234, spi::Timestamp(_maxTimestamp + 10), 1024, 1024);

    MergeHandler handler(getPersistenceProvider(), getEnv());

    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(cmd, *_context);

    std::shared_ptr<api::GetBucketDiffCommand> getCmd(
            fetchSingleMessage<api::GetBucketDiffCommand>());

    CPPUNIT_ASSERT(!getCmd->getDiff().empty());
    CPPUNIT_ASSERT(getCmd->getDiff().back()._timestamp <= _maxTimestamp);
}

void
MergeHandlerTest::fillDummyApplyDiff(
        std::vector<api::ApplyBucketDiffCommand::Entry>& diff)
{
    document::TestDocMan docMan;
    document::Document::SP doc(
            docMan.createRandomDocumentAtLocation(_location));
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

    std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
            new api::ApplyBucketDiffCommand(_bucket, _nodes, 1024*1024));
    applyBucketDiffCmd->getDiff() = applyDiff;
    return applyBucketDiffCmd;
}

// Must match up with diff used in createDummyApplyDiff
std::shared_ptr<api::GetBucketDiffCommand>
MergeHandlerTest::createDummyGetBucketDiff(int timestampOffset,
                                           uint16_t hasMask)
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

    std::shared_ptr<api::GetBucketDiffCommand> getBucketDiffCmd(
            new api::GetBucketDiffCommand(_bucket, _nodes, 1024*1024));
    getBucketDiffCmd->getDiff() = diff;
    return getBucketDiffCmd;
}

void
MergeHandlerTest::testSPIFlushGuard()
{
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    MergeHandler handler(providerWrapper, getEnv());

    providerWrapper.setResult(
            spi::Result(spi::Result::PERMANENT_ERROR,
                        "who you gonna call?"));

    setUpChain(MIDDLE);
    // Fail applying unrevertable remove
    providerWrapper.setFailureMask(
            PersistenceProviderWrapper::FAIL_REMOVE);
    providerWrapper.clearOperationLog();
    try {
        handler.handleApplyBucketDiff(*createDummyApplyDiff(6000), *_context);
        CPPUNIT_FAIL("No exception thrown on failing in-place remove");
    } catch (const std::runtime_error& e) {
        CPPUNIT_ASSERT(std::string(e.what()).find("Failed remove")
                       != std::string::npos);
    }
    // Test that we always flush after applying diff locally, even when
    // errors are encountered.
    const std::vector<std::string>& opLog(providerWrapper.getOperationLog());
    CPPUNIT_ASSERT(!opLog.empty());
    CPPUNIT_ASSERT_EQUAL(
            std::string("flush(Bucket(0x40000000000004d2, partition 0))"),
            opLog.back());
}

void
MergeHandlerTest::testBucketNotFoundInDb()
{
    MergeHandler handler(getPersistenceProvider(), getEnv());
    // Send merge for unknown bucket
    api::MergeBucketCommand cmd(makeDocumentBucket(document::BucketId(16, 6789)), _nodes, _maxTimestamp);
    MessageTracker::UP tracker = handler.handleMergeBucket(cmd, *_context);
    CPPUNIT_ASSERT(tracker->getResult().isBucketDisappearance());
}

void
MergeHandlerTest::testMergeProgressSafeGuard()
{
    MergeHandler handler(getPersistenceProvider(), getEnv());
    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(cmd, *_context);

    std::shared_ptr<api::GetBucketDiffCommand> getBucketDiffCmd(
            fetchSingleMessage<api::GetBucketDiffCommand>());
    api::GetBucketDiffReply::UP getBucketDiffReply(
            new api::GetBucketDiffReply(*getBucketDiffCmd));

    handler.handleGetBucketDiffReply(*getBucketDiffReply, messageKeeper());

    std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
            fetchSingleMessage<api::ApplyBucketDiffCommand>());
    api::ApplyBucketDiffReply::UP applyBucketDiffReply(
            new api::ApplyBucketDiffReply(*applyBucketDiffCmd));

    MessageSenderStub stub;
    handler.handleApplyBucketDiffReply(*applyBucketDiffReply, stub);

    CPPUNIT_ASSERT_EQUAL(1, (int)stub.replies.size());

    api::MergeBucketReply::SP mergeReply(
            std::dynamic_pointer_cast<api::MergeBucketReply>(
                    stub.replies[0]));
    CPPUNIT_ASSERT(mergeReply.get());
    CPPUNIT_ASSERT(mergeReply->getResult().getResult()
                   == api::ReturnCode::INTERNAL_FAILURE);
}

void
MergeHandlerTest::testSafeGuardNotInvokedWhenHasMaskChanges()
{
    MergeHandler handler(getPersistenceProvider(), getEnv());
    _nodes.clear();
    _nodes.push_back(api::MergeBucketCommand::Node(0, false));
    _nodes.push_back(api::MergeBucketCommand::Node(1, false));
    _nodes.push_back(api::MergeBucketCommand::Node(2, false));
    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(cmd, *_context);

    std::shared_ptr<api::GetBucketDiffCommand> getBucketDiffCmd(
            fetchSingleMessage<api::GetBucketDiffCommand>());
    api::GetBucketDiffReply::UP getBucketDiffReply(
            new api::GetBucketDiffReply(*getBucketDiffCmd));

    handler.handleGetBucketDiffReply(*getBucketDiffReply, messageKeeper());

    std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
            fetchSingleMessage<api::ApplyBucketDiffCommand>());
    api::ApplyBucketDiffReply::UP applyBucketDiffReply(
            new api::ApplyBucketDiffReply(*applyBucketDiffCmd));
    CPPUNIT_ASSERT(!applyBucketDiffReply->getDiff().empty());
    // Change a hasMask to indicate something changed during merging.
    applyBucketDiffReply->getDiff()[0]._entry._hasMask = 0x5;

    MessageSenderStub stub;
    LOG(debug, "sending apply bucket diff reply");
    handler.handleApplyBucketDiffReply(*applyBucketDiffReply, stub);

    CPPUNIT_ASSERT_EQUAL(1, (int)stub.commands.size());

    api::ApplyBucketDiffCommand::SP applyBucketDiffCmd2(
            std::dynamic_pointer_cast<api::ApplyBucketDiffCommand>(
                    stub.commands[0]));
    CPPUNIT_ASSERT(applyBucketDiffCmd2.get());
    CPPUNIT_ASSERT_EQUAL(applyBucketDiffCmd->getDiff().size(),
                         applyBucketDiffCmd2->getDiff().size());
    CPPUNIT_ASSERT_EQUAL(uint16_t(0x5),
                         applyBucketDiffCmd2->getDiff()[0]._entry._hasMask);
}

void
MergeHandlerTest::testEntryRemovedAfterGetBucketDiff()
{
    MergeHandler handler(getPersistenceProvider(), getEnv());
    std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff;
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = 13001; // Removed in persistence
        e._entry._hasMask = 0x2;
        e._entry._flags = MergeHandler::IN_USE;
        applyDiff.push_back(e);
    }
    setUpChain(BACK);
    std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
            new api::ApplyBucketDiffCommand(_bucket, _nodes, 1024*1024));
    applyBucketDiffCmd->getDiff() = applyDiff;

    MessageTracker::UP tracker = handler.handleApplyBucketDiff(*applyBucketDiffCmd, *_context);

    api::ApplyBucketDiffReply::SP applyBucketDiffReply(
            std::dynamic_pointer_cast<api::ApplyBucketDiffReply>(
                    tracker->getReply()));
    CPPUNIT_ASSERT(applyBucketDiffReply.get());

    std::vector<api::ApplyBucketDiffCommand::Entry>& diff(
            applyBucketDiffReply->getDiff());
    CPPUNIT_ASSERT_EQUAL(size_t(1), diff.size());
    CPPUNIT_ASSERT(!diff[0].filled());
    CPPUNIT_ASSERT_EQUAL(uint16_t(0x0), diff[0]._entry._hasMask);
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
        spi::Context& context)
{
    api::MergeBucketCommand cmd(test._bucket, test._nodes, test._maxTimestamp);
    handler.handleMergeBucket(cmd, context);
}

void
MergeHandlerTest::testMergeBucketSPIFailures()
{
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    MergeHandler handler(providerWrapper, getEnv());
    providerWrapper.setResult(
            spi::Result(spi::Result::PERMANENT_ERROR,
                        "who you gonna call?"));
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
        CPPUNIT_ASSERT_EQUAL(std::string(),
                             doTestSPIException(handler,
                                                providerWrapper,
                                                invoker,
                                                *it));
    }
}

void
MergeHandlerTest::HandleGetBucketDiffInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context& context)
{
    api::GetBucketDiffCommand cmd(test._bucket, test._nodes, test._maxTimestamp);
    handler.handleGetBucketDiff(cmd, context);
}

void
MergeHandlerTest::testGetBucketDiffSPIFailures()
{
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    MergeHandler handler(providerWrapper, getEnv());
    providerWrapper.setResult(
            spi::Result(spi::Result::PERMANENT_ERROR,
                        "who you gonna call?"));
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
        CPPUNIT_ASSERT_EQUAL(std::string(),
                             doTestSPIException(handler,
                                                providerWrapper,
                                                invoker,
                                                *it));
    }
}

void
MergeHandlerTest::HandleApplyBucketDiffInvoker::invoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context& context)
{
    ++_counter;
    std::shared_ptr<api::ApplyBucketDiffCommand> cmd(
            test.createDummyApplyDiff(100000 * _counter));
    handler.handleApplyBucketDiff(*cmd, context);
}

void
MergeHandlerTest::testApplyBucketDiffSPIFailures()
{
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    MergeHandler handler(providerWrapper, getEnv());
    providerWrapper.setResult(
            spi::Result(spi::Result::PERMANENT_ERROR,
                        "who you gonna call?"));
    setUpChain(MIDDLE);

    ExpectedExceptionSpec exceptions[] = {
        { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
        { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
        { PersistenceProviderWrapper::FAIL_PUT, "Failed put" },
        { PersistenceProviderWrapper::FAIL_REMOVE, "Failed remove" },
        { PersistenceProviderWrapper::FAIL_FLUSH, "Failed flush" },
    };

    typedef ExpectedExceptionSpec* ExceptionIterator;
    ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

    for (ExceptionIterator it = exceptions; it != last; ++it) {
        HandleApplyBucketDiffInvoker invoker;
        CPPUNIT_ASSERT_EQUAL(std::string(),
                             doTestSPIException(handler,
                                                providerWrapper,
                                                invoker,
                                                *it));
        // Casual, in-place testing of bug 6752085.
        // This will fail if we give NaN to the metric in question.
        CPPUNIT_ASSERT(std::isfinite(getEnv()._metrics
                           .mergeAverageDataReceivedNeeded.getLast()));
    }
}

void
MergeHandlerTest::HandleGetBucketDiffReplyInvoker::beforeInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context& context)
{
    api::MergeBucketCommand cmd(test._bucket, test._nodes, test._maxTimestamp);
    handler.handleMergeBucket(cmd, context);
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

void
MergeHandlerTest::testGetBucketDiffReplySPIFailures()
{
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    MergeHandler handler(providerWrapper, getEnv());
    providerWrapper.setResult(
            spi::Result(spi::Result::PERMANENT_ERROR,
                        "who you gonna call?"));
    HandleGetBucketDiffReplyInvoker invoker;

    setUpChain(FRONT);

    ExpectedExceptionSpec exceptions[] = {
        { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
        { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
    };

    typedef ExpectedExceptionSpec* ExceptionIterator;
    ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

    for (ExceptionIterator it = exceptions; it != last; ++it) {
        CPPUNIT_ASSERT_EQUAL(std::string(),
                             doTestSPIException(handler,
                                                providerWrapper,
                                                invoker,
                                                *it));
    }
}

void
MergeHandlerTest::HandleApplyBucketDiffReplyInvoker::beforeInvoke(
        MergeHandlerTest& test,
        MergeHandler& handler,
        spi::Context& context)
{
    ++_counter;
    _stub.clear();
    if (getChainPos() == FRONT) {
        api::MergeBucketCommand cmd(test._bucket, test._nodes, test._maxTimestamp);
        handler.handleMergeBucket(cmd, context);
        std::shared_ptr<api::GetBucketDiffCommand> diffCmd(
                test.fetchSingleMessage<api::GetBucketDiffCommand>());
        std::shared_ptr<api::GetBucketDiffCommand> dummyDiff(
                test.createDummyGetBucketDiff(100000 * _counter, 0x4));
        diffCmd->getDiff() = dummyDiff->getDiff();

        api::GetBucketDiffReply diffReply(*diffCmd);
        handler.handleGetBucketDiffReply(diffReply, _stub);

        CPPUNIT_ASSERT_EQUAL(size_t(1), _stub.commands.size());
        _applyCmd = std::dynamic_pointer_cast<api::ApplyBucketDiffCommand>(
                _stub.commands[0]);
    } else {
        // Pretend last node in chain has data and that it will be fetched when
        // chain is unwinded.
        std::shared_ptr<api::ApplyBucketDiffCommand> cmd(
                test.createDummyApplyDiff(100000 * _counter, 0x4, false));
        handler.handleApplyBucketDiff(*cmd, context);
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

void
MergeHandlerTest::testApplyBucketDiffReplySPIFailures()
{
    PersistenceProviderWrapper providerWrapper(
            getPersistenceProvider());
    HandleApplyBucketDiffReplyInvoker invoker;
    for (int i = 0; i < 2; ++i) {
        ChainPos pos(i == 0 ? FRONT : MIDDLE);
        setUpChain(pos);
        invoker.setChainPos(pos);
        MergeHandler handler(providerWrapper, getEnv());
        providerWrapper.setResult(
                spi::Result(spi::Result::PERMANENT_ERROR,
                            "who you gonna call?"));

        ExpectedExceptionSpec exceptions[] = {
            { PersistenceProviderWrapper::FAIL_CREATE_ITERATOR, "create iterator" },
            { PersistenceProviderWrapper::FAIL_ITERATE, "iterate" },
            { PersistenceProviderWrapper::FAIL_PUT, "Failed put" },
            { PersistenceProviderWrapper::FAIL_REMOVE, "Failed remove" },
            { PersistenceProviderWrapper::FAIL_FLUSH, "Failed flush" },
        };

        typedef ExpectedExceptionSpec* ExceptionIterator;
        ExceptionIterator last = exceptions + sizeof(exceptions)/sizeof(exceptions[0]);

        for (ExceptionIterator it = exceptions; it != last; ++it) {
            CPPUNIT_ASSERT_EQUAL(std::string(),
                                 doTestSPIException(handler,
                                                    providerWrapper,
                                                    invoker,
                                                    *it));
        }
    }
}

void
MergeHandlerTest::testRemoveFromDiff()
{
    framework::defaultimplementation::FakeClock clock;
    MergeStatus status(clock, documentapi::LoadType::DEFAULT, 0, 0);

    std::vector<api::GetBucketDiffCommand::Entry> diff(2);
    diff[0]._timestamp = 1234;
    diff[0]._flags = 0x1;
    diff[0]._hasMask = 0x2;

    diff[1]._timestamp = 5678;
    diff[1]._flags = 0x3;
    diff[1]._hasMask = 0x6;

    status.diff.insert(status.diff.end(), diff.begin(), diff.end());

    {
        std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff(2);
        applyDiff[0]._entry._timestamp = 1234;
        applyDiff[0]._entry._flags = 0x1;
        applyDiff[0]._entry._hasMask = 0x0; // Removed during merging

        applyDiff[1]._entry._timestamp = 5678;
        applyDiff[1]._entry._flags = 0x3;
        applyDiff[1]._entry._hasMask = 0x7;

        CPPUNIT_ASSERT(status.removeFromDiff(applyDiff, 0x7));
        CPPUNIT_ASSERT(status.diff.empty());
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

        CPPUNIT_ASSERT(!status.removeFromDiff(applyDiff, 0x7));
        CPPUNIT_ASSERT_EQUAL(size_t(2), status.diff.size());
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

        CPPUNIT_ASSERT(status.removeFromDiff(applyDiff, 0x7));
        CPPUNIT_ASSERT_EQUAL(size_t(2), status.diff.size());
    }
}

void
MergeHandlerTest::testRemovePutOnExistingTimestamp()
{
    setUpChain(BACK);

    document::TestDocMan docMan;
    document::Document::SP doc(
            docMan.createRandomDocumentAtLocation(_location));
    spi::Timestamp ts(10111);
    doPut(doc, ts);

    MergeHandler handler(getPersistenceProvider(), getEnv());
    std::vector<api::ApplyBucketDiffCommand::Entry> applyDiff;
    {
        api::ApplyBucketDiffCommand::Entry e;
        e._entry._timestamp = ts;
        e._entry._hasMask = 0x1;
        e._docName = doc->getId().toString();
        e._entry._flags = MergeHandler::IN_USE | MergeHandler::DELETED;
        applyDiff.push_back(e);
    }

    std::shared_ptr<api::ApplyBucketDiffCommand> applyBucketDiffCmd(
            new api::ApplyBucketDiffCommand(_bucket, _nodes, 1024*1024));
    applyBucketDiffCmd->getDiff() = applyDiff;

    MessageTracker::UP tracker = handler.handleApplyBucketDiff(*applyBucketDiffCmd, *_context);

    api::ApplyBucketDiffReply::SP applyBucketDiffReply(
            std::dynamic_pointer_cast<api::ApplyBucketDiffReply>(
                    tracker->getReply()));
    CPPUNIT_ASSERT(applyBucketDiffReply.get());

    api::MergeBucketCommand cmd(_bucket, _nodes, _maxTimestamp);
    handler.handleMergeBucket(cmd, *_context);

    std::shared_ptr<api::GetBucketDiffCommand> getBucketDiffCmd(
            fetchSingleMessage<api::GetBucketDiffCommand>());

    // Timestamp should now be a regular remove
    bool foundTimestamp = false;
    for (size_t i = 0; i < getBucketDiffCmd->getDiff().size(); ++i) {
        const api::GetBucketDiffCommand::Entry& e(
                getBucketDiffCmd->getDiff()[i]);
        if (e._timestamp == ts) {
            CPPUNIT_ASSERT_EQUAL(
                    uint16_t(MergeHandler::IN_USE | MergeHandler::DELETED),
                    e._flags);
            foundTimestamp = true;
            break;
        }
    }
    CPPUNIT_ASSERT(foundTimestamp);
}

} // storage
