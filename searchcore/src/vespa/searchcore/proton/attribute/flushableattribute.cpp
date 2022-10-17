// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushableattribute.h"
#include "attributedisklayout.h"
#include "attribute_directory.h"
#include <vespa/searchlib/attribute/attributefilesavetarget.h>
#include <vespa/searchlib/attribute/attributesaver.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <filesystem>
#include <fstream>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.flushableattribute");

using namespace search;
using namespace vespalib;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using searchcorespi::IFlushTarget;

namespace proton {

/**
 * Task performing the actual flushing to disk.
 **/
class FlushableAttribute::Flusher : public Task {
private:
    FlushableAttribute                      & _fattr;
    search::AttributeMemorySaveTarget         _saveTarget;
    std::unique_ptr<search::AttributeSaver>   _saver;
    uint64_t                                  _syncToken;
    vespalib::string                          _flushFile;

    bool saveAttribute(); // not updating snap info.
public:
    Flusher(FlushableAttribute & fattr, uint64_t syncToken, AttributeDirectory::Writer &writer);
    ~Flusher() override;
    uint64_t getSyncToken() const { return _syncToken; }
    bool flush(AttributeDirectory::Writer &writer);
    void updateStats();
    bool cleanUp(AttributeDirectory::Writer &writer);
    // Implements vespalib::Executor::Task
    void run() override;

    SerialNum getFlushSerial() const override {
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
    fattr._attr->commit(CommitParam(syncToken));
    AttributeVector &attr = *_fattr._attr;
    // Called by attribute field writer executor
    _flushFile = writer.getSnapshotDir(_syncToken) + "/" + attr.getName();
    _saver = attr.initSave(_flushFile);
    if (!_saver) {
        // New style background save not available, use old style save.
        attr.save(_saveTarget, _flushFile);
    }
}

FlushableAttribute::Flusher::~Flusher() = default;

bool
FlushableAttribute::Flusher::saveAttribute()
{
    std::filesystem::create_directory(std::filesystem::path(vespalib::dirname(_flushFile)));
    SerialNumFileHeaderContext fileHeaderContext(_fattr._fileHeaderContext, _syncToken);
    bool saveSuccess = true;
    if (_saver && _saver->hasGenerationGuard() &&
        _fattr._hwInfo.disk().slow()) {
        saveSuccess = _saver->save(_saveTarget);
        _saver.reset();
    }
    if (saveSuccess) {
        if (_saver) {
            search::AttributeFileSaveTarget saveTarget(_fattr._tuneFileAttributes, fileHeaderContext);
            saveSuccess = _saver->save(saveTarget);
            _saver.reset();
        } else {
            saveSuccess = _saveTarget.writeToFile(_fattr._tuneFileAttributes, fileHeaderContext);
        }
    }
    return saveSuccess;
}

bool
FlushableAttribute::Flusher::flush(AttributeDirectory::Writer &writer)
{
    writer.createInvalidSnapshot(_syncToken);
    if (!saveAttribute()) {
        LOG(warning, "Could not write attribute vector '%s' to disk", _flushFile.c_str());
        return false;
    }
    writer.markValidSnapshot(_syncToken);
    writer.setLastFlushTime(search::FileKit::getModificationTime(vespalib::dirname(_flushFile)));
    return true;
}

void
FlushableAttribute::Flusher::updateStats()
{
    _fattr._lastStats.setPath(vespalib::dirname(_flushFile));
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

FlushableAttribute::FlushableAttribute(AttributeVectorSP attr,
                                       const std::shared_ptr<AttributeDirectory> &attrDir,
                                       const TuneFileAttributes &
                                       tuneFileAttributes,
                                       const FileHeaderContext &
                                       fileHeaderContext,
                                       vespalib::ISequencedTaskExecutor &
                                       attributeFieldWriter,
                                       const HwInfo &hwInfo)
    : IFlushTarget(make_string("attribute.flush.%s", attr->getName().c_str()), Type::SYNC, Component::ATTRIBUTE),
      _attr(attr),
      _cleanUpAfterFlush(true),
      _lastStats(),
      _tuneFileAttributes(tuneFileAttributes),
      _fileHeaderContext(fileHeaderContext),
      _attributeFieldWriter(attributeFieldWriter),
      _hwInfo(hwInfo),
      _attrDir(attrDir),
      _replay_operation_cost(0.0)
{
    _lastStats.setPathElementsToLog(8);
    auto &config = attr->getConfig();
    if (config.basicType() == search::attribute::BasicType::Type::TENSOR &&
        config.tensorType().is_dense() && config.hnsw_index_params().has_value())
    {
        _replay_operation_cost = 400.0; // replaying operations to hnsw index is 400 times more expensive than reading from tls
    }
}


FlushableAttribute::~FlushableAttribute() = default;

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
    _attr->reclaim_unused_memory();
    SerialNum syncToken = std::max(currentSerial, _attr->getStatus().getLastSyncToken());
    auto writer = _attrDir->tryGetWriter();
    if (!writer) {
        return Task::UP();
    }
    if (syncToken <= getFlushedSerialNum()) {
        writer->setLastFlushTime(vespalib::system_clock::now());
        LOG(debug,"No attribute vector to flush. Update flush time to current: lastFlushTime(%f)",
            vespalib::to_s(getLastFlushTime().time_since_epoch()));
        return Task::UP();
    }
    return std::make_unique<Flusher>(*this, syncToken, *writer);
}


IFlushTarget::Task::UP
FlushableAttribute::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken>)
{
    // Called by document db executor
    std::promise<IFlushTarget::Task::UP> promise;
    std::future<IFlushTarget::Task::UP> future = promise.get_future();
    _attributeFieldWriter.execute(_attributeFieldWriter.getExecutorIdFromName(_attr->getNamePrefix()),
                                  [&]() { promise.set_value(internalInitFlush(currentSerial)); });
    return future.get();
}


uint64_t
FlushableAttribute::getApproxBytesToWriteToDisk() const
{
    return _attr->getEstimatedSaveByteSize();
}

double
FlushableAttribute::get_replay_operation_cost() const
{
    return _replay_operation_cost;
}

} // namespace proton
