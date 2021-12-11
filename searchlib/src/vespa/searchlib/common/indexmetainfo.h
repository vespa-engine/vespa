// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search {

class IndexMetaInfo
{
public:
    struct Snapshot
    {
        bool        valid;
        uint64_t    syncToken;
        vespalib::string dirName;
        Snapshot() noexcept : valid(false), syncToken(0), dirName() {}
        Snapshot(bool valid_, uint64_t syncToken_, const vespalib::string &dirName_)
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
    typedef std::vector<Snapshot> SnapshotList;
    typedef SnapshotList::iterator SnapItr;

private:
    vespalib::string  _path;
    SnapshotList _snapshots;

    vespalib::string makeFileName(const vespalib::string &baseName);
    Snapshot &getCreateSnapshot(uint32_t idx);

    SnapItr findSnapshot(uint64_t syncToken);

public:
    IndexMetaInfo(const vespalib::string &path);
    ~IndexMetaInfo();
    vespalib::string getPath() const { return _path; }
    void setPath(const vespalib::string &path) { _path = path; }

    const SnapshotList &snapshots() const { return _snapshots; }

    Snapshot getSnapshot(uint64_t syncToken) const;
    Snapshot getBestSnapshot() const;
    bool addSnapshot(const Snapshot &snap);
    bool removeSnapshot(uint64_t syncToken);
    bool validateSnapshot(uint64_t syncToken);
    bool invalidateSnapshot(uint64_t syncToken);

    void clear();
    bool load(const vespalib::string &baseName = "meta-info.txt");
    bool save(const vespalib::string &baseName = "meta-info.txt");
};

} // namespace search

