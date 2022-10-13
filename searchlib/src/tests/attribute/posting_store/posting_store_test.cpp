// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/searchlib/attribute/postingstore.h>
#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreerootbase.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/searchlib/attribute/postingstore.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <ostream>

using vespalib::GenerationHandler;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

namespace search::attribute {

using MyValueStore = EnumStoreT<int32_t>;
using MyPostingStore = PostingStore<int32_t>;

namespace {

constexpr uint32_t lid_limit = 20000;
constexpr uint32_t huge_sequence_length = 800;

struct PostingStoreSetup {
    bool enable_only_bitvector;
    explicit PostingStoreSetup(bool enable_only_bitvector_in)
        : enable_only_bitvector(enable_only_bitvector_in)
    {
    }
};

std::ostream& operator<<(std::ostream& os, const PostingStoreSetup setup)
{
    os << (setup.enable_only_bitvector ? "onlybv" : "mixed");
    return os;
}

Config make_config(PostingStoreSetup param) {
    Config cfg;
    cfg.setEnableOnlyBitVector(param.enable_only_bitvector);
    return cfg;
}

}

class PostingStoreTest : public ::testing::TestWithParam<PostingStoreSetup>
{
protected:
    GenerationHandler _gen_handler;
    Config            _config;
    Status            _status;
    MyValueStore      _value_store;
    MyPostingStore    _store;

    PostingStoreTest();
    ~PostingStoreTest() override;

    void inc_generation()
    {
        _value_store.freeze_dictionary();
        _store.freeze();
        _value_store.assign_generation(_gen_handler.getCurrentGeneration());
        _store.assign_generation(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _value_store.reclaim_memory(_gen_handler.get_oldest_used_generation());
        _store.reclaim_memory(_gen_handler.get_oldest_used_generation());
    }

    EntryRef add_sequence(int start_key, int end_key)
    {
        std::vector<MyPostingStore::KeyDataType> additions;
        std::vector<MyPostingStore::KeyType> removals;
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

    void populate(uint32_t sequence_length);
    EntryRef get_posting_ref(int key);
    void test_compact_btree_nodes(uint32_t sequence_length);
    void test_compact_sequence(uint32_t sequence_length);
};

PostingStoreTest::PostingStoreTest()
    : _gen_handler(),
      _config(make_config(GetParam())),
      _status(),
      _value_store(true, _config.get_dictionary_config()),
      _store(_value_store.get_dictionary(), _status, _config)
{
    _store.resizeBitVectors(lid_limit, lid_limit);
}

PostingStoreTest::~PostingStoreTest()
{
    _value_store.get_dictionary().clear_all_posting_lists([this](EntryRef posting_idx) { _store.clear(posting_idx); });
    _store.clearBuilder();
    inc_generation();
}

void
PostingStoreTest::populate(uint32_t sequence_length)
{
    auto& store = _store;
    auto& dictionary = _value_store.get_dictionary();
    std::vector<EntryRef> refs;
    for (int i = 0; i < 9000; ++i) {
        refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
    }
    dictionary.update_posting_list(_value_store.insert(1), _value_store.get_comparator(), [this, sequence_length](EntryRef) { return add_sequence(4, 4 + sequence_length); });
    dictionary.update_posting_list(_value_store.insert(2), _value_store.get_comparator(), [this, sequence_length](EntryRef) { return add_sequence(5, 5 + sequence_length); });
    for (int i = 9000; i < 11000; ++i) {
        refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
    }
    for (auto& ref : refs) {
        store.clear(ref);
    }
    inc_generation();
}

EntryRef
PostingStoreTest::get_posting_ref(int key)
{
    auto &dictionary = _value_store.get_dictionary();
    auto root = dictionary.get_frozen_root();
    return dictionary.find_posting_list(_value_store.make_comparator(key), root).second;
}

void
PostingStoreTest::test_compact_sequence(uint32_t sequence_length)
{
    populate(sequence_length);
    auto &store = _store;
    EntryRef old_ref1 = get_posting_ref(1);
    EntryRef old_ref2 = get_posting_ref(2);
    auto usage_before = store.getMemoryUsage();
    bool compaction_done = false;
    CompactionStrategy compaction_strategy(0.05, 0.2);
    for (uint32_t pass = 0; pass < 45; ++pass) {
        store.update_stat(compaction_strategy);
        auto guard = _gen_handler.takeGuard();
        if (!store.consider_compact_worst_buffers(compaction_strategy)) {
            compaction_done = true;
            break;
        }
        inc_generation();
        EXPECT_FALSE(store.consider_compact_worst_buffers(compaction_strategy));
        guard = GenerationHandler::Guard();
        inc_generation();
    }
    EXPECT_TRUE(compaction_done);
    EntryRef ref1 = get_posting_ref(1);
    EntryRef ref2 = get_posting_ref(2);
    EXPECT_NE(old_ref1, ref1);
    EXPECT_NE(old_ref2, ref2);
    EXPECT_EQ(make_exp_sequence(4, 4 + sequence_length), get_sequence(ref1));
    EXPECT_EQ(make_exp_sequence(5, 5 + sequence_length), get_sequence(ref2));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
}

void
PostingStoreTest::test_compact_btree_nodes(uint32_t sequence_length)
{
    populate(sequence_length);
    auto &store = _store;
    EntryRef old_ref1 = get_posting_ref(1);
    EntryRef old_ref2 = get_posting_ref(2);
    auto usage_before = store.getMemoryUsage();
    bool compaction_done = false;
    CompactionStrategy compaction_strategy(0.05, 0.2);
    for (uint32_t pass = 0; pass < 55; ++pass) {
        store.update_stat(compaction_strategy);
        auto guard = _gen_handler.takeGuard();
        if (!store.consider_compact_worst_btree_nodes(compaction_strategy)) {
            compaction_done = true;
            break;
        }
        inc_generation();
        EXPECT_FALSE(store.consider_compact_worst_btree_nodes(compaction_strategy));
        guard = GenerationHandler::Guard();
        inc_generation();
    }
    EXPECT_TRUE(compaction_done);
    EntryRef ref1 = get_posting_ref(1);
    EntryRef ref2 = get_posting_ref(2);
    EXPECT_EQ(old_ref1, ref1);
    EXPECT_EQ(old_ref2, ref2);
    EXPECT_EQ(make_exp_sequence(4, 4 + sequence_length), get_sequence(ref1));
    EXPECT_EQ(make_exp_sequence(5, 5 + sequence_length), get_sequence(ref2));
    auto usage_after = store.getMemoryUsage();
    if (sequence_length < huge_sequence_length ||
        !_config.getEnableOnlyBitVector()) {
        EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    } else {
        EXPECT_EQ(usage_before.deadBytes(), usage_after.deadBytes());
    }
}

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(PostingStoreMultiTest,
                                     PostingStoreTest,
                                     testing::Values(PostingStoreSetup(false), PostingStoreSetup(true)), testing::PrintToStringParamName());

TEST_P(PostingStoreTest, require_that_nodes_for_multiple_small_btrees_are_compacted)
{
    test_compact_btree_nodes(30);
}

TEST_P(PostingStoreTest, require_that_nodes_for_multiple_large_btrees_are_compacted)
{
    test_compact_btree_nodes(huge_sequence_length);
}

TEST_P(PostingStoreTest, require_that_short_arrays_are_compacted)
{
    test_compact_sequence(4);
}

TEST_P(PostingStoreTest, require_that_btree_roots_are_compacted)
{
    test_compact_sequence(10);
}

TEST_P(PostingStoreTest, require_that_bitvectors_are_compacted)
{
    test_compact_sequence(huge_sequence_length);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
