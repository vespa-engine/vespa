// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskindexcleaner.h"
#include "disk_indexes.h"
#include "indexdisklayout.h"
#include "index_disk_dir.h"
#include <vespa/fastos/file.h>
#include <vespa/vespalib/io/fileutil.h>
#include <filesystem>
#include <sstream>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.diskindexcleaner");

using std::istringstream;
using vespalib::string;
using std::vector;

namespace searchcorespi::index {

namespace {
vector<string> readIndexes(const string &base_dir) {
    vector<string> indexes;
    FastOS_DirectoryScan dir_scan(base_dir.c_str());
    while (dir_scan.ReadNext()) {
        string name = dir_scan.GetName();
        if (!dir_scan.IsDirectory() || name.find("index.") != 0) {
            continue;
        }
        indexes.push_back(name);
    }
    return indexes;
}

bool isValidIndex(const string &index_dir) {
    FastOS_File serial_file((index_dir + "/serial.dat").c_str());
    return serial_file.OpenReadOnlyExisting();
}

void invalidateIndex(const string &index_dir) {
    vespalib::unlink(index_dir + "/serial.dat");
    vespalib::File::sync(index_dir);
}

uint32_t findLastFusionId(const string &base_dir,
                          const vector<string> &indexes) {
    uint32_t fusion_id = 0;
    const string prefix = "index.fusion.";
    for (size_t i = 0; i < indexes.size(); ++i) {
        if (indexes[i].find(prefix) != 0) {
            continue;
        }
        if (!isValidIndex(base_dir + "/" + indexes[i])) {
            continue;
        }

        uint32_t new_id = 0;
        istringstream ist(indexes[i].substr(prefix.size()));
        ist >> new_id;
        fusion_id = std::max(fusion_id, new_id);
    }
    return fusion_id;
}

void removeDir(const string &dir) {
    LOG(debug, "Removing index dir '%s'", dir.c_str());
    invalidateIndex(dir);
    std::filesystem::remove_all(std::filesystem::path(dir));
}

bool isOldIndex(const string &index, uint32_t last_fusion_id) {
    string::size_type pos = index.rfind(".");
    istringstream ist(index.substr(pos + 1));
    uint32_t id = last_fusion_id;
    ist >> id;
    if (id < last_fusion_id) {
        return true;
    } else if (id == last_fusion_id) {
        return index.find("flush") != string::npos;
    }
    return false;
}

void removeOld(const string &base_dir, const vector<string> &indexes,
               DiskIndexes &disk_indexes, bool remove) {
    uint32_t last_fusion_id = findLastFusionId(base_dir, indexes);
    for (size_t i = 0; i < indexes.size(); ++i) {
        const string index_dir = base_dir + "/" + indexes[i];
        auto index_disk_dir = IndexDiskLayout::get_index_disk_dir(indexes[i]);
        if (isOldIndex(indexes[i], last_fusion_id) &&
            disk_indexes.remove(index_disk_dir)) {
            if (remove) {
                removeDir(index_dir);
            } else {
                invalidateIndex(index_dir);
            }
        }
    }
}

void removeInvalid(const string &base_dir, const vector<string> &indexes) {
    for (size_t i = 0; i < indexes.size(); ++i) {
        const string index_dir = base_dir + "/" + indexes[i];
        if (!isValidIndex(index_dir)) {
            LOG(debug, "Found invalid index dir '%s'", index_dir.c_str());
            removeDir(index_dir);
        }
    }
}
}  // namespace

void DiskIndexCleaner::clean(const string &base_dir,
                             DiskIndexes &disk_indexes) {
    vector<string> indexes = readIndexes(base_dir);
    removeOld(base_dir, indexes, disk_indexes, false);
    removeInvalid(base_dir, indexes);
}

void DiskIndexCleaner::removeOldIndexes(
        const string &base_dir, DiskIndexes &disk_indexes) {
    vector<string> indexes = readIndexes(base_dir);
    removeOld(base_dir, indexes, disk_indexes, true);
}

}
