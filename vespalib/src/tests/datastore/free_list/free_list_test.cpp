// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/entryref.hpp>
#include <vespa/vespalib/datastore/free_list.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using namespace vespalib::datastore;

using MyEntryRef = EntryRefT<8, 4>;

struct FreeListTest : public testing::Test
{
    FreeList list;
    std::atomic<EntryCount> dead_entries;
    std::vector<BufferFreeList> bufs;
    FreeListTest()
        : list(),
          bufs()
    {
        for (size_t i = 0; i < 3; ++i) {
            bufs.emplace_back(dead_entries);
        }
    }
    void TearDown() override {
        for (auto& buf : bufs) {
            buf.disable();
        }
    }
    void enable(uint32_t buffer_id) {
        bufs[buffer_id].enable(list);
    }
    void enable_all() {
        for (auto& buf : bufs) {
            buf.enable(list);
        }
    }
    void push_entry(MyEntryRef ref) {
        bufs[ref.bufferId()].push_entry(ref);
    }
    MyEntryRef pop_entry() {
        return {list.pop_entry()};
    }
};

TEST_F(FreeListTest, entry_refs_are_reused_in_lifo_order)
{
    enable(0);
    push_entry({10, 0});
    push_entry({11, 0});
    push_entry({12, 0});
    EXPECT_EQ(MyEntryRef(12, 0), pop_entry());
    EXPECT_EQ(MyEntryRef(11, 0), pop_entry());
    EXPECT_EQ(MyEntryRef(10, 0), pop_entry());
}

TEST_F(FreeListTest, buffer_free_list_attaches_and_detaches_from_free_list)
{
    enable(0);
    EXPECT_TRUE(list.empty());
    push_entry({10, 0});
    EXPECT_EQ(1, list.size());
    push_entry({11, 0});
    pop_entry();
    EXPECT_EQ(1, list.size());
    pop_entry();
    EXPECT_TRUE(list.empty());
}

TEST_F(FreeListTest, disable_clears_all_entry_refs_and_detaches_from_free_list)
{
    enable(0);
    push_entry({10, 0});
    EXPECT_EQ(1, list.size());
    EXPECT_FALSE(bufs[0].empty());
    EXPECT_TRUE(bufs[0].enabled());

    bufs[0].disable();
    EXPECT_TRUE(list.empty());
    EXPECT_TRUE(bufs[0].empty());
    EXPECT_FALSE(bufs[0].enabled());
}

TEST_F(FreeListTest, buffer_free_lists_are_reused_in_lifo_order)
{
    enable_all();
    EXPECT_TRUE(list.empty());
    push_entry({10, 0});
    EXPECT_EQ(1, list.size());
    push_entry({11, 0});
    push_entry({20, 1});
    EXPECT_EQ(2, list.size());
    push_entry({21, 1});
    push_entry({30, 2});
    EXPECT_EQ(3, list.size());
    push_entry({31, 2});

    EXPECT_EQ(MyEntryRef(31, 2), pop_entry());
    EXPECT_EQ(MyEntryRef(30, 2), pop_entry());
    EXPECT_EQ(2, list.size());
    EXPECT_EQ(MyEntryRef(21, 1), pop_entry());
    EXPECT_EQ(MyEntryRef(20, 1), pop_entry());
    EXPECT_EQ(1, list.size());
    EXPECT_EQ(MyEntryRef(11, 0), pop_entry());

    push_entry({32, 2});
    EXPECT_EQ(2, list.size());

    EXPECT_EQ(MyEntryRef(32, 2), pop_entry());
    EXPECT_EQ(1, list.size());
    EXPECT_EQ(MyEntryRef(10, 0), pop_entry());
    EXPECT_TRUE(list.empty());
}

TEST_F(FreeListTest, buffer_free_list_can_be_disabled_and_detached_when_not_currently_reused)
{
    enable_all();
    push_entry({10, 0});
    push_entry({20, 1});
    EXPECT_EQ(2, list.size());
    bufs[0].disable();
    EXPECT_EQ(1, list.size());
    EXPECT_EQ(MyEntryRef(20, 1), pop_entry());
    EXPECT_TRUE(list.empty());
}

TEST_F(FreeListTest, dead_entries_count_is_updated_when_popping_an_entry)
{
    enable(0);
    push_entry({10, 0});
    dead_entries.store(18, std::memory_order_relaxed);
    pop_entry();
    EXPECT_EQ(17, dead_entries.load(std::memory_order_relaxed));
}

GTEST_MAIN_RUN_ALL_TESTS()

