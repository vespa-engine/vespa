// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore.documentmetastoreflushtarget");

#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include "documentmetastoreflushtarget.h"
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/attributesaver.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <fstream>
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchcore/proton/server/itlssyncer.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/attribute/attribute_directory.h>

using namespace search;
using namespace vespalib;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using vespalib::makeTask;
using vespalib::makeClosure;

namespace proton {

/**
 * Task performing the actual flushing to disk.
 **/
class DocumentMetaStoreFlushTarget::Flusher : public Task {
private:
    DocumentMetaStoreFlushTarget     &_dmsft;
    std::unique_ptr<search::AttributeSaver> _saver;
    uint64_t                          _syncToken;
    vespalib::string                  _flushDir;

    bool saveDocumentMetaStore(); // not updating snap info.
public:
    Flusher(DocumentMetaStoreFlushTarget &dmsft, uint64_t syncToken, AttributeDirectory::Writer &writer);
    ~Flusher();
    uint64_t getSyncToken() const { return _syncToken; }
    bool saveSnapInfo();
    bool flush(AttributeDirectory::Writer &writer);
    void updateStats();
    bool cleanUp(AttributeDirectory::Writer &writer);
    // Implements vespalib::Executor::Task
    virtual void run();

    virtual SerialNum
    getFlushSerial(void) const
    {
        return _syncToken;
    }
};

DocumentMetaStoreFlushTarget::Flusher::
Flusher(DocumentMetaStoreFlushTarget &dmsft,
        SerialNum syncToken, AttributeDirectory::Writer &writer)
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
    _flushDir = writer.getSnapshotDir(syncToken);
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
DocumentMetaStoreFlushTarget::Flusher::saveDocumentMetaStore()
{
    vespalib::mkdir(_flushDir, false);
    SerialNumFileHeaderContext fileHeaderContext(_dmsft._fileHeaderContext,
                                                 _syncToken);
    bool saveSuccess = false;
    if (_dmsft._hwInfo.slowDisk()) {
        search::AttributeMemorySaveTarget memorySaveTarget;
        saveSuccess = _saver->save(memorySaveTarget);
        _saver.reset();
        if (saveSuccess) {
            saveSuccess = memorySaveTarget.writeToFile(_dmsft._tuneFileAttributes,
                                                       fileHeaderContext);
        }
    } else {
        search::AttributeFileSaveTarget saveTarget(_dmsft._tuneFileAttributes,
                                                   fileHeaderContext);
        saveSuccess = _saver->save(saveTarget);
        _saver.reset();
    }
    return saveSuccess;
}

bool
DocumentMetaStoreFlushTarget::Flusher::flush(AttributeDirectory::Writer &writer)
{
    writer.createInvalidSnapshot(_syncToken);
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
     *
     * Assume only flush engine tries to flush document meta store, i.e.
     * noone else tries to get writer while flush task is flushing
     * document meta store to disk.
     */
    _dmsft._tlsSyncer.sync();
    writer.markValidSnapshot(_syncToken);
    writer.setLastFlushTime(search::FileKit::getModificationTime(_flushDir));
    return true;
}

void
DocumentMetaStoreFlushTarget::Flusher::updateStats()
{
    _dmsft._lastStats.setPath(_flushDir);
}

bool
DocumentMetaStoreFlushTarget::Flusher::cleanUp(AttributeDirectory::Writer &writer)
{
    if (_dmsft._cleanUpAfterFlush) {
        writer.invalidateOldSnapshots();
        writer.removeInvalidSnapshots(false);
    }
    return true;
}

void
DocumentMetaStoreFlushTarget::Flusher::run()
{
    auto writer = _dmsft._dmsDir->getWriter();
    if (!writer || _syncToken <= _dmsft.getFlushedSerialNum()) {
        // another flusher has created an equal or better snapshot
        // after this flusher was created
        return;
    }
    if (!flush(*writer)) {
        // TODO (geirst): throw exception ?
    }
    updateStats();
    if (!cleanUp(*writer)) {
        // TODO (geirst): throw exception ?
    }
}

DocumentMetaStoreFlushTarget::
DocumentMetaStoreFlushTarget(const DocumentMetaStore::SP dms,
                             ITlsSyncer &tlsSyncer,
                             const vespalib::string & baseDir,
                             const TuneFileAttributes &tuneFileAttributes,
                             const FileHeaderContext &fileHeaderContext,
                             const HwInfo &hwInfo)
    : IFlushTarget("documentmetastore", Type::SYNC, Component::ATTRIBUTE),
      _dms(dms),
      _tlsSyncer(tlsSyncer),
      _baseDir(baseDir),
      _cleanUpAfterFlush(true),
      _lastStats(),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _hwInfo(hwInfo),
      _diskLayout(AttributeDiskLayout::createSimple(baseDir)),
      _dmsDir(_diskLayout->createAttributeDir(""))

{
    _lastStats.setPathElementsToLog(8);
}


DocumentMetaStoreFlushTarget::~DocumentMetaStoreFlushTarget()
{
}


IFlushTarget::SerialNum
DocumentMetaStoreFlushTarget::getFlushedSerialNum() const
{
    return _dmsDir->getFlushedSerialNum();
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
    return _dmsDir->getLastFlushTime();
}


IFlushTarget::Task::UP
DocumentMetaStoreFlushTarget::initFlush(SerialNum currentSerial)
{
    // Called by document db executor
    _dms->removeAllOldGenerations();
    SerialNum syncToken = std::max(currentSerial,
                                   _dms->getStatus().getLastSyncToken());
    auto writer = _dmsDir->tryGetWriter();
    if (!writer) {
        return Task::UP();
    }
    if (syncToken <= getFlushedSerialNum()) {
        writer->setLastFlushTime(fastos::ClockSystem::now());
        LOG(debug,
            "No document meta store to flush."
            " Update flush time to current: lastFlushTime(%f)",
            getLastFlushTime().sec());
        return Task::UP();
    }
    return Task::UP(new Flusher(*this, syncToken, *writer));
}


uint64_t
DocumentMetaStoreFlushTarget::getApproxBytesToWriteToDisk() const
{
    return _dms->getEstimatedSaveByteSize();
}


} // namespace proton
