// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_features_store.h"
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.predicate.document_features_store");
#include "predicate_index.h"
#include "predicate_range_expander.h"
#include "predicate_tree_annotator.h"
#include <vespa/searchlib/btree/btreenode.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <unordered_map>
#include <vector>

using search::btree::BTreeNoLeafData;
using search::datastore::EntryRef;
using vespalib::DataBuffer;
using vespalib::stringref;
using std::unordered_map;
using std::vector;

namespace search {
namespace predicate {

void
DocumentFeaturesStore::setCurrent(uint32_t docId, FeatureVector *features) {
    _currDocId = docId;
    _currFeatures = features;
}

DocumentFeaturesStore::DocumentFeaturesStore(uint32_t arity)
    : _docs(),
      _ranges(),
      _word_store(),
      _word_index(),
      _currDocId(0),
      _currFeatures(),
      _numFeatures(0),
      _numRanges(0),
      _arity(arity) {
}

namespace {
template <typename KeyComp, typename WordIndex>
void deserializeWords(DataBuffer &buffer,
                      memoryindex::WordStore &word_store,
                      WordIndex &word_index,
                      vector<EntryRef> &word_refs) {
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

template <typename RangeFeaturesMap>
void deserializeRanges(DataBuffer &buffer, vector<EntryRef> &word_refs,
                       RangeFeaturesMap &ranges, size_t &num_ranges) {
    typedef typename RangeFeaturesMap::mapped_type::value_type Range;
    uint32_t ranges_size = buffer.readInt32();
    for (uint32_t i = 0; i < ranges_size; ++i) {
        uint32_t doc_id = buffer.readInt32();
        uint32_t range_count = buffer.readInt32();
        auto &range_vector = ranges[doc_id];
        range_vector.reserve(range_count);
        for (uint32_t j = 0; j < range_count; ++j) {
            Range range;
            range.label_ref = word_refs[buffer.readInt32()];
            range.from = buffer.readInt64();
            range.to = buffer.readInt64();
            range_vector.push_back(range);
        }
        num_ranges += range_count;
    }
}

template <typename DocumentFeaturesMap>
void deserializeDocs(DataBuffer &buffer, DocumentFeaturesMap &docs,
                     size_t &num_features) {
    uint32_t docs_size = buffer.readInt32();
    for (uint32_t i = 0; i < docs_size; ++i) {
        uint32_t doc_id = buffer.readInt32();
        uint32_t feature_count = buffer.readInt32();
        auto &feature_vector = docs[doc_id];
        feature_vector.reserve(feature_count);
        for (uint32_t j = 0; j < feature_count; ++j) {
            feature_vector.push_back(buffer.readInt64());
        }
        num_features += feature_count;
    }
}
}  // namespace

DocumentFeaturesStore::DocumentFeaturesStore(DataBuffer &buffer)
    : DocumentFeaturesStore(0) {
    _arity = buffer.readInt16();

    vector<EntryRef> word_refs;
    deserializeWords<KeyComp>(buffer, _word_store, _word_index, word_refs);
    deserializeRanges(buffer, word_refs, _ranges, _numRanges);
    deserializeDocs(buffer, _docs, _numFeatures);
}

DocumentFeaturesStore::~DocumentFeaturesStore() {
    _word_index.disableFreeLists();
    _word_index.disableElemHoldList();
    _word_index.getAllocator().freeze();
    _word_index.clear();
}

void DocumentFeaturesStore::insert(uint64_t featureId, uint32_t docId) {
    assert(docId != 0);
    if (_currDocId != docId) {
        auto docsItr = _docs.find(docId);
        if (docsItr == _docs.end()) {
            docsItr =
                _docs.insert(std::make_pair(docId, FeatureVector())).first;
        }
        setCurrent(docId, &docsItr->second);
    }
    _currFeatures->push_back(featureId);
    ++_numFeatures;
}

void DocumentFeaturesStore::insert(const PredicateTreeAnnotations &annotations,
                                   uint32_t doc_id) {
    assert(doc_id != 0);
    if (!annotations.features.empty()) {
        auto it = _docs.find(doc_id);
        if (it == _docs.end()) {
            it = _docs.insert(std::make_pair(doc_id, FeatureVector())).first;
        }
        size_t size = it->second.size();
        it->second.resize(size + annotations.features.size());
        memcpy(&it->second[size], &annotations.features[0],
               annotations.features.size() * sizeof(annotations.features[0]));
        _numFeatures += annotations.features.size();
    }
    if (!annotations.range_features.empty()) {
        auto it = _ranges.find(doc_id);
        if (it == _ranges.end()) {
            it = _ranges.insert(std::make_pair(doc_id, RangeVector())).first;
        }
        for (const auto &range : annotations.range_features) {
            stringref word(range.label.data, range.label.size);
            KeyComp cmp(_word_store, word);
            auto word_it = _word_index.find(datastore::EntryRef(), cmp);
            datastore::EntryRef ref;
            if (word_it.valid()) {
                ref = word_it.getKey();
            } else {
                ref = _word_store.addWord(word);
                _word_index.insert(ref, BTreeNoLeafData(), cmp);
            }
            it->second.push_back({ref, range.from, range.to});
        }
        _numRanges += annotations.range_features.size();
    }
}

DocumentFeaturesStore::FeatureSet
DocumentFeaturesStore::get(uint32_t docId) const {
    FeatureSet features;
    auto docsItr = _docs.find(docId);
    if (docsItr != _docs.end()) {
        features.insert(docsItr->second.begin(), docsItr->second.end());
    }
    auto rangeItr = _ranges.find(docId);
    if (rangeItr != _ranges.end()) {
        for (auto range : rangeItr->second) {
            const char *label = _word_store.getWord(range.label_ref);
            PredicateRangeExpander::expandRange(
                    label, range.from, range.to, _arity,
                    std::inserter(features, features.end()));
        }
    }
    return features;
}

void DocumentFeaturesStore::remove(uint32_t doc_id) {
    auto itr = _docs.find(doc_id);
    if (itr != _docs.end()) {
        _numFeatures = _numFeatures >= itr->second.size() ?
                        (_numFeatures - itr->second.size()) : 0;
        _docs.erase(itr);
    }
    auto range_itr = _ranges.find(doc_id);
    if (range_itr != _ranges.end()) {
        _numRanges = _numRanges >= range_itr->second.size() ?
                     (_numRanges - range_itr->second.size()) : 0;
        _ranges.erase(range_itr);
    }
    if (_currDocId == doc_id) {
        setCurrent(0, NULL);
    }
}

search::MemoryUsage DocumentFeaturesStore::getMemoryUsage() const {
    search::MemoryUsage usage;
    usage.incAllocatedBytes(_docs.getMemoryConsumption());
    usage.incUsedBytes(_docs.getMemoryUsed());
    usage.incAllocatedBytes(_ranges.getMemoryConsumption());
    usage.incUsedBytes(_ranges.getMemoryUsed());
    // Note: allocated bytes in FeatureVector is slighly larger, but
    // this should be good enough.
    usage.incAllocatedBytes(_numFeatures * sizeof(uint64_t));
    usage.incUsedBytes(_numFeatures * sizeof(uint64_t));
    usage.incAllocatedBytes(_numRanges * sizeof(Range));
    usage.incUsedBytes(_numRanges * sizeof(Range));

    usage.merge(_word_store.getMemoryUsage());
    usage.merge(_word_index.getMemoryUsage());

    return usage;
}

namespace {
template <typename RangeFeaturesMap>
void findUsedWords(const RangeFeaturesMap &ranges,
                   unordered_map<uint32_t, uint32_t> &word_map,
                   vector<EntryRef> &word_list) {
    for (const auto &range_features_entry : ranges) {
        for (const auto &range : range_features_entry.second) {
            if (!word_map.count(range.label_ref.ref())) {
                word_map[range.label_ref.ref()] = word_list.size();
                word_list.push_back(range.label_ref);
            }
        }
    }
}

void serializeWords(DataBuffer &buffer, const vector<EntryRef> &word_list,
                    const memoryindex::WordStore &word_store) {
    buffer.writeInt32(word_list.size());
    for (const auto &word_ref : word_list) {
        const char *word = word_store.getWord(word_ref);
        uint32_t len = strlen(word);
        buffer.writeInt32(len);
        buffer.writeBytes(word, len);
    }
}

template <typename RangeFeaturesMap>
void serializeRanges(DataBuffer &buffer, RangeFeaturesMap &ranges,
                     unordered_map<uint32_t, uint32_t> &word_map) {
    buffer.writeInt32(ranges.size());
    for (const auto &range_features_entry : ranges) {
        buffer.writeInt32(range_features_entry.first);  // doc id
        buffer.writeInt32(range_features_entry.second.size());
        for (const auto &range : range_features_entry.second) {
            buffer.writeInt32(word_map[range.label_ref.ref()]);
            buffer.writeInt64(range.from);
            buffer.writeInt64(range.to);
        }
    }
}

template <typename DocumentFeaturesMap>
void serializeDocs(DataBuffer &buffer, DocumentFeaturesMap &docs) {
    buffer.writeInt32(docs.size());
    for (const auto &doc_features_entry : docs) {
        buffer.writeInt32(doc_features_entry.first);  // doc id
        buffer.writeInt32(doc_features_entry.second.size());
        for (const auto &feature : doc_features_entry.second) {
            buffer.writeInt64(feature);
        }
    }
}
}  // namespace

void DocumentFeaturesStore::serialize(DataBuffer &buffer) const {
    vector<EntryRef> word_list;
    unordered_map<uint32_t, uint32_t> word_map;

    findUsedWords(_ranges, word_map, word_list);

    buffer.writeInt16(_arity);
    serializeWords(buffer, word_list, _word_store);
    serializeRanges(buffer, _ranges, word_map);
    serializeDocs(buffer, _docs);
}

}  // namespace predicate
}  // namespace search
