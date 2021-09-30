// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/common/statusmetricconsumer.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/storage/visiting/visitormetrics.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".test.metrics");

using namespace ::testing;

namespace storage {

struct MetricsTest : public Test {
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
    ~MetricsTest() override;

    void SetUp() override;
    void TearDown() override;
    void createFakeLoad();
};

namespace {
    struct MetricClock : public metrics::MetricManager::Timer
    {
        framework::Clock& _clock;
        explicit MetricClock(framework::Clock& c) : _clock(c) {}
        time_t getTime() const override { return _clock.getTimeInSeconds().getTime(); }
        time_t getTimeInMilliSecs() const override { return _clock.getTimeInMillis().getTime(); }
    };
}

MetricsTest::MetricsTest()
    : _clock(nullptr),
      _top(),
      _metricsConsumer()
{
}

MetricsTest::~MetricsTest() = default;

void MetricsTest::SetUp() {
    _config = std::make_unique<vdstestlib::DirConfig>(getStandardConfig(true, "metricstest"));
    vespalib::rmdir(getRootFolder(*_config), true);
    try {
        _node = std::make_unique<TestServiceLayerApp>(NodeIndex(0), _config->getConfigId());
        _node->setupDummyPersistence();
        _clock = &_node->getClock();
        _clock->setAbsoluteTimeInSeconds(1000000);
        _top = std::make_unique<DummyStorageLink>();
    } catch (config::InvalidConfigException& e) {
        fprintf(stderr, "%s\n", e.what());
    }
    _metricManager = std::make_unique<metrics::MetricManager>(std::make_unique<MetricClock>(*_clock));
    _topSet.reset(new metrics::MetricSet("vds", {}, ""));
    {
        metrics::MetricLockGuard guard(_metricManager->getMetricLock());
        _metricManager->registerMetric(guard, *_topSet);
    }

    _metricsConsumer = std::make_unique<StatusMetricConsumer>(
            _node->getComponentRegister(),
            *_metricManager,
            "status");

    _filestorMetrics = std::make_shared<FileStorMetrics>();
    _filestorMetrics->initDiskMetrics(1, 1);
    _topSet->registerMetric(*_filestorMetrics);

    _bucketManagerMetrics = std::make_shared<BucketManagerMetrics>(_node->getComponentRegister().getBucketSpaceRepo());
    _topSet->registerMetric(*_bucketManagerMetrics);

    _visitorMetrics = std::make_shared<VisitorMetrics>();
    _visitorMetrics->initThreads(4);
    _topSet->registerMetric(*_visitorMetrics);
    _metricManager->init(_config->getConfigId(), _node->getThreadPool());
}

void MetricsTest::TearDown() {
    _metricManager->stop();
    _metricsConsumer.reset();
    _topSet.reset();
    _metricManager.reset();
    _top.reset();
    _node.reset();
    _config.reset();
    _filestorMetrics.reset();
    _bucketManagerMetrics.reset();
    _visitorMetrics.reset();
}

void MetricsTest::createFakeLoad()
{
    _clock->addSecondsToTime(1);
    _metricManager->timeChangedNotification();
    uint32_t n = 5;
    {
        DataStoredMetrics& metrics(*_bucketManagerMetrics->disk);
        metrics.docs.inc(10 * n);
        metrics.bytes.inc(10240 * n);
    }
    _filestorMetrics->directoryEvents.inc(5);
    _filestorMetrics->partitionEvents.inc(4);
    _filestorMetrics->diskEvents.inc(3);
    {
        FileStorDiskMetrics& disk(*_filestorMetrics->disk);
        disk.queueSize.addValue(4 * n);
        disk.averageQueueWaitingTime.addValue(10 * n);
        disk.pendingMerges.addValue(4 * n);
        for (uint32_t j=0; j<disk.threads.size(); ++j) {
            FileStorThreadMetrics& thread(*disk.threads[j]);
            thread.operations.inc(120 * n);
            thread.failedOperations.inc(2 * n);

            thread.put.count.inc(10 * n);
            thread.put.latency.addValue(5 * n);
            thread.get.count.inc(12 * n);
            thread.get.notFound.inc(2 * n);
            thread.get.latency.addValue(3 * n);
            thread.remove.count.inc(6 * n);
            thread.remove.notFound.inc(1 * n);
            thread.remove.latency.addValue(2 * n);
            thread.update.count.inc(2 * n);
            thread.update.notFound.inc(1 * n);
            thread.update.latencyRead.addValue(2 * n);
            thread.update.latency.addValue(7 * n);
            thread.revert.count.inc(2 * n);
            thread.revert.notFound.inc(n / 2);
            thread.revert.latency.addValue(2 * n);
            thread.visit.count.inc(6 * n);

            thread.deleteBuckets.count.inc(1 * n);
            thread.repairs.count.inc(3 * n);
            thread.repairFixed.inc(1 * n);
            thread.splitBuckets.count.inc(20 * n);
            thread.movedBuckets.count.inc(1 * n);
            thread.readBucketInfo.count.inc(2 * n);
            thread.internalJoin.count.inc(3 * n);

            thread.mergeBuckets.count.inc(2 * n);
            thread.getBucketDiff.count.inc(4 * n);
            thread.getBucketDiffReply.inc(4 * n);
            thread.applyBucketDiff.count.inc(4 * n);
            thread.applyBucketDiffReply.inc(4 * n);
            thread.merge_handler_metrics.bytesMerged.inc(1000 * n);
            thread.merge_handler_metrics.mergeLatencyTotal.addValue(300 * n);
            thread.merge_handler_metrics.mergeMetadataReadLatency.addValue(20 * n);
            thread.merge_handler_metrics.mergeDataReadLatency.addValue(40 * n);
            thread.merge_handler_metrics.mergeDataWriteLatency.addValue(50 * n);
            thread.merge_handler_metrics.mergeAverageDataReceivedNeeded.addValue(0.8);
        }
    }
    for (uint32_t i=0; i<_visitorMetrics->threads.size(); ++i) {
        VisitorThreadMetrics& thread(*_visitorMetrics->threads[i]);
        thread.queueSize.addValue(2);
        thread.averageQueueWaitingTime.addValue(10);
        thread.averageVisitorLifeTime.addValue(1000);
        thread.createdVisitors.inc(5 * n);
        thread.abortedVisitors.inc(1 * n);
        thread.completedVisitors.inc(4 * n);
        thread.failedVisitors.inc(2 * n);
    }
    _clock->addSecondsToTime(60);
    _metricManager->timeChangedNotification();
    while (uint64_t(_metricManager->getLastProcessedTime())
                < _clock->getTimeInSeconds().getTime())
    {
        std::this_thread::sleep_for(5ms);
        _metricManager->timeChangedNotification();
    }
}

TEST_F(MetricsTest, filestor_metrics) {
    createFakeLoad();
    std::ostringstream ost;
    framework::HttpUrlPath path("metrics?interval=-1&format=text");
    bool retVal = _metricsConsumer->reportStatus(ost, path);
    ASSERT_TRUE(retVal) << "_metricsConsumer->reportStatus failed";
    std::string s = ost.str();
    EXPECT_THAT(s, HasSubstr("vds.filestor.alldisks.allthreads.get.sum.count count=60"));
    EXPECT_THAT(s, HasSubstr("vds.filestor.alldisks.allthreads.put.sum.count count=50"));
    EXPECT_THAT(s, HasSubstr("vds.filestor.alldisks.allthreads.remove.sum.count count=30"));
    EXPECT_THAT(s, HasSubstr("vds.filestor.alldisks.allthreads.remove.sum.not_found count=5"));
}

#define ASSERT_METRIC(interval, metric, count) \
{ \
    std::ostringstream pathost; \
    pathost << "metrics?interval=" << interval << "&format=text"; \
    std::ostringstream ost;\
    framework::HttpUrlPath path(pathost.str()); \
    bool retVal = _metricsConsumer->reportStatus(ost, path); \
    ASSERT_TRUE(retVal) << "_metricsConsumer->reportStatus failed"; \
    std::string s = ost.str(); \
    if (count == -1) { \
        ASSERT_TRUE(s.find(metric) == std::string::npos) << std::string("Metric ") + metric + " was set"; \
    } else { \
        std::ostringstream valueost; \
        valueost << metric << " count=" << count; \
        ASSERT_TRUE(s.find(valueost.str()) != std::string::npos) \
                << "Did not find value " + valueost.str() + " in metric dump " + s; \
    } \
}

TEST_F(MetricsTest, snapshot_presenting) {
    FileStorDiskMetrics& disk0(*_filestorMetrics->disk);
    FileStorThreadMetrics& thread0(*disk0.threads[0]);

    LOG(debug, "Adding to get metric");

    thread0.get.count.inc(1);

    LOG(debug, "Waiting for 5 minute snapshot to be taken");
    // Wait until active metrics have been added to 5 min snapshot and reset
    for (uint32_t i=0; i<6; ++i) {
        _clock->addSecondsToTime(60);
        _metricManager->timeChangedNotification();
        while (
            uint64_t(_metricManager->getLastProcessedTime())
                    < _clock->getTimeInSeconds().getTime())
        {
            std::this_thread::sleep_for(1ms);
        }
    }
    LOG(debug, "5 minute snapshot should have been taken. Adding put count");

    thread0.put.count.inc(1);

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

TEST_F(MetricsTest, html_metrics_report) {
    createFakeLoad();
    _clock->addSecondsToTime(6 * 60);
    _metricManager->timeChangedNotification();
    createFakeLoad();
    std::ostringstream ost;
    framework::HttpUrlPath path("metrics?interval=300&format=html");
    bool retVal = _metricsConsumer->reportStatus(ost, path);
    ASSERT_TRUE(retVal) << "_metricsConsumer->reportStatus failed";
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
    ASSERT_TRUE(_metricsConsumer->reportStatus(report, uri));
    std::ostringstream expectedSubstr;
    expectedSubstr << " last=" << expected;
    auto str = report.str();
    ASSERT_TRUE(str.find(expectedSubstr.str()) != std::string::npos)
            << "Did not find value " + expectedSubstr.str() + " in metric dump " + str;
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

TEST_F(MetricsTest, current_gauge_values_override_snapshot_values) {
    auto& metrics(*_bucketManagerMetrics->disk);
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

TEST_F(MetricsTest, verbose_report_includes_non_set_metrics_even_after_snapshot) {
    createSnapshotForPeriod(5min);
    // When using verbosity=2 (which is what the system test framework invokes),
    // all metrics should be included regardless of whether they've been set or
    // not. In this case, the bytes gauge metric has not been set explicitly
    // but should be reported as zero.
    assertMetricLastValue("vds.datastored.alldisks.bytes", -1, 0);
}

} // storage
