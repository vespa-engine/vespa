// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/i_transient_resource_usage_provider.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/time.h>
#include <condition_variable>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>

namespace proton {

class AttributeDiskLayout;

/*
 * Class used to track changes to a directory containing saved
 * snapshots of an attribute vector.
 */
class AttributeDirectory
{
public:
    class Writer;
    using SerialNum = search::SerialNum;

private:
    // Keeps track of the disk size (in bytes) for attribute snapshots.
    // The disk size is calculated and set when a snapshot is marked as valid.
    using SnapshotDiskSizes = std::unordered_map<SerialNum, std::optional<uint64_t>>;

    std::weak_ptr<AttributeDiskLayout> _diskLayout;
    const std::string  _name;
    vespalib::system_time    _lastFlushTime;
    Writer                 *_writer; // current writer
    mutable std::mutex      _mutex;
    std::condition_variable _cv;
    search::IndexMetaInfo   _snapInfo;
    SnapshotDiskSizes       _disk_sizes;

    void saveSnapInfo();
    std::string getSnapshotDir(SerialNum serialNum) const;
    void setLastFlushTime(vespalib::system_time lastFlushTime);
    void createInvalidSnapshot(SerialNum serialNum);
    void markValidSnapshot(SerialNum serialNum);
    void invalidateOldSnapshots(SerialNum serialNum);
    void invalidateOldSnapshots();
    void removeInvalidSnapshots();
    bool removeDiskDir();
    void detach();
    std::string getDirName() const;

public:
    AttributeDirectory(const std::shared_ptr<AttributeDiskLayout> &diskLayout,
                       const std::string &name);
    ~AttributeDirectory();

    const std::string & getAttrName() const { return _name; }

    /*
     * Class to make changes to an attribute directory in a
     * controlled manner. An exclusive lock is held during lifetime to
     * ensure only one active writer at a time for an attribute directory.
     */
    class Writer {
        AttributeDirectory &_dir;

    public:
        Writer(AttributeDirectory &dir);
        ~Writer();

        // methods called when saving an attribute.
        void setLastFlushTime(vespalib::system_time lastFlushTime) { _dir.setLastFlushTime(lastFlushTime); }
        void createInvalidSnapshot(SerialNum serialNum) { _dir.createInvalidSnapshot(serialNum); }
        void markValidSnapshot(SerialNum serialNum) { _dir.markValidSnapshot(serialNum); }
        std::string getSnapshotDir(SerialNum serialNum) { return _dir.getSnapshotDir(serialNum); }

        // methods called while pruning old snapshots or removing attribute
        void invalidateOldSnapshots(SerialNum serialNum) { _dir.invalidateOldSnapshots(serialNum); }
        void invalidateOldSnapshots() { _dir.invalidateOldSnapshots(); }
        void removeInvalidSnapshots() { _dir.removeInvalidSnapshots(); }
        bool removeDiskDir() { return _dir.removeDiskDir(); }
        void detach() { _dir.detach(); }
    };

    std::unique_ptr<Writer> getWriter();
    std::unique_ptr<Writer> tryGetWriter();
    SerialNum getFlushedSerialNum() const;
    vespalib::system_time getLastFlushTime() const;
    bool empty() const;
    std::string getAttributeFileName(SerialNum serialNum);
    TransientResourceUsage get_transient_resource_usage() const;
};

} // namespace proton
