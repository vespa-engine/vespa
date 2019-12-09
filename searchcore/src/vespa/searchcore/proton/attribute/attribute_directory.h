// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/fastos/timestamp.h>
#include <mutex>
#include <condition_variable>

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
    std::weak_ptr<AttributeDiskLayout> _diskLayout;
    const vespalib::string  _name;
    vespalib::system_time    _lastFlushTime;
    Writer                 *_writer; // current writer
    mutable std::mutex      _mutex;
    std::condition_variable _cv;
    search::IndexMetaInfo   _snapInfo;

    void saveSnapInfo();
    vespalib::string getSnapshotDir(SerialNum serialNum);
    void setLastFlushTime(vespalib::system_time lastFlushTime);
    void createInvalidSnapshot(SerialNum serialNum);
    void markValidSnapshot(SerialNum serialNum);
    void invalidateOldSnapshots(SerialNum serialNum);
    void invalidateOldSnapshots();
    void removeInvalidSnapshots();
    bool removeDiskDir();
    void detach();
    vespalib::string getDirName() const;

public:
    AttributeDirectory(const std::shared_ptr<AttributeDiskLayout> &diskLayout,
                       const vespalib::string &name);
    ~AttributeDirectory();

    const vespalib::string & getAttrName() const { return _name; }

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
        vespalib::string getSnapshotDir(SerialNum serialNum) { return _dir.getSnapshotDir(serialNum); }

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
    vespalib::string getAttributeFileName(SerialNum serialNum);
};

} // namespace proton
