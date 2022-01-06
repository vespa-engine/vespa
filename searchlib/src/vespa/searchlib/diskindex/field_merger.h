// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdint>
#include <memory>
#include <vector>

namespace search {
class IFlushToken;
template <class IN> class PostingPriorityQueue;
}

namespace search::index { class FieldLengthInfo; }

namespace search::diskindex {

class DictionaryWordReader;
class FieldLengthScanner;
class FieldReader;
class FieldWriter;
class FusionOutputIndex;
class WordNumMapping;

/*
 * Class for merging posting lists for a single field during fusion.
 */
class FieldMerger
{
    using WordNumMappingList = std::vector<WordNumMapping>;

    uint32_t                 _id;
    vespalib::string         _field_dir;
    const FusionOutputIndex& _fusion_out_index;

    void make_tmp_dirs();
    bool clean_tmp_dirs();
    bool open_input_word_readers(std::vector<std::unique_ptr<DictionaryWordReader>>& readers, PostingPriorityQueue<DictionaryWordReader>& heap);
    bool read_mapping_files(WordNumMappingList& list);
    bool renumber_word_ids(WordNumMappingList& list, uint64_t& numWordIds, const IFlushToken& flush_token);
    std::shared_ptr<FieldLengthScanner> allocate_field_length_scanner();
    bool open_input_field_readers(const WordNumMappingList& list, std::vector<std::unique_ptr<FieldReader>>& readers);
    bool open_field_writer(FieldWriter& writer, const index::FieldLengthInfo& field_length_info);
    bool select_cooked_or_raw_features(FieldReader& reader, FieldWriter& writer);
    bool setup_merge_heap(const std::vector<std::unique_ptr<FieldReader>>& readers, FieldWriter& writer, PostingPriorityQueue<FieldReader>& heap);
    bool merge_postings(const WordNumMappingList& list, uint64_t numWordIds, const IFlushToken& flush_token);
public:
    FieldMerger(uint32_t id, const FusionOutputIndex& fusion_out_index);
    ~FieldMerger();
    bool merge_field(std::shared_ptr<IFlushToken> flush_token);
};

}
