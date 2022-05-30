// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace searchcorespi {
namespace index {

class IndexDiskDir;

/**
 * Utility class used to get static aspects of the disk layout (i.e directory and file names)
 * needed by the index maintainer.
 */
class IndexDiskLayout {
public:
    static const vespalib::string FlushDirPrefix;
    static const vespalib::string FusionDirPrefix;
    static const vespalib::string SerialNumTag;

private:
    vespalib::string _baseDir;

public:
    IndexDiskLayout(const vespalib::string &baseDir);
    vespalib::string getFlushDir(uint32_t sourceId) const;
    vespalib::string getFusionDir(uint32_t sourceId) const;
    static IndexDiskDir get_index_disk_dir(const vespalib::string& dir);

    static vespalib::string getSerialNumFileName(const vespalib::string &dir);
    static vespalib::string getSchemaFileName(const vespalib::string &dir);
    static vespalib::string getSelectorFileName(const vespalib::string &dir);
};

} // namespace index
} // namespace searchcorespi


