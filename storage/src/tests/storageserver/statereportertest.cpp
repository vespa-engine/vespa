// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/filestorage/filestormetrics.h>
#include <vespa/storage/storageserver/applicationgenerationfetcher.h>
#include <vespa/storage/storageserver/statereporter.h>
#include <vespa/metrics/metricmanager.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".test.statereporter");

using namespace ::testing;

namespace storage {

class DummyApplicationGenerationFether : public ApplicationGenerationFetcher {
public:
    [[nodiscard]] int64_t getGeneration() const override { return 1; }
    [[nodiscard]] std::string getComponentName() const override { return "component"; }
};

struct StateReporterTest : Test {
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
    ~StateReporterTest() override;

    void SetUp() override;
    void TearDown() override;
};

namespace {

struct MetricClock : public metrics::MetricManager::Timer
{
    framework::Clock& _clock;
    explicit MetricClock(framework::Clock& c) : _clock(c) {}
    [[nodiscard]] time_t getTime() const override { return vespalib::count_s(_clock.getMonotonicTime().time_since_epoch()); }
    [[nodiscard]] time_t getTimeInMilliSecs() const override { return vespalib::count_ms(_clock.getMonotonicTime().time_since_epoch()); }
};

}

StateReporterTest::StateReporterTest()
    : _threadPool(),
      _clock(nullptr),
      _top(),
      _stateReporter()
{
}

StateReporterTest::~StateReporterTest() = default;

void StateReporterTest::SetUp() {
    _config = std::make_unique<vdstestlib::DirConfig>(getStandardConfig(true, "statereportertest"));
    assert(system(("rm -rf " + getRootFolder(*_config)).c_str()) == 0);

    _node = std::make_unique<TestServiceLayerApp>(NodeIndex(0), _config->getConfigId());
    _node->setupDummyPersistence();
    _clock = &_node->getClock();
    _clock->setAbsoluteTimeInSeconds(1000000);
    _top = std::make_unique<DummyStorageLink>();

    _metricManager = std::make_unique<metrics::MetricManager>(std::make_unique<MetricClock>(*_clock));
    _topSet.reset(new metrics::MetricSet("vds", {}, ""));
    {
        metrics::MetricLockGuard guard(_metricManager->getMetricLock());
        _metricManager->registerMetric(guard, *_topSet);
    }

    _stateReporter = std::make_unique<StateReporter>(
            _node->getComponentRegister(),
            *_metricManager,
            _generationFetcher,
            "status");

    _filestorMetrics = std::make_shared<FileStorMetrics>();
    _filestorMetrics->initDiskMetrics(1, 1);
    _topSet->registerMetric(*_filestorMetrics);

    _metricManager->init(config::ConfigUri(_config->getConfigId()));
}

void StateReporterTest::TearDown() {
    _metricManager->stop();
    _stateReporter.reset();
    _topSet.reset();
    _metricManager.reset();
    _top.reset();
    _node.reset();
    _config.reset();
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
        ASSERT_EQ(jsonData.size(), parsed) << "Failed to parse JSON: '\n" \
              << jsonData << "':" << buffer.get().make_string(); \
    } \
}

#define ASSERT_GENERATION(jsonData, component, generation) \
{ \
    PARSE_JSON(jsonData); \
    ASSERT_EQ( \
            generation, \
            slime.get()["config"][component]["generation"].asDouble()); \
}

#define ASSERT_NODE_STATUS(jsonData, code, message) \
{ \
    PARSE_JSON(jsonData); \
    ASSERT_EQ( \
            vespalib::string(code), \
            slime.get()["status"]["code"].asString().make_string()); \
    ASSERT_EQ( \
            vespalib::string(message), \
            slime.get()["status"]["message"].asString().make_string()); \
}

#define ASSERT_METRIC_GET_PUT(jsonData, expGetCount, expPutCount) \
{ \
    PARSE_JSON(jsonData); \
    double getCount = -1; \
    double putCount = -1; \
    size_t metricCount = slime.get()["metrics"]["values"].children(); \
    for (size_t j=0; j<metricCount; j++) { \
        const vespalib::string name = slime.get()["metrics"]["values"][j]["name"] \
                                 .asString().make_string(); \
        if (name.compare("vds.filestor.allthreads.get.count") == 0) \
        { \
            getCount = slime.get()["metrics"]["values"][j]["values"]["count"] \
                       .asDouble(); \
        } else if (name.compare("vds.filestor.allthreads.put.count") == 0) \
        { \
            putCount = slime.get()["metrics"]["values"][j]["values"]["count"] \
                       .asDouble(); \
        } \
    } \
    ASSERT_EQ(expGetCount, getCount); \
    ASSERT_EQ(expPutCount, putCount); \
    ASSERT_GT(metricCount, 100); \
}


TEST_F(StateReporterTest, report_config_generation) {
    std::ostringstream ost;
    framework::HttpUrlPath path("/state/v1/config");
    _stateReporter->reportStatus(ost, path);
    std::string jsonData = ost.str();
    ASSERT_GENERATION(jsonData, "component", 1.0);
}

TEST_F(StateReporterTest, report_health) {
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
        ASSERT_NODE_STATUS(jsonData, codes[i], messages[i]);
    }
}

TEST_F(StateReporterTest, report_metrics) {
    FileStorThreadMetrics& thread0(*_filestorMetrics->threads[0]);

    LOG(debug, "Adding to get metric");

    thread0.get.count.inc(1);

    LOG(debug, "Waiting for 5 minute snapshot to be taken");
    // Wait until active metrics have been added to 5 min snapshot and reset
    for (uint32_t i = 0; i < 6; ++i) {
        _clock->addSecondsToTime(60);
        _metricManager->timeChangedNotification();
        while (int64_t(_metricManager->getLastProcessedTime()) < vespalib::count_s(_clock->getMonotonicTime().time_since_epoch()))
        {
            std::this_thread::sleep_for(1ms);
        }
    }
    LOG(debug, "5 minute snapshot should have been taken. Adding put count");

    thread0.put.count.inc(1);

    const int pathCount = 2;
    const char* paths[pathCount] = {
        "/state/v1/metrics",
        "/state/v1/metrics?consumer=status"
    };

    for (auto & path_str : paths) {
        framework::HttpUrlPath path(path_str);
        std::ostringstream ost;
        _stateReporter->reportStatus(ost, path);
        std::string jsonData = ost.str();
        ASSERT_METRIC_GET_PUT(jsonData, 1.0, 0.0);
    }
 }

} // storage
