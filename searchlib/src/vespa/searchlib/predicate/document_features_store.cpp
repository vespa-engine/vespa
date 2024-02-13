// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_features_store.h"
#include "predicate_range_expander.h"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.hpp>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.hpp>

using vespalib::btree::BTreeNoLeafData;
using vespalib::datastore::ArrayStore;
using vespalib::datastore::ArrayStoreConfig;
using vespalib::datastore::EntryRef;
using vespalib::DataBuffer;
using vespalib::stringref;
using std::unordered_map;
using std::vector;

namespace search::predicate {

namespace {

constexpr double array_store_grow_factor = 1.03;
constexpr uint32_t array_store_max_type_id = 300;
constexpr float alloc_grow_factor = 0.2;
constexpr size_t max_buffer_size = ArrayStoreConfig::default_max_buffer_size;

}

DocumentFeaturesStore::FeaturesStoreTypeMapper
DocumentFeaturesStore::make_features_store_type_mapper()
{
    return FeaturesStoreTypeMapper(array_store_max_type_id, array_store_grow_factor, max_buffer_size);
}

ArrayStoreConfig
DocumentFeaturesStore::make_features_store_config()
{
    auto mapper = make_features_store_type_mapper();
    auto result = FeaturesStore::optimizedConfigForHugePage(array_store_max_type_id, mapper, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE, vespalib::alloc::MemoryAllocator::PAGE_SIZE, max_buffer_size, 8_Ki, alloc_grow_factor);
    result.enable_free_lists(true);
    return result;
}

DocumentFeaturesStore::RangesStoreTypeMapper
DocumentFeaturesStore::make_ranges_store_type_mapper()
{
    return RangesStoreTypeMapper(array_store_max_type_id, array_store_grow_factor, max_buffer_size);
}

ArrayStoreConfig
DocumentFeaturesStore::make_ranges_store_config()
{
    auto mapper = make_ranges_store_type_mapper();
    auto result = RangesStore::optimizedConfigForHugePage(array_store_max_type_id, mapper, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE, vespalib::alloc::MemoryAllocator::PAGE_SIZE, max_buffer_size, 8_Ki, alloc_grow_factor);
    result.enable_free_lists(true);
    return result;
}

DocumentFeaturesStore::DocumentFeaturesStore(uint32_t arity)
    : _refs(),
      _features(make_features_store_config(),
                std::shared_ptr<vespalib::alloc::MemoryAllocator>(),
                make_features_store_type_mapper()),
      _ranges(make_ranges_store_config(),
                std::shared_ptr<vespalib::alloc::MemoryAllocator>(),
                make_ranges_store_type_mapper()),
      _word_store(),
      _word_index(),
      _arity(arity)
{
}

namespace {
template <typename KeyComp, typename WordIndex>
void
deserializeWords(DataBuffer &buffer, memoryindex::WordStore &word_store, WordIndex &word_index, vector<EntryRef> &word_refs)
{
    uint32_t word_list_size = buffer.readInt32();
    word_refs.reserve(word_list_size);
    vector<char> word;
    KeyComp cmp(word_store, "");
    for (uint32_t i = 0; i < word_list_size; ++i) {
        uint32_t size = buffer.readInt32();
        word.clear();
        word.resize(size);
        buffer.readBytes(&word[0], size);
        word_refs.push_back(word_store.addWord(stringref(&word[0], size)));
        word_index.insert(word_refs.back(), BTreeNoLeafData(), cmp);
    }
}

template <typename RefsVector, typename RangesStore>
void
deserialize_ranges(DataBuffer &buffer, vector<EntryRef> &word_refs, RefsVector& refs, RangesStore& ranges)
{
    using Range = typename RangesStore::ElemType;
    std::vector<Range> range_vector;
    uint32_t ranges_size = buffer.readInt32();
    for (uint32_t i = 0; i < ranges_size; ++i) {
        uint32_t doc_id = buffer.readInt32();
        if (doc_id >= refs.size()) {
            refs.resize(doc_id + 1);
        }
        auto& cur_refs = refs[doc_id];
        assert(!cur_refs._ranges.valid());
        uint32_t range_count = buffer.readInt32();
        range_vector.clear();
        range_vector.reserve(range_count);
        for (uint32_t j = 0; j < range_count; ++j) {
            Range range;
            range.label_ref = word_refs[buffer.readInt32()];
            range.from = buffer.readInt64();
            range.to = buffer.readInt64();
            range_vector.push_back(range);
        }
        cur_refs._ranges = ranges.add(range_vector);
    }
}

template <typename RefsVector, typename FeaturesStore>
void
deserialize_features(DataBuffer &buffer, RefsVector& refs, FeaturesStore &features)
{
    std::vector<typename FeaturesStore::ElemType> feature_vector;
    uint32_t docs_size = buffer.readInt32();
    for (uint32_t i = 0; i < docs_size; ++i) {
        uint32_t doc_id = buffer.readInt32();
        if (doc_id >= refs.size()) {
            refs.resize(doc_id + 1);
        }
        auto& cur_refs = refs[doc_id];
        assert(!cur_refs._features.valid());
        uint32_t feature_count = buffer.readInt32();
        feature_vector.clear();
        feature_vector.reserve(feature_count);
        for (uint32_t j = 0; j < feature_count; ++j) {
            feature_vector.push_back(buffer.readInt64());
        }
        cur_refs._features = features.add(feature_vector);
    }
}
}  // namespace

DocumentFeaturesStore::DocumentFeaturesStore(DataBuffer &buffer)
    : DocumentFeaturesStore(0) {
    _arity = buffer.readInt16();

    vector<EntryRef> word_refs;
    deserializeWords<KeyComp>(buffer, _word_store, _word_index, word_refs);
    deserialize_ranges(buffer, word_refs, _refs, _ranges);
    deserialize_features(buffer, _refs, _features);
}

DocumentFeaturesStore::~DocumentFeaturesStore() {
    _word_index.disableFreeLists();
    _word_index.disable_entry_hold_list();
    _word_index.getAllocator().freeze();
    _word_index.clear();
}

void
DocumentFeaturesStore::insert(const PredicateTreeAnnotations &annotations, uint32_t doc_id) {
    assert(doc_id != 0);
    if (doc_id >= _refs.size()) {
        _refs.resize(doc_id + 1);
    }
    auto& cur_refs = _refs[doc_id];
    if (!annotations.features.empty()) {
        auto old_features_ref = cur_refs._features;
        auto old_features = _features.get(old_features_ref);
        std::vector<uint64_t> features(old_features.begin(), old_features.end());
        size_t size = features.size();
        features.resize(size + annotations.features.size());
        memcpy(&features[size], &annotations.features[0],
               annotations.features.size() * sizeof(annotations.features[0]));
        cur_refs._features = _features.add(features);
        if (old_features_ref.valid()) {
            _features.remove(old_features_ref);
        }
    }
    if (!annotations.range_features.empty()) {
        auto old_ranges_ref = cur_refs._ranges;
        auto old_ranges =  _ranges.get(old_ranges_ref);
        std::vector<Range> ranges(old_ranges.begin(), old_ranges.end());
        for (const auto &range : annotations.range_features) {
            stringref word(range.label.data, range.label.size);
            KeyComp cmp(_word_store, word);
            auto word_it = _word_index.find(vespalib::datastore::EntryRef(), cmp);
            vespalib::datastore::EntryRef ref;
            if (word_it.valid()) {
                ref = word_it.getKey();
            } else {
                ref = _word_store.addWord(word);
                _word_index.insert(ref, BTreeNoLeafData(), cmp);
            }
            ranges.emplace_back(ref, range.from, range.to);
        }
        cur_refs._ranges = _ranges.add(ranges);
        if (old_ranges_ref.valid()) {
            _ranges.remove(old_ranges_ref);
        }
    }
}

DocumentFeaturesStore::FeatureSet
DocumentFeaturesStore::get(uint32_t docId) const {
    FeatureSet features;
    if (docId >= _refs.size()) {
        return features;
    }
    auto& cur_refs = _refs[docId];
    if (cur_refs._features.valid()) {
        auto old_features = _features.get(cur_refs._features);
        features.insert(old_features.begin(), old_features.end());
    }
    if (cur_refs._ranges.valid()) {
        auto old_ranges = _ranges.get(cur_refs._ranges);
        for (auto range : old_ranges) {
            const char *label = _word_store.getWord(range.label_ref);
            PredicateRangeExpander::expandRange(label, range.from, range.to, _arity,
                                                std::inserter(features, features.end()));
        }
    }
    return features;
}

void
DocumentFeaturesStore::remove(uint32_t doc_id) {
    if (doc_id >= _refs.size()) {
        return;
    }
    auto& cur_refs = _refs[doc_id];
    auto old_features_ref = cur_refs._features;
    if (old_features_ref.valid()) {
        _features.remove(old_features_ref);
        cur_refs._features = EntryRef();
    }
    auto old_ranges_ref = cur_refs._ranges;
    if (old_ranges_ref.valid()) {
        _ranges.remove(old_ranges_ref);
        cur_refs._ranges = EntryRef();
    }
}

void
DocumentFeaturesStore::reclaim_memory(generation_t oldest_used_gen)
{
    _features.reclaim_memory(oldest_used_gen);
    _ranges.reclaim_memory(oldest_used_gen);
}

void
DocumentFeaturesStore::assign_generation(generation_t current_gen)
{
    _features.assign_generation(current_gen);
    _ranges.assign_generation(current_gen);
}

vespalib::MemoryUsage
DocumentFeaturesStore::getMemoryUsage() const {
    vespalib::MemoryUsage usage;
    usage.incAllocatedBytes(_refs.capacity() * sizeof(Refs));
    usage.incUsedBytes(_refs.size() * sizeof(Refs));
    usage.merge(_features.getMemoryUsage());
    usage.merge(_ranges.getMemoryUsage());
    usage.merge(_word_store.getMemoryUsage());
    usage.merge(_word_index.getMemoryUsage());

    return usage;
}

namespace {

template <typename RefsVector, typename RangesStore>
void
findUsedWords(const RefsVector& refs, const RangesStore& ranges,
              unordered_map<uint32_t, uint32_t> &word_map,
              vector<EntryRef> &word_list)
{
    for (auto& cur_refs : refs) {
        auto ranges_ref = cur_refs._ranges;
        if (ranges_ref.valid()) {
            auto range_vector = ranges.get(ranges_ref);
            for (const auto& range : range_vector) {
                if (!word_map.count(range.label_ref.ref())) {
                    word_map[range.label_ref.ref()] = word_list.size();
                    word_list.push_back(range.label_ref);
                }
            }
        }
    }
}

void
serializeWords(DataBuffer &buffer, const vector<EntryRef> &word_list,
               const memoryindex::WordStore &word_store)
{
    buffer.writeInt32(word_list.size());
    for (const auto &word_ref : word_list) {
        const char *word = word_store.getWord(word_ref);
        uint32_t len = strlen(word);
        buffer.writeInt32(len);
        buffer.writeBytes(word, len);
    }
}

template <typename RefsVector, typename RangesStore>
void
serialize_ranges(DataBuffer &buffer, const RefsVector& refs, const RangesStore &ranges,
                unordered_map<uint32_t, uint32_t> &word_map)
{
    uint32_t ranges_size = 0;
    if (!refs.empty()) {
        assert(!refs.front()._ranges.valid());
        for (auto& cur_refs : refs) {
            if (cur_refs._ranges.valid()) {
                ++ranges_size;
            }
        }
    }
    buffer.writeInt32(ranges_size);
    for (uint32_t doc_id = 0; doc_id < refs.size(); ++doc_id) {
        auto ranges_ref = refs[doc_id]._ranges;
        if (ranges_ref.valid()) {
            buffer.writeInt32(doc_id);
            auto range_vector = ranges.get(ranges_ref);
            buffer.writeInt32(range_vector.size());
            for (const auto &range : range_vector) {
                buffer.writeInt32(word_map[range.label_ref.ref()]);
                buffer.writeInt64(range.from);
                buffer.writeInt64(range.to);
            }
        }
    }
}

template <typename RefsVector, typename FeaturesStore>
void
serialize_features(DataBuffer &buffer, const RefsVector& refs, const FeaturesStore& features)
{
    uint32_t features_size = 0;
    if (!refs.empty()) {
        assert(!refs.front()._features.valid());
        for (auto& cur_refs : refs) {
            if (cur_refs._features.valid()) {
                ++features_size;
            }
        }
    }
    buffer.writeInt32(features_size);
    for (uint32_t doc_id = 0; doc_id < refs.size(); ++doc_id) {
        auto features_ref = refs[doc_id]._features;
        if (features_ref.valid()) {
            buffer.writeInt32(doc_id);
            auto feature_vector = features.get(features_ref);
            buffer.writeInt32(feature_vector.size());
            for (const auto &feature : feature_vector) {
                buffer.writeInt64(feature);
            }
        }
    }
}

}  // namespace

void
DocumentFeaturesStore::serialize(DataBuffer &buffer) const
{
    vector<EntryRef> word_list;
    unordered_map<uint32_t, uint32_t> word_map;

    findUsedWords(_refs, _ranges, word_map, word_list);

    buffer.writeInt16(_arity);
    serializeWords(buffer, word_list, _word_store);
    serialize_ranges(buffer, _refs, _ranges, word_map);
    serialize_features(buffer, _refs, _features);
}

}
