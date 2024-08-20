// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <string>

namespace searchcorespi {
namespace index {

class IndexDiskDir;

/**
 * Utility class used to get static aspects of the disk layout (i.e directory and file names)
 * needed by the index maintainer.
 */
class IndexDiskLayout {
public:
    static const std::string FlushDirPrefix;
    static const std::string FusionDirPrefix;
    static const std::string SerialNumTag;

private:
    std::string _baseDir;

public:
    IndexDiskLayout(const std::string &baseDir);
    std::string getFlushDir(uint32_t sourceId) const;
    std::string getFusionDir(uint32_t sourceId) const;
    static IndexDiskDir get_index_disk_dir(const std::string& dir);

    static std::string getSerialNumFileName(const std::string &dir);
    static std::string getSchemaFileName(const std::string &dir);
    static std::string getSelectorFileName(const std::string &dir);
};

} // namespace index
} // namespace searchcorespi


