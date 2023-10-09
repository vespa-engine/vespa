// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_index.

#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/simple_index.hpp>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>

#include <vespa/log/log.h>
LOG_SETUP("predicate_index_test");

using namespace search;
using namespace search::predicate;
using std::make_pair;
using std::pair;
using std::vector;

namespace {

struct DummyDocIdLimitProvider : public DocIdLimitProvider {
    virtual uint32_t getDocIdLimit() const override { return 10000; }
    virtual uint32_t getCommittedDocIdLimit() const override { return 10000; }
};

vespalib::GenerationHandler generation_handler;
vespalib::GenerationHolder generation_holder;
DummyDocIdLimitProvider dummy_provider;
SimpleIndexConfig simple_index_config;

TEST("require that PredicateIndex can index empty documents") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
    index.indexEmptyDocument(2);
    index.commit();
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());
}

TEST("require that indexDocument don't index empty documents") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
    PredicateTreeAnnotations annotations;
    index.indexDocument(3, annotations);
    index.commit();
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
}

TEST("require that PredicateIndex can remove empty documents") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
    index.indexEmptyDocument(2);
    index.commit();
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());
    index.removeDocument(2);
    index.commit();
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
}

TEST("require that indexing the same empty document multiple times is ok") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
    index.indexEmptyDocument(2);
    index.commit();
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());
    index.indexEmptyDocument(2);
    index.commit();
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());
}

void indexFeature(PredicateIndex &attr, uint32_t doc_id, int min_feature,
                  const vector<pair<uint64_t, Interval>> &intervals,
                  const vector<pair<uint64_t, IntervalWithBounds>> &bounds) {
    PredicateTreeAnnotations annotations(min_feature);
    for (auto &p : intervals) {
        annotations.interval_map[p.first] = vector<Interval>{{p.second}};
        annotations.features.push_back(p.first);
    }
    for (auto &p : bounds) {
        annotations.bounds_map[p.first] = vector<IntervalWithBounds>{{p.second}};
        annotations.features.push_back(p.first);
    }
    attr.indexDocument(doc_id, annotations);
}

PredicateIndex::BTreeIterator
lookupPosting(const PredicateIndex &index, uint64_t hash) {
    const auto &interval_index = index.getIntervalIndex();
    auto it = interval_index.lookup(hash);
    ASSERT_TRUE(it.valid());
    auto entry = it.getData();
    EXPECT_TRUE(entry.valid());

    auto posting_it = interval_index.getBTreePostingList(entry);
    ASSERT_TRUE(posting_it.valid());
    return posting_it;
}

const int min_feature = 3;
const uint32_t doc_id = 2;
const uint64_t hash = 0x12345;
const uint64_t hash2 = 0x3456;
const Interval interval = {0x0001ffff};
const IntervalWithBounds bounds = {0x0001ffff, 0x03};
Interval single_buf;

TEST("require that PredicateIndex can index document") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    indexFeature(index, doc_id, min_feature, {{hash, interval}}, {});
    index.commit();
    auto posting_it = lookupPosting(index, hash);
    EXPECT_EQUAL(doc_id, posting_it.getKey());
    uint32_t size;
    const auto &interval_list = index.getIntervalStore().get(posting_it.getData(), size, &single_buf);
    ASSERT_EQUAL(1u, size);
    EXPECT_EQUAL(interval, interval_list[0]);
}

TEST("require that bit vector cache is initialized correctly") {
    BitVectorCache::KeyAndCountSet keySet;
    keySet.emplace_back(hash, dummy_provider.getDocIdLimit()/2);
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    indexFeature(index, doc_id, min_feature, {{hash, interval}}, {});
    index.requireCachePopulation();
    index.populateIfNeeded(dummy_provider.getDocIdLimit());
    EXPECT_TRUE(index.lookupCachedSet(keySet).empty());
    index.commit();
    EXPECT_TRUE(index.getIntervalIndex().lookup(hash).valid());
    EXPECT_TRUE(index.lookupCachedSet(keySet).empty());

    index.requireCachePopulation();
    index.populateIfNeeded(dummy_provider.getDocIdLimit());
    EXPECT_FALSE(index.lookupCachedSet(keySet).empty());
}


TEST("require that PredicateIndex can index document with bounds") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    indexFeature(index, doc_id, min_feature, {}, {{hash, bounds}});
    index.commit();

    const auto &bounds_index = index.getBoundsIndex();
    auto it = bounds_index.lookup(hash);
    ASSERT_TRUE(it.valid());
    auto entry = it.getData();
    EXPECT_TRUE(entry.valid());

    auto posting_it = bounds_index.getBTreePostingList(entry);
    ASSERT_TRUE(posting_it.valid());
    EXPECT_EQUAL(doc_id, posting_it.getKey());

    uint32_t size;
    IntervalWithBounds single;
    const auto &interval_list = index.getIntervalStore().get(posting_it.getData(), size, &single);
    ASSERT_EQUAL(1u, size);
    EXPECT_EQUAL(bounds, interval_list[0]);
}

TEST("require that PredicateIndex can index multiple documents with the same feature") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    for (uint32_t id = 1; id < 100; ++id) {
        indexFeature(index, id, min_feature, {{hash, interval}}, {});
    }
    index.commit();

    auto posting_it = lookupPosting(index, hash);
    for (uint32_t id = 1; id < 100; ++id) {
        ASSERT_TRUE(posting_it.valid());
        EXPECT_EQUAL(id, posting_it.getKey());
        uint32_t size;
        const auto &interval_list = index.getIntervalStore().get(posting_it.getData(), size, &single_buf);
        ASSERT_EQUAL(1u, size);
        EXPECT_EQUAL(interval, interval_list[0]);
        ++posting_it;
    }
    ASSERT_FALSE(posting_it.valid());
}

TEST("require that PredicateIndex can remove indexed documents") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    indexFeature(index, doc_id, min_feature, {{hash, interval}}, {{hash2, bounds}});
    index.removeDocument(doc_id);
    index.commit();
    auto it = index.getIntervalIndex().lookup(hash);
    ASSERT_FALSE(it.valid());
    auto it2 = index.getBoundsIndex().lookup(hash2);
    ASSERT_FALSE(it2.valid());

    // Remove again. Nothing should happen.
    index.removeDocument(doc_id);
}

TEST("require that PredicateIndex can remove multiple documents") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    const auto &interval_index = index.getIntervalIndex();
    EXPECT_FALSE(interval_index.lookup(hash).valid());
    for (uint32_t id = 1; id < 100; ++id) {
        indexFeature(index, id, min_feature, {{hash, interval}}, {});
    }
    index.commit();
    for (uint32_t id = 1; id < 110; ++id) {
        index.removeDocument(id);
        index.commit();
        auto it = interval_index.lookup(hash);
        if (id < 99) {
            ASSERT_TRUE(it.valid());
        } else {
            ASSERT_FALSE(it.valid());
        }
    }
}

TEST("require that PredicateIndex can remove multiple documents with multiple features") {
    vector<pair<uint64_t, Interval>> intervals;
    vector<pair<uint64_t, IntervalWithBounds>> bounds_intervals;
    for (int i = 0; i < 100; ++i) {
        intervals.push_back(make_pair(hash + i, interval));
        bounds_intervals.push_back(make_pair(hash2 + i, bounds));
    }
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    const auto &interval_index = index.getIntervalIndex();
    EXPECT_FALSE(interval_index.lookup(hash).valid());
    for (uint32_t id = 1; id < 100; ++id) {
        indexFeature(index, id, id, intervals, bounds_intervals);
    }
    index.commit();
    for (uint32_t id = 1; id < 100; ++id) {
        index.removeDocument((id + 50) % 99 + 1);
        index.commit();
        auto it = interval_index.lookup(hash);
        if (id < 99) {
            ASSERT_TRUE(it.valid());
        } else {
            ASSERT_FALSE(it.valid());
        }
    }
}

// Helper function for next test.
template <typename Iterator, typename IntervalT>
void checkAllIntervals(Iterator posting_it, IntervalT expected_interval,
                       const PredicateIntervalStore &interval_store)
{
    for (uint32_t id = 1; id < 100u; ++id) {
        ASSERT_TRUE(posting_it.valid());
        EXPECT_EQUAL(id, posting_it.getKey());
        vespalib::datastore::EntryRef ref = posting_it.getData();
        ASSERT_TRUE(ref.valid());
        uint32_t size;
        IntervalT single;
        const IntervalT *read_interval = interval_store.get(ref, size, &single);
        EXPECT_EQUAL(1u, size);
        EXPECT_EQUAL(expected_interval, read_interval[0]);
        ++posting_it;
    }
}

namespace {
struct DocIdLimitFinder : SimpleIndexDeserializeObserver<> {
    uint32_t &_doc_id_limit;
    DocIdLimitFinder(uint32_t &doc_id_limit) : _doc_id_limit(doc_id_limit)
    {
        doc_id_limit = 0u;
    }
    void notifyInsert(uint64_t, uint32_t doc_id, uint32_t) override {
        _doc_id_limit = std::max(_doc_id_limit, doc_id);
    }
};
}

TEST("require that PredicateIndex can be (de)serialized") {
    vector<pair<uint64_t, Interval>> intervals;
    vector<pair<uint64_t, IntervalWithBounds>> bounds_intervals;
    for (int i = 0; i < 100; ++i) {
        intervals.push_back(make_pair(hash + i, interval));
        bounds_intervals.push_back(make_pair(hash2 + i, bounds));
    }
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 8);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    for (uint32_t id = 1; id < 100; ++id) {
        indexFeature(index, id, id, intervals, bounds_intervals);
        index.indexEmptyDocument(id + 100);
    }
    index.commit();

    vespalib::DataBuffer buffer;
    index.serialize(buffer);
    uint32_t doc_id_limit;
    DocIdLimitFinder finder(doc_id_limit);
    PredicateIndex index2(generation_holder, dummy_provider, simple_index_config,
                          buffer, finder, PredicateAttribute::PREDICATE_ATTRIBUTE_VERSION);
    const PredicateIntervalStore &interval_store = index2.getIntervalStore();
    EXPECT_EQUAL(199u, doc_id_limit);

    EXPECT_EQUAL(index.getArity(), index2.getArity());
    EXPECT_EQUAL(index.getZeroConstraintDocs().size(),index2.getZeroConstraintDocs().size());
    {
        auto it = index2.getZeroConstraintDocs().begin();
        for (uint32_t i = 1; i < 100u; ++i) {
            TEST_STATE(vespalib::make_string("%d", i).c_str());
            ASSERT_TRUE(it.valid());
            EXPECT_EQUAL(i + 100, it.getKey());
            ++it;
        }
        EXPECT_FALSE(it.valid());
    }

    const auto &interval_index = index2.getIntervalIndex();
    const auto &bounds_index = index2.getBoundsIndex();
    for (int i = 0; i < 100; ++i) {
        {
            auto it = interval_index.lookup(hash + i);
            ASSERT_TRUE(it.valid());
            auto posting_it = interval_index.getBTreePostingList(it.getData());
            checkAllIntervals(posting_it, interval, interval_store);
        }
        {
            auto it = bounds_index.lookup(hash2 + i);
            ASSERT_TRUE(it.valid());
            auto posting_it = bounds_index.getBTreePostingList(it.getData());
            checkAllIntervals(posting_it, bounds, interval_store);
        }
    }
}

TEST("require that DocumentFeaturesStore is restored on deserialization") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    EXPECT_FALSE(index.getIntervalIndex().lookup(hash).valid());
    indexFeature(index, doc_id, min_feature, {{hash, interval}}, {{hash2, bounds}});
    vespalib::DataBuffer buffer;
    index.serialize(buffer);
    uint32_t doc_id_limit;
    DocIdLimitFinder finder(doc_id_limit);
    PredicateIndex index2(generation_holder, dummy_provider, simple_index_config,
                          buffer, finder, PredicateAttribute::PREDICATE_ATTRIBUTE_VERSION);
    const auto &interval_index = index2.getIntervalIndex();
    const auto &bounds_index = index2.getBoundsIndex();
    EXPECT_EQUAL(doc_id, doc_id_limit);

    auto it = interval_index.lookup(hash);
    EXPECT_TRUE(it.valid());
    auto it2 = bounds_index.lookup(hash2);
    EXPECT_TRUE(it2.valid());

    index2.removeDocument(doc_id);
    index2.commit();

    it = interval_index.lookup(hash);
    EXPECT_FALSE(it.valid());
    it2 = bounds_index.lookup(hash2);
    EXPECT_FALSE(it2.valid());
}

TEST("require that hold lists are attempted emptied on destruction") {
    PredicateIndex index(generation_holder, dummy_provider, simple_index_config, 10);
    indexFeature(index, doc_id, min_feature, {{hash, interval}}, {{hash2, bounds}});
    {
        auto guard = generation_handler.takeGuard();
        index.removeDocument(doc_id);
        index.commit();
    }
    // No assert on index destruction.
}
}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
