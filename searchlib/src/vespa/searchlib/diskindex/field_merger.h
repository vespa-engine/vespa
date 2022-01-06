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
    std::shared_ptr<IFlushToken> _flush_token;
    std::vector<std::unique_ptr<DictionaryWordReader>> _word_readers;
    std::unique_ptr<PostingPriorityQueue<DictionaryWordReader>> _word_heap;
    WordNumMappingList _word_num_mappings;
    uint64_t _num_word_ids;
    std::vector<std::unique_ptr<FieldReader>> _readers;
    std::unique_ptr<PostingPriorityQueue<FieldReader>> _heap;
    std::unique_ptr<FieldWriter> _writer;

    void make_tmp_dirs();
    bool clean_tmp_dirs();
    bool open_input_word_readers();
    bool read_mapping_files();
    bool renumber_word_ids();
    std::shared_ptr<FieldLengthScanner> allocate_field_length_scanner();
    bool open_input_field_readers();
    bool open_field_writer();
    bool select_cooked_or_raw_features(FieldReader& reader);
    bool setup_merge_heap();
    bool merge_postings();
public:
    FieldMerger(uint32_t id, const FusionOutputIndex& fusion_out_index, std::shared_ptr<IFlushToken> flush_token);
    ~FieldMerger();
    bool merge_field();
};

}
