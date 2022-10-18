// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/job_tracked_flush_target.h>
#include <vespa/searchcore/proton/test/dummy_flush_target.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("documentdb_job_trackers_test");

using namespace proton;
using namespace searchcorespi;

constexpr double EPS = 0.000001;

typedef IFlushTarget::Type FTT;
typedef IFlushTarget::Component FTC;

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

struct Fixture
{
    DocumentDBJobTrackers _trackers;
    DocumentDBTaggedMetrics::JobMetrics _metrics;
    Fixture()
        : _trackers(),
          _metrics(nullptr)
    {
    }
};

void
startJobs(IJobTracker &tracker, uint32_t numJobs)
{
    for (uint32_t i = 0; i < numJobs; ++i) {
        tracker.start();
    }
}

TEST_F("require that job metrics are updated", Fixture)
{
    startJobs(f._trackers.getAttributeFlush(), 1);
    startJobs(f._trackers.getMemoryIndexFlush(), 2);
    startJobs(f._trackers.getDiskIndexFusion(), 3);
    startJobs(f._trackers.getDocumentStoreFlush(), 4);
    startJobs(f._trackers.getDocumentStoreCompact(), 5);
    startJobs(*f._trackers.getBucketMove(), 6);
    startJobs(*f._trackers.getLidSpaceCompact(), 7);
    startJobs(*f._trackers.getRemovedDocumentsPrune(), 8);

    // Update metrics 2 times to ensure that all jobs are running
    // in the last interval we actually care about.
    f._trackers.updateMetrics(f._metrics);
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    f._trackers.updateMetrics(f._metrics);

    EXPECT_APPROX(1.0, f._metrics.attributeFlush.getLast(), EPS);
    EXPECT_APPROX(2.0, f._metrics.memoryIndexFlush.getLast(), EPS);
    EXPECT_APPROX(3.0, f._metrics.diskIndexFusion.getLast(), EPS);
    EXPECT_APPROX(4.0, f._metrics.documentStoreFlush.getLast(), EPS);
    EXPECT_APPROX(5.0, f._metrics.documentStoreCompact.getLast(), EPS);
    EXPECT_APPROX(6.0, f._metrics.bucketMove.getLast(), EPS);
    EXPECT_APPROX(7.0, f._metrics.lidSpaceCompact.getLast(), EPS);
    EXPECT_APPROX(8.0, f._metrics.removedDocumentsPrune.getLast(), EPS);
    EXPECT_APPROX(36.0, f._metrics.total.getLast(), EPS);
}

bool
assertFlushTarget(const IJobTracker &tracker, const IFlushTarget &target)
{
    const auto *tracked = dynamic_cast<const JobTrackedFlushTarget *>(&target);
    if (!EXPECT_TRUE(tracked != nullptr)) return false;
    if (!EXPECT_EQUAL(&tracker, &tracked->getTracker())) return false;
    return true;
}

TEST_F("require that known flush targets are tracked", Fixture)
{
    IFlushTarget::List input;
    input.emplace_back(std::make_shared<AttributeFlush>());
    input.emplace_back(std::make_shared<MemoryIndexFlush>());
    input.emplace_back(std::make_shared<DiskIndexFusion>());
    input.emplace_back(std::make_shared<DocStoreFlush>());
    input.emplace_back(std::make_shared<DocStoreCompaction>());
    input.emplace_back(std::make_shared<AttributeShrink>());

    IFlushTarget::List output = f._trackers.trackFlushTargets(input);
    EXPECT_EQUAL(6u, output.size());
    EXPECT_TRUE(assertFlushTarget(f._trackers.getAttributeFlush(), *output[0]));
    EXPECT_TRUE(assertFlushTarget(f._trackers.getMemoryIndexFlush(), *output[1]));
    EXPECT_TRUE(assertFlushTarget(f._trackers.getDiskIndexFusion(), *output[2]));
    EXPECT_TRUE(assertFlushTarget(f._trackers.getDocumentStoreFlush(), *output[3]));
    EXPECT_TRUE(assertFlushTarget(f._trackers.getDocumentStoreCompact(), *output[4]));
    EXPECT_TRUE(assertFlushTarget(f._trackers.getAttributeFlush(), *output[5]));
}

TEST_F("require that un-known flush targets are not tracked", Fixture)
{
    IFlushTarget::List input;
    input.emplace_back(std::make_shared<OtherFlush>());

    IFlushTarget::List output = f._trackers.trackFlushTargets(input);
    EXPECT_EQUAL(1u, output.size());
    EXPECT_EQUAL(&*output[0].get(), &*input[0]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
