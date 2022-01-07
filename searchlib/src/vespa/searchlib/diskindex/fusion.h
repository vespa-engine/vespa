// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fusion_output_index.h"
#include <vespa/vespalib/util/threadexecutor.h>

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

    bool mergeFields(vespalib::ThreadExecutor& executor, std::shared_ptr<IFlushToken> flush_token);
    bool readSchemaFiles();
    bool checkSchemaCompat();

    const Schema &getSchema() const { return _fusion_out_index.get_schema(); }

    FusionOutputIndex _fusion_out_index;
public:
    Fusion(const Fusion &) = delete;
    Fusion& operator=(const Fusion &) = delete;
    Fusion(uint32_t docIdLimit, const Schema &schema, const vespalib::string &dir,
           const std::vector<vespalib::string> & sources, const SelectorArray &selector, bool dynamicKPosIndexFormat,
           const TuneFileIndexing &tuneFileIndexing, const common::FileHeaderContext &fileHeaderContext);

    ~Fusion();

    static bool
    merge(const Schema &schema, const vespalib::string &dir, const std::vector<vespalib::string> &sources,
          const SelectorArray &docIdSelector, bool dynamicKPosOccFormat, const TuneFileIndexing &tuneFileIndexing,
          const common::FileHeaderContext &fileHeaderContext, vespalib::ThreadExecutor & executor,
          std::shared_ptr<IFlushToken> flush_token);
};

}
