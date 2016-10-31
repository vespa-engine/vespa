// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore.documentmetastoreflushtarget");

#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include "documentmetastoreflushtarget.h"
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/attributesaver.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/closuretask.h>
#include <fstream>
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchcore/proton/server/itlssyncer.h>

using namespace search;
using namespace vespalib;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using vespalib::makeTask;
using vespalib::makeClosure;

namespace proton {

DocumentMetaStoreFlushTarget::Flusher::
Flusher(DocumentMetaStoreFlushTarget &dmsft,
        SerialNum syncToken)
    : _dmsft(dmsft),
      _saver(),
      _syncToken(syncToken),
      _flushDir("")
{
    DocumentMetaStore &dms = *_dmsft._dms;
    // Called by document db executor
    if (dms.canShrinkLidSpace()) {
        dms.shrinkLidSpace();
    }
    _flushDir = _dmsft.getSnapshotDir(syncToken);
    vespalib::string newBaseFileName(_flushDir + "/" + dms.getName());
    dms.setBaseFileName(newBaseFileName);
    _saver = dms.initSave();
    assert(_saver);
}

DocumentMetaStoreFlushTarget::Flusher::~Flusher()
{
    // empty
}

bool
DocumentMetaStoreFlushTarget::Flusher::saveSnapInfo()
{
    if (!_dmsft._snapInfo.save()) {
        LOG(warning,
            "Could not save meta-info file for document meta store"
            " '%s' to disk",
            _dmsft._dms->getBaseFileName().c_str());
        return false;
    }
    return true;
}

bool
DocumentMetaStoreFlushTarget::Flusher::saveDocumentMetaStore()
{
    vespalib::mkdir(_flushDir, false);
    SerialNumFileHeaderContext fileHeaderContext(_dmsft._fileHeaderContext,
                                                 _syncToken);
    search::AttributeFileSaveTarget saveTarget(_dmsft._tuneFileAttributes,
                                               fileHeaderContext);
    bool saveSuccess = _saver->save(saveTarget);
    _saver.reset();
    return saveSuccess;
}

bool
DocumentMetaStoreFlushTarget::Flusher::flush()
{
    IndexMetaInfo::Snapshot newSnap(false, _syncToken,
                                    getSnapshotName(_syncToken));
    {
        vespalib::LockGuard guard(_dmsft._snapInfoLock);
        _dmsft._snapInfo.addSnapshot(newSnap);
    }
    if (!saveSnapInfo()) {
        return false;
    }
    if (!saveDocumentMetaStore()) {
        LOG(warning, "Could not write document meta store '%s' to disk",
            _dmsft._dms->getBaseFileName().c_str());
        return false;
    }
    /*
     * Sync transaction log again.  This is needed when background
     * flush is activated to ensure that same future will occur that has
     * already been observable in the saved document meta store (future
     * timestamp or bucket id).
     */
    _dmsft._tlsSyncer.sync();
    {
        vespalib::LockGuard guard(_dmsft._snapInfoLock);
        _dmsft._snapInfo.validateSnapshot(_syncToken);
    }
    if (!saveSnapInfo()) {
        return false;
    }
    _dmsft._lastFlushTime = search::FileKit::getModificationTime(_flushDir);
    return true;
}

void
DocumentMetaStoreFlushTarget::Flusher::updateStats()
{
    _dmsft._lastStats.setPath(_flushDir);
}

bool
DocumentMetaStoreFlushTarget::Flusher::cleanUp()
{
    if (_dmsft._cleanUpAfterFlush) {
        if (!AttributeDiskLayout::removeOldSnapshots(_dmsft._snapInfo,
                    _dmsft._snapInfoLock)) {
            LOG(warning,
                "Encountered problems when removing old snapshot directories"
                "after flushing document meta store '%s' to disk",
                _dmsft._dms->getBaseFileName().c_str());
            return false;
        }
    }
    return true;
}

void
DocumentMetaStoreFlushTarget::Flusher::run()
{
    vespalib::LockGuard guard(_dmsft._flusherLock);
    if (_syncToken <= _dmsft.getFlushedSerialNum()) {
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

DocumentMetaStoreFlushTarget::
DocumentMetaStoreFlushTarget(const DocumentMetaStore::SP dms,
                             ITlsSyncer &tlsSyncer,
                             const vespalib::string & baseDir,
                             const TuneFileAttributes &tuneFileAttributes,
                             const FileHeaderContext &fileHeaderContext)
    : IFlushTarget("documentmetastore", Type::SYNC, Component::ATTRIBUTE),
      _dms(dms),
      _tlsSyncer(tlsSyncer),
      _baseDir(baseDir),
      _snapInfo(_baseDir),
      _snapInfoLock(),
      _flusherLock(),
      _cleanUpAfterFlush(true),
      _lastStats(),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _lastFlushTime()

{
    if (!_snapInfo.load()) {
        _snapInfo.save();
    } else {
        vespalib::string dirName(getSnapshotDir(getFlushedSerialNum()));
        _lastFlushTime = search::FileKit::getModificationTime(dirName);
    }
    _lastStats.setPathElementsToLog(8);
}


DocumentMetaStoreFlushTarget::~DocumentMetaStoreFlushTarget()
{
}


vespalib::string
DocumentMetaStoreFlushTarget::getSnapshotName(uint64_t syncToken)
{
    return vespalib::make_string("snapshot-%" PRIu64, syncToken);
}


vespalib::string
DocumentMetaStoreFlushTarget::getSnapshotDir(uint64_t syncToken)
{
    return vespalib::make_string("%s/%s",
                                 _baseDir.c_str(),
                                 getSnapshotName(syncToken).c_str());
}


IFlushTarget::SerialNum
DocumentMetaStoreFlushTarget::getFlushedSerialNum() const
{
    vespalib::LockGuard guard(_snapInfoLock);
    IndexMetaInfo::Snapshot bestSnap = _snapInfo.getBestSnapshot();
    return bestSnap.valid ? bestSnap.syncToken : 0;
}


IFlushTarget::MemoryGain
DocumentMetaStoreFlushTarget::getApproxMemoryGain() const
{
    int64_t used(_dms->getStatus().getUsed());
    int64_t canFree = 0;
    if (_dms->canShrinkLidSpace()) {
        uint32_t committedDocIdLimit = _dms->getCommittedDocIdLimit();
        uint32_t numDocs = _dms->getNumDocs();
        if (committedDocIdLimit < numDocs) {
            canFree = sizeof(RawDocumentMetaData) *
                      (numDocs - committedDocIdLimit);
            if (canFree > used)
                canFree = used;
        }
    }
    return MemoryGain(used, used - canFree);
}


IFlushTarget::DiskGain
DocumentMetaStoreFlushTarget::getApproxDiskGain() const
{
    return DiskGain(0, 0);
}


IFlushTarget::Time
DocumentMetaStoreFlushTarget::getLastFlushTime() const
{
    return _lastFlushTime;
}


IFlushTarget::Task::UP
DocumentMetaStoreFlushTarget::initFlush(SerialNum currentSerial)
{
    // Called by document db executor
    (void)currentSerial;
    _dms->removeAllOldGenerations();
    SerialNum syncToken = currentSerial;
    syncToken = std::max(currentSerial,
                         _dms->getStatus().getLastSyncToken());
    if (syncToken <= getFlushedSerialNum()) {
        vespalib::LockGuard guard(_flusherLock);
        _lastFlushTime = fastos::ClockSystem::now();
        LOG(debug,
            "No document meta store to flush."
            " Update flush time to current: lastFlushTime(%f)",
            _lastFlushTime.sec());
        return Task::UP();
    }
    return Task::UP(new Flusher(*this, syncToken));
}


uint64_t
DocumentMetaStoreFlushTarget::getApproxBytesToWriteToDisk() const
{
    return _dms->getEstimatedSaveByteSize();
}


} // namespace proton
