// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_features_store_saver.h"
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/objects/nbo.h>

using vespalib::datastore::EntryRef;
using search::BufferWriter;

namespace search::predicate {

DocumentFeaturesStoreSaver::DocumentFeaturesStoreSaver(const DocumentFeaturesStore& store)
    : _refs(store._refs),
      _features(store._features),
      _ranges(store._ranges),
      _word_store(store._word_store),
      _arity(store._arity)
{
}

DocumentFeaturesStoreSaver::~DocumentFeaturesStoreSaver() = default;

namespace {

template <typename T>
void nbo_write(BufferWriter& writer, T value)
{
    auto value_nbo = vespalib::nbo::n2h(value);
    writer.write(&value_nbo, sizeof(value_nbo));
}

template <typename RefsVector, typename RangesStore>
void
find_used_words(const RefsVector& refs, const RangesStore& ranges,
                std::unordered_map<uint32_t, uint32_t>& word_map,
                std::vector<EntryRef>& word_list)
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
serialize_words(BufferWriter& writer, const std::vector<EntryRef>& word_list,
                const memoryindex::WordStore& word_store)
{
    nbo_write<uint32_t>(writer, word_list.size());;
    for (const auto &word_ref : word_list) {
        const char *word = word_store.getWord(word_ref);
        uint32_t len = strlen(word);
        nbo_write(writer, len);
        writer.write(word, len);
    }
}

template <typename RefsVector, typename RangesStore>
void
serialize_ranges(BufferWriter& writer, const RefsVector& refs, const RangesStore& ranges,
                 std::unordered_map<uint32_t, uint32_t>& word_map)
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
    nbo_write(writer, ranges_size);
    for (uint32_t doc_id = 0; doc_id < refs.size(); ++doc_id) {
        auto ranges_ref = refs[doc_id]._ranges;
        if (ranges_ref.valid()) {
            nbo_write(writer, doc_id);
            auto range_vector = ranges.get(ranges_ref);
            nbo_write<uint32_t>(writer, range_vector.size());
            for (const auto &range : range_vector) {
                nbo_write(writer, word_map[range.label_ref.ref()]);
                nbo_write(writer, range.from);
                nbo_write(writer, range.to);
            }
        }
    }
}

template <typename RefsVector, typename FeaturesStore>
void
serialize_features(BufferWriter& writer, const RefsVector& refs, const FeaturesStore& features)
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
    nbo_write(writer, features_size);
    for (uint32_t doc_id = 0; doc_id < refs.size(); ++doc_id) {
        auto features_ref = refs[doc_id]._features;
        if (features_ref.valid()) {
            nbo_write(writer, doc_id);
            auto feature_vector = features.get(features_ref);
            nbo_write<uint32_t>(writer, feature_vector.size());
            for (const auto &feature : feature_vector) {
                nbo_write(writer, feature);
            }
        }
    }
}

}  // namespace

void
DocumentFeaturesStoreSaver::save(BufferWriter& writer) const
{
    std::vector<EntryRef> word_list;
    std::unordered_map<uint32_t, uint32_t> word_map;

    find_used_words(_refs, _ranges, word_map, word_list);

    nbo_write<uint16_t>(writer, _arity);
    serialize_words(writer, word_list, _word_store);
    serialize_ranges(writer, _refs, _ranges, word_map);
    serialize_features(writer, _refs, _features);
}

}
