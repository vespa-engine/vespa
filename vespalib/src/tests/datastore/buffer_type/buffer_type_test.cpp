// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/buffer_type.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace vespalib::datastore;

using IntBufferType = BufferType<int>;
constexpr uint32_t ARRAYS_SIZE(4);
constexpr uint32_t MAX_ENTRIES(128);
constexpr uint32_t NUM_ENTRIES_FOR_NEW_BUFFER(0);

struct Setup {
    uint32_t  _min_entries;
    std::atomic<EntryCount> _used_entries;
    EntryCount _needed_entries;
    std::atomic<EntryCount> _dead_entries;
    uint32_t  _bufferId;
    float     _allocGrowFactor;
    bool      _resizing;
    Setup()
        : _min_entries(0),
          _used_entries(0),
          _needed_entries(0),
          _dead_entries(0),
          _bufferId(1),
          _allocGrowFactor(0.5),
          _resizing(false)
    {}
    Setup(const Setup& rhs) noexcept;
    Setup &min_entries(uint32_t value) { _min_entries = value; return *this; }
    Setup &used(size_t value) { _used_entries = value; return *this; }
    Setup &needed(size_t value) { _needed_entries = value; return *this; }
    Setup &dead(size_t value) { _dead_entries = value; return *this; }
    Setup &bufferId(uint32_t value) { _bufferId = value; return *this; }
    Setup &resizing(bool value) { _resizing = value; return *this; }
};

Setup::Setup(const Setup& rhs) noexcept
    : _min_entries(rhs._min_entries),
      _used_entries(rhs._used_entries.load(std::memory_order_relaxed)),
      _needed_entries(rhs._needed_entries),
      _dead_entries(rhs._dead_entries.load(std::memory_order_relaxed)),
      _bufferId(rhs._bufferId),
      _allocGrowFactor(rhs._allocGrowFactor),
      _resizing(rhs._resizing)
{
}

struct Fixture {
    std::vector<Setup> setups;
    IntBufferType bufferType;
    int buffer[ARRAYS_SIZE];
    Fixture(const Setup &setup_)
        : setups(),
          bufferType(ARRAYS_SIZE, setup_._min_entries, MAX_ENTRIES, NUM_ENTRIES_FOR_NEW_BUFFER, setup_._allocGrowFactor),
          buffer()
    {
        setups.reserve(4);
        setups.push_back(setup_);
    }
    ~Fixture() {
        for (auto& setup : setups) {
            bufferType.on_hold(setup._bufferId, &setup._used_entries, &setup._dead_entries);
            bufferType.on_free(setup._used_entries);
        }
    }
    Setup& curr_setup() {
        return setups.back();
    }
    void add_setup(const Setup& setup_in) {
        // The buffer type stores pointers to EntryCount (from Setup) and we must ensure these do not move in memory.
        assert(setups.size() < setups.capacity());
        setups.push_back(setup_in);
    }
    void onActive() {
        bufferType.on_active(curr_setup()._bufferId, &curr_setup()._used_entries, &curr_setup()._dead_entries, &buffer[0]);
    }
    size_t entries_to_alloc() {
        return bufferType.calc_entries_to_alloc(curr_setup()._bufferId, curr_setup()._needed_entries, curr_setup()._resizing);
    }
    void assert_entries_to_alloc(size_t exp) {
        onActive();
        EXPECT_EQUAL(exp, entries_to_alloc());
    }
};

void
assert_entries_to_alloc(size_t exp, const Setup &setup)
{
    Fixture f(setup);
    f.assert_entries_to_alloc(exp);
}

TEST("require that entries are allocated")
{
    TEST_DO(assert_entries_to_alloc(1, Setup().needed(1)));
    TEST_DO(assert_entries_to_alloc(2, Setup().needed(2)));
    TEST_DO(assert_entries_to_alloc(3, Setup().needed(3)));
    TEST_DO(assert_entries_to_alloc(4, Setup().needed(4)));
    TEST_DO(assert_entries_to_alloc(5, Setup().needed(5)));
}

TEST("require that reserved entries are taken into account when not resizing")
{
    TEST_DO(assert_entries_to_alloc(2, Setup().needed(1).bufferId(0)));
    TEST_DO(assert_entries_to_alloc(5, Setup().needed(4).bufferId(0)));
    TEST_DO(assert_entries_to_alloc(6, Setup().needed(5).bufferId(0)));
}

TEST("require that entries to alloc is based on currently used entries (no resizing)")
{
    TEST_DO(assert_entries_to_alloc(2, Setup().used(4).needed(1)));
    TEST_DO(assert_entries_to_alloc(4, Setup().used(8).needed(1)));
}

TEST("require that entries to alloc is based on currently used entries (with resizing)")
{
    TEST_DO(assert_entries_to_alloc(4 + 2, Setup().used(4).needed(1).resizing(true)));
    TEST_DO(assert_entries_to_alloc(8 + 4, Setup().used(8).needed(1).resizing(true)));
    TEST_DO(assert_entries_to_alloc(4 + 3, Setup().used(4).needed(3).resizing(true)));
}

TEST("require that entries to alloc always contain entries needed")
{
    TEST_DO(assert_entries_to_alloc(2, Setup().used(4).needed(2)));
    TEST_DO(assert_entries_to_alloc(3, Setup().used(4).needed(3)));
    TEST_DO(assert_entries_to_alloc(4, Setup().used(4).needed(4)));
}

TEST("require that entries to alloc is capped to max entries")
{
    TEST_DO(assert_entries_to_alloc(127, Setup().used(254).needed(1)));
    TEST_DO(assert_entries_to_alloc(128, Setup().used(256).needed(1)));
    TEST_DO(assert_entries_to_alloc(128, Setup().used(258).needed(2)));
}

TEST("require that arrays to alloc is capped to min arrays")
{
    TEST_DO(assert_entries_to_alloc(16, Setup().used(30).needed(1).min_entries(16)));
    TEST_DO(assert_entries_to_alloc(16, Setup().used(32).needed(1).min_entries(16)));
    TEST_DO(assert_entries_to_alloc(17, Setup().used(34).needed(1).min_entries(16)));
}

TEST("entries to alloc considers used entries across all active buffers of same type (no resizing)")
{
    Fixture f(Setup().used(6));
    f.assert_entries_to_alloc(6 * 0.5);
    f.add_setup(Setup().used(8).bufferId(2));
    f.assert_entries_to_alloc((6 + 8) * 0.5);
    f.add_setup(Setup().used(10).bufferId(3));
    f.assert_entries_to_alloc((6 + 8 + 10) * 0.5);
}

TEST("entries to alloc considers used entries across all active buffers of same type when resizing")
{
    Fixture f(Setup().used(6));
    f.assert_entries_to_alloc(6 * 0.5);
    f.add_setup(Setup().used(8).resizing(true).bufferId(2));
    f.assert_entries_to_alloc(8 + (6 + 8) * 0.5);
}

TEST("entries to alloc considers (and subtracts) dead entries across all active buffers of same type (no resizing)")
{
    Fixture f(Setup().used(6).dead(2));
    f.assert_entries_to_alloc((6 - 2) * 0.5);
    f.add_setup(Setup().used(12).dead(4).bufferId(2));
    f.assert_entries_to_alloc((6 - 2 + 12 - 4) * 0.5);
    f.add_setup(Setup().used(20).dead(6).bufferId(3));
    f.assert_entries_to_alloc((6 - 2 + 12 - 4 + 20 - 6) * 0.5);
}

TEST("arrays to alloc considers (and subtracts) dead elements across all active buffers of same type when resizing")
{
    Fixture f(Setup().used(6).dead(2));
    f.assert_entries_to_alloc((6 - 2) * 0.5);
    f.add_setup(Setup().used(12).dead(4).resizing(true).bufferId(2));
    f.assert_entries_to_alloc(12 + (6 - 2 + 12 - 4) * 0.5);
}

TEST_MAIN() { TEST_RUN_ALL(); }
