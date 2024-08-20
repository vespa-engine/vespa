// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/common/serialnum.h>
#include <string>

namespace searchcorespi::index {

/**
 * Utility class with functions to write aspects of an index to disk.
 * Used by the index maintainer.
 */
struct IndexWriteUtilities
{
    static void
    writeSerialNum(search::SerialNum serialNum,
                   const std::string &dir,
                   const search::common::FileHeaderContext &fileHeaderContext);

    static bool
    copySerialNumFile(const std::string &sourceDir,
                      const std::string &destDir);

    static void
    writeSourceSelector(search::FixedSourceSelector::SaveInfo &saveInfo,
                        uint32_t sourceId,
                        const search::TuneFileAttributes &tuneFileAttributes,
                        const search::common::FileHeaderContext &
                        fileHeaderContext,
                        search::SerialNum serialNum);

    static void
    updateDiskIndexSchema(const std::string &indexDir,
                          const search::index::Schema &schema,
                          search::SerialNum serialNum);
};

}


