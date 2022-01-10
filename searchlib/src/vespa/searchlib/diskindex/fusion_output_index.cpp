// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fusion_output_index.h"
#include "fusion_input_index.h"

namespace search::diskindex {

FusionOutputIndex::FusionOutputIndex(const index::Schema& schema, const vespalib::string& path, const std::vector<FusionInputIndex>& old_indexes, uint32_t doc_id_limit, const TuneFileIndexing& tune_file_indexing, const common::FileHeaderContext& file_header_context)
    : _schema(schema),
      _path(path),
      _old_indexes(std::move(old_indexes)),
      _doc_id_limit(doc_id_limit),
      _dynamic_k_pos_index_format(false),
      _force_small_merge_chunk(false),
      _tune_file_indexing(tune_file_indexing),
      _file_header_context(file_header_context)
{
}

FusionOutputIndex::~FusionOutputIndex() = default;

}
