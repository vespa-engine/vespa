// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fusion_output_index.h"
#include <vespa/vespalib/util/executor.h>

namespace search {
class IFlushToken;
class TuneFileIndexing;
}

namespace vespalib { template <typename T> class Array; }

namespace search::diskindex {

using SelectorArray = vespalib::Array<uint8_t>;

/*
 * Class that handles fusion of a set of disk indexes into a new disk
 * index.
 */
class Fusion
{
private:
    using Schema = index::Schema;

    bool mergeFields(vespalib::Executor& shared_executor, std::shared_ptr<IFlushToken> flush_token);
    bool readSchemaFiles();
    bool checkSchemaCompat();

    const Schema &getSchema() const { return _fusion_out_index.get_schema(); }

    std::vector<FusionInputIndex> _old_indexes;
    FusionOutputIndex _fusion_out_index;
public:
    Fusion(const Fusion &) = delete;
    Fusion& operator=(const Fusion &) = delete;
    Fusion(const Schema& schema, const vespalib::string& dir,
           const std::vector<vespalib::string>& sources, const SelectorArray& selector,
           const TuneFileIndexing& tuneFileIndexing, const common::FileHeaderContext& fileHeaderContext);

    ~Fusion();
    void set_dynamic_k_pos_index_format(bool dynamic_k_pos_index_format) { _fusion_out_index.set_dynamic_k_pos_index_format(dynamic_k_pos_index_format); }
    void set_force_small_merge_chunk(bool force_small_merge_chunk) { _fusion_out_index.set_force_small_merge_chunk(force_small_merge_chunk); }
    bool merge(vespalib::Executor& shared_executor, std::shared_ptr<IFlushToken> flush_token);
};

}
