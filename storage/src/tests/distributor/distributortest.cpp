// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <boost/assign/std/vector.hpp> // for 'operator+=()'
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/vespalib/text/stringtokenizer.h>

using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using document::FixedBucketSpaces;

namespace storage {

namespace distributor {

class Distributor_Test : public CppUnit::TestFixture,
                         public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(Distributor_Test);
    CPPUNIT_TEST(testOperationGeneration);
    CPPUNIT_TEST(testOperationsGeneratedAndStartedWithoutDuplicates);
    CPPUNIT_TEST(testRecoveryModeOnClusterStateChange);
    CPPUNIT_TEST(testOperationsAreThrottled);
    CPPUNIT_TEST_IGNORED(testRecoveryModeEntryResetsScanner);
    CPPUNIT_TEST_IGNORED(testReprioritizeBucketOnMaintenanceReply);
    CPPUNIT_TEST(testHandleUnknownMaintenanceReply);
    CPPUNIT_TEST(testContainsTimeStatement);
    CPPUNIT_TEST(testUpdateBucketDatabase);
    CPPUNIT_TEST(testTickProcessesStatusRequests);
    CPPUNIT_TEST(testMetricUpdateHookUpdatesPendingMaintenanceMetrics);
    CPPUNIT_TEST(testPriorityConfigIsPropagatedToDistributorConfiguration);
    CPPUNIT_TEST(testNoDbResurrectionForBucketNotOwnedInPendingState);
    CPPUNIT_TEST(testAddedDbBucketsWithoutGcTimestampImplicitlyGetCurrentTime);
    CPPUNIT_TEST(mergeStatsAreAccumulatedDuringDatabaseIteration);
    CPPUNIT_TEST(statsGeneratedForPreemptedOperations);
    CPPUNIT_TEST(hostInfoReporterConfigIsPropagatedToReporter);
    CPPUNIT_TEST(replicaCountingModeIsConfiguredToTrustedByDefault);
    CPPUNIT_TEST(replicaCountingModeConfigIsPropagatedToMetricUpdater);
    CPPUNIT_TEST(bucketActivationIsEnabledByDefault);
    CPPUNIT_TEST(bucketActivationConfigIsPropagatedToDistributorConfiguration);
    CPPUNIT_TEST(max_clock_skew_config_is_propagated_to_distributor_config);
    CPPUNIT_TEST(configured_safe_time_point_rejection_works_end_to_end);
    CPPUNIT_TEST(sequencing_config_is_propagated_to_distributor_config);
    CPPUNIT_TEST(merge_busy_inhibit_duration_config_is_propagated_to_distributor_config);
    CPPUNIT_TEST(merge_busy_inhibit_duration_is_propagated_to_pending_message_tracker);
    CPPUNIT_TEST(external_client_requests_are_handled_individually_in_priority_order);
    CPPUNIT_TEST(internal_messages_are_started_in_fifo_order_batch);
    CPPUNIT_TEST(closing_aborts_priority_queued_client_requests);
    CPPUNIT_TEST_SUITE_END();

public:
    Distributor_Test();

protected:
    void testOperationGeneration();
    void testOperationsGeneratedAndStartedWithoutDuplicates();
    void testRecoveryModeOnClusterStateChange();
    void testOperationsAreThrottled();
    void testRecoveryModeEntryResetsScanner();
    void testReprioritizeBucketOnMaintenanceReply();
    void testHandleUnknownMaintenanceReply();
    void testContainsTimeStatement();
    void testUpdateBucketDatabase();
    void testTickProcessesStatusRequests();
    void testMetricUpdateHookUpdatesPendingMaintenanceMetrics();
    void testPriorityConfigIsPropagatedToDistributorConfiguration();
    void testNoDbResurrectionForBucketNotOwnedInPendingState();
    void testAddedDbBucketsWithoutGcTimestampImplicitlyGetCurrentTime();
    void mergeStatsAreAccumulatedDuringDatabaseIteration();
    void statsGeneratedForPreemptedOperations();
    void hostInfoReporterConfigIsPropagatedToReporter();
    void replicaCountingModeIsConfiguredToTrustedByDefault();
    void replicaCountingModeConfigIsPropagatedToMetricUpdater();
    void bucketActivationIsEnabledByDefault();
    void bucketActivationConfigIsPropagatedToDistributorConfiguration();
    void max_clock_skew_config_is_propagated_to_distributor_config();
    void configured_safe_time_point_rejection_works_end_to_end();
    void sequencing_config_is_propagated_to_distributor_config();
    void merge_busy_inhibit_duration_config_is_propagated_to_distributor_config();
    void merge_busy_inhibit_duration_is_propagated_to_pending_message_tracker();
    void external_client_requests_are_handled_individually_in_priority_order();
    void internal_messages_are_started_in_fifo_order_batch();
    void closing_aborts_priority_queued_client_requests();

    std::vector<document::BucketSpace> _bucketSpaces;

public:
    void setUp() override {
        createLinks();
        _bucketSpaces = getBucketSpaces();
    };

    void tearDown() override {
        close();
    }

private:
    // Simple type aliases to make interfacing with certain utility functions
    // easier. Note that this is only for readability and does not provide any
    // added type safety.
    using NodeCount = int;
    using Redundancy = int;

    using ConfigBuilder = vespa::config::content::core::StorDistributormanagerConfigBuilder;

    void configureDistributor(const ConfigBuilder& config) {
        getConfig().configure(config);
        _distributor->enableNextConfig();
    }

    auto currentReplicaCountingMode() const noexcept {
        return _distributor->_bucketDBMetricUpdater
                .getMinimumReplicaCountingMode();
    }

    std::string testOp(api::StorageMessage* msg)
    {
        api::StorageMessage::SP msgPtr(msg);
        _distributor->handleMessage(msgPtr);

        std::string tmp = _sender.getCommands();
        _sender.clear();
        return tmp;
    }

    void tickDistributorNTimes(uint32_t n) {
        for (uint32_t i = 0; i < n; ++i) {
            tick();
        }
    }

    typedef bool ResetTrusted;

    std::string updateBucketDB(const std::string& firstState,
                               const std::string& secondState,
                               bool resetTrusted = false)
    {
        std::vector<std::string> states(toVector<std::string>(firstState, secondState));

        for (uint32_t i = 0; i < states.size(); ++i) {
            std::vector<uint16_t> removedNodes;
            std::vector<BucketCopy> changedNodes;

            vespalib::StringTokenizer tokenizer(states[i], ",");
            for (uint32_t j = 0; j < tokenizer.size(); ++j) {
                vespalib::StringTokenizer tokenizer2(tokenizer[j], ":");

                bool trusted = false;
                if (tokenizer2.size() > 2) {
                    trusted = true;
                }

                uint16_t node = atoi(tokenizer2[0].c_str());
                if (tokenizer2[1] == "r") {
                    removedNodes.push_back(node);
                } else {
                    uint32_t checksum = atoi(tokenizer2[1].c_str());
                    changedNodes.push_back(
                            BucketCopy(
                                    i + 1,
                                    node,
                                    api::BucketInfo(
                                            checksum,
                                            checksum / 2,
                                            checksum / 4)).setTrusted(trusted));
                }
            }

            getExternalOperationHandler().removeNodesFromDB(makeDocumentBucket(document::BucketId(16, 1)), removedNodes);

            uint32_t flags(DatabaseUpdate::CREATE_IF_NONEXISTING
                           | (resetTrusted ? DatabaseUpdate::RESET_TRUSTED : 0));

            getExternalOperationHandler().updateBucketDatabase(makeDocumentBucket(document::BucketId(16, 1)),
                                            changedNodes,
                                            flags);
        }

        std::string retVal = dumpBucket(document::BucketId(16, 1));
        getBucketDatabase().clear();
        return retVal;
    }

    void configureMaxClusterClockSkew(int seconds);
    void sendDownClusterStateCommand();
    void replyToSingleRequestBucketInfoCommandWith1Bucket();
    void sendDownDummyRemoveCommand();
    void assertSingleBouncedRemoveReplyPresent();
    void assertNoMessageBounced();
    void configure_mutation_sequencing(bool enabled);
    void configure_merge_busy_inhibit_duration(int seconds);
};

CPPUNIT_TEST_SUITE_REGISTRATION(Distributor_Test);

Distributor_Test::Distributor_Test()
    : CppUnit::TestFixture(),
    DistributorTestUtil(),
    _bucketSpaces()
{
}

void
Distributor_Test::testOperationGeneration()
{
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    document::BucketId bid;
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    CPPUNIT_ASSERT_EQUAL(std::string("Remove"),
                         testOp(new api::RemoveCommand(
                                        makeDocumentBucket(bid),
                                        document::DocumentId("userdoc:m:1:foo"),
                                        api::Timestamp(1234))));

    api::CreateVisitorCommand* cmd = new api::CreateVisitorCommand(makeBucketSpace(), "foo", "bar", "");
    cmd->addBucketToBeVisited(document::BucketId(16, 1));
    cmd->addBucketToBeVisited(document::BucketId());

    CPPUNIT_ASSERT_EQUAL(std::string("Visitor Create"), testOp(cmd));
}

void
Distributor_Test::testOperationsGeneratedAndStartedWithoutDuplicates()
{
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }

    tickDistributorNTimes(20);

    CPPUNIT_ASSERT(!tick());

    CPPUNIT_ASSERT_EQUAL(6, (int)_sender.commands.size());
}

void
Distributor_Test::testRecoveryModeOnClusterStateChange()
{
    setupDistributor(Redundancy(1), NodeCount(2),
                     "storage:1 .0.s:d distributor:1");
    _distributor->enableClusterState(
            lib::ClusterState("storage:1 distributor:1"));

    CPPUNIT_ASSERT(_distributor->isInRecoveryMode());
    for (uint32_t i = 0; i < 3; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }
    for (int i = 0; i < 3; ++i) {
        tick();
        CPPUNIT_ASSERT(_distributor->isInRecoveryMode());
    }
    tick();
    CPPUNIT_ASSERT(!_distributor->isInRecoveryMode());

    _distributor->enableClusterState(lib::ClusterState("storage:2 distributor:1"));
    CPPUNIT_ASSERT(_distributor->isInRecoveryMode());
}

void
Distributor_Test::testOperationsAreThrottled()
{
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    getConfig().setMinPendingMaintenanceOps(1);
    getConfig().setMaxPendingMaintenanceOps(1);

    for (uint32_t i = 0; i < 6; ++i) {
        addNodesToBucketDB(document::BucketId(16, i), "0=1");
    }
    tickDistributorNTimes(20);
    CPPUNIT_ASSERT_EQUAL(1, (int)_sender.commands.size());
}

void
Distributor_Test::testRecoveryModeEntryResetsScanner()
{
    CPPUNIT_FAIL("TODO: refactor so this can be mocked and tested easily");
}

void
Distributor_Test::testReprioritizeBucketOnMaintenanceReply()
{
    CPPUNIT_FAIL("TODO: refactor so this can be mocked and tested easily");
}

void
Distributor_Test::testHandleUnknownMaintenanceReply()
{
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    {
        api::SplitBucketCommand::SP cmd(
                new api::SplitBucketCommand(makeDocumentBucket(document::BucketId(16, 1234))));
        api::SplitBucketReply::SP reply(new api::SplitBucketReply(*cmd));

        CPPUNIT_ASSERT(_distributor->handleReply(reply));
    }

    {
        // RemoveLocationReply must be treated as a maintenance reply since
        // it's what GC is currently built around.
        auto cmd = std::make_shared<api::RemoveLocationCommand>(
                "false", makeDocumentBucket(document::BucketId(30, 1234)));
        auto reply = std::shared_ptr<api::StorageReply>(cmd->makeReply());
        CPPUNIT_ASSERT(_distributor->handleReply(reply));
    }
}

void
Distributor_Test::testContainsTimeStatement()
{
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    CPPUNIT_ASSERT_EQUAL(false, getConfig().containsTimeStatement(""));
    CPPUNIT_ASSERT_EQUAL(false, getConfig().containsTimeStatement("testdoctype1"));
    CPPUNIT_ASSERT_EQUAL(false, getConfig().containsTimeStatement("testdoctype1.headerfield > 42"));
    CPPUNIT_ASSERT_EQUAL(true, getConfig().containsTimeStatement("testdoctype1.headerfield > now()"));
    CPPUNIT_ASSERT_EQUAL(true, getConfig().containsTimeStatement("testdoctype1.headerfield > now() - 3600"));
    CPPUNIT_ASSERT_EQUAL(true, getConfig().containsTimeStatement("testdoctype1.headerfield == now() - 3600"));
}

void
Distributor_Test::testUpdateBucketDatabase()
{
    _distributor->enableClusterState(lib::ClusterState("distributor:1 storage:3"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
                    "node(idx=1,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false)"
                    ),
            updateBucketDB("0:456,1:456,2:789", "2:r"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
                    "node(idx=2,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
                    "node(idx=1,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false)"
                    ),
            updateBucketDB("0:456,1:456", "2:456"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0x315,docs=394/394,bytes=197/197,trusted=false,active=false,ready=false), "
                    "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=false,active=false,ready=false), "
                    "node(idx=1,crc=0x34a,docs=421/421,bytes=210/210,trusted=false,active=false,ready=false)"
                    ),
            updateBucketDB("0:456:t,1:456:t,2:123", "0:789,1:842,2:333"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0x315,docs=394/394,bytes=197/197,trusted=true,active=false,ready=false), "
                    "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=false,active=false,ready=false), "
                    "node(idx=1,crc=0x315,docs=394/394,bytes=197/197,trusted=true,active=false,ready=false)"
                    ),
            updateBucketDB("0:456:t,1:456:t,2:123", "0:789,1:789,2:333"));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                        "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=true,active=false,ready=false)"),
            updateBucketDB("0:456:t,1:456:t", "0:r,1:r,2:333"));

    // Copies are in sync so should still be trusted even if explicitly reset.
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
                    "node(idx=2,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false), "
                    "node(idx=1,crc=0x1c8,docs=228/228,bytes=114/114,trusted=true,active=false,ready=false)"
                    ),
            updateBucketDB("0:456,1:456", "2:456", ResetTrusted(true)));

    // When resetting, first inserted copy should not end up as implicitly trusted.
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000000001) : "
                    "node(idx=0,crc=0x1c8,docs=228/228,bytes=114/114,trusted=false,active=false,ready=false), "
                    "node(idx=2,crc=0x14d,docs=166/166,bytes=83/83,trusted=false,active=false,ready=false)"
                    ),
            updateBucketDB("0:456",
                           "2:333",
                           ResetTrusted(true)));
}

namespace {

using namespace framework::defaultimplementation;

class StatusRequestThread : public framework::Runnable
{
    StatusReporterDelegate& _reporter;
    std::string _result;
public:
    StatusRequestThread(StatusReporterDelegate& reporter)
        : _reporter(reporter)
    {}
    void run(framework::ThreadHandle&) override {
        framework::HttpUrlPath path("/distributor?page=buckets");
        std::ostringstream stream;
        _reporter.reportStatus(stream, path);
        _result = stream.str();
    }

    std::string getResult() const {
        return _result;
    }
};

}

void
Distributor_Test::testTickProcessesStatusRequests()
{
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");

    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    // Must go via delegate since reportStatus is now just a rendering
    // function and not a request enqueuer (see Distributor::handleStatusRequest).
    StatusRequestThread thread(_distributor->_distributorStatusDelegate);
    FakeClock clock;
    ThreadPoolImpl pool(clock);
    
    uint64_t tickWaitMs = 5;
    uint64_t tickMaxProcessTime = 5000;
    int ticksBeforeWait = 1;
    framework::Thread::UP tp(pool.startThread(
        thread, "statustest", tickWaitMs, tickMaxProcessTime, ticksBeforeWait));

    while (true) {
        FastOS_Thread::Sleep(1);
        framework::TickingLockGuard guard(
            _distributor->_threadPool.freezeCriticalTicks());
        if (!_distributor->_statusToDo.empty()) break;
        
    }
    CPPUNIT_ASSERT(tick());

    tp->interruptAndJoin(0);

    CPPUNIT_ASSERT_CONTAIN("BucketId(0x4000000000000001)", thread.getResult());
}

void
Distributor_Test::testMetricUpdateHookUpdatesPendingMaintenanceMetrics()
{
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    // To ensure we count all operations, not just those fitting within the
    // pending window.
    getConfig().setMinPendingMaintenanceOps(1);
    getConfig().setMaxPendingMaintenanceOps(1);

    // 1 bucket must be merged, 1 must be split, 1 should be activated.
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a,1=2/2/2");
    addNodesToBucketDB(document::BucketId(16, 2),
                       "0=100/10000000/200000/t/a,1=100/10000000/200000/t");
    addNodesToBucketDB(document::BucketId(16, 3),
                       "0=200/300/400/t,1=200/300/400/t");

    // Go many full scanner rounds to check that metrics are set, not
    // added to existing.
    tickDistributorNTimes(50);

    // By this point, no hook has been called so the metrics have not been
    // set.
    typedef MaintenanceOperation MO;
    {
        const IdealStateMetricSet& metrics(getIdealStateManager().getMetrics());
        CPPUNIT_ASSERT_EQUAL(int64_t(0),
                             metrics.operations[MO::MERGE_BUCKET]
                                ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0), metrics.operations[MO::SPLIT_BUCKET]
                                         ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0),
                             metrics.operations[MO::SET_BUCKET_STATE]
                                ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0), metrics.operations[MO::DELETE_BUCKET]
                                            ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0), metrics.operations[MO::JOIN_BUCKET]
                                            ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0),
                             metrics.operations[MO::GARBAGE_COLLECTION]
                                ->pending.getLast());
    }

    // Force trigger update hook
    vespalib::Monitor l;
    _distributor->_metricUpdateHook.updateMetrics(vespalib::MonitorGuard(l));
    // Metrics should now be updated to the last complete working state
    {
        const IdealStateMetricSet& metrics(getIdealStateManager().getMetrics());
        CPPUNIT_ASSERT_EQUAL(int64_t(1),
                             metrics.operations[MO::MERGE_BUCKET]
                                ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(1), metrics.operations[MO::SPLIT_BUCKET]
                                         ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(1),
                             metrics.operations[MO::SET_BUCKET_STATE]
                                ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0), metrics.operations[MO::DELETE_BUCKET]
                                            ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0), metrics.operations[MO::JOIN_BUCKET]
                                            ->pending.getLast());
        CPPUNIT_ASSERT_EQUAL(int64_t(0),
                             metrics.operations[MO::GARBAGE_COLLECTION]
                                ->pending.getLast());
    }
}

void
Distributor_Test::testPriorityConfigIsPropagatedToDistributorConfiguration()
{
    using namespace vespa::config::content::core;
    using ConfigBuilder = StorDistributormanagerConfigBuilder;

    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    ConfigBuilder builder;
    builder.priorityMergeMoveToIdealNode = 1;
    builder.priorityMergeOutOfSyncCopies = 2;
    builder.priorityMergeTooFewCopies = 3;
    builder.priorityActivateNoExistingActive = 4;
    builder.priorityActivateWithExistingActive = 5;
    builder.priorityDeleteBucketCopy = 6;
    builder.priorityJoinBuckets = 7;
    builder.prioritySplitDistributionBits = 8;
    builder.prioritySplitLargeBucket = 9;
    builder.prioritySplitInconsistentBucket = 10;
    builder.priorityGarbageCollection = 11;

    getConfig().configure(builder);

    const DistributorConfiguration::MaintenancePriorities& mp(
            getConfig().getMaintenancePriorities());
    CPPUNIT_ASSERT_EQUAL(1, static_cast<int>(mp.mergeMoveToIdealNode));
    CPPUNIT_ASSERT_EQUAL(2, static_cast<int>(mp.mergeOutOfSyncCopies));
    CPPUNIT_ASSERT_EQUAL(3, static_cast<int>(mp.mergeTooFewCopies));
    CPPUNIT_ASSERT_EQUAL(4, static_cast<int>(mp.activateNoExistingActive));
    CPPUNIT_ASSERT_EQUAL(5, static_cast<int>(mp.activateWithExistingActive));
    CPPUNIT_ASSERT_EQUAL(6, static_cast<int>(mp.deleteBucketCopy));
    CPPUNIT_ASSERT_EQUAL(7, static_cast<int>(mp.joinBuckets));
    CPPUNIT_ASSERT_EQUAL(8, static_cast<int>(mp.splitDistributionBits));
    CPPUNIT_ASSERT_EQUAL(9, static_cast<int>(mp.splitLargeBucket));
    CPPUNIT_ASSERT_EQUAL(10, static_cast<int>(mp.splitInconsistentBucket));
    CPPUNIT_ASSERT_EQUAL(11, static_cast<int>(mp.garbageCollection));
}

void
Distributor_Test::testNoDbResurrectionForBucketNotOwnedInPendingState()
{
    setupDistributor(Redundancy(1), NodeCount(10), "storage:2 distributor:2");
    lib::ClusterState newState("storage:10 distributor:10");
    auto stateCmd = std::make_shared<api::SetSystemStateCommand>(newState);
    // Force newState into being the pending state. According to the initial
    // state we own the bucket, but according to the pending state, we do
    // not. This must be handled correctly by the database update code.
    getBucketDBUpdater().onSetSystemState(stateCmd);

    document::BucketId nonOwnedBucket(16, 3);
    CPPUNIT_ASSERT(!getBucketDBUpdater()
                   .checkOwnershipInPendingState(makeDocumentBucket(nonOwnedBucket)).isOwned());
    CPPUNIT_ASSERT(!getBucketDBUpdater().getDistributorComponent()
                   .checkOwnershipInPendingAndCurrentState(makeDocumentBucket(nonOwnedBucket))
                   .isOwned());

    std::vector<BucketCopy> copies;
    copies.emplace_back(1234, 0, api::BucketInfo(0x567, 1, 2));
    getExternalOperationHandler().updateBucketDatabase(makeDocumentBucket(nonOwnedBucket), copies,
                                    DatabaseUpdate::CREATE_IF_NONEXISTING);

    CPPUNIT_ASSERT_EQUAL(std::string("NONEXISTING"),
                         dumpBucket(nonOwnedBucket));
}

void
Distributor_Test::testAddedDbBucketsWithoutGcTimestampImplicitlyGetCurrentTime()
{
    setupDistributor(Redundancy(1), NodeCount(10), "storage:2 distributor:2");
    getClock().setAbsoluteTimeInSeconds(101234);
    document::BucketId bucket(16, 7654);

    std::vector<BucketCopy> copies;
    copies.emplace_back(1234, 0, api::BucketInfo(0x567, 1, 2));
    getExternalOperationHandler().updateBucketDatabase(makeDocumentBucket(bucket), copies,
                                    DatabaseUpdate::CREATE_IF_NONEXISTING);
    BucketDatabase::Entry e(getBucket(bucket));
    CPPUNIT_ASSERT_EQUAL(uint32_t(101234), e->getLastGarbageCollectionTime());
}


void
Distributor_Test::mergeStatsAreAccumulatedDuringDatabaseIteration()
{
    setupDistributor(Redundancy(2), NodeCount(3), "storage:3 distributor:1");
    // Copies out of sync. Not possible for distributor to _reliably_ tell
    // which direction(s) data will flow, so for simplicity assume that we
    // must sync both copies.
    // Note that we mark certain copies as active to prevent the bucketstate
    // checker from pre-empting the merges.
    // -> syncing[0] += 1, syncing[2] += 1
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a,2=2/2/2");
    // Must add missing node 2 for bucket
    // -> copyingOut[0] += 1, copyingIn[2] += 1
    addNodesToBucketDB(document::BucketId(16, 2), "0=1/1/1/t/a");
    // Moving from non-ideal node 1 to ideal node 2. Both nodes 0 and 1 will
    // be involved in this merge, but only node 1 will be tagged as source only
    // (i.e. to be deleted after the merge is completed).
    // -> copyingOut[0] += 1, movingOut[1] += 1, copyingIn[2] += 1
    addNodesToBucketDB(document::BucketId(16, 3), "0=2/2/2/t/a,1=2/2/2/t");

    // Go many full scanner rounds to check that stats are set, not
    // added to existing.
    tickDistributorNTimes(50);

    const auto& stats(_distributor->_maintenanceStats);
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        wanted.copyingOut = 2;
        CPPUNIT_ASSERT_EQUAL(wanted, stats.perNodeStats.forNode(0));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.movingOut = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, stats.perNodeStats.forNode(1));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        wanted.copyingIn = 2;
        CPPUNIT_ASSERT_EQUAL(wanted, stats.perNodeStats.forNode(2));
    }
}

/**
 * Since maintenance operations are prioritized differently, activation
 * pre-empts merging and other ops. If this also implies pre-empting running
 * their state checkers at all, we won't get any statistics from any other
 * operations for the bucket.
 */
void
Distributor_Test::statsGeneratedForPreemptedOperations()
{
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    // For this test it suffices to have a single bucket with multiple aspects
    // wrong about it. In this case, let a bucket be both out of sync _and_
    // missing an active copy. This _should_ give a statistic with both nodes 0
    // and 1 requiring a sync. If instead merge stats generation is preempted
    // by activation, we'll see no merge stats at all.
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1,1=2/2/2");
    tickDistributorNTimes(50);
    const auto& stats(_distributor->_maintenanceStats);
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, stats.perNodeStats.forNode(0));
    }
    {
        NodeMaintenanceStats wanted;
        wanted.syncing = 1;
        CPPUNIT_ASSERT_EQUAL(wanted, stats.perNodeStats.forNode(1));
    }
}

void
Distributor_Test::hostInfoReporterConfigIsPropagatedToReporter()
{
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    // Default is enabled=true.
    CPPUNIT_ASSERT(_distributor->_hostInfoReporter.isReportingEnabled());

    ConfigBuilder builder;
    builder.enableHostInfoReporting = false;
    configureDistributor(builder);

    CPPUNIT_ASSERT(!_distributor->_hostInfoReporter.isReportingEnabled());
}

void
Distributor_Test::replicaCountingModeIsConfiguredToTrustedByDefault()
{
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    CPPUNIT_ASSERT_EQUAL(ConfigBuilder::TRUSTED, currentReplicaCountingMode());
}

void
Distributor_Test::replicaCountingModeConfigIsPropagatedToMetricUpdater()
{
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    ConfigBuilder builder;
    builder.minimumReplicaCountingMode = ConfigBuilder::ANY;
    configureDistributor(builder);
    CPPUNIT_ASSERT_EQUAL(ConfigBuilder::ANY, currentReplicaCountingMode());
}

void
Distributor_Test::bucketActivationIsEnabledByDefault()
{
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");
    CPPUNIT_ASSERT(getConfig().isBucketActivationDisabled() == false);
}

void
Distributor_Test::bucketActivationConfigIsPropagatedToDistributorConfiguration()
{
    using namespace vespa::config::content::core;
    using ConfigBuilder = StorDistributormanagerConfigBuilder;

    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    ConfigBuilder builder;
    builder.disableBucketActivation = true;
    getConfig().configure(builder);

    CPPUNIT_ASSERT(getConfig().isBucketActivationDisabled());
}

void
Distributor_Test::configureMaxClusterClockSkew(int seconds) {
    using namespace vespa::config::content::core;
    using ConfigBuilder = StorDistributormanagerConfigBuilder;

    ConfigBuilder builder;
    builder.maxClusterClockSkewSec = seconds;
    getConfig().configure(builder);
    _distributor->enableNextConfig();
}

void
Distributor_Test::max_clock_skew_config_is_propagated_to_distributor_config() {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    configureMaxClusterClockSkew(5);
    CPPUNIT_ASSERT(getConfig().getMaxClusterClockSkew()
                   == std::chrono::seconds(5));
}

namespace {

auto makeDummyRemoveCommand() {
    return std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(document::BucketId(0)),
            document::DocumentId("id:foo:testdoctype1:n=1:foo"),
            api::Timestamp(0));
}

}

void Distributor_Test::sendDownClusterStateCommand() {
    lib::ClusterState newState("bits:1 storage:1 distributor:1");
    auto stateCmd = std::make_shared<api::SetSystemStateCommand>(newState);
    _distributor->handleMessage(stateCmd);
}

void Distributor_Test::replyToSingleRequestBucketInfoCommandWith1Bucket() {
    CPPUNIT_ASSERT_EQUAL(_bucketSpaces.size(), _sender.commands.size());
    for (uint32_t i = 0; i < _sender.commands.size(); ++i) {
        CPPUNIT_ASSERT_EQUAL(api::MessageType::REQUESTBUCKETINFO,
                             _sender.commands[i]->getType());
        auto& bucketReq(static_cast<api::RequestBucketInfoCommand&>
                        (*_sender.commands[i]));
        auto bucketReply = bucketReq.makeReply();
        if (bucketReq.getBucketSpace() == FixedBucketSpaces::default_space()) {
            // Make sure we have a bucket to route our remove op to, or we'd get
            // an immediate reply anyway.
            dynamic_cast<api::RequestBucketInfoReply&>(*bucketReply)
                .getBucketInfo().push_back(
                        api::RequestBucketInfoReply::Entry(document::BucketId(1, 1),
                                                           api::BucketInfo(20, 10, 12, 50, 60, true, true)));
        }
        _distributor->handleMessage(std::move(bucketReply));
    }
    _sender.commands.clear();
}

void Distributor_Test::sendDownDummyRemoveCommand() {
    _distributor->handleMessage(makeDummyRemoveCommand());
}

void Distributor_Test::assertSingleBouncedRemoveReplyPresent() {
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size()); // Rejected remove
    CPPUNIT_ASSERT_EQUAL(api::MessageType::REMOVE_REPLY,
                         _sender.replies[0]->getType());
    auto& reply(static_cast<api::RemoveReply&>(*_sender.replies[0]));
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::STALE_TIMESTAMP,
                         reply.getResult().getResult());
    _sender.replies.clear();
}

void Distributor_Test::assertNoMessageBounced() {
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.replies.size());
}

// TODO refactor this to set proper highest timestamp as part of bucket info
// reply once we have the "highest timestamp across all owned buckets" feature
// in place.
void
Distributor_Test::configured_safe_time_point_rejection_works_end_to_end() {
    setupDistributor(Redundancy(2), NodeCount(2),
                     "bits:1 storage:1 distributor:2");
    getClock().setAbsoluteTimeInSeconds(1000);
    configureMaxClusterClockSkew(10);

    sendDownClusterStateCommand();
    replyToSingleRequestBucketInfoCommandWith1Bucket();
    // SetSystemStateCommand sent down chain at this point.
    sendDownDummyRemoveCommand();
    assertSingleBouncedRemoveReplyPresent();

    // Increment time to first whole second of clock + 10 seconds of skew.
    // Should now not get any feed rejections.
    getClock().setAbsoluteTimeInSeconds(1011);

    sendDownDummyRemoveCommand();
    assertNoMessageBounced();
}

void Distributor_Test::configure_mutation_sequencing(bool enabled) {
    using namespace vespa::config::content::core;
    using ConfigBuilder = StorDistributormanagerConfigBuilder;

    ConfigBuilder builder;
    builder.sequenceMutatingOperations = enabled;
    getConfig().configure(builder);
    _distributor->enableNextConfig();
}

void Distributor_Test::sequencing_config_is_propagated_to_distributor_config() {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    // Should be enabled by default
    CPPUNIT_ASSERT(getConfig().getSequenceMutatingOperations());

    // Explicitly disabled.
    configure_mutation_sequencing(false);
    CPPUNIT_ASSERT(!getConfig().getSequenceMutatingOperations());

    // Explicitly enabled.
    configure_mutation_sequencing(true);
    CPPUNIT_ASSERT(getConfig().getSequenceMutatingOperations());
}

void
Distributor_Test::configure_merge_busy_inhibit_duration(int seconds) {
    using namespace vespa::config::content::core;
    using ConfigBuilder = StorDistributormanagerConfigBuilder;

    ConfigBuilder builder;
    builder.inhibitMergeSendingOnBusyNodeDurationSec = seconds;
    getConfig().configure(builder);
    _distributor->enableNextConfig();
}

void Distributor_Test::merge_busy_inhibit_duration_config_is_propagated_to_distributor_config() {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:2 distributor:1");

    configure_merge_busy_inhibit_duration(7);
    CPPUNIT_ASSERT(getConfig().getInhibitMergesOnBusyNodeDuration() == std::chrono::seconds(7));
}

void Distributor_Test::merge_busy_inhibit_duration_is_propagated_to_pending_message_tracker() {
    setupDistributor(Redundancy(2), NodeCount(2), "storage:1 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t");

    configure_merge_busy_inhibit_duration(100);
    auto cmd = makeDummyRemoveCommand(); // Remove is for bucket 1
    _distributor->handleMessage(cmd);

    // Should send to content node 0
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.commands.size());
    CPPUNIT_ASSERT_EQUAL(api::MessageType::REMOVE, _sender.commands[0]->getType());
    auto& fwd_cmd = dynamic_cast<api::RemoveCommand&>(*_sender.commands[0]);
    auto reply = fwd_cmd.makeReply();
    reply->setResult(api::ReturnCode(api::ReturnCode::BUSY));
    _distributor->handleReply(std::shared_ptr<api::StorageReply>(std::move(reply)));

    auto& node_info = _distributor->getPendingMessageTracker().getNodeInfo();

    CPPUNIT_ASSERT(node_info.isBusy(0));
    getClock().addSecondsToTime(99);
    CPPUNIT_ASSERT(node_info.isBusy(0));
    getClock().addSecondsToTime(2);
    CPPUNIT_ASSERT(!node_info.isBusy(0));
}

void Distributor_Test::external_client_requests_are_handled_individually_in_priority_order() {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    addNodesToBucketDB(document::BucketId(16, 1), "0=1/1/1/t/a");

    std::vector<api::StorageMessage::Priority> priorities({50, 255, 10, 40, 0});
    document::DocumentId id("id:foo:testdoctype1:n=1:foo");
    vespalib::stringref field_set = "";
    for (auto pri : priorities) {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId()), id, field_set);
        cmd->setPriority(pri);
        // onDown appends to internal message FIFO queue, awaiting hand-off.
        _distributor->onDown(cmd);
    }
    // At the hand-off point we expect client requests to be prioritized.
    // For each tick, a priority-order client request is processed and sent off.
    for (size_t i = 1; i <= priorities.size(); ++i) {
        tickDistributorNTimes(1);
        CPPUNIT_ASSERT_EQUAL(size_t(i), _sender.commands.size());
    }

    std::vector<int> expected({0, 10, 40, 50, 255});
    std::vector<int> actual;
    for (auto& msg : _sender.commands) {
        actual.emplace_back(static_cast<int>(msg->getPriority()));
    }
    CPPUNIT_ASSERT_EQUAL(expected, actual);
}

void Distributor_Test::internal_messages_are_started_in_fifo_order_batch() {
    // To test internal request ordering, we use NotifyBucketChangeCommand
    // for the reason that it explicitly updates the bucket database for
    // each individual invocation.
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");

    std::vector<api::StorageMessage::Priority> priorities({50, 255, 10, 40, 1});
    for (auto pri : priorities) {
        api::BucketInfo fake_info(pri, pri, pri);
        auto cmd = std::make_shared<api::NotifyBucketChangeCommand>(makeDocumentBucket(bucket), fake_info);
        cmd->setSourceIndex(0);
        cmd->setPriority(pri);
        _distributor->onDown(cmd);
    }

    // Doing a single tick should process all internal requests in one batch
    tickDistributorNTimes(1);
    CPPUNIT_ASSERT_EQUAL(size_t(5), _sender.replies.size());

    // The bucket info for priority 1 (last FIFO-order change command received, but
    // highest priority) should be the end-state of the bucket database, _not_ that
    // of lowest priority 255.
    BucketDatabase::Entry e(getBucket(bucket));
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(1, 1, 1), e.getBucketInfo().getNode(0)->getBucketInfo());
}

void Distributor_Test::closing_aborts_priority_queued_client_requests() {
    setupDistributor(Redundancy(1), NodeCount(1), "storage:1 distributor:1");
    document::BucketId bucket(16, 1);
    addNodesToBucketDB(bucket, "0=1/1/1/t");

    document::DocumentId id("id:foo:testdoctype1:n=1:foo");
    vespalib::stringref field_set = "";
    for (int i = 0; i < 10; ++i) {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId()), id, field_set);
        _distributor->onDown(cmd);
    }
    tickDistributorNTimes(1);
    // Closing should trigger 1 abort via startet GetOperation and 9 aborts from pri queue
    _distributor->close();
    CPPUNIT_ASSERT_EQUAL(size_t(10), _sender.replies.size());
    for (auto& msg : _sender.replies) {
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED,
                             dynamic_cast<api::StorageReply&>(*msg).getResult().getResult());
    }
}

}

}
