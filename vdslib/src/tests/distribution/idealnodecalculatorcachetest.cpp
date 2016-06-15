// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/idealnodecalculatorcache.h>
#include <vespa/vdslib/distribution/idealnodecalculatorimpl.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace lib {

struct IdealNodeCalculatorCacheTest : public CppUnit::TestFixture {

    /** Test that you get a correct result forwarded through simple. */
    void testSimple();
    /** Test that similar buckets use different cache slots. */
    void testLocalityCached();
    /** Test that buckets using same cache slot invalidates each other. */
    void testBucketsSameCacheSlot();
    /** Test that cache is invalidated on changes. */
    void testCacheInvalidationOnChanges();
    /** Test that values for different upstates are kept for themselves. */
    void testDifferentUpStates();
    /** Test that values for different node types are kept for themselves. */
    void testDifferentNodeTypes();
    /**
     * Do a performance test, verifying that cache actually significantly
     * increase performance.
     */
    void testPerformance();

    CPPUNIT_TEST_SUITE(IdealNodeCalculatorCacheTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testLocalityCached);
    CPPUNIT_TEST(testBucketsSameCacheSlot);
    CPPUNIT_TEST(testCacheInvalidationOnChanges);
    CPPUNIT_TEST(testDifferentUpStates);
    CPPUNIT_TEST(testDifferentNodeTypes);
    CPPUNIT_TEST(testPerformance);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(IdealNodeCalculatorCacheTest);

void
IdealNodeCalculatorCacheTest::testSimple()
{
    ClusterState state("storage:10");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    IdealNodeCalculatorCache cache(impl, 4);

    IdealNodeCalculatorConfigurable& configurable(cache);
    IdealNodeCalculator& calc(cache);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

    std::string expected("[storage.8, storage.9, storage.6]");
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());
}

void
IdealNodeCalculatorCacheTest::testLocalityCached()
{
    ClusterState state("bits:6 storage:10");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    IdealNodeCalculatorCache cache(impl, 1024);

    IdealNodeCalculatorConfigurable& configurable(cache);
    IdealNodeCalculator& calc(cache);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

    std::vector<document::BucketId> local;
    local.push_back(document::BucketId(15, 134));
    local.push_back(document::BucketId(16, 134));
    local.push_back(document::BucketId(17, 134));
    local.push_back(document::BucketId(17, 134 | (1 << 16)));

    for (uint32_t i=0; i<local.size(); ++i) {
        calc.getIdealStorageNodes(local[i]);
    }

    CPPUNIT_ASSERT_EQUAL(4u, cache.getMissCount());
    CPPUNIT_ASSERT_EQUAL(0u, cache.getHitCount());

    for (uint32_t i=0; i<local.size(); ++i) {
        calc.getIdealStorageNodes(local[i]);
    }

    CPPUNIT_ASSERT_EQUAL(4u, cache.getMissCount());
    CPPUNIT_ASSERT_EQUAL(4u, cache.getHitCount());
}

void
IdealNodeCalculatorCacheTest::testBucketsSameCacheSlot()
{
    ClusterState state("bits:6 storage:10");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    IdealNodeCalculatorCache cache(impl, 1); // Only one slot available

    IdealNodeCalculatorConfigurable& configurable(cache);
    IdealNodeCalculator& calc(cache);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

        // See that you don't get same result as last one
    std::string expected("[storage.8, storage.9, storage.6]");
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());
    expected = "[storage.8, storage.6, storage.1]";
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 6)).toString());
}

void
IdealNodeCalculatorCacheTest::testCacheInvalidationOnChanges()
{
    ClusterState state("bits:6 storage:10");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    IdealNodeCalculatorCache cache(impl, 1); // Only one slot available

    IdealNodeCalculatorConfigurable& configurable(cache);
    IdealNodeCalculator& calc(cache);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

        // See that you don't get same result as last one
    std::string expected("[storage.8, storage.9, storage.6]");
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());

    CPPUNIT_ASSERT_EQUAL(1u, cache.getMissCount());
    CPPUNIT_ASSERT_EQUAL(0u, cache.getHitCount());

    configurable.setClusterState(state);

    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());

    CPPUNIT_ASSERT_EQUAL(2u, cache.getMissCount());
    CPPUNIT_ASSERT_EQUAL(0u, cache.getHitCount());

    configurable.setDistribution(distr);

    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());

    CPPUNIT_ASSERT_EQUAL(3u, cache.getMissCount());
    CPPUNIT_ASSERT_EQUAL(0u, cache.getHitCount());
}

void
IdealNodeCalculatorCacheTest::testDifferentUpStates()
{
    ClusterState state("bits:6 storage:10 .6.s:m .8.s:r");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    IdealNodeCalculatorCache cache(impl, 4);

    IdealNodeCalculatorConfigurable& configurable(cache);
    IdealNodeCalculator& calc(cache);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

    std::string expected("[storage.9, storage.4, storage.1]");
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5),
                                      IdealNodeCalculator::UpInit).toString());

    expected = "[storage.9, storage.6, storage.4]";
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(
                    document::BucketId(16, 5),
                    IdealNodeCalculator::UpInitMaintenance).toString());
}

void
IdealNodeCalculatorCacheTest::testDifferentNodeTypes()
{
    ClusterState state("bits:6 distributor:10 storage:10 .6.s:m .8.s:r");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 10));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    IdealNodeCalculatorCache cache(impl, 4);

    IdealNodeCalculatorConfigurable& configurable(cache);
    IdealNodeCalculator& calc(cache);
    configurable.setDistribution(distr);
    configurable.setClusterState(state);

    std::string expected("[storage.9, storage.4, storage.1]");
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealStorageNodes(document::BucketId(16, 5)).toString());

    expected = "[distributor.8]";
    CPPUNIT_ASSERT_EQUAL(
            expected,
            calc.getIdealDistributorNodes(
                    document::BucketId(16, 5)).toString());
}

namespace {

    uint64_t getCurrentTimeInMicros() {
        struct timeval mytime;
        gettimeofday(&mytime, 0);
        return mytime.tv_sec * 1000000llu + mytime.tv_usec;
    }

    void addBucketTree(std::vector<document::BucketId>& v,
                       uint64_t location,
                       uint32_t currentUsedBits,
                       uint32_t maxUsedBits)
    {
        document::BucketId id(currentUsedBits, location);
        v.push_back(id);
        if (currentUsedBits < maxUsedBits) {
            addBucketTree(v, location,
                          currentUsedBits + 1, maxUsedBits);
            addBucketTree(v, location | (uint64_t(1) << currentUsedBits),
                          currentUsedBits + 1, maxUsedBits);
        }
    }

    uint64_t runPerformanceTest(IdealNodeCalculator& calc) {
        std::vector<document::BucketId> buckets;

            //  Addvarious location split levels for a user
        addBucketTree(buckets, 123, 20, 22);
            // Add various gid bit split levels for a user
        addBucketTree(buckets, 123, 40, 42);
            
        {
            std::set<document::BucketId> uniqueBuckets;
            for (uint32_t i=0; i<buckets.size(); ++i) {
                uniqueBuckets.insert(buckets[i]);
                calc.getIdealStorageNodes(buckets[i]);
            }
            CPPUNIT_ASSERT_EQUAL(buckets.size(), uniqueBuckets.size());
            CPPUNIT_ASSERT_EQUAL(size_t(14), buckets.size());
        }
        IdealNodeCalculatorCache* cache(dynamic_cast<IdealNodeCalculatorCache*>(
                &calc));
        if (cache != 0) cache->clearCounts();
        uint32_t value;
        uint64_t start = getCurrentTimeInMicros();
        for (uint32_t j=0; j<1024; ++j) {
            for (uint32_t i=0; i<buckets.size(); ++i) {
                IdealNodeList result(calc.getIdealStorageNodes(buckets[i]));
                value += (result[0].getIndex() + result[1].getIndex())
                         / result[2].getIndex();
            }
        }
        uint64_t stop = getCurrentTimeInMicros();
        return (stop - start);
    }

    struct MapIdealNodeCalculator : public IdealNodeCalculator {
        mutable std::map<document::BucketId, IdealNodeList> values;
        const IdealNodeCalculator& calc;

        MapIdealNodeCalculator(const IdealNodeCalculator& c) : calc(c) {}

        virtual IdealNodeList getIdealNodes(const NodeType& nodeType,
                                            const document::BucketId& bucketId,
                                            UpStates upStates) const
        {
            std::map<document::BucketId, IdealNodeList>::const_iterator it(
                    values.find(bucketId));
            if (it != values.end()) return it->second;
            IdealNodeList result(
                    calc.getIdealNodes(nodeType, bucketId, upStates));
            values[bucketId] = result;
            return result;
        }
    };
}

void
IdealNodeCalculatorCacheTest::testPerformance()
{
    ClusterState state("bits:18 distributor:100 storage:100 .6.s:m .8.s:r");
    Distribution distr(Distribution::getDefaultDistributionConfig(3, 100));
    IdealNodeCalculatorImpl::SP impl(new IdealNodeCalculatorImpl);
    impl->setDistribution(distr);
    impl->setClusterState(state);

    uint64_t rawPerformance = runPerformanceTest(*impl);

    IdealNodeCalculatorCache cache(impl, 14);

    uint64_t cachePerformance = runPerformanceTest(cache);
    double hitrate = (100.0 * cache.getHitCount()
                      / (cache.getHitCount() + cache.getMissCount()));
    CPPUNIT_ASSERT(hitrate > 99.99);

    MapIdealNodeCalculator mapCalc(*impl);

    uint64_t mapPerformance = runPerformanceTest(mapCalc);

    IdealNodeCalculatorCache cache2(impl, 13);
    uint64_t cacheMissPerformance = runPerformanceTest(cache2);
    double hitrate2 = (100.0 * cache2.getHitCount()
                       / (cache2.getHitCount() + cache2.getMissCount()));
    CPPUNIT_ASSERT(hitrate2 < 0.01);

    std::cerr << "\n"
              << "  Cache is "
              << (static_cast<double>(rawPerformance) / cachePerformance)
              << " x faster than skipping cache with 100% hitrate\n"
              << "  Cache is "
              << (static_cast<double>(mapPerformance) / cachePerformance)
              << " x faster than std::map cache with all data\n"
              << "  Cache is "
              << (static_cast<double>(rawPerformance) / cacheMissPerformance)
              << " x faster than skipping cache with 0% hitrate\n";
}

} // lib
} // storage
