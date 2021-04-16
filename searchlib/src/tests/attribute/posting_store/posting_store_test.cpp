// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
using vespalib::datastore::EntryRef;

namespace search::attribute {

using MyValueStore = EnumStoreT<int32_t>;
using MyPostingStore = PostingStore<int32_t>;

namespace {

static constexpr uint32_t lid_limit = 20000;
static constexpr uint32_t huge_sequence_length = 800;

struct PostingStoreSetup {
    bool enable_bitvectors;
    bool enable_only_bitvector;
    PostingStoreSetup(bool enable_bitvectors_in, bool enable_only_bitvector_in)
        : enable_bitvectors(enable_bitvectors_in),
          enable_only_bitvector(enable_only_bitvector_in)
    {
    }
};

std::ostream& operator<<(std::ostream& os, const PostingStoreSetup setup)
{
    os << (setup.enable_bitvectors ? "bv" : "nobv") << "_" << (setup.enable_only_bitvector ? "onlybv" : "mixed");
    return os;
}

Config make_config(PostingStoreSetup param) {
    Config cfg;
    cfg.setEnableBitVectors(param.enable_bitvectors);
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
        _store.freeze();
        _store.transferHoldLists(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _store.trimHoldLists(_gen_handler.getFirstUsedGeneration());
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

    std::vector<EntryRef> populate(uint32_t sequence_length);
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
    _store.clearBuilder();
    inc_generation();
}

std::vector<EntryRef>
PostingStoreTest::populate(uint32_t sequence_length)
{
    auto &store = _store;
    EntryRef ref1 = add_sequence(4, 4 + sequence_length);
    EntryRef ref2 = add_sequence(5, 5 + sequence_length);
    std::vector<EntryRef> refs;
    for (int i = 0; i < 1000; ++i) {
        refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
    }
    for (auto& ref : refs) {
        store.clear(ref);
    }
    inc_generation();
    return { ref1, ref2 };
}

void
PostingStoreTest::test_compact_sequence(uint32_t sequence_length)
{
    auto populated_refs = populate(sequence_length);
    auto &store = _store;
    EntryRef ref1 = populated_refs[0];
    EntryRef ref2 = populated_refs[1];
    auto usage_before = store.getMemoryUsage();
    for (uint32_t pass = 0; pass < 15; ++pass) {
        auto to_hold = store.start_compact_worst_buffers();
        ref1 = store.move(ref1);
        ref2 = store.move(ref2);
        store.finishCompact(to_hold);
        inc_generation();
    }
    EXPECT_NE(populated_refs[0], ref1);
    EXPECT_NE(populated_refs[1], ref2);
    EXPECT_EQ(make_exp_sequence(4, 4 + sequence_length), get_sequence(ref1));
    EXPECT_EQ(make_exp_sequence(5, 5 + sequence_length), get_sequence(ref2));
    auto usage_after = store.getMemoryUsage();
    EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    store.clear(ref1);
    store.clear(ref2);
}

void
PostingStoreTest::test_compact_btree_nodes(uint32_t sequence_length)
{
    auto populated_refs = populate(sequence_length);
    auto &store = _store;
    EntryRef ref1 = populated_refs[0];
    EntryRef ref2 = populated_refs[1];
    auto usage_before = store.getMemoryUsage();
    for (uint32_t pass = 0; pass < 15; ++pass) {
        auto to_hold = store.start_compact_worst_btree_nodes();
        store.move_btree_nodes(ref1);
        store.move_btree_nodes(ref2);
        store.finish_compact_worst_btree_nodes(to_hold);
        inc_generation();
    }
    EXPECT_EQ(make_exp_sequence(4, 4 + sequence_length), get_sequence(ref1));
    EXPECT_EQ(make_exp_sequence(5, 5 + sequence_length), get_sequence(ref2));
    auto usage_after = store.getMemoryUsage();
    if (sequence_length < huge_sequence_length ||
        !_config.getEnableBitVectors() ||
        !_config.getEnableOnlyBitVector()) {
        EXPECT_GT(usage_before.deadBytes(), usage_after.deadBytes());
    } else {
        EXPECT_EQ(usage_before.deadBytes(), usage_after.deadBytes());
    }
    store.clear(ref1);
    store.clear(ref2);
}

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(PostingStoreMultiTest,
                                     PostingStoreTest,
                                     testing::Values(PostingStoreSetup(false, false), PostingStoreSetup(true, false), PostingStoreSetup(true, true)), testing::PrintToStringParamName());

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
