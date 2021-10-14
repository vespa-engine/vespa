// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::GenerationHandler;
using vespalib::datastore::EntryRef;

namespace vespalib::btree {

using MyTraits = BTreeTraits<4, 4, 31, false>;
using TreeStore = BTreeStore<int, int, btree::NoAggregated, std::less<int>, MyTraits>;

class BTreeStoreTest : public ::testing::Test {
protected:
    GenerationHandler _gen_handler;
    TreeStore _store;
    
    BTreeStoreTest();
    ~BTreeStoreTest();

    void inc_generation()
    {
        _store.freeze();
        _store.transferHoldLists(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _store.trimHoldLists(_gen_handler.getFirstUsedGeneration());
    }

    EntryRef add_sequence(int start_key, int end_key)
    {
        std::vector<TreeStore::KeyDataType> additions;
        std::vector<TreeStore::KeyType> removals;
        EntryRef root;
        for (int i = start_key; i < end_key; ++i) {
            additions.emplace_back(i, 0);
        }
        _store.apply(root,
                     &additions[0], &additions[0] + additions.size(),
                     &removals[0], &removals[0] + removals.size());
        return root;
    }
    static std::vector<int> make_exp_sequence(int start_key, int end_key)
    {
        std::vector<int> sequence;
        for (int i = start_key; i < end_key; ++i) {
            sequence.emplace_back(i);
        }
        return sequence;
    }
    std::vector<int> get_sequence(EntryRef root) const {
        std::vector<int> sequence;
        _store.foreach_frozen_key(root, [&sequence](int key) { sequence.emplace_back(key); });
        return sequence;
    }

    void test_compact_sequence(uint32_t sequence_length);
};

BTreeStoreTest::BTreeStoreTest()
    : _gen_handler(),
      _store()
{
}

BTreeStoreTest::~BTreeStoreTest()
{
    _store.clearBuilder();
    inc_generation();
}

void
BTreeStoreTest::test_compact_sequence(uint32_t sequence_length)
{
    auto &store = _store;
    EntryRef ref1 = add_sequence(4, 4 + sequence_length);
    EntryRef ref2 = add_sequence(5, 5 + sequence_length);
    EntryRef old_ref1 = ref1;
    EntryRef old_ref2 = ref2;
    std::vector<EntryRef> refs;
    for (int i = 0; i < 1000; ++i) {
        refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
    }
    for (auto& ref : refs) {
        store.clear(ref);
    }
    inc_generation();
    auto usage_before = store.getMemoryUsage();
    for (uint32_t pass = 0; pass < 15; ++pass) {
        auto to_hold = store.start_compact_worst_buffers();
        ref1 = store.move(ref1);
        ref2 = store.move(ref2);
        store.finishCompact(to_hold);
        inc_generation();
    }
    EXPECT_NE(old_ref1, ref1);
    EXPECT_NE(old_ref2, ref2);
    EXPECT_EQ(make_exp_sequence(4, 4 + sequence_length), get_sequence(ref1));
    EXPECT_EQ(make_exp_sequence(5, 5 + sequence_length), get_sequence(ref2));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    store.clear(ref1);
    store.clear(ref2);
}

TEST_F(BTreeStoreTest, require_that_nodes_for_multiple_btrees_are_compacted)
{
    auto &store = this->_store;
    EntryRef ref1 = add_sequence(4, 40);
    EntryRef ref2 = add_sequence(100, 130);
    store.clear(add_sequence(1000, 20000));
    inc_generation();
    auto usage_before = store.getMemoryUsage();
    for (uint32_t pass = 0; pass < 15; ++pass) {
        auto to_hold = store.start_compact_worst_btree_nodes();
        store.move_btree_nodes(ref1);
        store.move_btree_nodes(ref2);
        store.finish_compact_worst_btree_nodes(to_hold);
        inc_generation();
    }
    EXPECT_EQ(make_exp_sequence(4, 40), get_sequence(ref1));
    EXPECT_EQ(make_exp_sequence(100, 130), get_sequence(ref2));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    store.clear(ref1);
    store.clear(ref2);
}

TEST_F(BTreeStoreTest, require_that_short_arrays_are_compacted)
{
    test_compact_sequence(4);
}

TEST_F(BTreeStoreTest, require_that_btree_roots_are_compacted)
{
    test_compact_sequence(10);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
