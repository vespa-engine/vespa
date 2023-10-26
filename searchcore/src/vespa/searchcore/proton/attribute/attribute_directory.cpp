// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_directory.h"
#include "attributedisklayout.h"
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_directory");

using search::IndexMetaInfo;
using search::SerialNum;

namespace {

vespalib::string
getSnapshotDirComponent(uint64_t syncToken)
{
    vespalib::asciistream os;
    os << "snapshot-" << syncToken;
    return os.str();
}

}

namespace proton {

AttributeDirectory::AttributeDirectory(const std::shared_ptr<AttributeDiskLayout> &diskLayout,
                                       const vespalib::string &name)
    : _diskLayout(diskLayout),
      _name(name),
      _lastFlushTime(vespalib::system_time()),
      _writer(nullptr),
      _mutex(),
      _cv(),
      _snapInfo(getDirName()),
      _disk_sizes()
{
    _snapInfo.load();
    SerialNum flushedSerialNum = getFlushedSerialNum();
    if (flushedSerialNum != 0) {
        vespalib::string dirName = getSnapshotDir(flushedSerialNum);
        _lastFlushTime = search::FileKit::getModificationTime(dirName);
    }
    for (const auto& snapshot : _snapInfo.snapshots()) {
        search::DirectoryTraverse dirt(getSnapshotDir(snapshot.syncToken));
        _disk_sizes[snapshot.syncToken] = dirt.GetTreeSize();
    }
}

AttributeDirectory::~AttributeDirectory()
{
    std::lock_guard<std::mutex> guard(_mutex);
    assert(_writer == nullptr);
}

vespalib::string
AttributeDirectory::getDirName() const
{
    std::shared_ptr<AttributeDiskLayout> diskLayout;
    {
        std::lock_guard<std::mutex> guard(_mutex);
        assert(!_diskLayout.expired());
        diskLayout = _diskLayout.lock();
    }
    assert(diskLayout);
    if (_name.empty()) {
        return diskLayout->getBaseDir();
    }
    return diskLayout->getBaseDir() + "/" + _name;
}

SerialNum
AttributeDirectory::getFlushedSerialNum() const
{
    std::lock_guard<std::mutex> guard(_mutex);
    IndexMetaInfo::Snapshot bestSnap = _snapInfo.getBestSnapshot();
    return bestSnap.valid ? bestSnap.syncToken : 0;
}

vespalib::system_time
AttributeDirectory::getLastFlushTime() const
{
    return _lastFlushTime;
}

void
AttributeDirectory::setLastFlushTime(vespalib::system_time lastFlushTime)
{
    _lastFlushTime = lastFlushTime;
}

void
AttributeDirectory::saveSnapInfo()
{
    if (!_snapInfo.save()) {
        vespalib::string dirName(getDirName());
        LOG(warning, "Could not save meta-info file for attribute vector '%s' to disk",
            dirName.c_str());
        LOG_ABORT("should not be reached");
    }
}

vespalib::string
AttributeDirectory::getSnapshotDir(search::SerialNum serialNum) const
{
    vespalib::string dirName(getDirName());
    return dirName + "/" + getSnapshotDirComponent(serialNum);
}

void
AttributeDirectory::createInvalidSnapshot(SerialNum serialNum)
{
    IndexMetaInfo::Snapshot newSnap(false, serialNum, getSnapshotDirComponent(serialNum));
    if (empty()) {
        vespalib::string dirName(getDirName());
        std::filesystem::create_directory(std::filesystem::path(dirName));
        vespalib::File::sync(vespalib::dirname(dirName));
    }
    {
        std::lock_guard<std::mutex> guard(_mutex);
        _snapInfo.addSnapshot(newSnap);
        _disk_sizes[serialNum] = std::nullopt;
    }
    saveSnapInfo();
}

void
AttributeDirectory::markValidSnapshot(SerialNum serialNum)
{
    {
        std::lock_guard<std::mutex> guard(_mutex);
        auto snap = _snapInfo.getSnapshot(serialNum);
        assert(!snap.valid);
        assert(snap.syncToken == serialNum);
        _snapInfo.validateSnapshot(serialNum);
    }
    vespalib::string snapshotDir(getSnapshotDir(serialNum));
    vespalib::File::sync(snapshotDir);
    vespalib::File::sync(dirname(snapshotDir));
    search::DirectoryTraverse dirt(snapshotDir);
    uint64_t size_on_disk = dirt.GetTreeSize();
    {
        std::lock_guard<std::mutex> guard(_mutex);
        _disk_sizes[serialNum] = size_on_disk;
    }
    saveSnapInfo();
}

void
AttributeDirectory::invalidateOldSnapshots(uint64_t serialNum)
{
    std::vector<SerialNum> toInvalidate;
    {
        std::lock_guard<std::mutex> guard(_mutex);
        auto &list = _snapInfo.snapshots();
        for (const auto &snap : list) {
            if (snap.valid && snap.syncToken < serialNum) {
                toInvalidate.emplace_back(snap.syncToken);
            }
        }
        for (const auto &invalidSerialNum : toInvalidate) {
            _snapInfo.invalidateSnapshot(invalidSerialNum);
        }
    }
    if (!toInvalidate.empty()) {
        saveSnapInfo();
    }
}

void
AttributeDirectory::invalidateOldSnapshots()
{
    auto best = _snapInfo.getBestSnapshot();
    if (best.valid) {
        invalidateOldSnapshots(best.syncToken);
    }
}

void
AttributeDirectory::removeInvalidSnapshots()
{
    std::vector<SerialNum> toRemove;
    auto &list = _snapInfo.snapshots();
    for (const auto &snap : list) {
        if (!snap.valid) {
            toRemove.emplace_back(snap.syncToken);
        }
    }
    for (const auto &serialNum : toRemove) {
        vespalib::string subDir(getSnapshotDir(serialNum));
        std::filesystem::remove_all(std::filesystem::path(subDir));
    }
    if (!toRemove.empty()) {
        vespalib::File::sync(getDirName());
        {
            std::lock_guard<std::mutex> guard(_mutex);
            for (const auto &serialNum : toRemove) {
                _snapInfo.removeSnapshot(serialNum);
                _disk_sizes.erase(serialNum);
            }
        }
        saveSnapInfo();
    }
}

bool
AttributeDirectory::removeDiskDir()
{
    if (empty()) {
        vespalib::string dirName(getDirName());
        std::filesystem::remove_all(std::filesystem::path(dirName));
        vespalib::File::sync(vespalib::dirname(dirName));
        return true;
    }
    return false;
}

void
AttributeDirectory::detach()
{
    assert(empty());
    std::lock_guard<std::mutex> guard(_mutex);
    _diskLayout.reset();
}

std::unique_ptr<AttributeDirectory::Writer>
AttributeDirectory::getWriter()
{
    std::unique_lock<std::mutex> guard(_mutex);
    while (_writer != nullptr) {
        _cv.wait(guard);
    }
    std::shared_ptr<AttributeDiskLayout> diskLayout(_diskLayout.lock());
    if (diskLayout) {
        return std::make_unique<Writer>(*this);
    } else {
        return std::unique_ptr<Writer>(); // detached, no more writes
    }
}

std::unique_ptr<AttributeDirectory::Writer>
AttributeDirectory::tryGetWriter()
{
    std::lock_guard<std::mutex> guard(_mutex);
    std::shared_ptr<AttributeDiskLayout> diskLayout(_diskLayout.lock());
    if (diskLayout && _writer == nullptr) {
        return std::make_unique<Writer>(*this);
    } else {
        return std::unique_ptr<Writer>();
    }
}

bool
AttributeDirectory::empty() const
{
    std::lock_guard<std::mutex> guard(_mutex);
    return _snapInfo.snapshots().empty();
}

vespalib::string
AttributeDirectory::getAttributeFileName(SerialNum serialNum)
{
    return getSnapshotDir(serialNum) + "/" + _name;
}

AttributeDirectory::Writer::Writer(AttributeDirectory &dir)
    : _dir(dir)
{
    _dir._writer = this;
}

AttributeDirectory::Writer::~Writer()
{
    std::lock_guard<std::mutex> guard(_dir._mutex);
    _dir._writer = nullptr;
    _dir._cv.notify_all();
}

TransientResourceUsage
AttributeDirectory::get_transient_resource_usage() const
{
    uint64_t total_size_on_disk = 0;
    std::vector<SerialNum> to_traverse;
    {
        std::lock_guard<std::mutex> guard(_mutex);
        auto best = _snapInfo.getBestSnapshot();
        // All snapshots except the best one count towards transient disk usage.
        for (const auto& snapshot : _disk_sizes) {
            auto serial_num = snapshot.first;
            if (serial_num != best.syncToken) {
                if (snapshot.second.has_value()) {
                    // The size of this snapshot has already been calculated.
                    total_size_on_disk += snapshot.second.value();
                } else {
                    // Writing of this snapshot is ongoing and the size must be calculated now.
                    to_traverse.push_back(serial_num);
                }
            }
        }
    }
    for (auto serial_num : to_traverse) {
        search::DirectoryTraverse dirt(getSnapshotDir(serial_num));
        total_size_on_disk += dirt.GetTreeSize();
    }
    return {total_size_on_disk, 0};
}

} // namespace proton
