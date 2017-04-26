// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/memfilepersistence/memfile/shared_data_location_tracker.h>

namespace storage {
namespace memfile {

class SharedDataLocationTrackerTest : public CppUnit::TestFixture
{
public:
    void headerIsPassedDownToCacheAccessor();
    void bodyIsPassedDownToCacheAccessor();
    void firstInvocationReturnsNewLocation();
    void multipleInvocationsForSharedSlotReturnSameLocation();

    CPPUNIT_TEST_SUITE(SharedDataLocationTrackerTest);
    CPPUNIT_TEST(headerIsPassedDownToCacheAccessor);
    CPPUNIT_TEST(bodyIsPassedDownToCacheAccessor);
    CPPUNIT_TEST(firstInvocationReturnsNewLocation);
    CPPUNIT_TEST(multipleInvocationsForSharedSlotReturnSameLocation);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SharedDataLocationTrackerTest);

namespace {

using Params = std::pair<Types::DocumentPart, DataLocation>;
constexpr auto HEADER = Types::HEADER;
constexpr auto BODY = Types::BODY;

/**
 * A simple mock of a buffer cache which records all invocations
 * and returns a location increasing by 100 for each invocation.
 */
struct MockBufferCacheCopier : BufferCacheCopier
{
    // This is practically _screaming_ for GoogleMock.
    std::vector<Params> invocations;

    DataLocation doCopyFromSourceToLocal(
            Types::DocumentPart part,
            DataLocation sourceLocation) override
    {
        Params params(part, sourceLocation);
        const size_t invocationsBefore = invocations.size();
        invocations.push_back(params);
        return DataLocation(invocationsBefore * 100,
                            invocationsBefore * 100 + 100);
    }
};

}

void
SharedDataLocationTrackerTest::headerIsPassedDownToCacheAccessor()
{
    MockBufferCacheCopier cache;
    SharedDataLocationTracker tracker(cache, HEADER);
    tracker.getOrCreateSharedLocation({0, 100});
    CPPUNIT_ASSERT_EQUAL(size_t(1), cache.invocations.size());
    CPPUNIT_ASSERT_EQUAL(Params(HEADER, {0, 100}), cache.invocations[0]);
}

void
SharedDataLocationTrackerTest::bodyIsPassedDownToCacheAccessor()
{
    MockBufferCacheCopier cache;
    SharedDataLocationTracker tracker(cache, BODY);
    tracker.getOrCreateSharedLocation({0, 100});
    CPPUNIT_ASSERT_EQUAL(size_t(1), cache.invocations.size());
    CPPUNIT_ASSERT_EQUAL(Params(BODY, {0, 100}), cache.invocations[0]);
}

void
SharedDataLocationTrackerTest::firstInvocationReturnsNewLocation()
{
    MockBufferCacheCopier cache;
    SharedDataLocationTracker tracker(cache, HEADER);
    // Auto-incrementing per cache copy invocation.
    CPPUNIT_ASSERT_EQUAL(DataLocation(0, 100),
                         tracker.getOrCreateSharedLocation({500, 600}));
    CPPUNIT_ASSERT_EQUAL(DataLocation(100, 200),
                         tracker.getOrCreateSharedLocation({700, 800}));

    CPPUNIT_ASSERT_EQUAL(size_t(2), cache.invocations.size());
    CPPUNIT_ASSERT_EQUAL(Params(HEADER, {500, 600}), cache.invocations[0]);
    CPPUNIT_ASSERT_EQUAL(Params(HEADER, {700, 800}), cache.invocations[1]);
}

void
SharedDataLocationTrackerTest
   ::multipleInvocationsForSharedSlotReturnSameLocation()
{
    MockBufferCacheCopier cache;
    SharedDataLocationTracker tracker(cache, HEADER);
    CPPUNIT_ASSERT_EQUAL(DataLocation(0, 100),
                         tracker.getOrCreateSharedLocation({500, 600}));
    // Same source location, thus we can reuse the same destination location
    // as well.
    CPPUNIT_ASSERT_EQUAL(DataLocation(0, 100),
                         tracker.getOrCreateSharedLocation({500, 600}));

    CPPUNIT_ASSERT_EQUAL(size_t(1), cache.invocations.size());
    CPPUNIT_ASSERT_EQUAL(Params(HEADER, {500, 600}), cache.invocations[0]);
}

} // memfile
} // storage

