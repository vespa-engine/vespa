// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search         { class TuneFileIndexing; }
namespace search::common { class FileHeaderContext; }
namespace search::index  { class Schema; }

namespace search::diskindex {

class FusionInputIndex;

/*
 * Class representing the portions of fusion output index state needed by
 * FieldMerger.
 */
class FusionOutputIndex
{
private:
    const index::Schema&                 _schema;
    const vespalib::string               _path;
    const std::vector<FusionInputIndex>& _old_indexes;
    const uint32_t                       _doc_id_limit;
    bool                                 _dynamic_k_pos_index_format;
    bool                                 _force_small_merge_chunk;
    const TuneFileIndexing&              _tune_file_indexing;
    const common::FileHeaderContext&     _file_header_context;
public:
    FusionOutputIndex(const index::Schema& schema, const vespalib::string& path, const std::vector<FusionInputIndex>& old_indexes, uint32_t doc_id_limit, const TuneFileIndexing& tune_file_indexing, const common::FileHeaderContext& file_header_context);
    ~FusionOutputIndex();

    void set_dynamic_k_pos_index_format(bool dynamic_k_pos_index_format) { _dynamic_k_pos_index_format = dynamic_k_pos_index_format; }
    void set_force_small_merge_chunk(bool force_small_merge_chunk) { _force_small_merge_chunk = force_small_merge_chunk; }
    const index::Schema& get_schema() const noexcept { return _schema; }
    const vespalib::string& get_path() const noexcept { return _path; }
    const std::vector<FusionInputIndex>& get_old_indexes() const noexcept { return _old_indexes; }
    uint32_t get_doc_id_limit() const noexcept { return _doc_id_limit; }
    bool get_dynamic_k_pos_index_format() const noexcept { return _dynamic_k_pos_index_format; }
    bool get_force_small_merge_chunk() const noexcept { return _force_small_merge_chunk; }
    const TuneFileIndexing& get_tune_file_indexing() const noexcept { return _tune_file_indexing; }
    const common::FileHeaderContext& get_file_header_context() const noexcept { return _file_header_context; }
};

}
