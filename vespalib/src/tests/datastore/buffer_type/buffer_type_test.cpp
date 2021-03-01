// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/buffer_type.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace vespalib::datastore;

using IntBufferType = BufferType<int>;
constexpr uint32_t ARRAYS_SIZE(4);
constexpr uint32_t MAX_ARRAYS(128);
constexpr uint32_t NUM_ARRAYS_FOR_NEW_BUFFER(0);

struct Setup {
    uint32_t  _minArrays;
    ElemCount _usedElems;
    ElemCount _neededElems;
    ElemCount _deadElems;
    uint32_t  _bufferId;
    float     _allocGrowFactor;
    bool      _resizing;
    Setup()
        : _minArrays(0),
          _usedElems(0),
          _neededElems(0),
          _deadElems(0),
          _bufferId(1),
          _allocGrowFactor(0.5),
          _resizing(false)
    {}
    Setup &minArrays(uint32_t value) { _minArrays = value; return *this; }
    Setup &used(size_t value) { _usedElems = value; return *this; }
    Setup &needed(size_t value) { _neededElems = value; return *this; }
    Setup &dead(size_t value) { _deadElems = value; return *this; }
    Setup &bufferId(uint32_t value) { _bufferId = value; return *this; }
    Setup &resizing(bool value) { _resizing = value; return *this; }
};

struct Fixture {
    std::vector<Setup> setups;
    IntBufferType bufferType;
    int buffer[ARRAYS_SIZE];
    Fixture(const Setup &setup_)
        : setups(),
          bufferType(ARRAYS_SIZE, setup_._minArrays, MAX_ARRAYS, NUM_ARRAYS_FOR_NEW_BUFFER, setup_._allocGrowFactor),
          buffer()
    {
        setups.reserve(4);
        setups.push_back(setup_);
    }
    ~Fixture() {
        for (auto& setup : setups) {
            bufferType.onHold(&setup._usedElems, &setup._deadElems);
            bufferType.onFree(setup._usedElems);
        }
    }
    Setup& curr_setup() {
        return setups.back();
    }
    void add_setup(const Setup& setup_in) {
        // The buffer type stores pointers to ElemCount (from Setup) and we must ensure these do not move in memory.
        assert(setups.size() < setups.capacity());
        setups.push_back(setup_in);
    }
    void onActive() {
        bufferType.onActive(curr_setup()._bufferId, &curr_setup()._usedElems, &curr_setup()._deadElems, &buffer[0]);
    }
    size_t arraysToAlloc() {
        return bufferType.calcArraysToAlloc(curr_setup()._bufferId, curr_setup()._neededElems, curr_setup()._resizing);
    }
    void assertArraysToAlloc(size_t exp) {
        onActive();
        EXPECT_EQUAL(exp, arraysToAlloc());
    }
};

void
assertArraysToAlloc(size_t exp, const Setup &setup)
{
    Fixture f(setup);
    f.assertArraysToAlloc(exp);
}

TEST("require that complete arrays are allocated")
{
    TEST_DO(assertArraysToAlloc(1, Setup().needed(1)));
    TEST_DO(assertArraysToAlloc(1, Setup().needed(2)));
    TEST_DO(assertArraysToAlloc(1, Setup().needed(3)));
    TEST_DO(assertArraysToAlloc(1, Setup().needed(4)));
    TEST_DO(assertArraysToAlloc(2, Setup().needed(5)));
}

TEST("require that reserved elements are taken into account when not resizing")
{
    TEST_DO(assertArraysToAlloc(2, Setup().needed(1).bufferId(0)));
    TEST_DO(assertArraysToAlloc(2, Setup().needed(4).bufferId(0)));
    TEST_DO(assertArraysToAlloc(3, Setup().needed(5).bufferId(0)));
}

TEST("require that arrays to alloc is based on currently used elements (no resizing)")
{
    TEST_DO(assertArraysToAlloc(2, Setup().used(4 * 4).needed(4)));
    TEST_DO(assertArraysToAlloc(4, Setup().used(8 * 4).needed(4)));
}

TEST("require that arrays to alloc is based on currently used elements (with resizing)")
{
    TEST_DO(assertArraysToAlloc(4 + 2, Setup().used(4 * 4).needed(4).resizing(true)));
    TEST_DO(assertArraysToAlloc(8 + 4, Setup().used(8 * 4).needed(4).resizing(true)));
    TEST_DO(assertArraysToAlloc(4 + 3, Setup().used(4 * 4).needed(3 * 4).resizing(true)));
}

TEST("require that arrays to alloc always contain elements needed")
{
    TEST_DO(assertArraysToAlloc(2, Setup().used(4 * 4).needed(2 * 4)));
    TEST_DO(assertArraysToAlloc(3, Setup().used(4 * 4).needed(3 * 4)));
    TEST_DO(assertArraysToAlloc(4, Setup().used(4 * 4).needed(4 * 4)));
}

TEST("require that arrays to alloc is capped to max arrays")
{
    TEST_DO(assertArraysToAlloc(127, Setup().used(254 * 4).needed(4)));
    TEST_DO(assertArraysToAlloc(128, Setup().used(256 * 4).needed(4)));
    TEST_DO(assertArraysToAlloc(128, Setup().used(258 * 4).needed(8)));
}

TEST("require that arrays to alloc is capped to min arrays")
{
    TEST_DO(assertArraysToAlloc(16, Setup().used(30 * 4).needed(4).minArrays(16)));
    TEST_DO(assertArraysToAlloc(16, Setup().used(32 * 4).needed(4).minArrays(16)));
    TEST_DO(assertArraysToAlloc(17, Setup().used(34 * 4).needed(4).minArrays(16)));
}

TEST("arrays to alloc considers used elements across all active buffers (no resizing)")
{
    Fixture f(Setup().used(6 * 4));
    f.assertArraysToAlloc(6 * 0.5);
    f.add_setup(Setup().used(8 * 4));
    f.assertArraysToAlloc((6 + 8) * 0.5);
    f.add_setup(Setup().used(10 * 4));
    f.assertArraysToAlloc((6 + 8 + 10) * 0.5);
}

TEST("arrays to alloc only considers used elements in current buffer when resizing")
{
    Fixture f(Setup().used(6 * 4));
    f.assertArraysToAlloc(6 * 0.5);
    f.add_setup(Setup().used(8 * 4).resizing(true));
    f.assertArraysToAlloc(8 + 8 * 0.5);
}

TEST("arrays to alloc considers (and subtracts) dead elements across all active buffers (no resizing)")
{
    Fixture f(Setup().used(6 * 4).dead(2 * 4));
    f.assertArraysToAlloc((6 - 2) * 0.5);
    f.add_setup(Setup().used(12 * 4).dead(4 * 4));
    f.assertArraysToAlloc((6 - 2 + 12 - 4) * 0.5);
    f.add_setup(Setup().used(20 * 4).dead(6 * 4));
    f.assertArraysToAlloc((6 - 2 + 12 - 4 + 20 - 6) * 0.5);
}

TEST("arrays to alloc only considers (and subtracts) dead elements in current buffer when resizing")
{
    Fixture f(Setup().used(6 * 4).dead(2 * 4));
    f.assertArraysToAlloc((6 - 2) * 0.5);
    f.add_setup(Setup().used(12 * 4).dead(4 * 4).resizing(true));
    f.assertArraysToAlloc(12 + (12 - 4) * 0.5);
}

TEST_MAIN() { TEST_RUN_ALL(); }
