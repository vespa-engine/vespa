// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchcommon/attribute/status.h>
#include <vespa/searchlib/attribute/postingstore.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>

#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/searchlib/attribute/postingstore.hpp>
#include <vespa/vespalib/btree/btreerootbase.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

#include <algorithm>
#include <limits>
#include <map>
#include <ostream>

using vespalib::GenerationGuard;
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
    explicit PostingStoreSetup(bool enable_only_bitvector_in) : enable_only_bitvector(enable_only_bitvector_in) {}
};

std::ostream& operator<<(std::ostream& os, const PostingStoreSetup setup) {
    os << (setup.enable_only_bitvector ? "onlybv" : "mixed");
    return os;
}

Config make_config(PostingStoreSetup param) {
    Config cfg;
    cfg.setIsFilter(param.enable_only_bitvector);
    return cfg;
}

} // namespace

class PostingStoreTest : public ::testing::TestWithParam<PostingStoreSetup> {
protected:
    GenerationHandler _gen_handler;
    Config            _config;
    Status            _status;
    MyValueStore      _value_store;
    MyPostingStore    _store;

    PostingStoreTest();
    ~PostingStoreTest() override;

    void inc_generation() {
        _value_store.freeze_dictionary();
        _store.freeze();
        _value_store.assign_generation(_gen_handler.getCurrentGeneration());
        _store.assign_generation(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _value_store.reclaim_memory(_gen_handler.get_oldest_used_generation());
        _store.reclaim_memory(_gen_handler.get_oldest_used_generation());
    }

    EntryRef add_sequence(int start_key, int end_key) {
        std::vector<MyPostingStore::KeyDataType> additions;
        std::vector<MyPostingStore::KeyType>     removals;
        EntryRef                                 root;
        for (int i = start_key; i < end_key; ++i) {
            additions.emplace_back(i, 0);
        }
        _store.apply(root, additions.data(), additions.data() + additions.size(), removals.data(),
                     removals.data() + removals.size());
        return root;
    }
    static std::vector<int> make_exp_sequence(int start_key, int end_key) {
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

    void populate(std::vector<uint32_t>                sequence_lengths,
                  std::optional<std::function<void()>> clear_callback = std::nullopt);
    EntryRef get_posting_ref(int key);
    void test_compact_btree_nodes(uint32_t sequence_length);
    void test_compact_sequence(uint32_t sequence_length);
};

PostingStoreTest::PostingStoreTest()
    : _gen_handler(),
      _config(make_config(GetParam())),
      _status(),
      _value_store(true, _config.get_dictionary_config()),
      _store(_value_store.get_dictionary(), _status, _config) {
    _store.resizeBitVectors(lid_limit, lid_limit);
}

PostingStoreTest::~PostingStoreTest() {
    _value_store.get_dictionary().clear_all_posting_lists(
        [this](EntryRef posting_idx) { _store.clear(posting_idx); });
    _store.clearBuilder();
    inc_generation();
}

void PostingStoreTest::populate(std::vector<uint32_t>                sequence_lengths,
                                std::optional<std::function<void()>> clear_callback) {
    auto&                 store = _store;
    auto&                 dictionary = _value_store.get_dictionary();
    std::vector<EntryRef> refs;
    for (auto sequence_length : sequence_lengths) {
        for (int i = 0; i < 9000; ++i) {
            refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
        }
    }
    dictionary.update_posting_list(_value_store.insert(1), _value_store.get_comparator(),
                                   [this, sequence_length = sequence_lengths.front()](EntryRef) {
                                       return add_sequence(4, 4 + sequence_length);
                                   });
    dictionary.update_posting_list(_value_store.insert(2), _value_store.get_comparator(),
                                   [this, sequence_length = sequence_lengths.front()](EntryRef) {
                                       return add_sequence(5, 5 + sequence_length);
                                   });
    for (auto sequence_length : sequence_lengths) {
        for (int i = 9000; i < 11000; ++i) {
            refs.emplace_back(add_sequence(i + 6, i + 6 + sequence_length));
        }
    }
    for (auto& ref : refs) {
        store.clear(ref);
        if (clear_callback.has_value()) {
            clear_callback.value()();
        }
    }
    inc_generation();
}

EntryRef PostingStoreTest::get_posting_ref(int key) {
    auto& dictionary = _value_store.get_dictionary();
    auto  root = dictionary.get_frozen_root();
    return dictionary.find_posting_list(_value_store.make_comparator(key), root).second;
}

void PostingStoreTest::test_compact_sequence(uint32_t sequence_length) {
    populate({sequence_length});
    auto&              store = _store;
    EntryRef           old_ref1 = get_posting_ref(1);
    EntryRef           old_ref2 = get_posting_ref(2);
    auto               usage_before = store.getMemoryUsage();
    bool               compaction_done = false;
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
        guard = GenerationGuard();
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
    EXPECT_GT(usage_before.total.deadBytes(), usage_after.total.deadBytes());
}

void PostingStoreTest::test_compact_btree_nodes(uint32_t sequence_length) {
    populate({sequence_length});
    auto&              store = _store;
    EntryRef           old_ref1 = get_posting_ref(1);
    EntryRef           old_ref2 = get_posting_ref(2);
    auto               usage_before = store.getMemoryUsage();
    bool               compaction_done = false;
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
        guard = GenerationGuard();
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
    if ((sequence_length < huge_sequence_length) || !_config.getIsFilter()) {
        EXPECT_GT(usage_before.total.deadBytes(), usage_after.total.deadBytes());
    } else {
        EXPECT_EQ(usage_before.total.deadBytes(), usage_after.total.deadBytes());
    }
}

INSTANTIATE_TEST_SUITE_P(PostingStoreMultiTest, PostingStoreTest,
                         testing::Values(PostingStoreSetup(false), PostingStoreSetup(true)),
                         testing::PrintToStringParamName());

TEST_P(PostingStoreTest, require_that_nodes_for_multiple_small_btrees_are_compacted) {
    test_compact_btree_nodes(30);
}

TEST_P(PostingStoreTest, require_that_nodes_for_multiple_large_btrees_are_compacted) {
    test_compact_btree_nodes(huge_sequence_length);
}

TEST_P(PostingStoreTest, require_that_short_arrays_are_compacted) {
    test_compact_sequence(4);
}

TEST_P(PostingStoreTest, require_that_btree_roots_are_compacted) {
    test_compact_sequence(10);
}

TEST_P(PostingStoreTest, require_that_bitvectors_are_compacted) {
    test_compact_sequence(huge_sequence_length);
}

namespace {

/*
 * Check if compaction of btree nodes or short arrays is suppressed due to
 * dead ratio being too low for the sum of both data stores.
 */
bool compaction_is_suppressed(MyPostingStore& store, CompactionStrategy& compaction_strategy) {
    store.update_stat(compaction_strategy);
    auto& compaction_spec = store.get_compaction_spec();
    auto  memory_usage = store.getMemoryUsage();
    return ((compaction_strategy.should_compact_memory(memory_usage.btrees) && !compaction_spec.btree_nodes()) ||
            (compaction_strategy.should_compact_memory(memory_usage.short_arrays) && !compaction_spec.store()));
}

} // namespace

TEST_P(PostingStoreTest, require_that_compaction_is_suppressed) {
    CompactionStrategy compaction_strategy(0.05, 0.2);
    bool               suppressed_compaction = false;
    auto               clear_callback = [this, &compaction_strategy, &suppressed_compaction]() mutable {
        inc_generation();
        if (compaction_is_suppressed(_store, compaction_strategy)) {
            suppressed_compaction = true;
        }
    };
    populate({4, 10}, clear_callback);
    EXPECT_TRUE(suppressed_compaction);
}

namespace {

// Bitvector thresholds after resizeBitVectors(lid_limit, ...), see PostingStoreBase2::resizeBitVectors()
constexpr uint32_t min_bv_doc_freq = lid_limit >> 7;
constexpr uint32_t max_bv_doc_freq = lid_limit >> 6;

float weight_of(uint32_t docid) {
    return docid * 0.25f - 100.0f;
}

} // namespace

class FloatPostingStoreTest : public ::testing::TestWithParam<PostingStoreSetup> {
protected:
    using FloatPostingStore = PostingStore<float>;
    using Additions = std::vector<FloatPostingStore::KeyDataType>;
    using Removals = std::vector<FloatPostingStore::KeyType>;
    using ExpMap = std::map<uint32_t, float>;
    enum class Layout { array, btree, bitvector };

    GenerationHandler _gen_handler;
    Config            _config;
    Status            _status;
    MyValueStore      _value_store;
    FloatPostingStore _store;

    FloatPostingStoreTest()
        : _gen_handler(),
          _config(make_config(GetParam())),
          _status(),
          _value_store(true, _config.get_dictionary_config()),
          _store(_value_store.get_dictionary(), _status, _config) {
        _store.resizeBitVectors(lid_limit, lid_limit);
    }
    ~FloatPostingStoreTest() override {
        _value_store.get_dictionary().clear_all_posting_lists(
            [this](EntryRef posting_idx) { _store.clear(posting_idx); });
        _store.clearBuilder();
        inc_generation();
    }

    bool is_filter() const { return _config.getIsFilter(); }

    void inc_generation() {
        _value_store.freeze_dictionary();
        _store.freeze();
        _value_store.assign_generation(_gen_handler.getCurrentGeneration());
        _store.assign_generation(_gen_handler.getCurrentGeneration());
        _gen_handler.incGeneration();
        _value_store.reclaim_memory(_gen_handler.get_oldest_used_generation());
        _store.reclaim_memory(_gen_handler.get_oldest_used_generation());
    }

    void apply(EntryRef& root, Additions additions, Removals removals) {
        _store.apply(root, additions.data(), additions.data() + additions.size(), removals.data(),
                     removals.data() + removals.size());
    }

    // Additions for docids [start, end) with weight_of(docid), also recorded in exp
    static Additions make_additions(uint32_t start, uint32_t end, ExpMap& exp) {
        Additions additions;
        for (uint32_t docid = start; docid < end; ++docid) {
            additions.emplace_back(docid, weight_of(docid));
            exp[docid] = weight_of(docid);
        }
        return additions;
    }

    static Removals make_removals(uint32_t start, uint32_t end, ExpMap& exp) {
        Removals removals;
        for (uint32_t docid = start; docid < end; ++docid) {
            removals.emplace_back(docid);
            exp.erase(docid);
        }
        return removals;
    }

    EntryRef make_posting_list(uint32_t start, uint32_t end, ExpMap& exp) {
        EntryRef root;
        apply(root, make_additions(start, end, exp), {});
        return root;
    }

    Layout layout(EntryRef root) const {
        if (_store.has_bitvector(root)) {
            return Layout::bitvector;
        }
        return (_store.getClusterSize(FloatPostingStore::RefType(root)) != 0) ? Layout::array : Layout::btree;
    }

    /*
     * A bitvector without a btree (filter mode) does not retain weights: foreach_frozen()
     * reports bitVectorWeight() for each docid, and the weights are still lost after the
     * bitvector is dropped (degraded btree).
     */
    static void use_bitvector_weight(ExpMap& exp) {
        for (auto& [docid, weight] : exp) {
            weight = FloatPostingStore::bitVectorWeight();
        }
    }

    ExpMap get_frozen(EntryRef root) const {
        ExpMap result;
        _store.foreach_frozen(root, [&result](uint32_t docid, float weight) { result[docid] = weight; });
        return result;
    }

    EntryRef get_posting_ref(int key) {
        auto& dictionary = _value_store.get_dictionary();
        auto  root = dictionary.get_frozen_root();
        return dictionary.find_posting_list(_value_store.make_comparator(key), root).second;
    }

    /*
     * Check frozen content, aggregated min/max and frozen iteration of the posting list.
     * A bitvector without a btree has no aggregated min/max and no btree to iterate.
     */
    void check(EntryRef root, const ExpMap& exp) {
        inc_generation();
        EXPECT_EQ(exp, get_frozen(root));
        if (_store.has_bitvector(root) && !_store.has_btree(root)) {
            EXPECT_TRUE(_store.getAggregated(root) == FloatPostingStore::AggregatedType());
            EXPECT_FALSE(_store.beginFrozen(root).valid());
            return;
        }
        float min = std::numeric_limits<float>::max();
        float max = std::numeric_limits<float>::lowest();
        for (const auto& [docid, weight] : exp) {
            min = std::min(min, weight);
            max = std::max(max, weight);
        }
        auto aggregated = _store.getAggregated(root);
        EXPECT_EQ(min, aggregated.getMin());
        EXPECT_EQ(max, aggregated.getMax());
        ExpMap iterated;
        for (auto it = _store.beginFrozen(root); it.valid(); ++it) {
            iterated[it.getKey()] = it.getData();
        }
        EXPECT_EQ(exp, iterated);
    }
};

INSTANTIATE_TEST_SUITE_P(FloatPostingStoreMultiTest, FloatPostingStoreTest,
                         testing::Values(PostingStoreSetup(false), PostingStoreSetup(true)),
                         testing::PrintToStringParamName());

TEST_P(FloatPostingStoreTest, float_weights_are_stored_and_aggregated) {
    EntryRef root;
    ExpMap   exp;
    apply(root, {{3, 0.5f}, {7, -2.25f}}, {});
    exp = {{3, 0.5f}, {7, -2.25f}};
    EXPECT_EQ(Layout::array, layout(root));
    check(root, exp);
    // updating weight of existing docid
    apply(root, {{3, 1.75f}}, {});
    exp[3] = 1.75f;
    EXPECT_EQ(Layout::array, layout(root));
    check(root, exp);
    // grow into btree, one docid at a time
    for (uint32_t docid = 100; docid < 200; ++docid) {
        apply(root, make_additions(docid, docid + 1, exp), {});
    }
    EXPECT_EQ(Layout::btree, layout(root));
    check(root, exp);
    apply(root, {}, {7, 100});
    exp.erase(7);
    exp.erase(100);
    EXPECT_EQ(Layout::btree, layout(root));
    check(root, exp);
    _store.clear(root);
}

TEST_P(FloatPostingStoreTest, float_weights_across_bitvector_transitions) {
    EntryRef root;
    ExpMap   exp;
    uint32_t end = max_bv_doc_freq - 10;
    apply(root, make_additions(1, end, exp), {});
    EXPECT_EQ(Layout::btree, layout(root));
    check(root, exp);
    // grow existing btree past threshold => bitvector is added (makeBitVector)
    apply(root, make_additions(end, end + 20, exp), {});
    end += 20;
    EXPECT_EQ(Layout::bitvector, layout(root));
    EXPECT_EQ(!is_filter(), _store.has_btree(root));
    if (is_filter()) {
        use_bitvector_weight(exp);
    }
    check(root, exp);
    // add and update while having bitvector
    apply(root, {{5, 99.5f}, {end + 50, -7.0f}}, {});
    exp[5] = 99.5f;
    exp[end + 50] = -7.0f;
    EXPECT_EQ(Layout::bitvector, layout(root));
    if (is_filter()) {
        use_bitvector_weight(exp);
    }
    check(root, exp);
    // shrink below threshold => bitvector is dropped (dropBitVector, makeDegradedTree in filter mode)
    uint32_t remove_end = end - (min_bv_doc_freq - 30);
    apply(root, {}, make_removals(1, remove_end, exp));
    EXPECT_GT(min_bv_doc_freq, exp.size());
    EXPECT_EQ(Layout::btree, layout(root));
    check(root, exp);
    // weights are retained again after bitvector is dropped
    apply(root, {{end + 100, 3.5f}}, {});
    exp[end + 100] = 3.5f;
    check(root, exp);
    _store.clear(root);
}

TEST_P(FloatPostingStoreTest, float_posting_list_is_created_as_bitvector) {
    ExpMap   exp;
    EntryRef root = make_posting_list(1, 2000, exp);
    EXPECT_EQ(Layout::bitvector, layout(root));
    EXPECT_EQ(!is_filter(), _store.has_btree(root));
    if (is_filter()) {
        use_bitvector_weight(exp);
    }
    check(root, exp);
    _store.clear(root);
}

TEST_P(FloatPostingStoreTest, float_posting_lists_are_compacted) {
    std::vector<uint32_t> sequence_lengths{4, 30, huge_sequence_length};
    std::vector<ExpMap>   exps(sequence_lengths.size());
    std::vector<EntryRef> junk;
    auto&                 dictionary = _value_store.get_dictionary();
    for (size_t i = 0; i < sequence_lengths.size(); ++i) {
        uint32_t len = sequence_lengths[i];
        for (uint32_t start = 6; start < 9006; ++start) {
            ExpMap ignore;
            junk.emplace_back(make_posting_list(start, start + len, ignore));
        }
        dictionary.update_posting_list(_value_store.insert(i + 1), _value_store.get_comparator(),
                                       [&](EntryRef) { return make_posting_list(i + 4, i + 4 + len, exps[i]); });
        for (uint32_t start = 9006; start < 11006; ++start) {
            ExpMap ignore;
            junk.emplace_back(make_posting_list(start, start + len, ignore));
        }
    }
    for (auto ref : junk) {
        _store.clear(ref);
    }
    inc_generation();
    std::vector<EntryRef> old_refs;
    for (size_t i = 0; i < sequence_lengths.size(); ++i) {
        old_refs.emplace_back(get_posting_ref(i + 1));
    }
    auto               usage_before = _store.getMemoryUsage();
    CompactionStrategy compaction_strategy(0.05, 0.2);
    bool               compacted_btree_nodes = false;
    bool               compacted_buffers = false;
    bool               done = false;
    for (uint32_t pass = 0; pass < 100; ++pass) {
        _store.update_stat(compaction_strategy);
        auto guard = _gen_handler.takeGuard();
        bool btree_nodes = _store.consider_compact_worst_btree_nodes(compaction_strategy);
        bool buffers = _store.consider_compact_worst_buffers(compaction_strategy);
        if (!btree_nodes && !buffers) {
            done = true;
            break;
        }
        compacted_btree_nodes |= btree_nodes;
        compacted_buffers |= buffers;
        inc_generation();
        // Compacted buffers are still on hold due to guard, blocking next compaction
        if (btree_nodes) {
            EXPECT_FALSE(_store.consider_compact_worst_btree_nodes(compaction_strategy));
        }
        if (buffers) {
            EXPECT_FALSE(_store.consider_compact_worst_buffers(compaction_strategy));
        }
        guard = GenerationGuard();
        inc_generation();
    }
    EXPECT_TRUE(done);
    EXPECT_TRUE(compacted_btree_nodes);
    EXPECT_TRUE(compacted_buffers);
    auto usage_after = _store.getMemoryUsage();
    EXPECT_GT(usage_before.total.deadBytes(), usage_after.total.deadBytes());
    for (size_t i = 0; i < sequence_lengths.size(); ++i) {
        SCOPED_TRACE(sequence_lengths[i]);
        EntryRef ref = get_posting_ref(i + 1);
        EXPECT_NE(old_refs[i], ref);
        if (_store.has_bitvector(ref) && !_store.has_btree(ref)) {
            use_bitvector_weight(exps[i]);
        }
        check(ref, exps[i]);
    }
}

} // namespace search::attribute

GTEST_MAIN_RUN_ALL_TESTS()
