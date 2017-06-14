// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/maintenance/simplemaintenancescanner.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <tests/distributor/maintenancemocks.h>
#include <vespa/vespalib/text/stringtokenizer.h>

namespace storage {

namespace distributor {

using document::BucketId;
typedef MaintenancePriority Priority;

class SimpleMaintenanceScannerTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(SimpleMaintenanceScannerTest);
    CPPUNIT_TEST(testPrioritizeSingleBucket);
    CPPUNIT_TEST(testPrioritizeMultipleBuckets);
    CPPUNIT_TEST(testPendingMaintenanceOperationStatistics);
    CPPUNIT_TEST(perNodeMaintenanceStatsAreTracked);
    CPPUNIT_TEST(testReset);
    CPPUNIT_TEST_SUITE_END();

    using PendingStats = SimpleMaintenanceScanner::PendingMaintenanceStats;

    std::string dumpPriorityDbToString(const BucketPriorityDatabase&) const;

    std::unique_ptr<MockMaintenancePriorityGenerator> _priorityGenerator;
    std::unique_ptr<MapBucketDatabase> _bucketDb;
    std::unique_ptr<SimpleBucketPriorityDatabase> _priorityDb;
    std::unique_ptr<SimpleMaintenanceScanner> _scanner;

    void addBucketToDb(int bucketNum);

    bool scanEntireDatabase(int expected);

    std::string stringifyGlobalPendingStats(const PendingStats&) const;

public:
    void testPrioritizeSingleBucket();
    void testPrioritizeMultipleBuckets();
    void testPendingMaintenanceOperationStatistics();
    void perNodeMaintenanceStatsAreTracked();
    void testReset();

    void setUp() override;
};

CPPUNIT_TEST_SUITE_REGISTRATION(SimpleMaintenanceScannerTest);

void
SimpleMaintenanceScannerTest::setUp()
{
    _priorityGenerator.reset(new MockMaintenancePriorityGenerator());
    _bucketDb.reset(new MapBucketDatabase());
    _priorityDb.reset(new SimpleBucketPriorityDatabase());
    _scanner.reset(new SimpleMaintenanceScanner(*_priorityDb, *_priorityGenerator, *_bucketDb));
}

void
SimpleMaintenanceScannerTest::addBucketToDb(int bucketNum)
{
    BucketDatabase::Entry entry(BucketId(16, bucketNum), BucketInfo());
    _bucketDb->update(entry);
}

std::string
SimpleMaintenanceScannerTest::stringifyGlobalPendingStats(
        const PendingStats& stats) const
{
    std::ostringstream ss;
    ss << stats.global;
    return ss.str();
}

void
SimpleMaintenanceScannerTest::testPrioritizeSingleBucket()
{
    addBucketToDb(1);
    std::string expected("PrioritizedBucket(BucketId(0x4000000000000001), pri VERY_HIGH)\n");

    CPPUNIT_ASSERT(!_scanner->scanNext().isDone());
    CPPUNIT_ASSERT_EQUAL(expected, _priorityDb->toString());

    CPPUNIT_ASSERT(_scanner->scanNext().isDone());
    CPPUNIT_ASSERT_EQUAL(expected, _priorityDb->toString());
}

namespace {
    std::string sortLines(const std::string& source) {
        vespalib::StringTokenizer st(source,"\n","");
        std::vector<std::string> lines;
        std::copy(st.begin(), st.end(), std::back_inserter(lines));
        std::sort(lines.begin(), lines.end());
        std::ostringstream ost;
        for (auto& line : lines) {
            ost << line << "\n";
        }
        return ost.str();
    }
}

void
SimpleMaintenanceScannerTest::testPrioritizeMultipleBuckets()
{
    addBucketToDb(1);
    addBucketToDb(2);
    addBucketToDb(3);
    std::string expected("PrioritizedBucket(BucketId(0x4000000000000001), pri VERY_HIGH)\n"
                         "PrioritizedBucket(BucketId(0x4000000000000002), pri VERY_HIGH)\n"
                         "PrioritizedBucket(BucketId(0x4000000000000003), pri VERY_HIGH)\n");

    CPPUNIT_ASSERT(scanEntireDatabase(3));
    CPPUNIT_ASSERT_EQUAL(sortLines(expected),
                         sortLines(_priorityDb->toString()));
}

bool
SimpleMaintenanceScannerTest::scanEntireDatabase(int expected)
{
    for (int i = 0; i < expected; ++i) {
        if (_scanner->scanNext().isDone()) {
            return false;
        }
    }
    return _scanner->scanNext().isDone();
}

void
SimpleMaintenanceScannerTest::testReset()
{
    addBucketToDb(1);
    addBucketToDb(3);

    CPPUNIT_ASSERT(scanEntireDatabase(2));
    std::string expected("PrioritizedBucket(BucketId(0x4000000000000001), pri VERY_HIGH)\n"
                         "PrioritizedBucket(BucketId(0x4000000000000003), pri VERY_HIGH)\n");
    CPPUNIT_ASSERT_EQUAL(expected, _priorityDb->toString());

    addBucketToDb(2);
    CPPUNIT_ASSERT(scanEntireDatabase(0));
    CPPUNIT_ASSERT_EQUAL(expected, _priorityDb->toString());

    _scanner->reset();
    CPPUNIT_ASSERT(scanEntireDatabase(3));

    expected = "PrioritizedBucket(BucketId(0x4000000000000001), pri VERY_HIGH)\n"
               "PrioritizedBucket(BucketId(0x4000000000000002), pri VERY_HIGH)\n"
               "PrioritizedBucket(BucketId(0x4000000000000003), pri VERY_HIGH)\n";
    CPPUNIT_ASSERT_EQUAL(sortLines(expected), sortLines(_priorityDb->toString()));
}

void
SimpleMaintenanceScannerTest::testPendingMaintenanceOperationStatistics()
{
    addBucketToDb(1);
    addBucketToDb(3);

    std::string expectedEmpty("delete bucket: 0, merge bucket: 0, "
                              "split bucket: 0, join bucket: 0, "
                              "set bucket state: 0, garbage collection: 0");
    {
        auto stats(_scanner->getPendingMaintenanceStats());
        CPPUNIT_ASSERT_EQUAL(expectedEmpty, stringifyGlobalPendingStats(stats));
    }

    CPPUNIT_ASSERT(scanEntireDatabase(2));

    // All mock operations generated have the merge type.
    {
        auto stats(_scanner->getPendingMaintenanceStats());
        std::string expected("delete bucket: 0, merge bucket: 2, "
                             "split bucket: 0, join bucket: 0, "
                             "set bucket state: 0, garbage collection: 0");
        CPPUNIT_ASSERT_EQUAL(expected, stringifyGlobalPendingStats(stats));
    }

    _scanner->reset();
    {
        auto stats(_scanner->getPendingMaintenanceStats());
        CPPUNIT_ASSERT_EQUAL(expectedEmpty, stringifyGlobalPendingStats(stats));
    }
}

void
SimpleMaintenanceScannerTest::perNodeMaintenanceStatsAreTracked()
{
    addBucketToDb(1);
    addBucketToDb(3);
    {
        auto stats(_scanner->getPendingMaintenanceStats());
        NodeMaintenanceStats emptyStats;
        CPPUNIT_ASSERT_EQUAL(emptyStats, stats.perNodeStats.forNode(0));
    }
    CPPUNIT_ASSERT(scanEntireDatabase(2));
    // Mock is currently hardwired to increment movingOut for node 1 and
    // copyingIn for node 2 per bucket iterated (we've got 2).
    auto stats(_scanner->getPendingMaintenanceStats());
    {
        NodeMaintenanceStats wantedNode1Stats;
        wantedNode1Stats.movingOut = 2;
        CPPUNIT_ASSERT_EQUAL(wantedNode1Stats, stats.perNodeStats.forNode(1));
    }
    {
        NodeMaintenanceStats wantedNode2Stats;
        wantedNode2Stats.copyingIn = 2;
        CPPUNIT_ASSERT_EQUAL(wantedNode2Stats, stats.perNodeStats.forNode(2));
    }
}

}
}
