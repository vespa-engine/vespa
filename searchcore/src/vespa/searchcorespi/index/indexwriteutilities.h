// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>

namespace searchcorespi::index {

/**
 * Utility class with functions to write aspects of an index to disk.
 * Used by the index maintainer.
 */
struct IndexWriteUtilities
{
    static void
    writeSerialNum(search::SerialNum serialNum,
                   const vespalib::string &dir,
                   const search::common::FileHeaderContext &fileHeaderContext);

    static bool
    copySerialNumFile(const vespalib::string &sourceDir,
                      const vespalib::string &destDir);

    static void
    writeSourceSelector(search::FixedSourceSelector::SaveInfo &saveInfo,
                        uint32_t sourceId,
                        const search::TuneFileAttributes &tuneFileAttributes,
                        const search::common::FileHeaderContext &
                        fileHeaderContext,
                        search::SerialNum serialNum);

    static void
    updateDiskIndexSchema(const vespalib::string &indexDir,
                          const search::index::Schema &schema,
                          search::SerialNum serialNum);
};

}


