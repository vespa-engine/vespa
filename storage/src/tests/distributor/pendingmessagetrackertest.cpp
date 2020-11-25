// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>

using document::test::makeDocumentBucket;
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

    std::shared_ptr<api::PutCommand> createPutToNode(uint16_t node) const {
        document::BucketId bucket(16, 1234);
        auto cmd = std::make_shared<api::PutCommand>(
                makeDocumentBucket(bucket),
                createDummyDocumentForBucket(bucket),
                api::Timestamp(123456));
        cmd->setAddress(makeStorageAddress(node));
        return cmd;
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
    _tracker = std::make_unique<PendingMessageTracker>(_compReg);
}
Fixture::~Fixture() = default;

} // anonymous namespace

TEST_F(PendingMessageTrackerTest, simple) {
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);

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
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> "
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
    PendingMessageTracker tracker(compReg);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<h1>Pending messages to storage nodes</h1>\n"
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
    PendingMessageTracker tracker(compReg);

    insertMessages(tracker);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<b>Bucket(BucketSpace(0x0000000000000001), BucketId(0x40000000000004d2))</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "</ul>\n"
                "<b>Bucket(BucketSpace(0x0000000000000001), BucketId(0x40000000000011d7))</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "</ul>\n"));
    }
    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=node"));

        EXPECT_THAT(ost.str(), HasSubstr(
                "<b>Node 0 (pending count: 4)</b>\n"
                "<ul>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "</ul>\n"
                "<b>Node 1 (pending count: 4)</b>\n"
                "<ul>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), priority=127)</li>\n"
                "</ul>\n"));
    }
}

namespace {

template <typename T>
std::string setToString(const std::set<T>& s)
{
    std::ostringstream ost;
    ost << '{';
    for (typename std::set<T>::const_iterator i(s.begin()), e(s.end());
         i != e; ++i)
    {
        if (i != s.begin()) {
            ost << ',';
        }
        ost << *i;
    }
    ost << '}';
    return ost.str();
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
    PendingMessageTracker tracker(compReg);
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
    PendingMessageTracker tracker(compReg);
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
    PendingMessageTracker tracker(compReg);

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
    f.tracker().setNodeBusyDuration(std::chrono::seconds(10));
    f.sendPutReply(*cmd, RequestBuilder(), api::ReturnCode(api::ReturnCode::BUSY));
    EXPECT_TRUE(f.tracker().getNodeInfo().isBusy(0));
    f.clock().addSecondsToTime(11);
    EXPECT_FALSE(f.tracker().getNodeInfo().isBusy(0));
}

}
