// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/buffer_type.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>

using namespace vespalib::datastore;

using IntBufferType = BufferType<int>;
constexpr uint32_t ARRAYS_SIZE(4);
constexpr uint32_t MAX_ENTRIES(128);
constexpr uint32_t NUM_ENTRIES_FOR_NEW_BUFFER(0);

struct MySetup {
    uint32_t  _min_entries;
    std::atomic<EntryCount> _used_entries;
    EntryCount _needed_entries;
    std::atomic<EntryCount> _dead_entries;
    uint32_t  _bufferId;
    float     _allocGrowFactor;
    bool      _resizing;
    MySetup()
        : _min_entries(0),
          _used_entries(0),
          _needed_entries(0),
          _dead_entries(0),
          _bufferId(1),
          _allocGrowFactor(0.5),
          _resizing(false)
    {}
    MySetup(const MySetup& rhs) noexcept;
    MySetup &min_entries(uint32_t value) { _min_entries = value; return *this; }
    MySetup &used(size_t value) { _used_entries = value; return *this; }
    MySetup &needed(size_t value) { _needed_entries = value; return *this; }
    MySetup &dead(size_t value) { _dead_entries = value; return *this; }
    MySetup &bufferId(uint32_t value) { _bufferId = value; return *this; }
    MySetup &resizing(bool value) { _resizing = value; return *this; }
};

MySetup::MySetup(const MySetup& rhs) noexcept
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
    std::vector<MySetup> setups;
    IntBufferType bufferType;
    int buffer[ARRAYS_SIZE];
    Fixture(const MySetup &setup_)
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
    MySetup& curr_setup() {
        return setups.back();
    }
    void add_setup(const MySetup& setup_in) {
        // The buffer type stores pointers to EntryCount (from MySetup) and we must ensure these do not move in memory.
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
        EXPECT_EQ(exp, entries_to_alloc());
    }
};

size_t
entries_to_alloc(const MySetup &setup)
{
    Fixture f(setup);
    f.onActive();
    return f.entries_to_alloc();
}

TEST(BufferTypeTest, require_that_entries_are_allocated)
{
    EXPECT_EQ(1, entries_to_alloc(MySetup().needed(1)));
    EXPECT_EQ(2, entries_to_alloc(MySetup().needed(2)));
    EXPECT_EQ(3, entries_to_alloc(MySetup().needed(3)));
    EXPECT_EQ(4, entries_to_alloc(MySetup().needed(4)));
    EXPECT_EQ(5, entries_to_alloc(MySetup().needed(5)));
}

TEST(BufferTypeTest, require_that_reserved_entries_are_taken_into_account_when_not_resizing)
{
    EXPECT_EQ(2, entries_to_alloc(MySetup().needed(1).bufferId(0)));
    EXPECT_EQ(5, entries_to_alloc(MySetup().needed(4).bufferId(0)));
    EXPECT_EQ(6, entries_to_alloc(MySetup().needed(5).bufferId(0)));
}

TEST(BufferTypeTest, require_that_entries_to_alloc_is_based_on_currently_used_entries_when_not_resizing)
{
    EXPECT_EQ(2, entries_to_alloc(MySetup().used(4).needed(1)));
    EXPECT_EQ(4, entries_to_alloc(MySetup().used(8).needed(1)));
}

TEST(BufferTypeTest, require_that_entries_to_alloc_is_based_on_currently_used_entries_when_resizing)
{
    EXPECT_EQ(4 + 2, entries_to_alloc(MySetup().used(4).needed(1).resizing(true)));
    EXPECT_EQ(8 + 4, entries_to_alloc(MySetup().used(8).needed(1).resizing(true)));
    EXPECT_EQ(4 + 3, entries_to_alloc(MySetup().used(4).needed(3).resizing(true)));
}

TEST(BufferTypeTest, require_that_entries_to_alloc_always_contain_entries_needed)
{
    EXPECT_EQ(2, entries_to_alloc(MySetup().used(4).needed(2)));
    EXPECT_EQ(3, entries_to_alloc(MySetup().used(4).needed(3)));
    EXPECT_EQ(4, entries_to_alloc(MySetup().used(4).needed(4)));
}

TEST(BufferTypeTest, require_that_entries_to_alloc_is_capped_to_max_entries)
{
    EXPECT_EQ(127, entries_to_alloc(MySetup().used(254).needed(1)));
    EXPECT_EQ(128, entries_to_alloc(MySetup().used(256).needed(1)));
    EXPECT_EQ(128, entries_to_alloc(MySetup().used(258).needed(2)));
}

TEST(BufferTypeTest, require_that_arrays_to_alloc_is_capped_to_min_arrays)
{
    EXPECT_EQ(16, entries_to_alloc(MySetup().used(30).needed(1).min_entries(16)));
    EXPECT_EQ(16, entries_to_alloc(MySetup().used(32).needed(1).min_entries(16)));
    EXPECT_EQ(17, entries_to_alloc(MySetup().used(34).needed(1).min_entries(16)));
}

TEST(BufferTypeTest, entries_to_alloc_considers_used_entries_across_all_active_buffers_of_same_type_when_not_resizing)
{
    Fixture f(MySetup().used(6));
    f.assert_entries_to_alloc(6 * 0.5);
    f.add_setup(MySetup().used(8).bufferId(2));
    f.assert_entries_to_alloc((6 + 8) * 0.5);
    f.add_setup(MySetup().used(10).bufferId(3));
    f.assert_entries_to_alloc((6 + 8 + 10) * 0.5);
}

TEST(BufferTypeTest, entries_to_alloc_considers_used_entries_across_all_active_buffers_of_same_type_when_resizing)
{
    Fixture f(MySetup().used(6));
    f.assert_entries_to_alloc(6 * 0.5);
    f.add_setup(MySetup().used(8).resizing(true).bufferId(2));
    f.assert_entries_to_alloc(8 + (6 + 8) * 0.5);
}

TEST(BufferTypeTest, entries_to_alloc_considers_and_subtracts_dead_entries_across_all_active_buffers_of_same_type_when_not_resizing)
{
    Fixture f(MySetup().used(6).dead(2));
    f.assert_entries_to_alloc((6 - 2) * 0.5);
    f.add_setup(MySetup().used(12).dead(4).bufferId(2));
    f.assert_entries_to_alloc((6 - 2 + 12 - 4) * 0.5);
    f.add_setup(MySetup().used(20).dead(6).bufferId(3));
    f.assert_entries_to_alloc((6 - 2 + 12 - 4 + 20 - 6) * 0.5);
}

TEST(BufferTypeTest, arrays_to_alloc_considers_and_subtracts_dead_elements_across_all_active_buffers_of_same_type_when_resizing)
{
    Fixture f(MySetup().used(6).dead(2));
    f.assert_entries_to_alloc((6 - 2) * 0.5);
    f.add_setup(MySetup().used(12).dead(4).resizing(true).bufferId(2));
    f.assert_entries_to_alloc(12 + (6 - 2 + 12 - 4) * 0.5);
}

GTEST_MAIN_RUN_ALL_TESTS()
