// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributedisklayout.h"
#include "flushableattribute.h"
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/attributesaver.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/closuretask.h>
#include <fstream>
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.flushableattribute");

using namespace search;
using namespace vespalib;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using vespalib::makeTask;
using vespalib::makeClosure;

namespace proton {

FlushableAttribute::Flusher::Flusher(FlushableAttribute & fattr, SerialNum syncToken)
    : _fattr(fattr),
      _saveTarget(),
      _saver(),
      _syncToken(syncToken),
      _flushFile("")
{
    fattr._attr->commit(syncToken, syncToken);
    AttributeVector &attr = *_fattr._attr;
    // Called by attribute field writer executor
    if (attr.canShrinkLidSpace()) {
        attr.shrinkLidSpace();
    }
    _flushFile = AttributeDiskLayout::getAttributeFileName(_fattr._baseDir,
                                                           attr.getName(),
                                                           _syncToken);
    attr.setBaseFileName(_flushFile);
    _saver = attr.initSave();
    if (!_saver) {
        // New style background save not available, use old style save.
        attr.save(_saveTarget);
    }
}

FlushableAttribute::Flusher::~Flusher()
{
    // empty
}

bool
FlushableAttribute::Flusher::saveSnapInfo()
{
    if (!_fattr._snapInfo.save()) {
        LOG(warning,
            "Could not save meta-info file for attribute vector '%s' to disk",
            _fattr._attr->getBaseFileName().c_str());
        return false;
    }
    return true;
}

bool
FlushableAttribute::Flusher::saveAttribute()
{
    vespalib::mkdir(_flushFile.getDirName(), false);
    SerialNumFileHeaderContext fileHeaderContext(_fattr._fileHeaderContext,
                                                 _syncToken);
    bool saveSuccess = true;
    if (_saver && _saver->hasGenerationGuard() &&
        _fattr._hwInfo.slowDisk()) {
        saveSuccess = _saver->save(_saveTarget);
        _saver.reset();
    }
    if (saveSuccess) {
        if (_saver) {
            search::AttributeFileSaveTarget saveTarget(_fattr._tuneFileAttributes,
                                                       fileHeaderContext);
            saveSuccess = _saver->save(saveTarget);
            _saver.reset();
        } else {
            saveSuccess = _saveTarget.writeToFile(_fattr._tuneFileAttributes,
                                                  fileHeaderContext);
        }
    }
    return saveSuccess;
}

bool
FlushableAttribute::Flusher::flush()
{
    IndexMetaInfo::Snapshot newSnap(false, _syncToken,
                                    _flushFile.getSnapshotName());
    {
        vespalib::LockGuard guard(_fattr._snapInfoLock);
        _fattr._snapInfo.addSnapshot(newSnap);
    }
    if (!saveSnapInfo()) {
        return false;
    }
    if (!saveAttribute()) {
        LOG(warning, "Could not write attribute vector '%s' to disk",
            _flushFile.c_str());
        return false;
    }
    {
        vespalib::LockGuard guard(_fattr._snapInfoLock);
        _fattr._snapInfo.validateSnapshot(_syncToken);
    }
    if (!saveSnapInfo()) {
        return false;
    }
    _fattr._lastFlushTime =
        search::FileKit::getModificationTime(_flushFile.getDirName());
    return true;
}

void
FlushableAttribute::Flusher::updateStats()
{
    _fattr._lastStats.setPath(_flushFile.getDirName());
}

bool
FlushableAttribute::Flusher::cleanUp()
{
    if (_fattr._cleanUpAfterFlush) {
        if (!AttributeDiskLayout::removeOldSnapshots(_fattr._snapInfo,
                    _fattr._snapInfoLock)) {
            LOG(warning,
                "Encountered problems when removing old snapshot directories"
                "after flushing attribute vector '%s' to disk",
                _fattr._attr->getBaseFileName().c_str());
            return false;
        }
    }
    return true;
}

void
FlushableAttribute::Flusher::run()
{
    vespalib::LockGuard guard(_fattr._flusherLock);
    if (_syncToken <= _fattr.getFlushedSerialNum()) {
        // another flusher has created an equal or better snapshot
        // after this flusher was created
        return;
    }
    if (!flush()) {
        // TODO (geirst): throw exception ?
    }
    updateStats();
    if (!cleanUp()) {
        // TODO (geirst): throw exception ?
    }
}

FlushableAttribute::FlushableAttribute(const AttributeVector::SP attr,
                                       const vespalib::string & baseDir,
                                       const TuneFileAttributes &
                                       tuneFileAttributes,
                                       const FileHeaderContext &
                                       fileHeaderContext,
                                       search::ISequencedTaskExecutor &
                                       attributeFieldWriter,
                                       const HwInfo &hwInfo)
    : IFlushTarget(vespalib::make_string(
                           "attribute.%s",
                           attr->getName().c_str()),
            Type::SYNC, Component::ATTRIBUTE),
      _attr(attr),
      _baseDir(baseDir),
      _snapInfo(AttributeDiskLayout::getAttributeBaseDir(baseDir,
                        attr->getName())),
      _snapInfoLock(),
      _flusherLock(),
      _cleanUpAfterFlush(true),
      _lastStats(),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _lastFlushTime(),
      _attributeFieldWriter(attributeFieldWriter),
      _hwInfo(hwInfo)
{
    if (!_snapInfo.load()) {
        _snapInfo.save();
    } else {
        vespalib::string dirName =
            AttributeDiskLayout::getAttributeFileName(_baseDir,
                    _attr->getName(),
                    getFlushedSerialNum()).getDirName();
        _lastFlushTime = search::FileKit::getModificationTime(dirName);
    }
    _lastStats.setPathElementsToLog(8);
}


FlushableAttribute::~FlushableAttribute()
{
}


IFlushTarget::SerialNum
FlushableAttribute::getFlushedSerialNum() const
{
    vespalib::LockGuard guard(_snapInfoLock);
    IndexMetaInfo::Snapshot bestSnap = _snapInfo.getBestSnapshot();
    return bestSnap.valid ? bestSnap.syncToken : 0;
}

IFlushTarget::MemoryGain
FlushableAttribute::getApproxMemoryGain() const
{
    int64_t used(_attr->getStatus().getUsed());
    int64_t canFree = 0;
    if (_attr->canShrinkLidSpace()) {
        uint32_t committedDocIdLimit = _attr->getCommittedDocIdLimit();
        uint32_t numDocs = _attr->getNumDocs();
        const attribute::Config &cfg = _attr->getConfig();
        if (committedDocIdLimit < numDocs) {
            uint32_t elemSize = 4;
            if (cfg.collectionType().isMultiValue()) {
                if (cfg.huge()) {
                    elemSize = 8;
                }
            } else if (cfg.fastSearch()) {
                // keep elemSize at 4
            } else {
                elemSize = cfg.basicType().fixedSize();
            }
            canFree = static_cast<int64_t>(elemSize) * 
                      (numDocs - committedDocIdLimit);
            if (canFree > used)
                canFree = used;
        }
    }
    return MemoryGain(used,
                      used - canFree);
}

IFlushTarget::DiskGain
FlushableAttribute::getApproxDiskGain() const
{
    return DiskGain(0, 0);
}

IFlushTarget::Time
FlushableAttribute::getLastFlushTime() const
{
    return _lastFlushTime;
}

IFlushTarget::Task::UP
FlushableAttribute::internalInitFlush(SerialNum currentSerial)
{
    // Called by document db executor
    (void)currentSerial;
    _attr->removeAllOldGenerations();
    SerialNum syncToken = currentSerial;
    syncToken = std::max(currentSerial,
                         _attr->getStatus().getLastSyncToken());
    if (syncToken <= getFlushedSerialNum()) {
        vespalib::LockGuard guard(_flusherLock);
        _lastFlushTime = fastos::ClockSystem::now();
        LOG(debug,
            "No attribute vector to flush."
            " Update flush time to current: lastFlushTime(%f)",
            _lastFlushTime.sec());
        return Task::UP();
    }
    return Task::UP(new Flusher(*this, syncToken));
}


IFlushTarget::Task::UP
FlushableAttribute::initFlush(SerialNum currentSerial)
{
    std::promise<IFlushTarget::Task::UP> promise;
    std::future<IFlushTarget::Task::UP> future = promise.get_future();
    _attributeFieldWriter.execute(_attr->getName(),
                                  [&]() { promise.set_value(
                                              internalInitFlush(currentSerial));
                                         });
    return future.get();
}


uint64_t
FlushableAttribute::getApproxBytesToWriteToDisk() const
{
    return _attr->getEstimatedSaveByteSize();
}


} // namespace proton
