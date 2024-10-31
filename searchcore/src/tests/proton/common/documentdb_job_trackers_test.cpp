// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/job_tracked_flush_target.h>
#include <vespa/searchcore/proton/test/dummy_flush_target.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>

using namespace proton;
using namespace searchcorespi;

namespace documentdb_job_trackers_test {

constexpr double EPS = 0.000001;

using FTT = IFlushTarget::Type;
using FTC = IFlushTarget::Component;

struct MFT : public test::DummyFlushTarget
{
    MFT(FTT type, FTC component) noexcept : test::DummyFlushTarget("", type, component) {}
};

struct AttributeFlush : public MFT { AttributeFlush() noexcept : MFT(FTT::SYNC, FTC::ATTRIBUTE) {} };
struct AttributeShrink : public MFT { AttributeShrink() noexcept : MFT(FTT::GC, FTC::ATTRIBUTE) {} };
struct MemoryIndexFlush : public MFT { MemoryIndexFlush() noexcept : MFT(FTT::FLUSH, FTC::INDEX) {} };
struct DiskIndexFusion : public MFT { DiskIndexFusion() noexcept : MFT(FTT::GC, FTC::INDEX) {} };
struct DocStoreFlush : public MFT { DocStoreFlush() noexcept : MFT(FTT::SYNC, FTC::DOCUMENT_STORE) {} };
struct DocStoreCompaction : public MFT { DocStoreCompaction() noexcept : MFT(FTT::GC, FTC::DOCUMENT_STORE) {} };
struct OtherFlush : public MFT { OtherFlush() noexcept : MFT(FTT::FLUSH, FTC::OTHER) {} };

class DocumentDBJobTrackersTest : public ::testing::Test
{
protected:
    DocumentDBJobTrackers _trackers;
    DocumentDBTaggedMetrics::JobMetrics _metrics;
    DocumentDBJobTrackersTest();
    ~DocumentDBJobTrackersTest() override;
};

DocumentDBJobTrackersTest::DocumentDBJobTrackersTest()
    : ::testing::Test(),
      _trackers(),
      _metrics(nullptr)
{
}

DocumentDBJobTrackersTest::~DocumentDBJobTrackersTest() = default;

void
startJobs(IJobTracker &tracker, uint32_t numJobs)
{
    for (uint32_t i = 0; i < numJobs; ++i) {
        tracker.start();
    }
}

TEST_F(DocumentDBJobTrackersTest, require_that_job_metrics_are_updated)
{
    startJobs(_trackers.getAttributeFlush(), 1);
    startJobs(_trackers.getMemoryIndexFlush(), 2);
    startJobs(_trackers.getDiskIndexFusion(), 3);
    startJobs(_trackers.getDocumentStoreFlush(), 4);
    startJobs(_trackers.getDocumentStoreCompact(), 5);
    startJobs(*_trackers.getBucketMove(), 6);
    startJobs(*_trackers.getLidSpaceCompact(), 7);
    startJobs(*_trackers.getRemovedDocumentsPrune(), 8);

    // Update metrics 2 times to ensure that all jobs are running
    // in the last interval we actually care about.
    _trackers.updateMetrics(_metrics);
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    _trackers.updateMetrics(_metrics);

    EXPECT_NEAR(1.0, _metrics.attributeFlush.getLast(), EPS);
    EXPECT_NEAR(2.0, _metrics.memoryIndexFlush.getLast(), EPS);
    EXPECT_NEAR(3.0, _metrics.diskIndexFusion.getLast(), EPS);
    EXPECT_NEAR(4.0, _metrics.documentStoreFlush.getLast(), EPS);
    EXPECT_NEAR(5.0, _metrics.documentStoreCompact.getLast(), EPS);
    EXPECT_NEAR(6.0, _metrics.bucketMove.getLast(), EPS);
    EXPECT_NEAR(7.0, _metrics.lidSpaceCompact.getLast(), EPS);
    EXPECT_NEAR(8.0, _metrics.removedDocumentsPrune.getLast(), EPS);
    EXPECT_NEAR(36.0, _metrics.total.getLast(), EPS);
}

bool
assertFlushTarget(const IJobTracker &tracker, const IFlushTarget &target)
{
    const auto *tracked = dynamic_cast<const JobTrackedFlushTarget *>(&target);
    bool failed = false;
    EXPECT_TRUE(tracked != nullptr) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(&tracker, &tracked->getTracker()) << (failed = true, "");
    return !failed;
}

TEST_F(DocumentDBJobTrackersTest, require_that_known_flush_targets_are_tracked)
{
    IFlushTarget::List input;
    input.emplace_back(std::make_shared<AttributeFlush>());
    input.emplace_back(std::make_shared<MemoryIndexFlush>());
    input.emplace_back(std::make_shared<DiskIndexFusion>());
    input.emplace_back(std::make_shared<DocStoreFlush>());
    input.emplace_back(std::make_shared<DocStoreCompaction>());
    input.emplace_back(std::make_shared<AttributeShrink>());

    IFlushTarget::List output = _trackers.trackFlushTargets(input);
    EXPECT_EQ(6u, output.size());
    EXPECT_TRUE(assertFlushTarget(_trackers.getAttributeFlush(), *output[0]));
    EXPECT_TRUE(assertFlushTarget(_trackers.getMemoryIndexFlush(), *output[1]));
    EXPECT_TRUE(assertFlushTarget(_trackers.getDiskIndexFusion(), *output[2]));
    EXPECT_TRUE(assertFlushTarget(_trackers.getDocumentStoreFlush(), *output[3]));
    EXPECT_TRUE(assertFlushTarget(_trackers.getDocumentStoreCompact(), *output[4]));
    EXPECT_TRUE(assertFlushTarget(_trackers.getAttributeFlush(), *output[5]));
}

TEST_F(DocumentDBJobTrackersTest, require_that_unknown_flush_targets_are_not_tracked)
{
    IFlushTarget::List input;
    input.emplace_back(std::make_shared<OtherFlush>());

    IFlushTarget::List output = _trackers.trackFlushTargets(input);
    EXPECT_EQ(1u, output.size());
    EXPECT_EQ(&*output[0].get(), &*input[0]);
}

}
