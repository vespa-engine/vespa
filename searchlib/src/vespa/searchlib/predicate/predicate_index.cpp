// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.predicate.predicate_index");
#include "predicate_index.h"
#include "predicate_hash.h"

using search::datastore::EntryRef;
using vespalib::DataBuffer;
using std::vector;

namespace search {
namespace predicate {

const vespalib::string PredicateIndex::z_star_attribute_name("z-star");
const uint64_t PredicateIndex::z_star_hash(
        PredicateHash::hash64(PredicateIndex::z_star_attribute_name));
const vespalib::string PredicateIndex::z_star_compressed_attribute_name("z-star-compressed");
const uint64_t PredicateIndex::z_star_compressed_hash(
        PredicateHash::hash64(PredicateIndex::z_star_compressed_attribute_name));

template <>
void PredicateIndex::addPosting<Interval>(
        uint64_t feature, uint32_t doc_id, EntryRef ref) {
    _interval_index.addPosting(feature, doc_id, ref);
}
template <>
void PredicateIndex::addPosting<IntervalWithBounds>(
        uint64_t feature, uint32_t doc_id, EntryRef ref) {
    _bounds_index.addPosting(feature, doc_id, ref);
}

template <typename IntervalT>
void PredicateIndex::indexDocumentFeatures(
        uint32_t doc_id, const PredicateIndex::FeatureMap<IntervalT> &interval_map) {
    if (interval_map.empty()) {
        return;
    }
    for (const auto &map_entry : interval_map) {
        uint64_t feature = map_entry.first;
        const auto &interval_list = map_entry.second;
        datastore::EntryRef ref = _interval_store.insert(interval_list);
        assert(ref.valid());
        addPosting<IntervalT>(feature, doc_id, ref);
        _cache.set(feature, doc_id, true);
    }
}

namespace {
constexpr double THRESHOLD_USE_BIT_VECTOR_CACHE = 0.1;

// PostingSerializer that writes intervals from interval store based
// on the EntryRef that is to be serialized.
template <typename IntervalT>
class IntervalSerializer : public PostingSerializer<EntryRef> {
    const PredicateIntervalStore &_store;
public:
    IntervalSerializer(const PredicateIntervalStore &store) : _store(store) {}
    void serialize(const EntryRef &ref, vespalib::DataBuffer &buffer) const override {
        uint32_t size;
        IntervalT single_buf;
        const IntervalT *interval = _store.get(ref, size, &single_buf);
        buffer.writeInt16(size);
        for (uint32_t i = 0; i < size; ++i) {
            interval[i].serialize(buffer);
        }
    }
};

// PostingDeserializer that writes intervals to interval store and
// returns an EntryRef to be stored in the PredicateIndex.
template <typename IntervalT>
class IntervalDeserializer : public PostingDeserializer<EntryRef> {
    PredicateIntervalStore &_store;
public:
    IntervalDeserializer(PredicateIntervalStore &store) : _store(store) {}
    EntryRef deserialize(vespalib::DataBuffer &buffer) override {
        std::vector<IntervalT> intervals;
        size_t size = buffer.readInt16();
        for (uint32_t i = 0; i < size; ++i) {
            intervals.push_back(IntervalT::deserialize(buffer));
        }
        return _store.insert(intervals);
    }
};

}  // namespace

PredicateIndex::PredicateIndex(GenerationHandler &generation_handler, GenerationHolder &genHolder,
                               const DocIdLimitProvider &limit_provider,
                               const SimpleIndexConfig &simple_index_config, uint32_t arity)
    : _arity(arity),
      _generation_handler(generation_handler),
      _limit_provider(limit_provider),
      _interval_index(genHolder, limit_provider, simple_index_config),
      _bounds_index(genHolder, limit_provider, simple_index_config),
      _interval_store(),
      _zero_constraint_docs(),
      _features_store(arity),
      _cache(genHolder)
{
}

PredicateIndex::PredicateIndex(GenerationHandler &generation_handler, GenerationHolder &genHolder,
                               const DocIdLimitProvider &limit_provider,
                               const SimpleIndexConfig &simple_index_config, DataBuffer &buffer,
                               SimpleIndexDeserializeObserver<> & observer, uint32_t version)
    : _arity(0),
      _generation_handler(generation_handler),
      _limit_provider(limit_provider),
      _interval_index(genHolder, limit_provider, simple_index_config),
      _bounds_index(genHolder, limit_provider, simple_index_config),
      _interval_store(),
      _zero_constraint_docs(),
      _features_store(buffer),
      _cache(genHolder)
{
    _arity = buffer.readInt16();
    uint32_t zero_constraint_doc_count = buffer.readInt32();
    typename BTreeSet::Builder builder(_zero_constraint_docs.getAllocator());
    for (size_t i = 0; i < zero_constraint_doc_count; ++i) {
        uint32_t raw_id = buffer.readInt32();
        uint32_t doc_id = version == 0 ? raw_id >> 6 : raw_id;
        builder.insert(doc_id, btree::BTreeNoLeafData::_instance);
        observer.notifyInsert(0, doc_id, 0);
    }
    _zero_constraint_docs.assign(builder);
    IntervalDeserializer<Interval> interval_deserializer(_interval_store);
    _interval_index.deserialize(buffer, interval_deserializer, observer, version);
    IntervalDeserializer<IntervalWithBounds>
        bounds_deserializer(_interval_store);
    _bounds_index.deserialize(buffer, bounds_deserializer, observer, version);
    commit();
}

PredicateIndex::~PredicateIndex() {}

void PredicateIndex::serialize(DataBuffer &buffer) const {
    _features_store.serialize(buffer);
    buffer.writeInt16(_arity);
    buffer.writeInt32(_zero_constraint_docs.size());
    for (auto it = _zero_constraint_docs.begin(); it.valid(); ++it) {
        buffer.writeInt32(it.getKey());
    }
    IntervalSerializer<Interval> interval_serializer(_interval_store);
    _interval_index.serialize(buffer, interval_serializer);
    IntervalSerializer<IntervalWithBounds> bounds_serializer(_interval_store);
    _bounds_index.serialize(buffer, bounds_serializer);
}

void PredicateIndex::onDeserializationCompleted() {
    _interval_index.promoteOverThresholdVectors();
    _bounds_index.promoteOverThresholdVectors();
}

void PredicateIndex::indexDocument(uint32_t doc_id, const PredicateTreeAnnotations &annotations) {
    indexDocumentFeatures(doc_id, annotations.interval_map);
    indexDocumentFeatures(doc_id, annotations.bounds_map);
    _features_store.insert(annotations, doc_id);
}

void PredicateIndex::indexEmptyDocument(uint32_t doc_id)
{
    _zero_constraint_docs.insert(doc_id, btree::BTreeNoLeafData::_instance);
}

namespace {
void removeFromIndex(
        uint64_t feature, uint32_t doc_id, SimpleIndex<datastore::EntryRef> &index, PredicateIntervalStore &interval_store)
{
    auto result = index.removeFromPostingList(feature, doc_id);
    if (result.second) { // Posting was removed
        auto ref = result.first;
        assert(ref.valid());
        interval_store.remove(ref);
    }
}

class DocIdIterator : public PopulateInterface::Iterator {
public:
    using BTreeIterator = SimpleIndex<datastore::EntryRef>::BTreeIterator;

    DocIdIterator(BTreeIterator it) : _it(it) { }
    int32_t getNext() override {
        if (_it.valid()) {
            uint32_t docId = _it.getKey();
            ++_it;
            return docId;
        }
        return -1;
    }
private:
    BTreeIterator _it;
};

}  // namespace

void PredicateIndex::removeDocument(uint32_t doc_id) {
    _zero_constraint_docs.remove(doc_id);

    auto features = _features_store.get(doc_id);
    if (!features.empty()) {
        for (auto feature : features) {
            removeFromIndex(feature, doc_id, _interval_index,
                            _interval_store);
            removeFromIndex(feature, doc_id, _bounds_index,
                            _interval_store);
        }
        _cache.removeIndex(doc_id);
    }
    _features_store.remove(doc_id);
}

void PredicateIndex::commit() {
    _interval_index.commit();
    _bounds_index.commit();
    _zero_constraint_docs.getAllocator().freeze();
}

void PredicateIndex::trimHoldLists(generation_t used_generation) {
    _interval_index.trimHoldLists(used_generation);
    _bounds_index.trimHoldLists(used_generation);
    _interval_store.trimHoldLists(used_generation);
    _zero_constraint_docs.getAllocator().trimHoldLists(used_generation);
}

void PredicateIndex::transferHoldLists(generation_t generation) {
    _interval_index.transferHoldLists(generation);
    _bounds_index.transferHoldLists(generation);
    _interval_store.transferHoldLists(generation);
    _zero_constraint_docs.getAllocator().transferHoldLists(generation);
}

MemoryUsage PredicateIndex::getMemoryUsage() const {
    // TODO Include bit vector cache memory usage
    MemoryUsage combined;
    combined.merge(_interval_index.getMemoryUsage());
    combined.merge(_bounds_index.getMemoryUsage());
    combined.merge(_zero_constraint_docs.getMemoryUsage());
    combined.merge(_interval_store.getMemoryUsage());
    combined.merge(_features_store.getMemoryUsage());
    return combined;
}

PopulateInterface::Iterator::UP
PredicateIndex::lookup(uint64_t key) const
{
    auto dictIterator = _interval_index.lookup(key);
    if (dictIterator.valid()) {
        auto it = _interval_index.getBTreePostingList(dictIterator.getData());
        if (it.valid()) {
            return PopulateInterface::Iterator::UP(new DocIdIterator(it));
        }
    }
    return PopulateInterface::Iterator::UP();
}

void
PredicateIndex::populateIfNeeded(size_t doc_id_limit)
{
    if ( _cache.needPopulation()) {
        _cache.populate(doc_id_limit, *this);
    }
}

BitVectorCache::KeySet
PredicateIndex::lookupCachedSet(const BitVectorCache::KeyAndCountSet & keys) const
{
    // Don't count documents using bit vector if combined length is less than threshold
    uint64_t total_length = 0;
    auto cached_keys = _cache.lookupCachedSet(keys);
    for (const auto &p : keys) {
        if (cached_keys.find(p.first) != cached_keys.end()) {
            total_length += p.second;
        }
    }
    double fill_ratio = total_length / static_cast<double>(_limit_provider.getDocIdLimit());
    if (fill_ratio < THRESHOLD_USE_BIT_VECTOR_CACHE) {
        cached_keys.clear();
    }
    return cached_keys;
}

void
PredicateIndex::computeCountVector(BitVectorCache::KeySet & keys, BitVectorCache::CountVector & v) const
{
    _cache.computeCountVector(keys, v);
}


void
PredicateIndex::adjustDocIdLimit(uint32_t docId)
{
    _cache.adjustDocIdLimit(docId);
}

}  // namespace predicate
}  // namespace search
