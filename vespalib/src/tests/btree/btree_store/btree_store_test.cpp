// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/compacting_buffers.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/entry_ref_filter.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::GenerationHandler;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
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
        _store.assign_generation(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _store.reclaim_memory(_gen_handler.get_oldest_used_generation());
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
                     additions.data(), additions.data() + additions.size(),
                     removals.data(), removals.data() + removals.size());
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

namespace {

class ChangeWriter {
    std::vector<EntryRef*> _old_refs;
public:
    ChangeWriter(uint32_t capacity);
    ~ChangeWriter();
    void write(const std::vector<EntryRef>& refs);
    void emplace_back(EntryRef& ref) { _old_refs.emplace_back(&ref); }
};

ChangeWriter::ChangeWriter(uint32_t capacity)
    : _old_refs()
{
    _old_refs.reserve(capacity);
}

ChangeWriter::~ChangeWriter() = default;

void
ChangeWriter::write(const std::vector<EntryRef> &refs)
{
    assert(refs.size() == _old_refs.size());
    auto old_ref_itr = _old_refs.begin();
    for (auto ref : refs) {
        **old_ref_itr = ref;
        ++old_ref_itr;
    }
    assert(old_ref_itr == _old_refs.end());
    _old_refs.clear();
}

}

void
BTreeStoreTest::test_compact_sequence(uint32_t sequence_length)
{
    auto &store = _store;
    EntryRef ref1 = add_sequence(4, 4 + sequence_length);
    EntryRef ref2 = add_sequence(5, 5 + sequence_length);
    std::vector<EntryRef> refs;
    refs.reserve(2);
    refs.emplace_back(ref1);
    refs.emplace_back(ref2);
    std::vector<EntryRef> temp_refs;
    for (int i = 0; i < 1000; ++i) {
        temp_refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
    }
    for (auto& ref : temp_refs) {
        store.clear(ref);
    }
    inc_generation();
    ChangeWriter change_writer(refs.size());
    std::vector<EntryRef> move_refs;
    move_refs.reserve(refs.size());
    auto usage_before = store.getMemoryUsage();
    for (uint32_t pass = 0; pass < 15; ++pass) {
        CompactionSpec compaction_spec(true, false);
        CompactionStrategy compaction_strategy;
        auto compacting_buffers = store.start_compact_worst_buffers(compaction_spec, compaction_strategy);
        auto filter = compacting_buffers->make_entry_ref_filter();
        for (auto& ref : refs) {
            if (ref.valid() && filter.has(ref)) {
                move_refs.emplace_back(ref);
                change_writer.emplace_back(ref);
            }
        }
        store.move(move_refs);
        change_writer.write(move_refs);
        move_refs.clear();
        compacting_buffers->finish();
        inc_generation();
    }
    EXPECT_NE(ref1, refs[0]);
    EXPECT_NE(ref2, refs[1]);
    EXPECT_EQ(make_exp_sequence(4, 4 + sequence_length), get_sequence(refs[0]));
    EXPECT_EQ(make_exp_sequence(5, 5 + sequence_length), get_sequence(refs[1]));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    store.clear(refs[0]);
    store.clear(refs[1]);
}

TEST_F(BTreeStoreTest, require_that_nodes_for_multiple_btrees_are_compacted)
{
    auto &store = this->_store;
    std::vector<EntryRef> refs;
    refs.emplace_back(add_sequence(4, 40));
    refs.emplace_back(add_sequence(100, 130));
    store.clear(add_sequence(1000, 20000));
    inc_generation();
    auto usage_before = store.getMemoryUsage();
    for (uint32_t pass = 0; pass < 15; ++pass) {
        CompactionStrategy compaction_strategy;
        auto compacting_buffers = store.start_compact_worst_btree_nodes(compaction_strategy);
        store.move_btree_nodes(refs);
        compacting_buffers->finish();
        inc_generation();
    }
    EXPECT_EQ(make_exp_sequence(4, 40), get_sequence(refs[0]));
    EXPECT_EQ(make_exp_sequence(100, 130), get_sequence(refs[1]));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    store.clear(refs[0]);
    store.clear(refs[1]);
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
