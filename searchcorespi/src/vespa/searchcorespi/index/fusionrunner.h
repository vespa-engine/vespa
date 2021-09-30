// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iindexmaintaineroperations.h"
#include "indexdisklayout.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search
{

namespace common
{

class FileHeaderContext;

}

}

namespace searchcorespi {
namespace index {
struct FusionSpec;

/**
 * FusionRunner runs fusion on a set of disk indexes, specified as a
 * vector of ids. The disk indexes must be stored in directories named
 * "index.flush.<id>" within the base dir, and the fusioned indexes
 * will be stored similarly in directories named "index.fusion.<id>".
 **/
class FusionRunner {
    const IndexDiskLayout _diskLayout;
    const search::index::Schema _schema;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;

public:
    /**
     * Create a FusionRunner that operates on indexes stored in the
     * base dir.
     **/
    FusionRunner(const vespalib::string &base_dir,
                 const search::index::Schema &schema,
                 const search::TuneFileAttributes &tuneFileAttributes,
                 const search::common::FileHeaderContext &fileHeaderContext);
    ~FusionRunner();

    /**
     * Combine the indexes specified by the ids by running fusion.
     *
     * @param fusion_spec the specification on which indexes to run fusion on.
     * @param lastSerialNum the serial number of the last flushed index part of the fusion spec.
     * @param operations interface used for running the actual fusion.
     * @return the id of the fusioned disk index
     **/
    uint32_t fuse(const FusionSpec &fusion_spec,
                  search::SerialNum lastSerialNum,
                  IIndexMaintainerOperations &operations,
                  std::shared_ptr<search::IFlushToken> flush_token);
};

}  // namespace index
}  // namespace searchcorespi

