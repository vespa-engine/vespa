// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexreadutilities.h"
#include "indexdisklayout.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/vespalib/data/fileheader.h>
#include <set>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexreadutilities");

using search::SerialNum;
using vespalib::FileHeader;

namespace searchcorespi::index {

namespace {

/**
 * Assumes that cleanup has removed all obsolete index dirs.
 **/
void
scanForIndexes(const vespalib::string &baseDir,
               std::vector<vespalib::string> &flushDirs,
               vespalib::string &fusionDir)
{
    FastOS_DirectoryScan dirScan(baseDir.c_str());
    while (dirScan.ReadNext()) {
        if (!dirScan.IsDirectory()) {
            continue;
        }
        vespalib::string name = dirScan.GetName();
        if (name.find(IndexDiskLayout::FlushDirPrefix) == 0) {
            flushDirs.push_back(name);
        }
        if (name.find(IndexDiskLayout::FusionDirPrefix) == 0) {
            if (!fusionDir.empty()) {
                // Should never happen, since we run cleanup before load.
                LOG(warning, "Base directory '%s' contains multiple fusion indexes",
                    baseDir.c_str());
            }
            fusionDir = name;
        }
    }
}

}

FusionSpec
IndexReadUtilities::readFusionSpec(const vespalib::string &baseDir)
{
    std::vector<vespalib::string> flushDirs;
    vespalib::string fusionDir;
    scanForIndexes(baseDir, flushDirs, fusionDir);

    uint32_t fusionId = 0;
    if (!fusionDir.empty()) {
        fusionId = atoi(fusionDir.substr(IndexDiskLayout::FusionDirPrefix.size()).c_str());
    }
    std::set<uint32_t> flushIds;
    for (size_t i = 0; i < flushDirs.size(); ++i) {
        uint32_t id = atoi(flushDirs[i].substr(IndexDiskLayout::FlushDirPrefix.size()).c_str());
        flushIds.insert(id);
    }

    FusionSpec fusionSpec;
    fusionSpec.last_fusion_id = fusionId;
    fusionSpec.flush_ids.assign(flushIds.begin(), flushIds.end());
    return fusionSpec;
}

SerialNum
IndexReadUtilities::readSerialNum(const vespalib::string &dir)
{
    const vespalib::string fileName = IndexDiskLayout::getSerialNumFileName(dir);
    Fast_BufferedFile file;
    file.ReadOpen(fileName.c_str());

    FileHeader fileHeader;
    fileHeader.readFile(file);
    if (fileHeader.hasTag(IndexDiskLayout::SerialNumTag)) {
        return fileHeader.getTag(IndexDiskLayout::SerialNumTag).asInteger();
    }
    return 0;
}

}
