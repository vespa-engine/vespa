// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <future>
#include "attribute_directory.h"
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.flushableattribute");

using namespace search;
using namespace vespalib;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using vespalib::makeTask;
using vespalib::makeClosure;
using searchcorespi::IFlushTarget;

namespace proton {

/**
 * Task performing the actual flushing to disk.
 **/
class FlushableAttribute::Flusher : public Task {
private:
    FlushableAttribute              & _fattr;
    search::AttributeMemorySaveTarget      _saveTarget;
    std::unique_ptr<search::AttributeSaver> _saver;
    uint64_t                          _syncToken;
    search::AttributeVector::BaseName _flushFile;

    bool saveAttribute(); // not updating snap info.
public:
    Flusher(FlushableAttribute & fattr, uint64_t syncToken, AttributeDirectory::Writer &writer);
    ~Flusher();
    uint64_t getSyncToken() const { return _syncToken; }
    bool flush(AttributeDirectory::Writer &writer);
    void updateStats();
    bool cleanUp(AttributeDirectory::Writer &writer);
    // Implements vespalib::Executor::Task
    virtual void run() override;

    virtual SerialNum
    getFlushSerial() const override
    {
        return _syncToken;
    }
};


FlushableAttribute::Flusher::Flusher(FlushableAttribute & fattr, SerialNum syncToken, AttributeDirectory::Writer &writer)
    : _fattr(fattr),
      _saveTarget(),
      _saver(),
      _syncToken(syncToken),
      _flushFile("")
{
    fattr._attr->commit(syncToken, syncToken);
    AttributeVector &attr = *_fattr._attr;
    // Called by attribute field writer executor
    _flushFile = writer.getSnapshotDir(_syncToken) + "/" + attr.getName();
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
FlushableAttribute::Flusher::saveAttribute()
{
    vespalib::mkdir(_flushFile.getDirName(), false);
    SerialNumFileHeaderContext fileHeaderContext(_fattr._fileHeaderContext,
                                                 _syncToken);
    bool saveSuccess = true;
    if (_saver && _saver->hasGenerationGuard() &&
        _fattr._hwInfo.disk().slow()) {
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
FlushableAttribute::Flusher::flush(AttributeDirectory::Writer &writer)
{
    writer.createInvalidSnapshot(_syncToken);
    if (!saveAttribute()) {
        LOG(warning, "Could not write attribute vector '%s' to disk",
            _flushFile.c_str());
        return false;
    }
    writer.markValidSnapshot(_syncToken);
    writer.setLastFlushTime(search::FileKit::getModificationTime(_flushFile.getDirName()));
    return true;
}

void
FlushableAttribute::Flusher::updateStats()
{
    _fattr._lastStats.setPath(_flushFile.getDirName());
}

bool
FlushableAttribute::Flusher::cleanUp(AttributeDirectory::Writer &writer)
{
    if (_fattr._cleanUpAfterFlush) {
        writer.invalidateOldSnapshots();
        writer.removeInvalidSnapshots();
    }
    return true;
}

void
FlushableAttribute::Flusher::run()
{
    auto writer = _fattr._attrDir->tryGetWriter();
    if (!writer || _syncToken <= _fattr.getFlushedSerialNum()) {
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

FlushableAttribute::FlushableAttribute(const AttributeVectorSP attr,
                                       const std::shared_ptr<AttributeDirectory> &attrDir,
                                       const TuneFileAttributes &
                                       tuneFileAttributes,
                                       const FileHeaderContext &
                                       fileHeaderContext,
                                       search::ISequencedTaskExecutor &
                                       attributeFieldWriter,
                                       const HwInfo &hwInfo)
    : IFlushTarget(vespalib::make_string(
                           "attribute.flush.%s",
                           attr->getName().c_str()),
            Type::SYNC, Component::ATTRIBUTE),
      _attr(attr),
      _cleanUpAfterFlush(true),
      _lastStats(),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _attributeFieldWriter(attributeFieldWriter),
      _hwInfo(hwInfo),
      _attrDir(attrDir)
{
    _lastStats.setPathElementsToLog(8);
}


FlushableAttribute::~FlushableAttribute()
{
}


IFlushTarget::SerialNum
FlushableAttribute::getFlushedSerialNum() const
{
    return _attrDir->getFlushedSerialNum();
}

IFlushTarget::MemoryGain
FlushableAttribute::getApproxMemoryGain() const
{
    int64_t used(_attr->getStatus().getUsed());
    return MemoryGain(used, used);
}

IFlushTarget::DiskGain
FlushableAttribute::getApproxDiskGain() const
{
    return DiskGain(0, 0);
}

IFlushTarget::Time
FlushableAttribute::getLastFlushTime() const
{
    return _attrDir->getLastFlushTime();
}

IFlushTarget::Task::UP
FlushableAttribute::internalInitFlush(SerialNum currentSerial)
{
    // Called by attribute field writer thread while document db executor waits
    _attr->removeAllOldGenerations();
    SerialNum syncToken = std::max(currentSerial,
                                   _attr->getStatus().getLastSyncToken());
    auto writer = _attrDir->tryGetWriter();
    if (!writer) {
        return Task::UP();
    }
    if (syncToken <= getFlushedSerialNum()) {
        writer->setLastFlushTime(fastos::ClockSystem::now());
        LOG(debug,
            "No attribute vector to flush."
            " Update flush time to current: lastFlushTime(%f)",
            getLastFlushTime().sec());
        return Task::UP();
    }
    return Task::UP(new Flusher(*this, syncToken, *writer));
}


IFlushTarget::Task::UP
FlushableAttribute::initFlush(SerialNum currentSerial)
{
    // Called by document db executor
    std::promise<IFlushTarget::Task::UP> promise;
    std::future<IFlushTarget::Task::UP> future = promise.get_future();
    _attributeFieldWriter.execute(_attributeFieldWriter.getExecutorId(_attr->getNamePrefix()),
                                  [&]() { promise.set_value(internalInitFlush(currentSerial)); });
    return future.get();
}


uint64_t
FlushableAttribute::getApproxBytesToWriteToDisk() const
{
    return _attr->getEstimatedSaveByteSize();
}

} // namespace proton
