// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/common/statusmetricconsumer.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/visiting/visitormetrics.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".test.metrics");

namespace storage {

struct MetricsTest : public CppUnit::TestFixture {
    FastOS_ThreadPool _threadPool;
    framework::defaultimplementation::FakeClock* _clock;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    std::unique_ptr<StatusMetricConsumer> _metricsConsumer;
    std::unique_ptr<vdstestlib::DirConfig> _config;
    std::unique_ptr<metrics::MetricSet> _topSet;
    std::unique_ptr<metrics::MetricManager> _metricManager;
    std::shared_ptr<FileStorMetrics> _filestorMetrics;
    std::shared_ptr<BucketManagerMetrics> _bucketManagerMetrics;
    std::shared_ptr<VisitorMetrics> _visitorMetrics;

    void createSnapshotForPeriod(std::chrono::seconds secs);
    void assertMetricLastValue(const std::string& name,
                               int interval,
                               uint64_t expected);

    MetricsTest();

    void setUp() override;
    void tearDown() override;
    void runLoad(uint32_t count = 1);
    void createFakeLoad();

    void testFileStorMetrics();
    void testSnapshotPresenting();
    void testHtmlMetricsReport();
    void testCurrentGaugeValuesOverrideSnapshotValues();
    void testVerboseReportIncludesNonSetMetricsEvenAfterSnapshot();

    CPPUNIT_TEST_SUITE(MetricsTest);
    CPPUNIT_TEST(testFileStorMetrics);
    CPPUNIT_TEST(testSnapshotPresenting);
    CPPUNIT_TEST(testHtmlMetricsReport);
    CPPUNIT_TEST(testCurrentGaugeValuesOverrideSnapshotValues);
    CPPUNIT_TEST(testVerboseReportIncludesNonSetMetricsEvenAfterSnapshot);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MetricsTest);

namespace {
    struct MetricClock : public metrics::MetricManager::Timer
    {
        framework::Clock& _clock;
        MetricClock(framework::Clock& c) : _clock(c) {}
        time_t getTime() const override { return _clock.getTimeInSeconds().getTime(); }
        time_t getTimeInMilliSecs() const override { return _clock.getTimeInMillis().getTime(); }
    };
}

MetricsTest::MetricsTest()
    : _threadPool(256*1024),
      _clock(0),
      _top(),
      _metricsConsumer()
{
}

void MetricsTest::setUp() {
    _config.reset(new vdstestlib::DirConfig(getStandardConfig(true, "metricstest")));
    assert(system(("rm -rf " + getRootFolder(*_config)).c_str()) == 0);
    try {
        _node.reset(new TestServiceLayerApp(DiskCount(4), NodeIndex(0),
                    _config->getConfigId()));
        _node->setupDummyPersistence();
        _clock = &_node->getClock();
        _clock->setAbsoluteTimeInSeconds(1000000);
        _top.reset(new DummyStorageLink);
    } catch (config::InvalidConfigException& e) {
        fprintf(stderr, "%s\n", e.what());
    }
    _metricManager.reset(new metrics::MetricManager(
            std::unique_ptr<metrics::MetricManager::Timer>(
                new MetricClock(*_clock))));
    _topSet.reset(new metrics::MetricSet("vds", "", ""));
    {
        metrics::MetricLockGuard guard(_metricManager->getMetricLock());
        _metricManager->registerMetric(guard, *_topSet);
    }

    _metricsConsumer.reset(new StatusMetricConsumer(
            _node->getComponentRegister(),
            *_metricManager,
            "status"));

    uint16_t diskCount = _node->getPartitions().size();
    documentapi::LoadTypeSet::SP loadTypes(_node->getLoadTypes());

    _filestorMetrics.reset(new FileStorMetrics(_node->getLoadTypes()->getMetricLoadTypes()));
    _filestorMetrics->initDiskMetrics(diskCount, loadTypes->getMetricLoadTypes(), 1, 1);
    _topSet->registerMetric(*_filestorMetrics);

    _bucketManagerMetrics.reset(new BucketManagerMetrics);
    _bucketManagerMetrics->setDisks(diskCount);
    _topSet->registerMetric(*_bucketManagerMetrics);

    _visitorMetrics.reset(new VisitorMetrics);
    _visitorMetrics->initThreads(4, loadTypes->getMetricLoadTypes());
    _topSet->registerMetric(*_visitorMetrics);
    _metricManager->init(_config->getConfigId(), _node->getThreadPool());
}

void MetricsTest::tearDown() {
    _metricManager->stop();
    _metricsConsumer.reset(0);
    _topSet.reset(0);
    _metricManager.reset(0);
    _top.reset(0);
    _node.reset(0);
    _config.reset(0);
    _filestorMetrics.reset();
    _bucketManagerMetrics.reset();
    _visitorMetrics.reset();
}

void MetricsTest::createFakeLoad()
{
    _clock->addSecondsToTime(1);
    _metricManager->timeChangedNotification();
    uint32_t n = 5;
    for (uint32_t i=0; i<_bucketManagerMetrics->disks.size(); ++i) {
        DataStoredMetrics& metrics(*_bucketManagerMetrics->disks[i]);
        metrics.docs.inc(10 * n);
        metrics.bytes.inc(10240 * n);
    }
    _filestorMetrics->directoryEvents.inc(5);
    _filestorMetrics->partitionEvents.inc(4);
    _filestorMetrics->diskEvents.inc(3);
    for (uint32_t i=0; i<_filestorMetrics->disks.size(); ++i) {
        FileStorDiskMetrics& disk(*_filestorMetrics->disks[i]);
        disk.queueSize.addValue(4 * n);
        //disk.averageQueueWaitingTime[documentapi::LoadType::DEFAULT].addValue(10 * n);
        disk.pendingMerges.addValue(4 * n);
        for (uint32_t j=0; j<disk.threads.size(); ++j) {
            FileStorThreadMetrics& thread(*disk.threads[j]);
            thread.operations.inc(120 * n);
            thread.failedOperations.inc(2 * n);

            using documentapi::LoadType;

            thread.put[LoadType::DEFAULT].count.inc(10 * n);
            thread.put[LoadType::DEFAULT].latency.addValue(5 * n);
            thread.get[LoadType::DEFAULT].count.inc(12 * n);
            thread.get[LoadType::DEFAULT].notFound.inc(2 * n);
            thread.get[LoadType::DEFAULT].latency.addValue(3 * n);
            thread.remove[LoadType::DEFAULT].count.inc(6 * n);
            thread.remove[LoadType::DEFAULT].notFound.inc(1 * n);
            thread.remove[LoadType::DEFAULT].latency.addValue(2 * n);
            thread.update[LoadType::DEFAULT].count.inc(2 * n);
            thread.update[LoadType::DEFAULT].notFound.inc(1 * n);
            thread.update[LoadType::DEFAULT].latencyRead.addValue(2 * n);
            thread.update[LoadType::DEFAULT].latency.addValue(7 * n);
            thread.revert[LoadType::DEFAULT].count.inc(2 * n);
            thread.revert[LoadType::DEFAULT].notFound.inc(n / 2);
            thread.revert[LoadType::DEFAULT].latency.addValue(2 * n);
            thread.visit[LoadType::DEFAULT].count.inc(6 * n);

            thread.deleteBuckets.count.inc(1 * n);
            thread.repairs.count.inc(3 * n);
            thread.repairFixed.inc(1 * n);
            thread.splitBuckets.count.inc(20 * n);
            thread.movedBuckets.count.inc(1 * n);
            thread.readBucketInfo.count.inc(2 * n);
            thread.internalJoin.count.inc(3 * n);

            thread.mergeBuckets.count.inc(2 * n);
            thread.bytesMerged.inc(1000 * n);
            thread.getBucketDiff.count.inc(4 * n);
            thread.getBucketDiffReply.inc(4 * n);
            thread.applyBucketDiff.count.inc(4 * n);
            thread.applyBucketDiffReply.inc(4 * n);
            thread.mergeLatencyTotal.addValue(300 * n);
            thread.mergeMetadataReadLatency.addValue(20 * n);
            thread.mergeDataReadLatency.addValue(40 * n);
            thread.mergeDataWriteLatency.addValue(50 * n);
            thread.mergeAverageDataReceivedNeeded.addValue(0.8);
        }
    }
    for (uint32_t i=0; i<_visitorMetrics->threads.size(); ++i) {
        VisitorThreadMetrics& thread(*_visitorMetrics->threads[i]);
        thread.queueSize.addValue(2);
        thread.averageQueueWaitingTime[documentapi::LoadType::DEFAULT].addValue(10);
        thread.averageVisitorLifeTime[documentapi::LoadType::DEFAULT].addValue(1000);
        thread.createdVisitors[documentapi::LoadType::DEFAULT].inc(5 * n);
        thread.abortedVisitors[documentapi::LoadType::DEFAULT].inc(1 * n);
        thread.completedVisitors[documentapi::LoadType::DEFAULT].inc(4 * n);
        thread.failedVisitors[documentapi::LoadType::DEFAULT].inc(2 * n);
    }
    _clock->addSecondsToTime(60);
    _metricManager->timeChangedNotification();
    while (uint64_t(_metricManager->getLastProcessedTime())
                < _clock->getTimeInSeconds().getTime())
    {
        FastOS_Thread::Sleep(5);
        _metricManager->timeChangedNotification();
    }
}

void MetricsTest::testFileStorMetrics() {
    createFakeLoad();
    std::ostringstream ost;
    framework::HttpUrlPath path("metrics?interval=-1&format=text");
    bool retVal = _metricsConsumer->reportStatus(ost, path);
    CPPUNIT_ASSERT_MESSAGE("_metricsConsumer->reportStatus failed", retVal);
    std::string s = ost.str();
    CPPUNIT_ASSERT_MESSAGE("No get statistics in:\n" + s,
            s.find("vds.filestor.alldisks.allthreads.get.sum.count count=240") != std::string::npos);
    CPPUNIT_ASSERT_MESSAGE("No put statistics in:\n" + s,
            s.find("vds.filestor.alldisks.allthreads.put.sum.count count=200") != std::string::npos);
    CPPUNIT_ASSERT_MESSAGE("No remove statistics in:\n" + s,
            s.find("vds.filestor.alldisks.allthreads.remove.sum.count count=120") != std::string::npos);
    CPPUNIT_ASSERT_MESSAGE("No removenotfound stats in:\n" + s,
            s.find("vds.filestor.alldisks.allthreads.remove.sum.not_found count=20") != std::string::npos);
}

#define ASSERT_METRIC(interval, metric, count) \
{ \
    std::ostringstream pathost; \
    pathost << "metrics?interval=" << interval << "&format=text"; \
    std::ostringstream ost;\
    framework::HttpUrlPath path(pathost.str()); \
    bool retVal = _metricsConsumer->reportStatus(ost, path); \
    CPPUNIT_ASSERT_MESSAGE("_metricsConsumer->reportStatus failed", retVal); \
    std::string s = ost.str(); \
    if (count == -1) { \
        CPPUNIT_ASSERT_MESSAGE(std::string("Metric ") + metric + " was set", \
                               s.find(metric) == std::string::npos); \
    } else { \
        std::ostringstream valueost; \
        valueost << metric << " count=" << count; \
        CPPUNIT_ASSERT_MESSAGE("Did not find value " + valueost.str() \
                                    + " in metric dump " + s, \
                               s.find(valueost.str()) != std::string::npos); \
    } \
}

void MetricsTest::testSnapshotPresenting() {
    FileStorDiskMetrics& disk0(*_filestorMetrics->disks[0]);
    FileStorThreadMetrics& thread0(*disk0.threads[0]);

    LOG(info, "Adding to get metric");

    using documentapi::LoadType;
    thread0.get[LoadType::DEFAULT].count.inc(1);

    LOG(info, "Waiting for 5 minute snapshot to be taken");
    // Wait until active metrics have been added to 5 min snapshot and reset
    for (uint32_t i=0; i<6; ++i) {
        _clock->addSecondsToTime(60);
        _metricManager->timeChangedNotification();
        while (
            uint64_t(_metricManager->getLastProcessedTime())
                    < _clock->getTimeInSeconds().getTime())
        {
            FastOS_Thread::Sleep(1);
        }
    }
    LOG(info, "5 minute snapshot should have been taken. Adding put count");

    thread0.put[LoadType::DEFAULT].count.inc(1);

    // Verify that active metrics have set put count but not get count
    ASSERT_METRIC(-2, "vds.filestor.alldisks.allthreads.put.sum.count", 1);
    ASSERT_METRIC(-2, "vds.filestor.alldisks.allthreads.get.sum.count", -1);

    // Verify that 5 min metrics have set get count but not put count
    ASSERT_METRIC(300, "vds.filestor.alldisks.allthreads.put.sum.count", -1);
    ASSERT_METRIC(300, "vds.filestor.alldisks.allthreads.get.sum.count", 1);

    // Verify that the total metrics is equal to 5 minute
    ASSERT_METRIC(0, "vds.filestor.alldisks.allthreads.put.sum.count", -1);
    ASSERT_METRIC(0, "vds.filestor.alldisks.allthreads.get.sum.count", 1);

    // Verify that total + active have set both
    ASSERT_METRIC(-1, "vds.filestor.alldisks.allthreads.put.sum.count", 1);
    ASSERT_METRIC(-1, "vds.filestor.alldisks.allthreads.get.sum.count", 1);
}

void MetricsTest::testHtmlMetricsReport() {
    createFakeLoad();
    _clock->addSecondsToTime(6 * 60);
    _metricManager->timeChangedNotification();
    _metricsConsumer->waitUntilTimeProcessed(_clock->getTimeInSeconds());
    createFakeLoad();
    std::ostringstream ost;
    framework::HttpUrlPath path("metrics?interval=300&format=html");
    bool retVal = _metricsConsumer->reportStatus(ost, path);
    CPPUNIT_ASSERT_MESSAGE("_metricsConsumer->reportStatus failed", retVal);
    std::string s = ost.str();
    // Not actually testing against content. Better to manually verify that
    // HTML look sane after changes.
    //std::cerr << s << "\n";
    {
        std::ofstream out("metricsreport.html");
        out << s;
        out.close();
    }
}

void
MetricsTest::assertMetricLastValue(const std::string& name,
                                   int interval,
                                   uint64_t expected)
{
    std::ostringstream path;
    path << "metrics?interval=" << interval
         << "&format=text&pattern=" << name
         << "&verbosity=2";
    std::ostringstream report;
    framework::HttpUrlPath uri(path.str());
    CPPUNIT_ASSERT(_metricsConsumer->reportStatus(report, uri));
    std::ostringstream expectedSubstr;
    expectedSubstr << " last=" << expected;
    auto str = report.str();
    CPPUNIT_ASSERT_MESSAGE("Did not find value " + expectedSubstr.str()
                           + " in metric dump " + str,
                           str.find(expectedSubstr.str()) != std::string::npos);
}

using namespace std::chrono_literals;

void
MetricsTest::createSnapshotForPeriod(std::chrono::seconds secs)
{
    _clock->addSecondsToTime(secs.count());
    _metricManager->timeChangedNotification();
    while (uint64_t(_metricManager->getLastProcessedTime())
           < _clock->getTimeInSeconds().getTime())
    {
        std::this_thread::sleep_for(100ms);
    }
}

void
MetricsTest::testCurrentGaugeValuesOverrideSnapshotValues()
{
    auto& metrics(*_bucketManagerMetrics->disks[0]);
    metrics.docs.set(1000);
    // Take a 5 minute snapshot of active metrics (1000 docs).
    createSnapshotForPeriod(5min);
    metrics.docs.set(2000);
    // Active metrics are now 2000 docs. Asking for metric snapshots with
    // an interval of -1 implies that the _active_ metric values should
    // be added to the total snapshot, which in the case of gauge metrics
    // only makes sense if the _active_ gauge value gets reported back.
    // In this case it means we should observe 2000 docs, not 1000.
    assertMetricLastValue("vds.datastored.alldisks.docs", -1, 2000);
}

void
MetricsTest::testVerboseReportIncludesNonSetMetricsEvenAfterSnapshot()
{
    createSnapshotForPeriod(5min);
    // When using verbosity=2 (which is what the system test framework invokes),
    // all metrics should be included regardless of whether they've been set or
    // not. In this case, the bytes gauge metric has not been set explicitly
    // but should be reported as zero.
    assertMetricLastValue("vds.datastored.alldisks.bytes", -1, 0);
}

} // storage
