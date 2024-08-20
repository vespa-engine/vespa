// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace search {

class IndexMetaInfo
{
public:
    struct Snapshot
    {
        bool        valid;
        uint64_t    syncToken;
        std::string dirName;
        Snapshot() noexcept : valid(false), syncToken(0), dirName() {}
        Snapshot(bool valid_, uint64_t syncToken_, const std::string &dirName_)
            : valid(valid_), syncToken(syncToken_), dirName(dirName_) {}
        bool operator==(const Snapshot &rhs) const {
            return (valid == rhs.valid
                    && syncToken == rhs.syncToken
                    && dirName == rhs.dirName);
        }
        bool operator<(const Snapshot &rhs) const {
            return syncToken < rhs.syncToken;
        }
    };
    using SnapshotList = std::vector<Snapshot>;
    using SnapItr = SnapshotList::iterator;

private:
    std::string  _path;
    SnapshotList _snapshots;

    std::string makeFileName(const std::string &baseName);
    Snapshot &getCreateSnapshot(uint32_t idx);

    SnapItr findSnapshot(uint64_t syncToken);

public:
    IndexMetaInfo(const std::string &path);
    ~IndexMetaInfo();
    std::string getPath() const { return _path; }
    void setPath(const std::string &path) { _path = path; }

    const SnapshotList &snapshots() const { return _snapshots; }

    Snapshot getSnapshot(uint64_t syncToken) const;
    Snapshot getBestSnapshot() const;
    bool addSnapshot(const Snapshot &snap);
    bool removeSnapshot(uint64_t syncToken);
    bool validateSnapshot(uint64_t syncToken);
    bool invalidateSnapshot(uint64_t syncToken);

    void clear();
    bool load(const std::string &baseName = "meta-info.txt");
    bool save(const std::string &baseName = "meta-info.txt");
};

} // namespace search

