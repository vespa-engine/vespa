// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/storageserver/applicationgenerationfetcher.h>
#include <vespa/storage/storageserver/statereporter.h>
#include <vespa/metrics/metricmanager.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP(".test.statereporter");

namespace storage {

class DummyApplicationGenerationFether : public ApplicationGenerationFetcher {
public:
    int64_t getGeneration() const override { return 1; }
    std::string getComponentName() const override { return "component"; }
};

struct StateReporterTest : public CppUnit::TestFixture {
    FastOS_ThreadPool _threadPool;
    framework::defaultimplementation::FakeClock* _clock;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    DummyApplicationGenerationFether _generationFetcher;
    std::unique_ptr<StateReporter> _stateReporter;
    std::unique_ptr<vdstestlib::DirConfig> _config;
    std::unique_ptr<metrics::MetricSet> _topSet;
    std::unique_ptr<metrics::MetricManager> _metricManager;
    std::shared_ptr<FileStorMetrics> _filestorMetrics;

    StateReporterTest();

    void setUp() override;
    void tearDown() override;
    void runLoad(uint32_t count = 1);

    void testReportConfigGeneration();
    void testReportHealth();
    void testReportMetrics();

    CPPUNIT_TEST_SUITE(StateReporterTest);
    CPPUNIT_TEST(testReportConfigGeneration);
    CPPUNIT_TEST(testReportHealth);
    CPPUNIT_TEST(testReportMetrics);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StateReporterTest);

namespace {
    struct MetricClock : public metrics::MetricManager::Timer
    {
        framework::Clock& _clock;
        MetricClock(framework::Clock& c) : _clock(c) {}
        time_t getTime() const override { return _clock.getTimeInSeconds().getTime(); }
        time_t getTimeInMilliSecs() const override { return _clock.getTimeInMillis().getTime(); }
    };
}

StateReporterTest::StateReporterTest()
    : _threadPool(256*1024),
      _clock(0),
      _top(),
      _stateReporter()
{
}

void StateReporterTest::setUp() {
    _config.reset(new vdstestlib::DirConfig(getStandardConfig(true, "statereportertest")));
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

    _stateReporter.reset(new StateReporter(
            _node->getComponentRegister(),
            *_metricManager,
            _generationFetcher,
            "status"));

    uint16_t diskCount = _node->getPartitions().size();
    documentapi::LoadTypeSet::SP loadTypes(_node->getLoadTypes());

    _filestorMetrics.reset(new FileStorMetrics(_node->getLoadTypes()->getMetricLoadTypes()));
    _filestorMetrics->initDiskMetrics(diskCount, loadTypes->getMetricLoadTypes(), 1, 1);
    _topSet->registerMetric(*_filestorMetrics);

    _metricManager->init(_config->getConfigId(), _node->getThreadPool());
}

void StateReporterTest::tearDown() {
    _metricManager->stop();
    _stateReporter.reset(0);
    _topSet.reset(0);
    _metricManager.reset(0);
    _top.reset(0);
    _node.reset(0);
    _config.reset(0);
    _filestorMetrics.reset();
}

#define PARSE_JSON(jsonData) \
vespalib::Slime slime; \
{ \
    using namespace vespalib::slime; \
    size_t parsed = JsonFormat::decode(vespalib::Memory(jsonData), slime); \
    vespalib::SimpleBuffer buffer;                                      \
    JsonFormat::encode(slime, buffer, false); \
    if (parsed == 0) { \
        std::ostringstream error; \
        error << "Failed to parse JSON: '\n" \
              << jsonData << "'\n:" << buffer.get().make_string() << "\n"; \
        CPPUNIT_ASSERT_EQUAL_MSG(error.str(), jsonData.size(), parsed); \
    } \
}

#define ASSERT_GENERATION(jsonData, component, generation) \
{ \
    PARSE_JSON(jsonData); \
    CPPUNIT_ASSERT_EQUAL( \
            generation, \
            slime.get()["config"][component]["generation"].asDouble()); \
}

#define ASSERT_NODE_STATUS(jsonData, code, message) \
{ \
    PARSE_JSON(jsonData); \
    CPPUNIT_ASSERT_EQUAL( \
            vespalib::string(code), \
            slime.get()["status"]["code"].asString().make_string()); \
    CPPUNIT_ASSERT_EQUAL( \
            vespalib::string(message), \
            slime.get()["status"]["message"].asString().make_string()); \
}

#define ASSERT_METRIC_GET_PUT(jsonData, expGetCount, expPutCount) \
{ \
    PARSE_JSON(jsonData); \
    double getCount = -1; \
    double putCount = -1; \
    size_t metricCount = slime.get()["metrics"]["values"].children(); \
    /*std::cerr << "\nmetric count=" << metricCount << "\n";*/ \
    for (size_t j=0; j<metricCount; j++) { \
        const vespalib::string name = slime.get()["metrics"]["values"][j]["name"] \
                                 .asString().make_string(); \
        if (name.compare("vds.filestor.alldisks.allthreads." \
                         "get.sum.count") == 0) \
        { \
            getCount = slime.get()["metrics"]["values"][j]["values"]["count"] \
                       .asDouble(); \
        } else if (name.compare("vds.filestor.alldisks.allthreads." \
                                "put.sum.count") == 0) \
        { \
            putCount = slime.get()["metrics"]["values"][j]["values"]["count"] \
                       .asDouble(); \
        } \
    } \
    CPPUNIT_ASSERT_EQUAL(expGetCount, getCount); \
    CPPUNIT_ASSERT_EQUAL(expPutCount, putCount); \
    CPPUNIT_ASSERT(metricCount > 100); \
}


void StateReporterTest::testReportConfigGeneration() {
    std::ostringstream ost;
    framework::HttpUrlPath path("/state/v1/config");
    _stateReporter->reportStatus(ost, path);
    std::string jsonData = ost.str();
    //std::cerr << "\nConfig: " << jsonData << "\n";
    ASSERT_GENERATION(jsonData, "component", 1.0);
}

void StateReporterTest::testReportHealth() {
    const int stateCount = 7;
    const lib::NodeState nodeStates[stateCount] = {
        lib::NodeState(lib::NodeType::STORAGE, lib::State::UNKNOWN),
        lib::NodeState(lib::NodeType::STORAGE, lib::State::MAINTENANCE),
        lib::NodeState(lib::NodeType::STORAGE, lib::State::DOWN),
        lib::NodeState(lib::NodeType::STORAGE, lib::State::STOPPING),
        lib::NodeState(lib::NodeType::STORAGE, lib::State::INITIALIZING),
        lib::NodeState(lib::NodeType::STORAGE, lib::State::RETIRED),
        lib::NodeState(lib::NodeType::STORAGE, lib::State::UP)
    };
    const char* codes[stateCount] = {
        "down",
        "down",
        "down",
        "down",
        "down",
        "down",
        "up"
    };
    const char* messages[stateCount] = {
        "Node state: Unknown",
        "Node state: Maintenance",
        "Node state: Down",
        "Node state: Stopping",
        "Node state: Initializing, init progress 0",
        "Node state: Retired",
        ""
    };

    framework::HttpUrlPath path("/state/v1/health");
    for (int i=0; i<stateCount; i++) {
        _node->getStateUpdater().setCurrentNodeState(nodeStates[i]);
        std::ostringstream ost;
        _stateReporter->reportStatus(ost, path);
        std::string jsonData = ost.str();
        //std::cerr << "\nHealth " << i << ":" << jsonData << "\n";
        ASSERT_NODE_STATUS(jsonData, codes[i], messages[i]);
    }
}

void StateReporterTest::testReportMetrics() {
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

    const int pathCount = 2;
    const char* paths[pathCount] = {
        "/state/v1/metrics",
        "/state/v1/metrics?consumer=status"
    };

    for (int i=0; i<pathCount; i++) {
        framework::HttpUrlPath path(paths[i]);
        std::ostringstream ost;
        _stateReporter->reportStatus(ost, path);
        std::string jsonData = ost.str();
        //std::cerr << "\nMetrics:" << jsonData << "\n";
        ASSERT_METRIC_GET_PUT(jsonData, 1.0, 0.0);
    }
 }

} // storage
