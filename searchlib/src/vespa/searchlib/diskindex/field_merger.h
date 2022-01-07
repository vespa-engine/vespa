// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <cstdint>
#include <memory>
#include <vector>

namespace search {
class IFlushToken;
template <class Reader, class Writer> class PostingPriorityQueueMerger;
}

namespace search::diskindex {

class DictionaryWordReader;
class FieldLengthScanner;
class FieldReader;
class FieldWriter;
class FusionOutputIndex;
class WordAggregator;
class WordNumMapping;

/*
 * Class for merging posting lists for a single field during fusion.
 */
class FieldMerger
{
    using WordNumMappingList = std::vector<WordNumMapping>;

    enum class State {
        MERGE_START,
        RENUMBER_WORD_IDS,
        RENUMBER_WORD_IDS_FINISH,
        MERGE_POSTINGS,
        MERGE_POSTINGS_FINISH,
        MERGE_DONE
    };

    uint32_t                 _id;
    vespalib::string         _field_name;
    vespalib::string         _field_dir;
    const FusionOutputIndex& _fusion_out_index;
    std::shared_ptr<IFlushToken> _flush_token;
    std::vector<std::unique_ptr<DictionaryWordReader>> _word_readers;
    std::unique_ptr<PostingPriorityQueueMerger<DictionaryWordReader, WordAggregator>> _word_heap;
    std::unique_ptr<WordAggregator> _word_aggregator;
    WordNumMappingList _word_num_mappings;
    uint64_t _num_word_ids;
    std::vector<std::unique_ptr<FieldReader>> _readers;
    std::unique_ptr<PostingPriorityQueueMerger<FieldReader, FieldWriter>> _heap;
    std::unique_ptr<FieldWriter> _writer;
    State _state;
    bool _failed;

    void make_tmp_dirs();
    bool clean_tmp_dirs();
    bool open_input_word_readers();
    bool read_mapping_files();
    bool renumber_word_ids_start();
    bool renumber_word_ids_main();
    bool renumber_word_ids_finish();
    void renumber_word_ids_failed();
    std::shared_ptr<FieldLengthScanner> allocate_field_length_scanner();
    bool open_input_field_readers();
    bool open_field_writer();
    bool select_cooked_or_raw_features(FieldReader& reader);
    bool setup_merge_heap();
    bool merge_postings_start();
    bool merge_postings_main();
    bool merge_postings_finish();
    void merge_postings_failed();
public:
    FieldMerger(uint32_t id, const FusionOutputIndex& fusion_out_index, std::shared_ptr<IFlushToken> flush_token);
    ~FieldMerger();
    void merge_field_start();
    void merge_field_finish();
    void process_merge_field(); // Called multiple times
    bool merge_field();
};

}
