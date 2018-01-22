// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/datastore/buffer_type.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace search::datastore;

using IntBufferType = BufferType<int>;
constexpr uint32_t CLUSTER_SIZE(4);
constexpr uint32_t MAX_CLUSTERS(128);
constexpr uint32_t NUM_CLUSTERS_FOR_NEW_BUFFER(0);

struct Setup {
    uint32_t _minClusters;
    size_t _usedElems;
    size_t _neededElems;
    uint32_t _bufferId;
    float _allocGrowFactor;
    bool _resizing;
    Setup()
        : _minClusters(0),
          _usedElems(0),
          _neededElems(0),
          _bufferId(1),
          _allocGrowFactor(0.5),
          _resizing(false)
    {}
    Setup &minClusters(uint32_t value) { _minClusters = value; return *this; }
    Setup &used(size_t value) { _usedElems = value; return *this; }
    Setup &needed(size_t value) { _neededElems = value; return *this; }
    Setup &bufferId(uint32_t value) { _bufferId = value; return *this; }
    Setup &resizing(bool value) { _resizing = value; return *this; }
};

struct Fixture {
    Setup setup;
    IntBufferType bufferType;
    size_t deadElems;
    int buffer;
    Fixture(const Setup &setup_)
        : setup(setup_),
          bufferType(CLUSTER_SIZE, setup._minClusters, MAX_CLUSTERS, NUM_CLUSTERS_FOR_NEW_BUFFER, setup._allocGrowFactor),
          deadElems(0),
          buffer(0)
    {}
    ~Fixture() {
        bufferType.onHold(&setup._usedElems);
        bufferType.onFree(setup._usedElems);
    }
    void onActive() {
        bufferType.onActive(setup._bufferId, &setup._usedElems, deadElems, &buffer);
    }
    size_t clustersToAlloc() {
        return bufferType.calcClustersToAlloc(setup._bufferId, setup._neededElems, setup._resizing);
    }
};

void
assertClustersToAlloc(size_t exp, const Setup &setup)
{
    Fixture f(setup);
    f.onActive();
    EXPECT_EQUAL(exp, f.clustersToAlloc());
}

TEST("require that complete clusters are allocated")
{
    TEST_DO(assertClustersToAlloc(1, Setup().needed(1)));
    TEST_DO(assertClustersToAlloc(1, Setup().needed(2)));
    TEST_DO(assertClustersToAlloc(1, Setup().needed(3)));
    TEST_DO(assertClustersToAlloc(1, Setup().needed(4)));
    TEST_DO(assertClustersToAlloc(2, Setup().needed(5)));
}

TEST("require that reserved elements are taken into account when not resizing")
{
    TEST_DO(assertClustersToAlloc(2, Setup().needed(1).bufferId(0)));
    TEST_DO(assertClustersToAlloc(2, Setup().needed(4).bufferId(0)));
    TEST_DO(assertClustersToAlloc(3, Setup().needed(5).bufferId(0)));
}

TEST("require that clusters to alloc is based on currently used elements (no resizing)")
{
    TEST_DO(assertClustersToAlloc(2, Setup().used(4 * 4).needed(4)));
    TEST_DO(assertClustersToAlloc(4, Setup().used(8 * 4).needed(4)));
}

TEST("require that clusters to alloc is based on currently used elements (with resizing)")
{
    TEST_DO(assertClustersToAlloc(4 + 2, Setup().used(4 * 4).needed(4).resizing(true)));
    TEST_DO(assertClustersToAlloc(8 + 4, Setup().used(8 * 4).needed(4).resizing(true)));
    TEST_DO(assertClustersToAlloc(4 + 3, Setup().used(4 * 4).needed(3 * 4).resizing(true)));
}

TEST("require that clusters to alloc always contain elements needed")
{
    TEST_DO(assertClustersToAlloc(2, Setup().used(4 * 4).needed(2 * 4)));
    TEST_DO(assertClustersToAlloc(3, Setup().used(4 * 4).needed(3 * 4)));
    TEST_DO(assertClustersToAlloc(4, Setup().used(4 * 4).needed(4 * 4)));
}

TEST("require that clusters to alloc is capped to max clusters")
{
    TEST_DO(assertClustersToAlloc(127, Setup().used(254 * 4).needed(4)));
    TEST_DO(assertClustersToAlloc(128, Setup().used(256 * 4).needed(4)));
    TEST_DO(assertClustersToAlloc(128, Setup().used(258 * 4).needed(8)));
}

TEST("require that clusters to alloc is capped to min clusters")
{
    TEST_DO(assertClustersToAlloc(16, Setup().used(30 * 4).needed(4).minClusters(16)));
    TEST_DO(assertClustersToAlloc(16, Setup().used(32 * 4).needed(4).minClusters(16)));
    TEST_DO(assertClustersToAlloc(17, Setup().used(34 * 4).needed(4).minClusters(16)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
