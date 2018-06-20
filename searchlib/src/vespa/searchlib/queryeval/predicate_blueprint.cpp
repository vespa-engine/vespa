// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_blueprint.h"
#include <vespa/searchlib/predicate/predicate_bounds_posting_list.h>
#include <vespa/searchlib/predicate/predicate_interval_posting_list.h>
#include <vespa/searchlib/predicate/predicate_zero_constraint_posting_list.h>
#include <vespa/searchlib/predicate/predicate_zstar_compressed_posting_list.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.predicate.predicate_blueprint");
#include <vespa/searchlib/predicate/predicate_range_term_expander.h>

using search::query::PredicateQuery;
using search::query::PredicateQueryTerm;
using std::make_pair;
using std::pair;
using std::vector;
using vespalib::string;
using namespace search::predicate;

namespace search {
namespace queryeval {

namespace {
typedef PredicateBlueprint::IntervalEntry IntervalEntry;
typedef PredicateBlueprint::BoundsEntry BoundsEntry;

template <typename Entry>
void pushValueDictionaryEntry(const Entry &entry,
                              const SimpleIndex<datastore::EntryRef> &interval_index,
                              vector<IntervalEntry> &interval_entries) {
    const std::string &hash_str = entry.getKey() + "=" + entry.getValue();
    uint64_t feature = PredicateHash::hash64(hash_str);
    auto iterator = interval_index.lookup(feature);
    if (iterator.valid()) {
        size_t sz = interval_index.getPostingListSize(iterator.getData());
        LOG(debug, "postinglist(%s) = (%d).size = %ld", hash_str.c_str(), iterator.getData().ref(), sz);
        interval_entries.push_back({iterator.getData(), entry.getSubQueryBitmap(), sz, feature});
    }
}

struct MyRangeHandler {
    const SimpleIndex<datastore::EntryRef> &interval_index;
    const SimpleIndex<datastore::EntryRef> &bounds_index;
    vector<IntervalEntry> &interval_entries;
    vector<BoundsEntry> &bounds_entries;
    uint64_t subquery_bitmap;

    void handleRange(const string &label) {
        uint64_t feature = PredicateHash::hash64(label);
        auto iterator = interval_index.lookup(feature);
        if (iterator.valid()) {
            size_t sz = interval_index.getPostingListSize(iterator.getData());
            interval_entries.push_back({iterator.getData(), subquery_bitmap, sz, feature});
        }
    }
    void handleEdge(const string &label, uint32_t value) {
        uint64_t feature = PredicateHash::hash64(label);
        auto iterator = bounds_index.lookup(feature);
        if (iterator.valid()) {
            size_t sz = bounds_index.getPostingListSize(iterator.getData());
            bounds_entries.push_back({iterator.getData(), value, subquery_bitmap, sz, feature});
        }
    }
};

template <typename Entry>
void pushRangeDictionaryEntries(
        const Entry &entry,
        const PredicateIndex &index,
        vector<IntervalEntry> &interval_entries,
        vector<BoundsEntry> &bounds_entries) {
    PredicateRangeTermExpander expander(index.getArity());
    MyRangeHandler handler{index.getIntervalIndex(), index.getBoundsIndex(), interval_entries,
                           bounds_entries, entry.getSubQueryBitmap()};
    expander.expand(entry.getKey(), entry.getValue(), handler);
}

void pushZStarPostingList(const SimpleIndex<datastore::EntryRef> &interval_index,
                          vector<IntervalEntry> &interval_entries) {
    uint64_t feature = PredicateIndex::z_star_hash;
    auto iterator = interval_index.lookup(feature);
    if (iterator.valid()) {
        size_t sz = interval_index.getPostingListSize(iterator.getData());
        interval_entries.push_back({iterator.getData(), UINT64_MAX, sz, feature});
    }
}

}  // namespace

void PredicateBlueprint::addPostingToK(uint64_t feature)
{
    const auto &interval_index = _index.getIntervalIndex();
    auto tmp = interval_index.lookup(feature);
    if (__builtin_expect(tmp.valid() && (_cachedFeatures.find(feature) == _cachedFeatures.end()), true)) {
        uint8_t *kVBase = &_kV[0];
        size_t kVSize = _kV.size();
        interval_index.foreach_frozen_key(
                tmp.getData(),
                feature,
                [=](uint32_t doc_id)
                {
                    if (__builtin_expect(doc_id < kVSize, true)) {
                        ++kVBase[doc_id];
                    }
                });
    }
}

void PredicateBlueprint::addBoundsPostingToK(uint64_t feature)
{
    const auto &bounds_index = _index.getBoundsIndex();
    auto tmp = bounds_index.lookup(feature);
    if (__builtin_expect(tmp.valid(), true)) {
        uint8_t *kVBase = &_kV[0];
        size_t kVSize = _kV.size();
        bounds_index.foreach_frozen_key(
                tmp.getData(),
                feature,
                [=](uint32_t doc_id)
                {
                    if (__builtin_expect(doc_id < kVSize, true)) {
                        ++kVBase[doc_id];
                    }
                });
    }
}

void PredicateBlueprint::addZeroConstraintToK()
{
    uint8_t *kVBase = &_kV[0];
    size_t kVSize = _kV.size();
    _index.getZeroConstraintDocs().foreach_key(
            [=](uint32_t doc_id)
            {
                if (__builtin_expect(doc_id < kVSize, true)) {
                    ++kVBase[doc_id];
                }
            });
}

PredicateBlueprint::PredicateBlueprint(const FieldSpecBase &field,
                                       const PredicateAttribute & attribute,
                                       const PredicateQuery &query)
    : ComplexLeafBlueprint(field),
      _attribute(attribute),
      _index(predicate_attribute().getIndex()),
      _kVBacking(),
      _kV(nullptr, 0),
      _cachedFeatures(),
      _interval_dict_entries(),
      _bounds_dict_entries(),
      _zstar_dict_entry(),
      _interval_btree_iterators(),
      _interval_vector_iterators(),
      _bounds_btree_iterators(),
      _bounds_vector_iterators(),
      _zstar_btree_iterator(),
      _zstar_vector_iterator()
{
    const auto &interval_index = _index.getIntervalIndex();
    const auto zero_constraints_docs = _index.getZeroConstraintDocs();
    const PredicateQueryTerm &term = *query.getTerm();
    for (const auto &entry : term.getFeatures()) {
        pushValueDictionaryEntry(entry, interval_index, _interval_dict_entries);
    }
    for (const auto &entry : term.getRangeFeatures()) {
        pushRangeDictionaryEntries(entry, _index, _interval_dict_entries,
                                   _bounds_dict_entries);
    }
    pushZStarPostingList(interval_index, _interval_dict_entries);

    BitVectorCache::KeyAndCountSet keys;
    keys.reserve(_interval_dict_entries.size());
    for (const auto & e : _interval_dict_entries) {
        keys.push_back({e.feature, e.size});
    }
    _cachedFeatures = _index.lookupCachedSet(keys);

    auto it = interval_index.lookup(PredicateIndex::z_star_compressed_hash);
    if (it.valid()) {
        _zstar_dict_entry = it.getData();
    }

    std::sort(_interval_dict_entries.begin(), _interval_dict_entries.end(),
        [&] (const auto & a, const auto & b) {
             return a.size > b.size;
        });

    std::sort(_bounds_dict_entries.begin(), _bounds_dict_entries.end(),
        [&] (const auto & a, const auto & b) {
             return a.size > b.size;
        });


    if (zero_constraints_docs.size() == 0 &&
        _interval_dict_entries.empty() && _bounds_dict_entries.empty() &&
        !_zstar_dict_entry.valid()) {
        setEstimate(HitEstimate(0, true));
    } else {
        setEstimate(HitEstimate(static_cast<uint32_t>(zero_constraints_docs.size()), false));
    }
}

PredicateBlueprint::~PredicateBlueprint() {}

namespace {

    template<typename DictEntry, typename VectorIteratorEntry, typename BTreeIteratorEntry>
    void lookupPostingLists(const std::vector<DictEntry> &dict_entries,
                            std::vector<VectorIteratorEntry> &vector_iterators,
                            std::vector<BTreeIteratorEntry> &btree_iterators,
                            const SimpleIndex<datastore::EntryRef> &index)
    {
        for (const auto &entry : dict_entries) {
            auto vector_iterator = index.getVectorPostingList(entry.feature);
            if (vector_iterator) {
                vector_iterators.push_back(VectorIteratorEntry{*vector_iterator, entry});
            } else {
                auto btree_iterator = index.getBTreePostingList(entry.entry_ref);
                btree_iterators.push_back(BTreeIteratorEntry{btree_iterator, entry});
            }
        }

    };

}

void PredicateBlueprint::fetchPostings(bool) {
    const auto &interval_index = _index.getIntervalIndex();
    const auto &bounds_index = _index.getBoundsIndex();
    lookupPostingLists(_interval_dict_entries, _interval_vector_iterators,
                       _interval_btree_iterators, interval_index);
    lookupPostingLists(_bounds_dict_entries, _bounds_vector_iterators,
                       _bounds_btree_iterators, bounds_index);

    // Lookup zstar interval iterator
    if (_zstar_dict_entry.valid()) {
        auto vector_iterator = interval_index.getVectorPostingList(
                PredicateIndex::z_star_compressed_hash);
        if (vector_iterator) {
            _zstar_vector_iterator.emplace(std::move(*vector_iterator));
        } else {
            _zstar_btree_iterator.emplace(interval_index.getBTreePostingList(_zstar_dict_entry));
        }
    }

    PredicateAttribute::MinFeatureHandle mfh = predicate_attribute().getMinFeatureVector();
    Alloc kv(Alloc::alloc(mfh.second, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE*4));
    _kVBacking.swap(kv);
    _kV = BitVectorCache::CountVector(static_cast<uint8_t *>(_kVBacking.get()), mfh.second);
    _index.computeCountVector(_cachedFeatures, _kV);
    for (const auto & entry : _bounds_dict_entries) {
        addBoundsPostingToK(entry.feature);
    }
    for (const auto & entry : _interval_dict_entries) {
        addPostingToK(entry.feature);
    }
    addPostingToK(PredicateIndex::z_star_compressed_hash);
    addZeroConstraintToK();
}

SearchIterator::UP
PredicateBlueprint::createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool) const {
    const auto &attribute = predicate_attribute();
    PredicateAttribute::MinFeatureHandle mfh = attribute.getMinFeatureVector();
    auto interval_range_vector = attribute.getIntervalRangeVector();
    auto max_interval_range = attribute.getMaxIntervalRange();
    return SearchIterator::UP(new PredicateSearch(mfh.first, interval_range_vector, max_interval_range, _kV,
                                                  createPostingLists(), tfmda));
}

namespace {

    template<typename IteratorEntry, typename PostingListFactory>
    void createPredicatePostingLists(const std::vector<IteratorEntry> &iterator_entries,
                                     std::vector<PredicatePostingList::UP> &posting_lists,
                                     PostingListFactory posting_list_factory)
    {
        for (const auto &entry : iterator_entries) {
            if (entry.iterator.valid()) {
                auto posting_list = posting_list_factory(entry);
                posting_list->setSubquery(entry.entry.subquery);
                posting_lists.emplace_back(PredicatePostingList::UP(posting_list));
            }
        }
    }

}

std::vector<PredicatePostingList::UP> PredicateBlueprint::createPostingLists() const {
    size_t total_size = _interval_btree_iterators.size() + _interval_vector_iterators.size() +
                        _bounds_btree_iterators.size() + _bounds_vector_iterators.size() + 2;
    std::vector<PredicatePostingList::UP> posting_lists;
    posting_lists.reserve(total_size);
    const auto &interval_store = _index.getIntervalStore();

    createPredicatePostingLists(
            _interval_vector_iterators, posting_lists,
            [&] (const IntervalIteratorEntry<VectorIterator> &entry) {
                return new PredicateIntervalPostingList<VectorIterator>(interval_store, entry.iterator);
            });

    createPredicatePostingLists(
            _interval_btree_iterators, posting_lists,
            [&] (const IntervalIteratorEntry<BTreeIterator> &entry) {
                return new PredicateIntervalPostingList<BTreeIterator>(interval_store, entry.iterator);
            });

    createPredicatePostingLists(
            _bounds_vector_iterators, posting_lists,
            [&] (const BoundsIteratorEntry<VectorIterator> &entry) {
                return new PredicateBoundsPostingList<VectorIterator>(interval_store, entry.iterator,
                                                                      entry.entry.value_diff);
            });

    createPredicatePostingLists(
            _bounds_btree_iterators, posting_lists,
            [&] (const BoundsIteratorEntry<BTreeIterator> &entry) {
                return new PredicateBoundsPostingList<BTreeIterator>(interval_store, entry.iterator,
                                                                     entry.entry.value_diff);
            });

    if (_zstar_vector_iterator && _zstar_vector_iterator->valid()) {
        auto posting_list = PredicatePostingList::UP(
                new PredicateZstarCompressedPostingList<VectorIterator>(interval_store, *_zstar_vector_iterator));
        posting_lists.emplace_back(std::move(posting_list));
    } else if (_zstar_btree_iterator && _zstar_btree_iterator->valid()) {
        auto posting_list = PredicatePostingList::UP(
                new PredicateZstarCompressedPostingList<BTreeIterator>(interval_store, *_zstar_btree_iterator));
        posting_lists.emplace_back(std::move(posting_list));
    }
    auto iterator = _index.getZeroConstraintDocs().begin();
    if (iterator.valid()) {
        auto posting_list = PredicatePostingList::UP(new PredicateZeroConstraintPostingList(iterator));
        posting_lists.emplace_back(std::move(posting_list));
    }
    return posting_lists;
}
}  // namespace search::queryeval
}  // namespace search
