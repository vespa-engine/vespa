// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/metrics.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/storage/frameworkimpl/memory/memorystatusviewer.h>
#include <vespa/storageframework/defaultimplementation/memory/prioritymemorylogic.h>
#include <tests/common/teststorageapp.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/exceptions.h>
#include <boost/lexical_cast.hpp>

namespace storage {

struct MemoryStatusViewerTest : public CppUnit::TestFixture
{
    static const int maxMemory = 1000;
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<framework::defaultimplementation::MemoryManager> _memMan;

    void setUp() override;

    void testEmptyState();
    void testSnapshots();

    CPPUNIT_TEST_SUITE(MemoryStatusViewerTest);
    CPPUNIT_TEST(testEmptyState);
    CPPUNIT_TEST(testSnapshots);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemoryStatusViewerTest);

void
MemoryStatusViewerTest::setUp()
{
    _node.reset(new TestServiceLayerApp(DiskCount(2)));
    framework::defaultimplementation::PriorityMemoryLogic* logic(
            new framework::defaultimplementation::PriorityMemoryLogic(
                            _node->getClock(), maxMemory));
    logic->setMinJumpToUpdateMax(1);
    _memMan.reset(new framework::defaultimplementation::MemoryManager(
            framework::defaultimplementation::AllocationLogic::UP(logic)));
}

void
MemoryStatusViewerTest::testEmptyState()
{
        // Add a memory manager, and add a bit of load to it, so it's not
        // totally empty.
    StorageComponent component(_node->getComponentRegister(), "test");

    metrics::MetricManager mm;
    MemoryStatusViewer viewer(
            *_memMan, mm, _node->getComponentRegister());
    std::ostringstream actual;
    viewer.reportStatus(actual, framework::HttpUrlPath("/"));
    CPPUNIT_ASSERT_MATCH_REGEX(".*Plotr.LineChart.*", actual.str());
    CPPUNIT_ASSERT_MATCH_REGEX(
            ".*Current: 1970-01-01 00:00:00 Max memory 1000 SnapShot\\(Used 0, w/o cache 0\\).*",
            actual.str());
    CPPUNIT_ASSERT_MATCH_REGEX(
            ".*Last hour: na.*", actual.str());
}

namespace {
    void waitForProcessedTime(
            const MemoryStatusViewer& viewer, framework::SecondTime time,
            framework::SecondTime timeout = framework::SecondTime(30))
    {
        framework::defaultimplementation::RealClock clock;
        framework::MilliSecTime endTime(
                clock.getTimeInMillis() + timeout.getMillis());
        framework::SecondTime processedTime(0);
        while (clock.getTimeInMillis() < endTime) {
            processedTime = viewer.getProcessedTime();
            if (processedTime >= time) return;
            FastOS_Thread::Sleep(1);
        }
        std::ostringstream ost;
        ost << "Timed out waiting " << timeout << " ms for time " << time
            << " to be processed. Currently time is only processed up to "
            << processedTime;
        throw new vespalib::IllegalStateException(ost.str(), VESPA_STRLOC);
    }
}

#define ASSERT_MEMORY(output, period, maxmem, used, usedwocache) \
{ \
    std::string::size_type _pos1_(output.find(period)); \
    std::string::size_type _pos2_(output.find("Max memory", _pos1_)); \
    std::string::size_type _pos3_(output.find("SnapShot", _pos2_)); \
    std::string _maxMemory_(output.substr(_pos2_ + 11, _pos3_ - _pos2_ - 12)); \
    std::string::size_type _pos4_(output.find(",", _pos3_)); \
    std::string _used_(output.substr(_pos3_ + 14, _pos4_ - _pos3_ - 14)); \
    std::string::size_type _pos5_(output.find(")", _pos4_)); \
    std::string _usedwo_(output.substr(_pos4_ + 12, _pos5_ - _pos4_ - 12)); \
    std::ostringstream _failure_; \
    _failure_ << "Wrong match in period " << period << " in output:\n" \
              << output << "\nFor value: "; \
 \
    CPPUNIT_ASSERT_EQUAL_MSG(_failure_.str() + "Max memory", \
            uint64_t(maxmem), boost::lexical_cast<uint64_t>(_maxMemory_)); \
    CPPUNIT_ASSERT_EQUAL_MSG(_failure_.str() + "Used memory", \
            uint64_t(used), boost::lexical_cast<uint64_t>(_used_)); \
    CPPUNIT_ASSERT_EQUAL_MSG(_failure_.str() + "Used memory w/o cache", \
            uint64_t(usedwocache), boost::lexical_cast<uint64_t>(_usedwo_)); \
}

void
MemoryStatusViewerTest::testSnapshots()
{
        // Add a memory manager, and add a bit of load to it, so it's not
        // totally empty.
    StorageComponent component(_node->getComponentRegister(), "test");
    const framework::MemoryAllocationType putAlloc(
            component.getMemoryManager().registerAllocationType(
                framework::MemoryAllocationType("PUT")));
    const framework::MemoryAllocationType getAlloc(
            component.getMemoryManager().registerAllocationType(
                framework::MemoryAllocationType("GET")));

    framework::MemoryToken::UP put = _memMan->allocate(putAlloc, 0, 100, 80);
    framework::MemoryToken::UP get = _memMan->allocate(getAlloc, 30, 200, 50);
    framework::MemoryToken::UP get2 = _memMan->allocate(getAlloc, 70, 150, 60);

    metrics::MetricManager mm;
    MemoryStatusViewer viewer(*_memMan, mm, _node->getComponentRegister());

    _node->getClock().addSecondsToTime(1000);
    viewer.notifyThread();
    waitForProcessedTime(viewer, framework::SecondTime(1000));

    std::ostringstream actual;
    viewer.printDebugOutput(actual);
    //std::cerr << actual.str() << "\n";
    ASSERT_MEMORY(actual.str(), "Current", 1000, 450, 450);
    ASSERT_MEMORY(actual.str(), "Last hour", 1000, 450, 450);
    ASSERT_MEMORY(actual.str(), "Last ever", 1000, 450, 450);

    put = _memMan->allocate(putAlloc, 0, 50, 80);
    get = _memMan->allocate(getAlloc, 100, 140, 50);
    get2 = _memMan->allocate(getAlloc, 20, 100, 70);

    _node->getClock().addSecondsToTime(3600);
    viewer.notifyThread();
    waitForProcessedTime(viewer, framework::SecondTime(4600));

    actual.str("");
    viewer.printDebugOutput(actual);
    //std::cerr << actual.str() << "\n";
    ASSERT_MEMORY(actual.str(), "Current", 1000, 290, 290);
    ASSERT_MEMORY(actual.str(), "Last hour", 1000, 540, 540);
    ASSERT_MEMORY(actual.str(), "Last ever", 1000, 540, 540);

    get.reset();

    _node->getClock().addSecondsToTime(3600);
    viewer.notifyThread();
    waitForProcessedTime(viewer, framework::SecondTime(4600 + 3600));

    actual.str("");
    viewer.printDebugOutput(actual);
    //std::cerr << actual.str() << "\n";
    ASSERT_MEMORY(actual.str(), "Current", 1000, 150, 150);
    ASSERT_MEMORY(actual.str(), "Last hour", 1000, 290, 290);
    ASSERT_MEMORY(actual.str(), "Last ever", 1000, 540, 540);

}

} // storage
