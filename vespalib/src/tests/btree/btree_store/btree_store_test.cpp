// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    EntryRef make_tree(int start_key, int end_key)
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
};

BTreeStoreTest::BTreeStoreTest()
    : _gen_handler(),
      _store()
{
}

BTreeStoreTest::~BTreeStoreTest() = default;

TEST_F(BTreeStoreTest, require_that_nodes_for_multiple_btrees_are_compacted)
{
    auto &store = this->_store;
    EntryRef root1 = make_tree(4, 40);
    EntryRef root2 = make_tree(100, 130);
    store.clear(make_tree(1000, 20000));
    inc_generation();
    auto usage_before = store.getMemoryUsage();
    auto to_hold = store.start_compact_worst_btree_nodes();
    store.move_btree_nodes(root1);
    store.move_btree_nodes(root2);
    store.finish_compact_worst_btree_nodes(to_hold);
    inc_generation();
    EXPECT_EQ(make_exp_sequence(4, 40), get_sequence(root1));
    EXPECT_EQ(make_exp_sequence(100, 130), get_sequence(root2));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    store.clear(root1);
    store.clear(root2);
    inc_generation();
}

}

GTEST_MAIN_RUN_ALL_TESTS()
