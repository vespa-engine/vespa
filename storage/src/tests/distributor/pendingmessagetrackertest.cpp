// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include <vespa/document/base/testdocman.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <iostream>
#include <memory>
#include <string>
#include <iterator>

namespace storage {
namespace distributor {

// Workaround typedef for not (yet) running with --std=c++14 which supports
// user defined literals. Once we do, replace ms(123) with 123ms.
using ms = std::chrono::milliseconds;

class PendingMessageTrackerCallback_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(PendingMessageTrackerCallback_Test);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testMultipleMessages);
    CPPUNIT_TEST(testStartPage);
    CPPUNIT_TEST(testGetPendingMessageTypes);
    CPPUNIT_TEST(testHasPendingMessage);
    CPPUNIT_TEST(testGetAllMessagesForSingleBucket);
    CPPUNIT_TEST(nodeStatsCanBeOutputStreamed);
    CPPUNIT_TEST(totalPutLatencyIsInitiallyZero);
    CPPUNIT_TEST(statsNotAlteredBeforeReplyReceived);
    CPPUNIT_TEST(totalPutLatencyIsTrackedForSingleRequest);
    CPPUNIT_TEST(statsAreTrackedSeparatelyPerNode);
    CPPUNIT_TEST(onlyPutMessagesAreTracked);
    CPPUNIT_TEST(totalPutLatencyIsAggregatedAcrossRequests);
    CPPUNIT_TEST(clearingMessagesDoesNotAffectStats);
    CPPUNIT_TEST(timeTravellingClockLatenciesNotRegistered);
    CPPUNIT_TEST(statsSnapshotIncludesAllNodes);
    CPPUNIT_TEST(latencyProviderForwardsToImplementation);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimple();
    void testMultipleMessages();
    void testStartPage();
    void testGetPendingMessageTypes();
    void testHasPendingMessage();
    void testGetAllMessagesForSingleBucket();
    void nodeStatsCanBeOutputStreamed();
    void totalPutLatencyIsInitiallyZero();
    void statsNotAlteredBeforeReplyReceived();
    void totalPutLatencyIsTrackedForSingleRequest();
    void statsAreTrackedSeparatelyPerNode();
    void onlyPutMessagesAreTracked();
    void totalPutLatencyIsAggregatedAcrossRequests();
    void clearingMessagesDoesNotAffectStats();
    void timeTravellingClockLatenciesNotRegistered();
    void statsSnapshotIncludesAllNodes();
    void latencyProviderForwardsToImplementation();

private:
    void insertMessages(PendingMessageTracker& tracker);

    OperationStats makeOpStats(std::chrono::milliseconds totalLatency,
                               uint64_t numRequests) const
    {
        OperationStats stats;
        stats.totalLatency = totalLatency;
        stats.numRequests = numRequests;
        return stats;
    }
};

bool
operator==(const OperationStats& a, const OperationStats& b)
{
    return (a.totalLatency == b.totalLatency
            && a.numRequests == b.numRequests);
}

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
                      const RequestBuilder& builder)
    {
        assignMockedTime(builder.atTime());
        auto putReply = putCmd.makeReply();
        _tracker->reply(*putReply);
    }

    std::shared_ptr<api::RemoveCommand> sendRemove(
            const RequestBuilder& builder)
    {
        assignMockedTime(builder.atTime());
        auto remove = createRemoveToNode(builder.toNode());
        _tracker->insert(remove);
        return remove;
    }

    void sendRemoveReply(api::RemoveCommand& removeCmd,
                         const RequestBuilder& builder)
    {
        assignMockedTime(builder.atTime());
        auto removeReply = removeCmd.makeReply();
        _tracker->reply(*removeReply);
    }

    void sendPutAndReplyWithLatency(uint16_t node,
                                    std::chrono::milliseconds latency)
    {
        auto put = sendPut(RequestBuilder().atTime(ms(1000)).toNode(node));
        sendPutReply(*put, RequestBuilder().atTime(ms(1000) + latency));
    }

    OperationStats getNodePutOperationStats(uint16_t node) {
        return _tracker->getNodeStats(node).puts;
    }

    PendingMessageTracker& tracker() { return *_tracker; }

private:
    std::string createDummyIdString(const document::BucketId& bucket) const {
        std::ostringstream id;
        id << "id:foo:testdoctype1:n=" << bucket.getId() << ":foo";
        return id.str();
    }

    document::Document::SP createDummyDocumentForBucket(
            const document::BucketId& bucket) const
    {
        return _testDocMan.createDocument("foobar", 
                                          createDummyIdString(bucket));
    }

    api::StorageMessageAddress makeStorageAddress(uint16_t node) const {
        return {"storage", lib::NodeType::STORAGE, node};
    }

    std::shared_ptr<api::PutCommand> createPutToNode(uint16_t node) const {
        document::BucketId bucket(16, 1234);
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(bucket,
                                    createDummyDocumentForBucket(bucket),
                                    api::Timestamp(123456)));
        cmd->setAddress(makeStorageAddress(node));
        return cmd;
    }

    std::shared_ptr<api::RemoveCommand> createRemoveToNode(
            uint16_t node) const
    {
        document::BucketId bucket(16, 1234);
        std::shared_ptr<api::RemoveCommand> cmd(
                new api::RemoveCommand(bucket,
                                       document::DocumentId(
                                            createDummyIdString(bucket)),
                                       api::Timestamp(123456)));
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
    _tracker = std::unique_ptr<PendingMessageTracker>(
            new PendingMessageTracker(_compReg));
}
Fixture::~Fixture() {}

}

CPPUNIT_TEST_SUITE_REGISTRATION(PendingMessageTrackerCallback_Test);

void
PendingMessageTrackerCallback_Test::testSimple()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);

    std::shared_ptr<api::RemoveCommand> remove(
            new api::RemoveCommand(
                    document::BucketId(16, 1234),
                    document::DocumentId("userdoc:footype:1234:foo"), 1001));
    remove->setAddress(
            api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 0));
    tracker.insert(remove);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        CPPUNIT_ASSERT_CONTAIN(
                std::string(
                        "<b>BucketId(0x40000000000004d2)</b>\n"
                        "<ul>\n"
                        "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> "
                        "Remove(BucketId(0x40000000000004d2), "
                        "userdoc:footype:1234:foo, timestamp 1001)</li>\n"
                        "</ul>\n"),
                ost.str());
    }

    api::RemoveReply reply(*remove);
    tracker.reply(reply);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        CPPUNIT_ASSERT_MSG(ost.str(),
                           ost.str().find("doc:") == std::string::npos);
    }
}

void
PendingMessageTrackerCallback_Test::insertMessages(PendingMessageTracker& tracker)
{
    for (uint32_t i = 0; i < 4; i++) {
        std::ostringstream ost;
        ost << "userdoc:footype:1234:" << i;
        std::shared_ptr<api::RemoveCommand> remove(
                new api::RemoveCommand(
                        document::BucketId(16, 1234),
                        document::DocumentId(ost.str()), 1000 + i));
        remove->setAddress(
                api::StorageMessageAddress("storage",
                                           lib::NodeType::STORAGE, i % 2));
        tracker.insert(remove);
    }

    for (uint32_t i = 0; i < 4; i++) {
        std::ostringstream ost;
        ost << "userdoc:footype:4567:" << i;
        std::shared_ptr<api::RemoveCommand> remove(new api::RemoveCommand(document::BucketId(16, 4567), document::DocumentId(ost.str()), 2000 + i));
        remove->setAddress(api::StorageMessageAddress("storage", lib::NodeType::STORAGE, i % 2));
        tracker.insert(remove);
    }
}

void
PendingMessageTrackerCallback_Test::testStartPage()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    PendingMessageTracker tracker(compReg);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages"));

        CPPUNIT_ASSERT_CONTAIN(
                std::string(
                        "<h1>Pending messages to storage nodes</h1>\n"
                        "View:\n"
                        "<ul>\n"
                        "<li><a href=\"?order=bucket\">Group by bucket</a></li>"
                        "<li><a href=\"?order=node\">Group by node</a></li>"),
                ost.str());

    }
}

void
PendingMessageTrackerCallback_Test::testMultipleMessages()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);

    insertMessages(tracker);

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=bucket"));

        CPPUNIT_ASSERT_CONTAIN(
                std::string(
                        "<b>BucketId(0x40000000000004d2)</b>\n"
                        "<ul>\n"
                        "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:0, timestamp 1000)</li>\n"
                        "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:2, timestamp 1002)</li>\n"
                        "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:1, timestamp 1001)</li>\n"
                        "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:3, timestamp 1003)</li>\n"
                        "</ul>\n"
                        "<b>BucketId(0x40000000000011d7)</b>\n"
                        "<ul>\n"
                        "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:0, timestamp 2000)</li>\n"
                        "<li><i>Node 0</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:2, timestamp 2002)</li>\n"
                        "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:1, timestamp 2001)</li>\n"
                        "<li><i>Node 1</i>: <b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:3, timestamp 2003)</li>\n"
                        "</ul>\n"),
                ost.str());
    }

    {
        std::ostringstream ost;
        tracker.reportStatus(ost, framework::HttpUrlPath("/pendingmessages?order=node"));

        CPPUNIT_ASSERT_CONTAIN(std::string(
                                     "<b>Node 0 (pending count: 4)</b>\n"
                                     "<ul>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:0, timestamp 1000)</li>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:2, timestamp 1002)</li>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:0, timestamp 2000)</li>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:2, timestamp 2002)</li>\n"
                                     "</ul>\n"
                                     "<b>Node 1 (pending count: 4)</b>\n"
                                     "<ul>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:1, timestamp 1001)</li>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000004d2), userdoc:footype:1234:3, timestamp 1003)</li>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:1, timestamp 2001)</li>\n"
                                     "<li><b>1970-01-01 00:00:01</b> Remove(BucketId(0x40000000000011d7), userdoc:footype:4567:3, timestamp 2003)</li>\n"
                                     "</ul>\n"
            ), ost.str());
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

    bool check(uint32_t msgType, uint16_t node, uint8_t p) {
        (void) node;
        if (msgType == api::MessageType::REMOVE_ID) {
            pri = p;
            return false;
        }

        return true;
    }
};


}

void
PendingMessageTrackerCallback_Test::testGetPendingMessageTypes()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);
    document::BucketId bid(16, 1234);

    std::shared_ptr<api::RemoveCommand> remove(
            new api::RemoveCommand(
                    bid,
                    document::DocumentId("userdoc:footype:1234:foo"), 1001));
    remove->setAddress(
            api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 0));
    tracker.insert(remove);

    {
        TestChecker checker;
        tracker.checkPendingMessages(0, bid, checker);
        CPPUNIT_ASSERT_EQUAL(127, (int)checker.pri);
    }

    {
        TestChecker checker;
        tracker.checkPendingMessages(0, document::BucketId(16, 1235), checker);
        CPPUNIT_ASSERT_EQUAL(255, (int)checker.pri);
    }

    {
        TestChecker checker;
        tracker.checkPendingMessages(1, bid, checker);
        CPPUNIT_ASSERT_EQUAL(255, (int)checker.pri);
    }
}

void
PendingMessageTrackerCallback_Test::testHasPendingMessage()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);
    document::BucketId bid(16, 1234);

    CPPUNIT_ASSERT(!tracker.hasPendingMessage(1, bid, api::MessageType::REMOVE_ID));

    {
        std::shared_ptr<api::RemoveCommand> remove(
                new api::RemoveCommand(
                        bid,
                        document::DocumentId("userdoc:footype:1234:foo"), 1001));
        remove->setAddress(
                api::StorageMessageAddress("storage", lib::NodeType::STORAGE, 1));
        tracker.insert(remove);
    }

    CPPUNIT_ASSERT(tracker.hasPendingMessage(1, bid, api::MessageType::REMOVE_ID));
    CPPUNIT_ASSERT(!tracker.hasPendingMessage(0, bid, api::MessageType::REMOVE_ID));
    CPPUNIT_ASSERT(!tracker.hasPendingMessage(2, bid, api::MessageType::REMOVE_ID));
    CPPUNIT_ASSERT(!tracker.hasPendingMessage(1,
                                              document::BucketId(16, 1233),
                                              api::MessageType::REMOVE_ID));
    CPPUNIT_ASSERT(!tracker.hasPendingMessage(1, bid, api::MessageType::DELETEBUCKET_ID));
}

namespace {

class OperationEnumerator : public PendingMessageTracker::Checker
{
    std::ostringstream ss;
public:
    bool check(uint32_t msgType, uint16_t node, uint8_t p) override {
        (void) p;
        ss << api::MessageType::get(static_cast<api::MessageType::Id>(msgType))
                .getName()
           << " -> " << node
           << "\n";

        return true;
    }

    std::string str() const { return ss.str(); }
};

} // anon ns

void
PendingMessageTrackerCallback_Test::testGetAllMessagesForSingleBucket()
{
    StorageComponentRegisterImpl compReg;
    framework::defaultimplementation::FakeClock clock;
    compReg.setClock(clock);
    clock.setAbsoluteTimeInSeconds(1);
    PendingMessageTracker tracker(compReg);

    insertMessages(tracker);

    {
        OperationEnumerator enumerator;
        tracker.checkPendingMessages(document::BucketId(16, 1234), enumerator);
        CPPUNIT_ASSERT_EQUAL(std::string("Remove -> 0\n"
                    "Remove -> 0\n"
                    "Remove -> 1\n"
                    "Remove -> 1\n"),
                enumerator.str());
    }
    {
        OperationEnumerator enumerator;
        tracker.checkPendingMessages(document::BucketId(16, 9876), enumerator);
        CPPUNIT_ASSERT_EQUAL(std::string(""), enumerator.str());
    }
}

void
PendingMessageTrackerCallback_Test::nodeStatsCanBeOutputStreamed()
{
    NodeStats stats;
    stats.puts = makeOpStats(ms(56789), 10);

    std::ostringstream os;
    os << stats;
    std::string expected(
            "NodeStats(puts=OperationStats("
                "totalLatency=56789ms, "
                "numRequests=10))");
    CPPUNIT_ASSERT_EQUAL(expected, os.str());
}

void
PendingMessageTrackerCallback_Test::totalPutLatencyIsInitiallyZero()
{
    Fixture fixture;
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(0), 0),
                         fixture.getNodePutOperationStats(0));
}

void
PendingMessageTrackerCallback_Test::statsNotAlteredBeforeReplyReceived()
{
    Fixture fixture;
    fixture.sendPut(RequestBuilder().atTime(ms(1000)).toNode(0));
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(0), 0),
                         fixture.getNodePutOperationStats(0));
}

void
PendingMessageTrackerCallback_Test::totalPutLatencyIsTrackedForSingleRequest()
{
    Fixture fixture;
    fixture.sendPutAndReplyWithLatency(0, ms(500));

    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(500), 1),
                         fixture.getNodePutOperationStats(0));
}

void
PendingMessageTrackerCallback_Test::statsAreTrackedSeparatelyPerNode()
{
    Fixture fixture;
    fixture.sendPutAndReplyWithLatency(0, ms(500));
    fixture.sendPutAndReplyWithLatency(1, ms(600));

    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(500), 1),
                         fixture.getNodePutOperationStats(0));
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(600), 1),
                         fixture.getNodePutOperationStats(1));
}

// Necessarily, this test will have to be altered when we add tracking of
// other message types as well.
void
PendingMessageTrackerCallback_Test::onlyPutMessagesAreTracked()
{
    Fixture fixture;
    auto remove = fixture.sendRemove(
            RequestBuilder().atTime(ms(1000)).toNode(0));
    fixture.sendRemoveReply(*remove, RequestBuilder().atTime(ms(2000)));
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(0), 0),
                         fixture.getNodePutOperationStats(0));
}

void
PendingMessageTrackerCallback_Test::totalPutLatencyIsAggregatedAcrossRequests()
{
    Fixture fixture;
    // Model 2 concurrent puts to node 0.
    fixture.sendPutAndReplyWithLatency(0, ms(500));
    fixture.sendPutAndReplyWithLatency(0, ms(600));
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(1100), 2),
                         fixture.getNodePutOperationStats(0));
}

void
PendingMessageTrackerCallback_Test::clearingMessagesDoesNotAffectStats()
{
    Fixture fixture;
    fixture.sendPutAndReplyWithLatency(2, ms(2000));
    fixture.tracker().clearMessagesForNode(2);
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(2000), 1),
                         fixture.getNodePutOperationStats(2));
}

void
PendingMessageTrackerCallback_Test::timeTravellingClockLatenciesNotRegistered()
{
    Fixture fixture;
    auto put = fixture.sendPut(RequestBuilder().atTime(ms(1000)).toNode(0));
    fixture.sendPutReply(*put, RequestBuilder().atTime(ms(999)));
    // Latency increase of zero, but we do count the request itself.
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(0), 1),
                         fixture.getNodePutOperationStats(0));
}

void
PendingMessageTrackerCallback_Test::statsSnapshotIncludesAllNodes()
{
    Fixture fixture;
    fixture.sendPutAndReplyWithLatency(0, ms(500));
    fixture.sendPutAndReplyWithLatency(1, ms(600));

    NodeStatsSnapshot snapshot = fixture.tracker().getLatencyStatistics();

    CPPUNIT_ASSERT_EQUAL(size_t(2), snapshot.nodeToStats.size());
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(500), 1),
                         snapshot.nodeToStats[0].puts);
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(600), 1),
                         snapshot.nodeToStats[1].puts);
}

void
PendingMessageTrackerCallback_Test::latencyProviderForwardsToImplementation()
{
    Fixture fixture;
    fixture.sendPutAndReplyWithLatency(0, ms(500));

    LatencyStatisticsProvider& provider(
            fixture.tracker().getLatencyStatisticsProvider());
    NodeStatsSnapshot snapshot = provider.getLatencyStatistics();

    CPPUNIT_ASSERT_EQUAL(size_t(1), snapshot.nodeToStats.size());
    CPPUNIT_ASSERT_EQUAL(makeOpStats(ms(500), 1),
                         snapshot.nodeToStats[0].puts);
}

} // distributor
} // storage
