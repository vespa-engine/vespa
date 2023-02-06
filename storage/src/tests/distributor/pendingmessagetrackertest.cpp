// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using namespace ::testing;

namespace storage::distributor {

using namespace std::chrono_literals;

struct PendingMessageTrackerTest : Test {
    void insertMessages(PendingMessageTracker& tracker);

};

namespace {

class RequestBuilder {
    uint16_t _toNode;
    std::chrono::milliseconds _atTime;
public:
    RequestBuilder()
        : _toNode(0),
          _atTime()
    {
    }

    RequestBuilder& atTime(std::chrono::milliseconds t) {
        _atTime = t;
        return *this;
    }

    RequestBuilder& toNode(uint16_t node) {
        _toNode = node;
        return *this;
    }

    uint16_t toNode() const { return _toNode; }
    std::chrono::milliseconds atTime() const { return _atTime; }
};

api::StorageMessageAddress
makeStorageAddress(uint16_t node) {
    static vespalib::string _storage("storage");
    return {&_storage, lib::NodeType::STORAGE, node};
}

class Fixture
{
    StorageComponentRegisterImpl _compReg;
    framework::defaultimplementation::FakeClock _clock;
    std::unique_ptr<PendingMessageTracker> _tracker;
    document::TestDocMan _testDocMan;
public:

    Fixture();
    ~Fixture();

    std::shared_ptr<api::PutCommand> sendPut(const RequestBuilder& builder) {
        assignMockedTime(builder.atTime());
        auto put = createPutToNode(builder.toNode());
        _tracker->insert(put);
        return put;
    }

    void sendPutReply(api::PutCommand& putCmd,
                      const RequestBuilder& builder,
                      const api::ReturnCode& result = api::ReturnCode())
    {
        assignMockedTime(builder.atTime());
        auto putReply = putCmd.makeReply();
        putReply->setResult(result);
        _tracker->reply(*putReply);
    }

    std::shared_ptr<api::PutCommand> createPutToNode(uint16_t node) const {
        document::BucketId bucket(16, 1234);
        auto cmd = std::make_shared<api::PutCommand>(
                makeDocumentBucket(bucket),
                createDummyDocumentForBucket(bucket),
                api::Timestamp(123456));
        cmd->setAddress(makeStorageAddress(node));
        return cmd;
    }

    std::shared_ptr<api::GetCommand> create_get_to_node(uint16_t node) const {
        document::BucketId bucket(16, 1234);
        auto cmd = std::make_shared<api::GetCommand>(
                makeDocumentBucket(bucket),
                document::DocumentId("id::testdoctype1:n=1234:foo"),
                "[all]");
        cmd->setAddress(makeStorageAddress(node));
        return cmd;
    }

    PendingMessageTracker& tracker() { return *_tracker; }
    auto& clock() { return _clock; }

private:
    std::string createDummyIdString(const document::BucketId& bucket) const {
        std::ostringstream id;
        id << "id:foo:testdoctype1:n=" << bucket.getId() << ":foo";
        return id.str();
    }

    document::Document::SP createDummyDocumentForBucket(const document::BucketId& bucket) const
    {
        return _testDocMan.createDocument("foobar", createDummyIdString(bucket));
    }

    std::shared_ptr<api::RemoveCommand> createRemoveToNode(
            uint16_t node) const
    {
        document::BucketId bucket(16, 1234);
        auto cmd = std::make_shared<api::RemoveCommand>(
                makeDocumentBucket(bucket),
                document::DocumentId(createDummyIdString(bucket)),
                api::Timestamp(123456));
        cmd->setAddress(makeStorageAddress(node));
        return cmd;
    }

    void assignMockedTime(std::chrono::milliseconds time) {
        _clock.setAbsoluteTimeInMicroSeconds(time.count() * 1000);
    }
};

Fixture::Fixture()
    : _compReg(),
      _clock(),
      _tracker(),
      _testDocMan()
{
    _compReg.setClock(_clock);
    _clock.setAbsoluteTimeInSeconds(1);
    // Have to set clock in compReg before constructing tracker, or it'll
    // flip out and die on an explicit nullptr check.
    _tracker = std::make_unique<PendingMessageTracker>(_compReg, 0);
}
Fixture::~Fixture() = default;

} // anonymous namespace

TEST_F(PendingMessageTrackerTest, simple) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg, 0);

    auto remove = std::make_shared<api::RemoveCommand>(
                    makeDocumentBucket(document::BucketId(16, 1234)),
                    document::DocumentId("id:footype:testdoc:n=1234:foo"), 1001);
    remove->setAddress(makeStorageAddress(0));
    tracker.insert(remove);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<b>Bucket(BucketSpace(0x0000000000000001), BucketId(0x40000000000004d2))</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> "
                "Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "</ul>\n"));
    }

    api::RemoveReply reply(*remove);
    tracker.reply(reply);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        EXPECT_THAT(ost.str(), Not(HasSubstr("id:")));
    }
}

void
PendingMessageTrackerTest::insertMessages(PendingMessageTracker& tracker)
{
    for (uint32_t i = 0; i < 4; i++) {
        std::ostringstream ost;
        ost << "id:footype:testdoc:n=1234:" << i;
        auto remove = std::make_shared<api::RemoveCommand>(
                        makeDocumentBucket(document::BucketId(16, 1234)),
                        document::DocumentId(ost.str()), 1000 + i);
        remove->setAddress(makeStorageAddress(i % 2));
        tracker.insert(remove);
    }

    for (uint32_t i = 0; i < 4; i++) {
        std::ostringstream ost;
        ost << "id:footype:testdoc:n=4567:" << i;
        auto remove = std::make_shared<api::RemoveCommand>(makeDocumentBucket(document::BucketId(16, 4567)), document::DocumentId(ost.str()), 2000 + i);
        remove->setAddress(makeStorageAddress(i % 2));
        tracker.insert(remove);
    }
}

TEST_F(PendingMessageTrackerTest, start_page) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    PendingMessageTracker tracker(compReg, 3);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages3"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<h1>Pending messages to storage nodes (stripe 3)</h1>\n"
                "View:\n"
                "<ul>\n"
                "<li><a href=\"?order=bucket\">Group by bucket</a></li>"
                "<li><a href=\"?order=node\">Group by node</a></li>"));
    }
}

TEST_F(PendingMessageTrackerTest, multiple_messages) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg, 0);

    insertMessages(tracker);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<b>Bucket(BucketSpace(0x0000000000000001), BucketId(0x40000000000004d2))</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "</ul>\n"
                "<b>Bucket(BucketSpace(0x0000000000000001), BucketId(0x40000000000011d7))</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "</ul>\n"));
    }
    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=node"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<b>Node 0 (pending count: 4)</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "</ul>\n"
                "<b>Node 1 (pending count: 4)</b>\n"
                "<ul>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01.000 UTC</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "</ul>\n"));
    }
}

namespace {

class TestChecker : public PendingMessageTracker::Checker
{
public:
    uint8_t pri;

    TestChecker() : pri(UINT8_MAX) {}

    bool check(uint32_t msgType, uint16_t node, uint8_t p) override {
        (void) node;
        if (msgType == api::MessageType::REMOVE_ID) {
            pri = p;
            return false;
        }

        return true;
    }
};


}

TEST_F(PendingMessageTrackerTest, get_pending_message_types) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg, 0);
    document::BucketId bid(16, 1234);

    auto remove = std::make_shared<api::RemoveCommand>(makeDocumentBucket(bid),
                                                       document::DocumentId("id:footype:testdoc:n=1234:foo"), 1001);
    remove->setAddress(makeStorageAddress(0));
    tracker.insert(remove);

    {
        TestChecker checker;
        tracker.checkPendingMessages(0, makeDocumentBucket(bid), checker);
        EXPECT_EQ(127, static_cast<int>(checker.pri));
    }

    {
        TestChecker checker;
        tracker.checkPendingMessages(0, makeDocumentBucket(document::BucketId(16, 1235)), checker);
        EXPECT_EQ(255, static_cast<int>(checker.pri));
    }

    {
        TestChecker checker;
        tracker.checkPendingMessages(1, makeDocumentBucket(bid), checker);
        EXPECT_EQ(255, static_cast<int>(checker.pri));
    }
}

TEST_F(PendingMessageTrackerTest, has_pending_message) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg, 0);
    document::BucketId bid(16, 1234);

    EXPECT_FALSE(tracker.hasPendingMessage(1, makeDocumentBucket(bid), api::MessageType::REMOVE_ID));

    {
        auto remove = std::make_shared<api::RemoveCommand>(makeDocumentBucket(bid),
                                                           document::DocumentId("id:footype:testdoc:n=1234:foo"), 1001);
        remove->setAddress(makeStorageAddress(1));
        tracker.insert(remove);
    }

    EXPECT_TRUE(tracker.hasPendingMessage(1, makeDocumentBucket(bid), api::MessageType::REMOVE_ID));
    EXPECT_FALSE(tracker.hasPendingMessage(0, makeDocumentBucket(bid), api::MessageType::REMOVE_ID));
    EXPECT_FALSE(tracker.hasPendingMessage(2, makeDocumentBucket(bid), api::MessageType::REMOVE_ID));
    EXPECT_FALSE(tracker.hasPendingMessage(1, makeDocumentBucket(document::BucketId(16, 1233)), api::MessageType::REMOVE_ID));
    EXPECT_FALSE(tracker.hasPendingMessage(1, makeDocumentBucket(bid), api::MessageType::DELETEBUCKET_ID));
}

namespace {

class OperationEnumerator : public PendingMessageTracker::Checker
{
    std::ostringstream ss;
public:
    bool check(uint32_t msgType, uint16_t node, uint8_t p) override {
        (void) p;
        ss << api::MessageType::get(static_cast<api::MessageType::Id>(msgType)).getName()
           << " -> " << node << "\n";

        return true;
    }

    std::string str() const { return ss.str(); }
};

} // anon ns

TEST_F(PendingMessageTrackerTest, get_all_messages_for_single_bucket) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg, 0);

    insertMessages(tracker);

    {
        OperationEnumerator enumerator;
        tracker.checkPendingMessages(makeDocumentBucket(document::BucketId(16, 1234)), enumerator);
        EXPECT_EQ("Remove -> 0\n"
                  "Remove -> 0\n"
                  "Remove -> 1\n"
                  "Remove -> 1\n",
                  enumerator.str());
    }
    {
        OperationEnumerator enumerator;
        tracker.checkPendingMessages(makeDocumentBucket(document::BucketId(16, 9876)), enumerator);
        EXPECT_EQ("", enumerator.str());
    }
}

// TODO don't set busy for visitor replies? These will mark the node as busy today,
// but have the same actual semantics as busy merges (i.e. "queue is full", not "node
// is too busy to accept new requests in general").

TEST_F(PendingMessageTrackerTest, busy_reply_marks_node_as_busy) {
    Fixture f;
    auto cmd = f.sendPut(RequestBuilder().toNode(0));
    EXPECT_FALSE(f.tracker().getNodeInfo().isBusy(0));
    f.sendPutReply(*cmd, RequestBuilder(), api::ReturnCode(api::ReturnCode::BUSY));
    EXPECT_TRUE(f.tracker().getNodeInfo().isBusy(0));
    EXPECT_FALSE(f.tracker().getNodeInfo().isBusy(1));
}

TEST_F(PendingMessageTrackerTest, busy_node_duration_can_be_adjusted) {
    Fixture f;
    auto cmd = f.sendPut(RequestBuilder().toNode(0));
    f.tracker().setNodeBusyDuration(10s);
    f.sendPutReply(*cmd, RequestBuilder(), api::ReturnCode(api::ReturnCode::BUSY));
    EXPECT_TRUE(f.tracker().getNodeInfo().isBusy(0));
    f.clock().addSecondsToTime(11);
    EXPECT_FALSE(f.tracker().getNodeInfo().isBusy(0));
}

namespace {

document::BucketId bucket_of(const document::DocumentId& id) {
    return document::BucketId(16, id.getGlobalId().convertToBucketId().getId());
}

}

TEST_F(PendingMessageTrackerTest, start_deferred_task_immediately_if_no_pending_write_ops) {
    Fixture f;
    auto cmd = f.createPutToNode(0);
    auto bucket_id = bucket_of(cmd->getDocumentId());
    auto state = TaskRunState::Aborted;
    f.tracker().run_once_no_pending_for_bucket(makeDocumentBucket(bucket_id), make_deferred_task([&](TaskRunState s){
        state = s;
    }));
    EXPECT_EQ(state, TaskRunState::OK);
}

TEST_F(PendingMessageTrackerTest, start_deferred_task_immediately_if_only_pending_read_ops) {
    Fixture f;
    auto cmd = f.create_get_to_node(0);
    f.tracker().insert(cmd);
    auto bucket_id = bucket_of(cmd->getDocumentId());
    auto state = TaskRunState::Aborted;
    f.tracker().run_once_no_pending_for_bucket(makeDocumentBucket(bucket_id), make_deferred_task([&](TaskRunState s){
        state = s;
    }));
    EXPECT_EQ(state, TaskRunState::OK);
}

TEST_F(PendingMessageTrackerTest, deferred_task_not_started_before_pending_ops_completed) {
    Fixture f;
    auto cmd = f.sendPut(RequestBuilder().toNode(0));
    auto bucket_id = bucket_of(cmd->getDocumentId());
    auto state = TaskRunState::Aborted;
    f.tracker().run_once_no_pending_for_bucket(makeDocumentBucket(bucket_id), make_deferred_task([&](TaskRunState s){
        state = s;
    }));
    EXPECT_EQ(state, TaskRunState::Aborted);
    f.sendPutReply(*cmd, RequestBuilder()); // Deferred task should be run as part of this.
    EXPECT_EQ(state, TaskRunState::OK);
}

TEST_F(PendingMessageTrackerTest, deferred_task_can_be_started_with_pending_read_op) {
    Fixture f;
    auto cmd = f.sendPut(RequestBuilder().toNode(0));
    auto bucket_id = bucket_of(cmd->getDocumentId());
    auto state = TaskRunState::Aborted;
    f.tracker().run_once_no_pending_for_bucket(makeDocumentBucket(bucket_id), make_deferred_task([&](TaskRunState s){
        state = s;
    }));
    EXPECT_EQ(state, TaskRunState::Aborted);
    f.tracker().insert(f.create_get_to_node(0)); // Concurrent Get and Put
    f.sendPutReply(*cmd, RequestBuilder()); // Deferred task should be allowed to run
    EXPECT_EQ(state, TaskRunState::OK);
}

TEST_F(PendingMessageTrackerTest, abort_invokes_deferred_tasks_with_aborted_status) {
    Fixture f;
    auto cmd = f.sendPut(RequestBuilder().toNode(0));
    auto bucket_id = bucket_of(cmd->getDocumentId());
    auto state = TaskRunState::OK;
    f.tracker().run_once_no_pending_for_bucket(makeDocumentBucket(bucket_id), make_deferred_task([&](TaskRunState s){
        state = s;
    }));
    EXPECT_EQ(state, TaskRunState::OK);
    f.tracker().abort_deferred_tasks();
    EXPECT_EQ(state, TaskRunState::Aborted);
}

TEST_F(PendingMessageTrackerTest, request_bucket_info_with_no_buckets_tracked_as_null_bucket) {
    Fixture f;
    auto msg = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), 0, lib::ClusterState(), "");
    msg->setAddress(makeStorageAddress(2));
    f.tracker().insert(msg);

    // Tracked as null bucket
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(document::BucketId()), enumerator);
        EXPECT_EQ("Request bucket info -> 2\n", enumerator.str());
    }

    // Nothing to a specific bucket
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(document::BucketId(16, 1234)), enumerator);
        EXPECT_EQ("", enumerator.str());
    }

    auto reply = std::shared_ptr<api::StorageReply>(msg->makeReply());
    f.tracker().reply(*reply);

    // No longer tracked as null bucket
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(document::BucketId()), enumerator);
        EXPECT_EQ("", enumerator.str());
    }
}

TEST_F(PendingMessageTrackerTest, request_bucket_info_with_bucket_tracked_with_superbucket) {
    Fixture f;
    document::BucketId bucket(16, 1234);
    auto msg = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), std::vector<document::BucketId>({bucket}));
    msg->setAddress(makeStorageAddress(3));
    f.tracker().insert(msg);

    // Not tracked as null bucket
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(document::BucketId()), enumerator);
        EXPECT_EQ("", enumerator.str());
    }
    // Tracked for superbucket
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(bucket), enumerator);
        EXPECT_EQ("Request bucket info -> 3\n", enumerator.str());
    }
    // Not tracked for other buckets
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(document::BucketId(16, 2345)), enumerator);
        EXPECT_EQ("", enumerator.str());
    }

    auto reply = std::shared_ptr<api::StorageReply>(msg->makeReply());
    f.tracker().reply(*reply);

    // No longer tracked for specified bucket
    {
        OperationEnumerator enumerator;
        f.tracker().checkPendingMessages(makeDocumentBucket(bucket), enumerator);
        EXPECT_EQ("", enumerator.str());
    }
}

}
