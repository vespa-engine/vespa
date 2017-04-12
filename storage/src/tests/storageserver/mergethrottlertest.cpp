// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cppunit/extensions/HelperMacros.h>
#include <memory>
#include <iterator>
#include <vector>
#include <algorithm>
#include <ctime>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <tests/common/testhelper.h>
#include <tests/common/storagelinktest.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/storage/storageserver/mergethrottler.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace document;
using namespace storage::api;

namespace storage {

namespace {

struct MergeBuilder
{
    document::BucketId _bucket;
    api::Timestamp _maxTimestamp;
    std::vector<uint16_t> _nodes;
    std::vector<uint16_t> _chain;
    uint64_t _clusterStateVersion;

    MergeBuilder(const document::BucketId& bucket)
        : _bucket(bucket),
          _maxTimestamp(1234),
          _chain(),
          _clusterStateVersion(1)
    {
        nodes(0, 1, 2);
    }

    MergeBuilder& nodes(uint16_t n0) {
        _nodes.push_back(n0);
        return *this;
    }
    MergeBuilder& nodes(uint16_t n0, uint16_t n1) {
        _nodes.push_back(n0);
        _nodes.push_back(n1);
        return *this;
    }
    MergeBuilder& nodes(uint16_t n0, uint16_t n1, uint16_t n2) {
        _nodes.push_back(n0);
        _nodes.push_back(n1);
        _nodes.push_back(n2);
        return *this;
    }
    MergeBuilder& maxTimestamp(api::Timestamp maxTs) {
        _maxTimestamp = maxTs;
        return *this;
    }
    MergeBuilder& clusterStateVersion(uint64_t csv) {
        _clusterStateVersion = csv;
        return *this;
    }
    MergeBuilder& chain(uint16_t n0) {
        _chain.clear();
        _chain.push_back(n0);
        return *this;
    }
    MergeBuilder& chain(uint16_t n0, uint16_t n1) {
        _chain.clear();
        _chain.push_back(n0);
        _chain.push_back(n1);
        return *this;
    }
    MergeBuilder& chain(uint16_t n0, uint16_t n1, uint16_t n2) {
        _chain.clear();
        _chain.push_back(n0);
        _chain.push_back(n1);
        _chain.push_back(n2);
        return *this;
    }

    api::MergeBucketCommand::SP create() const {
        std::vector<api::MergeBucketCommand::Node> n;
        for (uint32_t i = 0; i < _nodes.size(); ++i) {
            n.push_back(_nodes[i]);
        }
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(_bucket, n, _maxTimestamp,
                                       _clusterStateVersion, _chain));
        StorageMessageAddress address("storage", lib::NodeType::STORAGE, _nodes[0]);
        cmd->setAddress(address);
        return cmd;
    }
};

std::shared_ptr<api::SetSystemStateCommand>
makeSystemStateCmd(const std::string& state)
{
    return std::make_shared<api::SetSystemStateCommand>(
            lib::ClusterState(state));
}

} // anon ns

class MergeThrottlerTest : public CppUnit::TestFixture
{
    CPPUNIT_TEST_SUITE(MergeThrottlerTest);
    CPPUNIT_TEST(testMergesConfig);
    CPPUNIT_TEST(testChain);
    CPPUNIT_TEST(testWithSourceOnlyNode);
    CPPUNIT_TEST(test42DistributorBehavior);
    CPPUNIT_TEST(test42DistributorBehaviorDoesNotTakeOwnership);
    CPPUNIT_TEST(testEndOfChainExecutionDoesNotTakeOwnership);
    CPPUNIT_TEST(testResendHandling);
    CPPUNIT_TEST(testPriorityQueuing);
    CPPUNIT_TEST(testCommandInQueueDuplicateOfKnownMerge);
    CPPUNIT_TEST(testInvalidReceiverNode);
    CPPUNIT_TEST(testForwardQueuedMerge);
    CPPUNIT_TEST(testExecuteQueuedMerge);
    CPPUNIT_TEST(testFlush);
    CPPUNIT_TEST(testUnseenMergeWithNodeInChain);
    CPPUNIT_TEST(testMergeWithNewerClusterStateFlushesOutdatedQueued);
    CPPUNIT_TEST(testUpdatedClusterStateFlushesOutdatedQueued);
    CPPUNIT_TEST(test42MergesDoNotTriggerFlush);
    CPPUNIT_TEST(testOutdatedClusterStateMergesAreRejectedOnArrival);
    CPPUNIT_TEST(testUnknownMergeWithSelfInChain);
    CPPUNIT_TEST(testBusyReturnedOnFullQueue);
    CPPUNIT_TEST(testBrokenCycle);
    CPPUNIT_TEST(testGetBucketDiffCommandNotInActiveSetIsRejected);
    CPPUNIT_TEST(testApplyBucketDiffCommandNotInActiveSetIsRejected);
    CPPUNIT_TEST(testNewClusterStateAbortsAllOutdatedActiveMerges);
    CPPUNIT_TEST_SUITE_END();
public:
    void setUp() override;
    void tearDown() override;

    void testMergesConfig();
    void testChain();
    void testWithSourceOnlyNode();
    void test42DistributorBehavior();
    void test42DistributorBehaviorDoesNotTakeOwnership();
    void testEndOfChainExecutionDoesNotTakeOwnership();
    void testResendHandling();
    void testPriorityQueuing();
    void testCommandInQueueDuplicateOfKnownMerge();
    void testInvalidReceiverNode();
    void testForwardQueuedMerge();
    void testExecuteQueuedMerge();
    void testFlush();
    void testUnseenMergeWithNodeInChain();
    void testMergeWithNewerClusterStateFlushesOutdatedQueued();
    void testUpdatedClusterStateFlushesOutdatedQueued();
    void test42MergesDoNotTriggerFlush();
    void testOutdatedClusterStateMergesAreRejectedOnArrival();
    void testUnknownMergeWithSelfInChain();
    void testBusyReturnedOnFullQueue();
    void testBrokenCycle();
    void testGetBucketDiffCommandNotInActiveSetIsRejected();
    void testApplyBucketDiffCommandNotInActiveSetIsRejected();
    void testNewClusterStateAbortsAllOutdatedActiveMerges();
private:
    static const int _storageNodeCount = 3;
    static const int _messageWaitTime = 100;

    // Using n storage node links and dummy servers
    std::vector<std::shared_ptr<DummyStorageLink> > _topLinks;
    std::vector<std::shared_ptr<TestServiceLayerApp> > _servers;
    std::vector<MergeThrottler*> _throttlers;
    std::vector<DummyStorageLink*> _bottomLinks;

    api::MergeBucketCommand::SP sendMerge(const MergeBuilder&);

    void sendAndExpectReply(
        const std::shared_ptr<api::StorageMessage>& msg,
        const api::MessageType& expectedReplyType,
        api::ReturnCode::Result expectedResultCode);
};

const int MergeThrottlerTest::_storageNodeCount;
const int MergeThrottlerTest::_messageWaitTime;

CPPUNIT_TEST_SUITE_REGISTRATION(MergeThrottlerTest);

void
MergeThrottlerTest::setUp()
{
    vdstestlib::DirConfig config(getStandardConfig(true));

    for (int i = 0; i < _storageNodeCount; ++i) {
        std::unique_ptr<TestServiceLayerApp> server(
                new TestServiceLayerApp(DiskCount(1), NodeIndex(i)));
        server->setClusterState(lib::ClusterState(
                    "distributor:100 storage:100 version:1"));
        std::unique_ptr<DummyStorageLink> top;

        top.reset(new DummyStorageLink);
        MergeThrottler* throttler = new MergeThrottler(config.getConfigId(), server->getComponentRegister());
        // MergeThrottler will be sandwiched in between two dummy links
        top->push_back(std::unique_ptr<StorageLink>(throttler));
        DummyStorageLink* bottom = new DummyStorageLink;
        throttler->push_back(std::unique_ptr<StorageLink>(bottom));

        _servers.push_back(std::shared_ptr<TestServiceLayerApp>(server.release()));
        _throttlers.push_back(throttler);
        _bottomLinks.push_back(bottom);
        top->open();
        _topLinks.push_back(std::shared_ptr<DummyStorageLink>(top.release()));
    }
}

void
MergeThrottlerTest::tearDown()
{
    for (std::size_t i = 0; i < _topLinks.size(); ++i) {
        if (_topLinks[i]->getState() == StorageLink::OPENED) {
            _topLinks[i]->close();
            _topLinks[i]->flush();
        }
        _topLinks[i] = std::shared_ptr<DummyStorageLink>();
    }
    _topLinks.clear();
    _bottomLinks.clear();
    _throttlers.clear();
    _servers.clear();
}

namespace {

template <typename Iterator>
bool
checkChain(const StorageMessage::SP& msg,
           Iterator first, Iterator end)
{
    const MergeBucketCommand& cmd =
        dynamic_cast<const MergeBucketCommand&>(*msg);

    if (cmd.getChain().size() != static_cast<std::size_t>(std::distance(first, end))) {
        return false;
    }

    return std::equal(cmd.getChain().begin(), cmd.getChain().end(), first);
}

void waitUntilMergeQueueIs(MergeThrottler& throttler, std::size_t sz, int timeout)
{
    std::time_t start = std::time(0);
    while (true) {
        std::size_t count;
        {
            vespalib::LockGuard lock(throttler.getStateLock());
            count = throttler.getMergeQueue().size();
        }
        if (count == sz) {
            break;
        }
        std::time_t now = std::time(0);
        if (now - start > timeout) {
            std::ostringstream os;
            os << "Timeout while waiting for merge queue with " << sz << " items. Had "
               << count << " at timeout.";
            throw vespalib::IllegalStateException(os.str(), VESPA_STRLOC);
        }
        FastOS_Thread::Sleep(1);
    }
}

}

// Extremely simple test that just checks that (min|max)_merges_per_node
// under the stor-server config gets propagated to all the nodes
void
MergeThrottlerTest::testMergesConfig()
{
    for (int i = 0; i < _storageNodeCount; ++i) {
        CPPUNIT_ASSERT_EQUAL(uint32_t(25), _throttlers[i]->getThrottlePolicy().getMaxPendingCount());
        CPPUNIT_ASSERT_EQUAL(std::size_t(20), _throttlers[i]->getMaxQueueSize());
    }
}

// Test that a distributor sending a merge to the lowest-index storage
// node correctly invokes a merge forwarding chain and subsequent unwind.
void
MergeThrottlerTest::testChain()
{
    uint16_t indices[_storageNodeCount];
    for (int i = 0; i < _storageNodeCount; ++i) {
        indices[i] = i;
        _servers[i]->setClusterState(lib::ClusterState("distributor:100 storage:100 version:123"));
    }

    BucketId bid(14, 0x1337);

    // Use different node permutations to ensure it works no matter which node is
    // set as the executor. More specifically, _all_ permutations.
    do {
        uint16_t lastNodeIdx = _storageNodeCount - 1;
        uint16_t executorNode = indices[0];

        //std::cout << "\n----\n";
        std::vector<MergeBucketCommand::Node> nodes;
        for (int i = 0; i < _storageNodeCount; ++i) {
            nodes.push_back(MergeBucketCommand::Node(indices[i], (i + executorNode) % 2 == 0));
            //std::cout << indices[i] << " ";
        }
        //std::cout << "\n";
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(bid, nodes, UINT_MAX, 123));
        cmd->setPriority(7);
        cmd->setTimeout(54321);
        StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);
        cmd->setAddress(address);
        const uint16_t distributorIndex = 123;
        cmd->setSourceIndex(distributorIndex); // Dummy distributor index that must be forwarded

        StorageMessage::SP fwd = cmd;
        StorageMessage::SP fwdToExec;

        // TODO: make generic wrt. _storageNodeCount

        for (int i = 0; i < _storageNodeCount - 1; ++i) {
            if (i == executorNode) {
                fwdToExec = fwd;
            }
            CPPUNIT_ASSERT_EQUAL(uint16_t(i), _servers[i]->getIndex());
            // No matter the node order, command is always sent to node 0 -> 1 -> 2 etc
            _topLinks[i]->sendDown(fwd);
            _topLinks[i]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

            //std::cout << "fwd " << i << " -> " << i+1 << "\n";

            // Forwarded merge should not be sent down. Should not be necessary
            // to lock throttler here, since it should be sleeping like a champion
            CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[i]->getNumCommands());
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[i]->getNumReplies());
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[i]->getActiveMerges().size());

            fwd = _topLinks[i]->getAndRemoveMessage(MessageType::MERGEBUCKET);
            CPPUNIT_ASSERT_EQUAL(uint16_t(i + 1), fwd->getAddress()->getIndex());
            CPPUNIT_ASSERT_EQUAL(distributorIndex, dynamic_cast<const StorageCommand&>(*fwd).getSourceIndex());
            {
                //uint16_t chain[] = { 0 };
                std::vector<uint16_t> chain;
                for (int j = 0; j <= i; ++j) {
                    chain.push_back(j);
                }
                CPPUNIT_ASSERT(checkChain(fwd, chain.begin(), chain.end()));
            }
            // Ensure priority, cluster state version and timeout is correctly forwarded
            CPPUNIT_ASSERT_EQUAL(7, static_cast<int>(fwd->getPriority()));
            CPPUNIT_ASSERT_EQUAL(uint32_t(123), dynamic_cast<const MergeBucketCommand&>(*fwd).getClusterStateVersion());
            CPPUNIT_ASSERT_EQUAL(uint32_t(54321), dynamic_cast<const StorageCommand&>(*fwd).getTimeout());
        }

        _topLinks[lastNodeIdx]->sendDown(fwd);

        // If node 2 is the first in the node list, it should immediately execute
        // the merge. Otherwise, a cycle with the first node should be formed.
        if (executorNode != lastNodeIdx) {
            //std::cout << "cycle " << lastNodeIdx << " -> " << executorNode << "\n";
            _topLinks[lastNodeIdx]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
            // Forwarded merge should not be sent down
            CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[lastNodeIdx]->getNumCommands());
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[lastNodeIdx]->getNumReplies());
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[lastNodeIdx]->getActiveMerges().size());

            fwd = _topLinks[lastNodeIdx]->getAndRemoveMessage(MessageType::MERGEBUCKET);
            CPPUNIT_ASSERT_EQUAL(uint16_t(executorNode), fwd->getAddress()->getIndex());
            CPPUNIT_ASSERT_EQUAL(distributorIndex, dynamic_cast<const StorageCommand&>(*fwd).getSourceIndex());
            {
                std::vector<uint16_t> chain;
                for (int j = 0; j < _storageNodeCount; ++j) {
                    chain.push_back(j);
                }
                CPPUNIT_ASSERT(checkChain(fwd, chain.begin(), chain.end()));
            }
            CPPUNIT_ASSERT_EQUAL(7, static_cast<int>(fwd->getPriority()));
            CPPUNIT_ASSERT_EQUAL(uint32_t(123), dynamic_cast<const MergeBucketCommand&>(*fwd).getClusterStateVersion());
            CPPUNIT_ASSERT_EQUAL(uint32_t(54321), dynamic_cast<const StorageCommand&>(*fwd).getTimeout());

            _topLinks[executorNode]->sendDown(fwd);
        }

        _bottomLinks[executorNode]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

        // Forwarded merge has now been sent down to persistence layer
        CPPUNIT_ASSERT_EQUAL(std::size_t(1), _bottomLinks[executorNode]->getNumCommands());
        CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[executorNode]->getNumReplies()); // No reply sent yet
        CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[executorNode]->getActiveMerges().size()); // no re-registering merge

        if (executorNode != lastNodeIdx) {
            // The MergeBucketCommand that is kept in the executor node should
            // be the one from the node it initially got it from, NOT the one
            // from the last node, since the chain has looped
            CPPUNIT_ASSERT(_throttlers[executorNode]->getActiveMerges().find(bid)
                           != _throttlers[executorNode]->getActiveMerges().end());
            CPPUNIT_ASSERT_EQUAL(static_cast<StorageMessage*>(fwdToExec.get()),
                                 _throttlers[executorNode]->getActiveMerges().find(bid)->second.getMergeCmd().get());
        }

        // Send reply up from persistence layer to simulate a completed
        // merge operation. Chain should now unwind properly
        fwd = _bottomLinks[executorNode]->getAndRemoveMessage(MessageType::MERGEBUCKET);
        CPPUNIT_ASSERT_EQUAL(7, static_cast<int>(fwd->getPriority()));
        CPPUNIT_ASSERT_EQUAL(uint32_t(123), dynamic_cast<const MergeBucketCommand&>(*fwd).getClusterStateVersion());
        CPPUNIT_ASSERT_EQUAL(uint32_t(54321), dynamic_cast<const StorageCommand&>(*fwd).getTimeout());

        std::shared_ptr<MergeBucketReply> reply(
                new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd)));
        reply->setResult(ReturnCode(ReturnCode::OK, "Great success! :D-|-<"));
        _bottomLinks[executorNode]->sendUp(reply);

        _topLinks[executorNode]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

        if (executorNode != lastNodeIdx) {
            // Merge should not be removed yet from executor, since it's pending an unwind
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[executorNode]->getActiveMerges().size());
            CPPUNIT_ASSERT_EQUAL(static_cast<StorageMessage*>(fwdToExec.get()),
                                 _throttlers[executorNode]->getActiveMerges().find(bid)->second.getMergeCmd().get());
        }
        // MergeBucketReply waiting to be sent back to node 2. NOTE: we don't have any
        // transport context stuff set up here to perform the reply mapping, so we
        // have to emulate it
        CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[executorNode]->getNumReplies());

        StorageMessage::SP unwind = _topLinks[executorNode]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
        CPPUNIT_ASSERT_EQUAL(uint16_t(executorNode), unwind->getAddress()->getIndex());

        // eg: 0 -> 2 -> 1 -> 0. Or: 2 -> 1 -> 0 if no cycle
        for (int i = (executorNode != lastNodeIdx ? _storageNodeCount - 1 : _storageNodeCount - 2); i >= 0; --i) {
            //std::cout << "unwind " << i << "\n";

            _topLinks[i]->sendDown(unwind);
            _topLinks[i]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

            CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[i]->getNumCommands());
            CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[i]->getNumReplies());
            CPPUNIT_ASSERT_EQUAL(std::size_t(0), _throttlers[i]->getActiveMerges().size());

            unwind = _topLinks[i]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
            CPPUNIT_ASSERT_EQUAL(uint16_t(i), unwind->getAddress()->getIndex());
        }

        const MergeBucketReply& mbr = dynamic_cast<const MergeBucketReply&>(*unwind);

        CPPUNIT_ASSERT_EQUAL(ReturnCode::OK, mbr.getResult().getResult());
        CPPUNIT_ASSERT_EQUAL(vespalib::string("Great success! :D-|-<"), mbr.getResult().getMessage());
        CPPUNIT_ASSERT_EQUAL(bid, mbr.getBucketId());

    } while (std::next_permutation(indices, indices + _storageNodeCount));

    //std::cout << "\n" << *_topLinks[0] << "\n";
}

void
MergeThrottlerTest::testWithSourceOnlyNode()
{
    BucketId bid(14, 0x1337);

    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 0);

    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(2);
    nodes.push_back(MergeBucketCommand::Node(1, true));
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(bid, nodes, UINT_MAX, 123));

    cmd->setAddress(address);
    _topLinks[0]->sendDown(cmd);

    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    StorageMessage::SP fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    CPPUNIT_ASSERT_EQUAL(uint16_t(1), fwd->getAddress()->getIndex());

    _topLinks[1]->sendDown(fwd);

    _topLinks[1]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    fwd = _topLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    CPPUNIT_ASSERT_EQUAL(uint16_t(2), fwd->getAddress()->getIndex());

    _topLinks[2]->sendDown(fwd);

    _topLinks[2]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    fwd = _topLinks[2]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    CPPUNIT_ASSERT_EQUAL(uint16_t(0), fwd->getAddress()->getIndex());

    _topLinks[0]->sendDown(fwd);
    _bottomLinks[0]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    _bottomLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd)));
    reply->setResult(ReturnCode(ReturnCode::OK, "Great success! :D-|-<"));
    _bottomLinks[0]->sendUp(reply);

    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);
    fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(uint16_t(0), fwd->getAddress()->getIndex());

    // Assume everything's fine from here on out
}

// 4.2 distributors don't guarantee they'll send to lowest node
// index, so we must detect such situations and execute the merge
// immediately rather than attempt to chain it. Test that this
// is done correctly.
void
MergeThrottlerTest::test42DistributorBehavior()
{
    BucketId bid(32, 0xfeef00);

    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    nodes.push_back(2);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(bid, nodes, 1234));

    // Send to node 1, which is not the lowest index
    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 1);

    cmd->setAddress(address);
    _topLinks[1]->sendDown(cmd);
    _bottomLinks[1]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

    // Should now have been sent to persistence layer
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _bottomLinks[1]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[1]->getNumReplies()); // No reply sent yet
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[1]->getActiveMerges().size());

    // Send reply up from persistence layer to simulate a completed
    // merge operation. Merge should be removed from state.
    _bottomLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*cmd)));
    reply->setResult(ReturnCode(ReturnCode::OK, "Tonight we dine on turtle soup!"));
    _bottomLinks[1]->sendUp(reply);
    _topLinks[1]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[1]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[1]->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _throttlers[1]->getActiveMerges().size());

    CPPUNIT_ASSERT_EQUAL(uint64_t(1), _throttlers[1]->getMetrics().local.ok.getValue());
}

// Test that we don't take ownership of the merge command when we're
// just passing it through to the persistence layer when receiving
// a merge command that presumably comes form a 4.2 distributor
void
MergeThrottlerTest::test42DistributorBehaviorDoesNotTakeOwnership()
{
    BucketId bid(32, 0xfeef00);

    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    nodes.push_back(2);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(bid, nodes, 1234));

    // Send to node 1, which is not the lowest index
    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 1);

    cmd->setAddress(address);
    _topLinks[1]->sendDown(cmd);
    _bottomLinks[1]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

    // Should now have been sent to persistence layer
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _bottomLinks[1]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[1]->getNumReplies()); // No reply sent yet
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[1]->getActiveMerges().size());

    _bottomLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // To ensure we don't try to deref any non-owned messages
    framework::HttpUrlPath path("?xml");
    std::ostringstream ss;
    _throttlers[1]->reportStatus(ss, path);

    // Flush throttler (synchronously). Should NOT generate a reply
    // for the merge command, as it is not owned by the throttler
    StorageLinkTest::callOnFlush(*_throttlers[1], true);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[1]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[1]->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _throttlers[1]->getActiveMerges().size());

    // Send a belated reply from persistence up just to ensure the
    // throttler doesn't throw a fit if it receives an unknown merge
    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*cmd)));
    reply->setResult(ReturnCode(ReturnCode::OK, "Tonight we dine on turtle soup!"));
    _bottomLinks[1]->sendUp(reply);
    _topLinks[1]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[1]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[1]->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _throttlers[1]->getActiveMerges().size());
}

// Test that we don't take ownership of the merge command when we're
// just passing it through to the persistence layer when we're at the
// the end of the chain and also the designated executor
void
MergeThrottlerTest::testEndOfChainExecutionDoesNotTakeOwnership()
{
    BucketId bid(32, 0xfeef00);

    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(2);
    nodes.push_back(1);
    nodes.push_back(0);
    std::vector<uint16_t> chain;
    chain.push_back(0);
    chain.push_back(1);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(bid, nodes, 1234, 1, chain));

    // Send to last node, which is not the lowest index
    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);

    cmd->setAddress(address);
    _topLinks[2]->sendDown(cmd);
    _bottomLinks[2]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

    // Should now have been sent to persistence layer
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _bottomLinks[2]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[2]->getNumReplies()); // No reply sent yet
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _throttlers[2]->getActiveMerges().size());

    _bottomLinks[2]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // To ensure we don't try to deref any non-owned messages
    framework::HttpUrlPath path("");
    std::ostringstream ss;
    _throttlers[2]->reportStatus(ss, path);

    // Flush throttler (synchronously). Should NOT generate a reply
    // for the merge command, as it is not owned by the throttler
    StorageLinkTest::callOnFlush(*_throttlers[2], true);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[2]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[2]->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _throttlers[2]->getActiveMerges().size());

    // Send a belated reply from persistence up just to ensure the
    // throttler doesn't throw a fit if it receives an unknown merge
    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*cmd)));
    reply->setResult(ReturnCode(ReturnCode::OK, "Tonight we dine on turtle soup!"));
    _bottomLinks[2]->sendUp(reply);
    _topLinks[2]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[2]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[2]->getNumReplies());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _throttlers[2]->getActiveMerges().size());
}

// Test that nodes resending a merge command won't lead to duplicate
// state registration/forwarding or erasing the already present state
// information.
void
MergeThrottlerTest::testResendHandling()
{
    BucketId bid(32, 0xbadbed);

    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    nodes.push_back(2);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(bid, nodes, 1234));

    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 1);

    cmd->setAddress(address);
    _topLinks[0]->sendDown(cmd);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

    StorageMessage::SP fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // Resend from "distributor". Just use same message, as that won't matter here
    _topLinks[0]->sendDown(cmd);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    // Reply should be BUSY
    StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);

    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
            ReturnCode::BUSY);

    _topLinks[1]->sendDown(fwd);
    _topLinks[1]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    fwd = _topLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    _topLinks[2]->sendDown(fwd);
    _topLinks[2]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    _topLinks[2]->sendDown(fwd);
    _topLinks[2]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    // Reply should be BUSY
    reply = _topLinks[2]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
            ReturnCode::BUSY);

    fwd = _topLinks[2]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    _topLinks[0]->sendDown(fwd);
    _bottomLinks[0]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    _topLinks[0]->sendDown(fwd);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
            ReturnCode::BUSY);
}

void
MergeThrottlerTest::testPriorityQueuing()
{
    // Fill up all active merges
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    nodes.push_back(2);
    CPPUNIT_ASSERT(maxPending >= 4u);
    for (std::size_t i = 0; i < maxPending; ++i) {
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234));
        cmd->setPriority(100);
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 0 queued
    _topLinks[0]->waitForMessages(maxPending, 5);
    waitUntilMergeQueueIs(*_throttlers[0], 0, _messageWaitTime);

    // Queue up some merges with different priorities
    int priorities[4] = { 200, 150, 120, 240 };
    int sortedPris[4] = { 120, 150, 200, 240 };
    for (int i = 0; i < 4; ++i) {
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, i), nodes, 1234));
        cmd->setPriority(priorities[i]);
        _topLinks[0]->sendDown(cmd);
    }

    waitUntilMergeQueueIs(*_throttlers[0], 4, _messageWaitTime);

    // Remove all but 4 forwarded merges
    for (std::size_t i = 0; i < maxPending - 4; ++i) {
        _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    }
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[0]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(4), _topLinks[0]->getNumReplies());

    // Now when we start replying to merges, queued merges should be
    // processed in priority order
    for (int i = 0; i < 4; ++i) {
        StorageMessage::SP replyTo = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
        std::shared_ptr<MergeBucketReply> reply(
                new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*replyTo)));
        reply->setResult(ReturnCode(ReturnCode::OK, "whee"));
        _topLinks[0]->sendDown(reply);
    }

    _topLinks[0]->waitForMessages(8, _messageWaitTime); // 4 merges, 4 replies
    waitUntilMergeQueueIs(*_throttlers[0], 0, _messageWaitTime);

    for (int i = 0; i < 4; ++i) {
        StorageMessage::SP cmd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
        CPPUNIT_ASSERT_EQUAL(uint8_t(sortedPris[i]), cmd->getPriority());
    }
}

// Test that we can detect and reject merges that due to resending
// and potential priority queue sneaking etc may end up with duplicates
// in the queue for a merge that is already known.
void
MergeThrottlerTest::testCommandInQueueDuplicateOfKnownMerge()
{
    // Fill up all active merges and 1 queued one
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    for (std::size_t i = 0; i < maxPending + 1; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(2 + i);
        nodes.push_back(5 + i);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234));
        cmd->setPriority(100 - i);
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 3 queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 1, _messageWaitTime);

    // Add a merge for the same bucket twice to the queue
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(12);
        nodes.push_back(123);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf000feee), nodes, 1234));
        _topLinks[0]->sendDown(cmd);
    }
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(124); // Different node set doesn't matter
        nodes.push_back(14);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf000feee), nodes, 1234));
        _topLinks[0]->sendDown(cmd);
    }

    waitUntilMergeQueueIs(*_throttlers[0], 3, _messageWaitTime);

    StorageMessage::SP fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // Remove and success-reply for 2 merges. This will give enough room
    // for the 2 first queued merges to be processed, the last one having a
    // duplicate in the queue.
    for (int i = 0; i < 2; ++i) {
        StorageMessage::SP fwd2 = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
        std::shared_ptr<MergeBucketReply> reply(
                new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd2)));
        reply->setResult(ReturnCode(ReturnCode::OK, ""));
        _topLinks[0]->sendDown(reply);
    }

    _topLinks[0]->waitForMessages(maxPending + 1, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 1, _messageWaitTime);

    // Remove all current merge commands/replies so we can work with a clean slate
    _topLinks[0]->getRepliesOnce();
    // Send a success-reply for fwd, allowing the duplicate from the queue
    // to have its moment to shine only to then be struck down mercilessly
    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd)));
    reply->setResult(ReturnCode(ReturnCode::OK, ""));
    _topLinks[0]->sendDown(reply);

    _topLinks[0]->waitForMessages(2, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 0, _messageWaitTime);

    // First reply is the successful merge reply
    StorageMessage::SP reply2 = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply2).getResult().getResult(),
            ReturnCode::OK);

    // Second reply should be the BUSY-rejected duplicate
    StorageMessage::SP reply1 = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply1).getResult().getResult(),
            ReturnCode::BUSY);
    CPPUNIT_ASSERT(static_cast<MergeBucketReply&>(*reply1).getResult()
                   .getMessage().find("out of date;") != std::string::npos);
}

// Test that sending a merge command to a node not in the set of
// to-be-merged nodes is handled gracefully.
// This is not a scenario that should ever actually happen, but for
// the sake of robustness, include it anyway.
void
MergeThrottlerTest::testInvalidReceiverNode()
{
    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(1);
    nodes.push_back(5);
    nodes.push_back(9);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(BucketId(32, 0xf00baaaa), nodes, 1234));

    // Send to node with index 0
    _topLinks[0]->sendDown(cmd);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
            ReturnCode::REJECTED);
    CPPUNIT_ASSERT(static_cast<MergeBucketReply&>(*reply).getResult()
                   .getMessage().find("which is not in its forwarding chain") != std::string::npos);
}

// Test that the throttling policy kicks in after a certain number of
// merges are forwarded and that the rest are queued in a prioritized
// order.
void
MergeThrottlerTest::testForwardQueuedMerge()
{
    // Fill up all active merges and then 3 queued ones
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    for (std::size_t i = 0; i < maxPending + 3; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(2 + i);
        nodes.push_back(5 + i);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234));
        cmd->setPriority(100 - i);
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 3 queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 3, _messageWaitTime);

    // Merge queue state should not be touched by worker thread now
    StorageMessage::SP nextMerge = _throttlers[0]->getMergeQueue().begin()->_msg;

    StorageMessage::SP fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // Remove all the rest of the active merges
    while (!_topLinks[0]->getReplies().empty()) {
        _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    }

    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd)));
    reply->setResult(ReturnCode(ReturnCode::OK, "Celebrate good times come on"));
    _topLinks[0]->sendDown(reply);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime); // Success rewind reply

    // Remove reply bound for distributor
    StorageMessage::SP distReply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*distReply).getResult().getResult(),
            ReturnCode::OK);

    waitUntilMergeQueueIs(*_throttlers[0], 2, _messageWaitTime);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[0]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), _topLinks[0]->getNumReplies());

    // First queued merge should now have been registered and forwarded
    fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    CPPUNIT_ASSERT_EQUAL(
            static_cast<const MergeBucketCommand&>(*fwd).getBucketId(),
            static_cast<const MergeBucketCommand&>(*nextMerge).getBucketId());

    CPPUNIT_ASSERT(
            static_cast<const MergeBucketCommand&>(*fwd).getNodes()
            == static_cast<const MergeBucketCommand&>(*nextMerge).getNodes());

    // Ensure forwarded merge has a higher priority than the next queued one
    CPPUNIT_ASSERT(fwd->getPriority() < _throttlers[0]->getMergeQueue().begin()->_msg->getPriority());

    CPPUNIT_ASSERT_EQUAL(uint64_t(1), _throttlers[0]->getMetrics().chaining.ok.getValue());

    /*framework::HttpUrlPath path("?xml");
      _forwarders[0]->reportStatus(std::cerr, path);*/
}

void
MergeThrottlerTest::testExecuteQueuedMerge()
{
    MergeThrottler& throttler(*_throttlers[1]);
    DummyStorageLink& topLink(*_topLinks[1]);
    DummyStorageLink& bottomLink(*_bottomLinks[1]);

    // Fill up all active merges and then 3 queued ones
    std::size_t maxPending = throttler.getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    for (std::size_t i = 0; i < maxPending + 3; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(1);
        nodes.push_back(5 + i);
        nodes.push_back(7 + i);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234, 1));
        cmd->setPriority(250 - i + 5);
        topLink.sendDown(cmd);
    }

    // Wait till we have maxPending replies and 3 queued
    topLink.waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(throttler, 3, _messageWaitTime);

    // Sneak in a higher priority message that is bound to be executed
    // on the given node
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(1);
        nodes.push_back(0);
        std::vector<uint16_t> chain;
        chain.push_back(0);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0x1337), nodes, 1234, 1, chain));
        cmd->setPriority(0);
        topLink.sendDown(cmd);
    }

    waitUntilMergeQueueIs(throttler, 4, _messageWaitTime);

    // Merge queue state should not be touched by worker thread now
    StorageMessage::SP nextMerge(throttler.getMergeQueue().begin()->_msg);
    /*StorageMessage::SP nextMerge;
    {
        vespalib::LockGuard lock(_throttlers[0]->getStateLock());
        // Dirty: have to check internal state
        nextMerge = _throttlers[0]->getMergeQueue().begin()->_msg;
        }*/

    CPPUNIT_ASSERT_EQUAL(
            BucketId(32, 0x1337),
            dynamic_cast<const MergeBucketCommand&>(*nextMerge).getBucketId());

    StorageMessage::SP fwd(topLink.getAndRemoveMessage(MessageType::MERGEBUCKET));

    // Remove all the rest of the active merges
    while (!topLink.getReplies().empty()) {
        topLink.getAndRemoveMessage(MessageType::MERGEBUCKET);
    }

    // Free up a merge slot
    std::shared_ptr<MergeBucketReply> reply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd)));
    reply->setResult(ReturnCode(ReturnCode::OK, "Celebrate good times come on"));
    topLink.sendDown(reply);

    topLink.waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);
    // Remove chain reply
    StorageMessage::SP distReply(topLink.getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY));
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*distReply).getResult().getResult(),
            ReturnCode::OK);

    waitUntilMergeQueueIs(throttler, 3, _messageWaitTime);
    bottomLink.waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), topLink.getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), topLink.getNumReplies());
    CPPUNIT_ASSERT_EQUAL(std::size_t(1), bottomLink.getNumCommands());

    // First queued merge should now have been registered and sent down
    StorageMessage::SP cmd(bottomLink.getAndRemoveMessage(MessageType::MERGEBUCKET));

    CPPUNIT_ASSERT_EQUAL(
            static_cast<const MergeBucketCommand&>(*cmd).getBucketId(),
            static_cast<const MergeBucketCommand&>(*nextMerge).getBucketId());

    CPPUNIT_ASSERT(
            static_cast<const MergeBucketCommand&>(*cmd).getNodes()
            == static_cast<const MergeBucketCommand&>(*nextMerge).getNodes());
}

void
MergeThrottlerTest::testFlush()
{
    // Fill up all active merges and then 3 queued ones
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    for (std::size_t i = 0; i < maxPending + 3; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234, 1));
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 3 queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 3, _messageWaitTime);

    // Remove all forwarded commands
    uint32_t removed = _topLinks[0]->getRepliesOnce().size();
    CPPUNIT_ASSERT(removed >= 5);

    // Flush the storage link, triggering an abort of all commands
    // no matter what their current state is.
    _topLinks[0]->close();
    _topLinks[0]->flush();
    _topLinks[0]->waitForMessages(maxPending + 3 - removed, _messageWaitTime);

    while (!_topLinks[0]->getReplies().empty()) {
        StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
        CPPUNIT_ASSERT_EQUAL(
                ReturnCode::ABORTED,
                static_cast<const MergeBucketReply&>(*reply).getResult().getResult());
    }
    // NOTE: merges that have been immediately executed (i.e. not cycled)
    // on the node should _not_ be replied to, since they're not owned
    // by the throttler at that point in time
}

// If a node goes down and another node has a merge chained through it in
// its queue, the original node can receive a final chain hop forwarding
// it knows nothing about when it comes back up. If this is not handled
// properly, it will attempt to forward this node again with a bogus
// index. This should be implicitly handled by checking for a full node
void
MergeThrottlerTest::testUnseenMergeWithNodeInChain()
{
    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(5);
    nodes.push_back(9);
    std::vector<uint16_t> chain;
    chain.push_back(0);
    chain.push_back(5);
    chain.push_back(9);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(BucketId(32, 0xdeadbeef), nodes, 1234, 1, chain));

    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 9);

    cmd->setAddress(address);
    _topLinks[0]->sendDown(cmd);

    // First, test that we get rejected when processing merge immediately
    // Should get a rejection in return
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);
    StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            ReturnCode::REJECTED,
            dynamic_cast<const MergeBucketReply&>(*reply).getResult().getResult());

    // Second, test that we get rejected before queueing up. This is to
    // avoid a hypothetical deadlock scenario.
    // Fill up all active merges
    {

        std::size_t maxPending(
                _throttlers[0]->getThrottlePolicy().getMaxPendingCount());
        for (std::size_t i = 0; i < maxPending; ++i) {
            std::shared_ptr<MergeBucketCommand> fillCmd(
                    new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234));
            _topLinks[0]->sendDown(fillCmd);
        }
    }

    _topLinks[0]->sendDown(cmd);

    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);
    reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            ReturnCode::REJECTED,
            dynamic_cast<const MergeBucketReply&>(*reply).getResult().getResult());
}

void
MergeThrottlerTest::testMergeWithNewerClusterStateFlushesOutdatedQueued()
{
    // Fill up all active merges and then 3 queued ones with the same
    // system state
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    std::vector<api::StorageMessage::Id> ids;
    for (std::size_t i = 0; i < maxPending + 3; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234, 1));
        ids.push_back(cmd->getMsgId());
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 3 queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 3, _messageWaitTime);

    // Send down merge with newer system state
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0x12345678), nodes, 1234, 2));
        ids.push_back(cmd->getMsgId());
        _topLinks[0]->sendDown(cmd);
    }

    // Queue should now be flushed with all messages being returned with
    // WRONG_DISTRIBUTION
    _topLinks[0]->waitForMessages(maxPending + 3, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 1, _messageWaitTime);

    for (int i = 0; i < 3; ++i) {
        StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
        CPPUNIT_ASSERT_EQUAL(
                static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
                ReturnCode::WRONG_DISTRIBUTION);
        CPPUNIT_ASSERT_EQUAL(1u, static_cast<MergeBucketReply&>(*reply).getClusterStateVersion());
        CPPUNIT_ASSERT_EQUAL(ids[maxPending + i], reply->getMsgId());
    }

    CPPUNIT_ASSERT_EQUAL(uint64_t(3), _throttlers[0]->getMetrics().chaining.failures.wrongdistribution.getValue());
}

void
MergeThrottlerTest::testUpdatedClusterStateFlushesOutdatedQueued()
{
    // State is version 1. Send down several merges with state version 2.
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    std::vector<api::StorageMessage::Id> ids;
    for (std::size_t i = 0; i < maxPending + 3; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234, 2));
        ids.push_back(cmd->getMsgId());
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 4 queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 3, _messageWaitTime);

    // Send down new system state (also set it explicitly)
    _servers[0]->setClusterState(lib::ClusterState("distributor:100 storage:100 version:3"));
    std::shared_ptr<api::SetSystemStateCommand> stateCmd(
            new api::SetSystemStateCommand(lib::ClusterState("distributor:100 storage:100 version:3")));
    _topLinks[0]->sendDown(stateCmd);

    // Queue should now be flushed with all being replied to with WRONG_DISTRIBUTION
    waitUntilMergeQueueIs(*_throttlers[0], 0, _messageWaitTime);
    _topLinks[0]->waitForMessages(maxPending + 3, 5);

    for (int i = 0; i < 3; ++i) {
        StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
        CPPUNIT_ASSERT_EQUAL(
                static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
                ReturnCode::WRONG_DISTRIBUTION);
        CPPUNIT_ASSERT_EQUAL(2u, static_cast<MergeBucketReply&>(*reply).getClusterStateVersion());
        CPPUNIT_ASSERT_EQUAL(ids[maxPending + i], reply->getMsgId());
    }

    CPPUNIT_ASSERT_EQUAL(uint64_t(3), _throttlers[0]->getMetrics().chaining.failures.wrongdistribution.getValue());
}

void
MergeThrottlerTest::test42MergesDoNotTriggerFlush()
{
    // Fill up all active merges and then 1 queued one
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    CPPUNIT_ASSERT(maxPending < 100);
    for (std::size_t i = 0; i < maxPending + 1; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00baa00 + i), nodes, 1234, 1));
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and 1 queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], 1, _messageWaitTime);

    StorageMessage::SP fwd = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // Remove all the rest of the active merges
    while (!_topLinks[0]->getReplies().empty()) {
        _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    }

    // Send down a merge with a cluster state version of 0, which should
    // be ignored and queued as usual
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xbaaadbed), nodes, 1234, 0));
        _topLinks[0]->sendDown(cmd);
    }

    waitUntilMergeQueueIs(*_throttlers[0], 2, _messageWaitTime);

    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[0]->getNumCommands());
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[0]->getNumReplies());

    CPPUNIT_ASSERT_EQUAL(uint64_t(0), _throttlers[0]->getMetrics().local.failures.wrongdistribution.getValue());
}

// Test that a merge that arrive with a state version that is less than
// that of the node is rejected immediately
void
MergeThrottlerTest::testOutdatedClusterStateMergesAreRejectedOnArrival()
{
    _servers[0]->setClusterState(lib::ClusterState("distributor:100 storage:100 version:10"));

    // Send down a merge with a cluster state version of 9, which should
    // be rejected
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xfeef00), nodes, 1234, 9));
        _topLinks[0]->sendDown(cmd);
    }

    _topLinks[0]->waitForMessages(1, _messageWaitTime);

    StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
    CPPUNIT_ASSERT_EQUAL(
            static_cast<MergeBucketReply&>(*reply).getResult().getResult(),
            ReturnCode::WRONG_DISTRIBUTION);

    CPPUNIT_ASSERT_EQUAL(uint64_t(1), _throttlers[0]->getMetrics().chaining.failures.wrongdistribution.getValue());
}

// Test erroneous case where node receives merge where the merge does
// not exist in the state, but it exists in the chain without the chain
// being full. This is something that shouldn't happen, but must still
// not crash the node
void
MergeThrottlerTest::testUnknownMergeWithSelfInChain()
{
    BucketId bid(32, 0xbadbed);

    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(0);
    nodes.push_back(1);
    nodes.push_back(2);
    std::vector<uint16_t> chain;
    chain.push_back(0);
    std::shared_ptr<MergeBucketCommand> cmd(
            new MergeBucketCommand(bid, nodes, 1234, 1, chain));

    StorageMessageAddress address("storage", lib::NodeType::STORAGE, 1);

    cmd->setAddress(address);
    _topLinks[0]->sendDown(cmd);
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);

    StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);

    CPPUNIT_ASSERT_EQUAL(
            ReturnCode::REJECTED,
            static_cast<MergeBucketReply&>(*reply).getResult().getResult());
}

void
MergeThrottlerTest::testBusyReturnedOnFullQueue()
{
    std::size_t maxPending = _throttlers[0]->getThrottlePolicy().getMaxPendingCount();
    std::size_t maxQueue = _throttlers[0]->getMaxQueueSize();
    CPPUNIT_ASSERT(maxPending < 100);
    for (std::size_t i = 0; i < maxPending + maxQueue; ++i) {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf00000 + i), nodes, 1234, 1));
        _topLinks[0]->sendDown(cmd);
    }

    // Wait till we have maxPending replies and maxQueue queued
    _topLinks[0]->waitForMessages(maxPending, _messageWaitTime);
    waitUntilMergeQueueIs(*_throttlers[0], maxQueue, _messageWaitTime);

    // Clear all forwarded merges
    _topLinks[0]->getRepliesOnce();
    // Send down another merge which should be immediately busy-returned
    {
        std::vector<MergeBucketCommand::Node> nodes;
        nodes.push_back(0);
        nodes.push_back(1);
        nodes.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xf000baaa), nodes, 1234, 1));
        _topLinks[0]->sendDown(cmd);
    }
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET_REPLY, _messageWaitTime);
    StorageMessage::SP reply = _topLinks[0]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);

    CPPUNIT_ASSERT_EQUAL(
            BucketId(32, 0xf000baaa),
            static_cast<MergeBucketReply&>(*reply).getBucketId());

    CPPUNIT_ASSERT_EQUAL(
            ReturnCode::BUSY,
            static_cast<MergeBucketReply&>(*reply).getResult().getResult());

    CPPUNIT_ASSERT_EQUAL(uint64_t(0),
                         _throttlers[0]->getMetrics().chaining
                         .failures.busy.getValue());
    CPPUNIT_ASSERT_EQUAL(uint64_t(1),
                         _throttlers[0]->getMetrics().local
                         .failures.busy.getValue());
}

void
MergeThrottlerTest::testBrokenCycle()
{
    std::vector<MergeBucketCommand::Node> nodes;
    nodes.push_back(1);
    nodes.push_back(0);
    nodes.push_back(2);
    {
        std::vector<uint16_t> chain;
        chain.push_back(0);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xfeef00), nodes, 1234, 1, chain));
        _topLinks[1]->sendDown(cmd);
    }

    _topLinks[1]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    StorageMessage::SP fwd = _topLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    CPPUNIT_ASSERT_EQUAL(uint16_t(2), fwd->getAddress()->getIndex());

    // Send cycled merge which will be executed
    {
        std::vector<uint16_t> chain;
        chain.push_back(0);
        chain.push_back(1);
        chain.push_back(2);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xfeef00), nodes, 1234, 1, chain));
        _topLinks[1]->sendDown(cmd);
    }

    _bottomLinks[1]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    StorageMessage::SP cycled = _bottomLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);

    // Now, node 2 goes down, auto sending back a failed merge
    std::shared_ptr<MergeBucketReply> nodeDownReply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*fwd)));
    nodeDownReply->setResult(ReturnCode(ReturnCode::NOT_CONNECTED, "Node went sightseeing"));

    _topLinks[1]->sendDown(nodeDownReply);
    // Merge reply also arrives from persistence
    std::shared_ptr<MergeBucketReply> persistenceReply(
            new MergeBucketReply(dynamic_cast<const MergeBucketCommand&>(*cycled)));
    persistenceReply->setResult(ReturnCode(ReturnCode::ABORTED, "Oh dear"));
    _bottomLinks[1]->sendUp(persistenceReply);

    // Should now be two replies from node 1, one to node 2 and one to node 0
    // since we must handle broken chains
    _topLinks[1]->waitForMessages(2, _messageWaitTime);
    // Unwind reply shares the result of the persistence reply
    for (int i = 0; i < 2; ++i) {
        StorageMessage::SP reply = _topLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET_REPLY);
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(ReturnCode::ABORTED, "Oh dear"),
                             static_cast<MergeBucketReply&>(*reply).getResult());
    }

    // Make sure it has been removed from the internal state so we can
    // send new merges for the bucket
    {
        std::vector<uint16_t> chain;
        chain.push_back(0);
        std::shared_ptr<MergeBucketCommand> cmd(
                new MergeBucketCommand(BucketId(32, 0xfeef00), nodes, 1234, 1, chain));
        _topLinks[1]->sendDown(cmd);
    }

    _topLinks[1]->waitForMessage(MessageType::MERGEBUCKET, 5);
    fwd = _topLinks[1]->getAndRemoveMessage(MessageType::MERGEBUCKET);
    CPPUNIT_ASSERT_EQUAL(uint16_t(2), fwd->getAddress()->getIndex());
}

void
MergeThrottlerTest::sendAndExpectReply(
        const std::shared_ptr<api::StorageMessage>& msg,
        const api::MessageType& expectedReplyType,
        api::ReturnCode::Result expectedResultCode)
{
    _topLinks[0]->sendDown(msg);
    _topLinks[0]->waitForMessage(expectedReplyType, _messageWaitTime);
    StorageMessage::SP reply(_topLinks[0]->getAndRemoveMessage(
                    expectedReplyType));
    api::StorageReply& storageReply(
            dynamic_cast<api::StorageReply&>(*reply));
    CPPUNIT_ASSERT_EQUAL(expectedResultCode,
                         storageReply.getResult().getResult());
}

void
MergeThrottlerTest::testGetBucketDiffCommandNotInActiveSetIsRejected()
{
    document::BucketId bucket(16, 1234);
    std::vector<api::GetBucketDiffCommand::Node> nodes;
    std::shared_ptr<api::GetBucketDiffCommand> getDiffCmd(
            new api::GetBucketDiffCommand(bucket, nodes, api::Timestamp(1234)));

    sendAndExpectReply(getDiffCmd,
                       api::MessageType::GETBUCKETDIFF_REPLY,
                       api::ReturnCode::ABORTED);
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[0]->getNumCommands());
}

void
MergeThrottlerTest::testApplyBucketDiffCommandNotInActiveSetIsRejected()
{
    document::BucketId bucket(16, 1234);
    std::vector<api::GetBucketDiffCommand::Node> nodes;
    std::shared_ptr<api::ApplyBucketDiffCommand> applyDiffCmd(
            new api::ApplyBucketDiffCommand(bucket, nodes, api::Timestamp(1234)));

    sendAndExpectReply(applyDiffCmd,
                       api::MessageType::APPLYBUCKETDIFF_REPLY,
                       api::ReturnCode::ABORTED);
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _bottomLinks[0]->getNumCommands());
}

api::MergeBucketCommand::SP
MergeThrottlerTest::sendMerge(const MergeBuilder& builder)
{
    api::MergeBucketCommand::SP cmd(builder.create());
    _topLinks[builder._nodes[0]]->sendDown(cmd);
    return cmd;
}

void
MergeThrottlerTest::testNewClusterStateAbortsAllOutdatedActiveMerges()
{
    document::BucketId bucket(16, 6789);
    _throttlers[0]->getThrottlePolicy().setMaxPendingCount(1);

    // Merge will be forwarded (i.e. active).
    sendMerge(MergeBuilder(bucket).clusterStateVersion(10));
    _topLinks[0]->waitForMessage(MessageType::MERGEBUCKET, _messageWaitTime);
    StorageMessage::SP fwd(_topLinks[0]->getAndRemoveMessage(
            MessageType::MERGEBUCKET));

    _topLinks[0]->sendDown(makeSystemStateCmd(
            "version:11 distributor:100 storage:100"));
    // Cannot send reply until we're unwinding
    CPPUNIT_ASSERT_EQUAL(std::size_t(0), _topLinks[0]->getNumReplies());

    // Trying to diff the bucket should now fail
    {
        std::shared_ptr<api::GetBucketDiffCommand> getDiffCmd(
                new api::GetBucketDiffCommand(bucket, {}, api::Timestamp(123)));

        sendAndExpectReply(getDiffCmd,
                           api::MessageType::GETBUCKETDIFF_REPLY,
                           api::ReturnCode::ABORTED);
    }
}

// TODO test message queue aborting (use rendezvous functionality--make guard)

} // namespace storage
