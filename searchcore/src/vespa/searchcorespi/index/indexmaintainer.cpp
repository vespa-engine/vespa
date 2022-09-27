// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexmaintainer.h"
#include "diskindexcleaner.h"
#include "eventlogger.h"
#include "fusionrunner.h"
#include "indexflushtarget.h"
#include "indexfusiontarget.h"
#include "indexreadutilities.h"
#include "indexwriteutilities.h"
#include "index_disk_dir.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcorespi/flush/lambdaflushtask.h>
#include <vespa/searchlib/common/i_flush_token.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/fastos/file.h>
#include <filesystem>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexmaintainer");

using document::Document;
using search::FixedSourceSelector;
using search::TuneFileAttributes;
using search::index::Schema;
using search::index::SchemaUtil;
using search::common::FileHeaderContext;
using search::queryeval::ISourceSelector;
using search::queryeval::Source;
using search::SerialNum;
using vespalib::makeLambdaTask;
using vespalib::makeSharedLambdaCallback;
using std::ostringstream;
using vespalib::string;
using vespalib::Executor;
using vespalib::Runnable;
using vespalib::IDestructorCallback;

namespace searchcorespi::index {

using Configure = IIndexManager::Configure;
using Reconfigurer = IIndexManager::Reconfigurer;

namespace {

class ReconfigRunnable : public Runnable {
public:
    bool &_result;
    Reconfigurer &_reconfigurer;
    std::unique_ptr<Configure>   _configure;

    ReconfigRunnable(bool &result, Reconfigurer &reconfigurer, std::unique_ptr<Configure> configure)
        : _result(result),
          _reconfigurer(reconfigurer),
          _configure(std::move(configure))
    { }

    void run() override {
        _result = _reconfigurer.reconfigure(std::move(_configure));
    }
};

class ReconfigRunnableTask : public Executor::Task {
private:
    Reconfigurer &_reconfigurer;
    std::unique_ptr<Configure>   _configure;
public:
    ReconfigRunnableTask(Reconfigurer &reconfigurer, std::unique_ptr<Configure> configure)
        : _reconfigurer(reconfigurer),
          _configure(std::move(configure))
    { }
    ~ReconfigRunnableTask() override;
    void run() override {
        _reconfigurer.reconfigure(std::move(_configure));
    }
};

ReconfigRunnableTask::~ReconfigRunnableTask() = default;

SerialNum noSerialNumHigh = std::numeric_limits<SerialNum>::max();


class DiskIndexWithDestructorCallback : public IDiskIndex {
private:
    std::shared_ptr<IDestructorCallback> _callback;
    IDiskIndex::SP                       _index;
    IndexDiskDir                         _index_disk_dir;
    IndexDiskLayout&                     _layout;
    DiskIndexes&                         _disk_indexes;

public:
    DiskIndexWithDestructorCallback(IDiskIndex::SP index,
                                    std::shared_ptr<IDestructorCallback> callback,
                                    IndexDiskLayout& layout,
                                    DiskIndexes& disk_indexes) noexcept
        : _callback(std::move(callback)),
          _index(std::move(index)),
          _index_disk_dir(IndexDiskLayout::get_index_disk_dir(_index->getIndexDir())),
          _layout(layout),
          _disk_indexes(disk_indexes)
    {
    }
    ~DiskIndexWithDestructorCallback() override;
    const IDiskIndex &getWrapped() const { return *_index; }

    /**
     * Implements searchcorespi::IndexSearchable
     */
    Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpec &field,
                    const Node &term) override
    {
        FieldSpecList fsl;
        fsl.add(field);
        return _index->createBlueprint(requestContext, fsl, term);
    }
    Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpecList &fields,
                    const Node &term) override
    {
        return _index->createBlueprint(requestContext, fields, term);
    }
    search::SearchableStats getSearchableStats() const override;
    search::SerialNum getSerialNum() const override {
        return _index->getSerialNum();
    }
    void accept(IndexSearchableVisitor &visitor) const override {
        _index->accept(visitor);
    }

    /**
     * Implements IFieldLengthInspector
     */
    search::index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        return _index->get_field_length_info(field_name);
    }

    /**
     * Implements IDiskIndex
     */
    const vespalib::string &getIndexDir() const override { return _index->getIndexDir(); }
    const search::index::Schema &getSchema() const override { return _index->getSchema(); }

};

DiskIndexWithDestructorCallback::~DiskIndexWithDestructorCallback() = default;

search::SearchableStats
DiskIndexWithDestructorCallback::getSearchableStats() const
{
    auto stats = _index->getSearchableStats();
    uint64_t transient_size = _disk_indexes.get_transient_size(_layout, _index_disk_dir);
    stats.fusion_size_on_disk(transient_size);
    return stats;
}


}  // namespace

IndexMaintainer::FrozenMemoryIndexRef::~FrozenMemoryIndexRef() = default;

IndexMaintainer::FusionArgs::FusionArgs()
    : _new_fusion_id(0u),
      _changeGens(),
      _schema(),
      _prunedSchema(),
      _old_source_list()
{ }

IndexMaintainer::FusionArgs::~FusionArgs() = default;

IndexMaintainer::SetSchemaArgs::SetSchemaArgs() = default;
IndexMaintainer::SetSchemaArgs::~SetSchemaArgs() = default;

uint32_t
IndexMaintainer::getNewAbsoluteId()
{
    return _next_id++;
}

string
IndexMaintainer::getFlushDir(uint32_t sourceId) const
{
    return _layout.getFlushDir(sourceId);
}

string
IndexMaintainer::getFusionDir(uint32_t sourceId) const
{
    return _layout.getFusionDir(sourceId);
}

bool
IndexMaintainer::reopenDiskIndexes(ISearchableIndexCollection &coll)
{
    bool hasReopenedAnything(false);
    assert(_ctx.getThreadingService().master().isCurrentThread());
    uint32_t count = coll.getSourceCount();
    for (uint32_t i = 0; i < count; ++i) {
        IndexSearchable &is = coll.getSearchable(i);
        const auto *const d = dynamic_cast<const DiskIndexWithDestructorCallback *>(&is);
        if (d == nullptr) {
            continue;	// not a disk index
        }
        const string indexDir = d->getIndexDir();
        vespalib::string schemaName = IndexDiskLayout::getSchemaFileName(indexDir);
        Schema trimmedSchema;
        if (!trimmedSchema.loadFromFile(schemaName)) {
            LOG(error, "Could not open schema '%s'", schemaName.c_str());
        }
        if (trimmedSchema != d->getSchema()) {
            IDiskIndex::SP newIndex(reloadDiskIndex(*d));
            coll.replace(coll.getSourceId(i), newIndex);
            hasReopenedAnything = true;
        }
    }
    return hasReopenedAnything;
}

void
IndexMaintainer::updateDiskIndexSchema(const vespalib::string &indexDir,
                                       const Schema &schema,
                                       SerialNum serialNum)
{
    // Called by a flush worker thread OR document db executor thread
    LockGuard lock(_schemaUpdateLock);
    IndexWriteUtilities::updateDiskIndexSchema(indexDir, schema, serialNum);
}

void
IndexMaintainer::updateIndexSchemas(IIndexCollection &coll,
                                    const Schema &schema,
                                    SerialNum serialNum)
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    uint32_t count = coll.getSourceCount();
    for (uint32_t i = 0; i < count; ++i) {
        IndexSearchable &is = coll.getSearchable(i);
        const auto *const d = dynamic_cast<const DiskIndexWithDestructorCallback *>(&is);
        if (d == nullptr) {
            IMemoryIndex *const m = dynamic_cast<IMemoryIndex *>(&is);
            if (m != nullptr) {
                m->pruneRemovedFields(schema);
            }
            continue;
        }
        updateDiskIndexSchema(d->getIndexDir(), schema, serialNum);
    }
}

void
IndexMaintainer::updateActiveFusionPrunedSchema(const Schema &schema)
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    for (;;) {
        Schema::SP activeFusionSchema;
        Schema::SP activeFusionPrunedSchema;
        Schema::SP newActiveFusionPrunedSchema;
        {
            LockGuard lock(_state_lock);
            activeFusionSchema = _activeFusionSchema;
            activeFusionPrunedSchema = _activeFusionPrunedSchema;
        }
        if (!activeFusionSchema)
            return;	// No active fusion
        if (!activeFusionPrunedSchema) {
            Schema::UP newSchema = Schema::intersect(*activeFusionSchema, schema);
            newActiveFusionPrunedSchema = std::move(newSchema);
        } else {
            Schema::UP newSchema = Schema::intersect(*activeFusionPrunedSchema, schema);
            newActiveFusionPrunedSchema = std::move(newSchema);
        }
        {
            LockGuard slock(_state_lock);
            LockGuard ilock(_index_update_lock);
            if (activeFusionSchema == _activeFusionSchema &&
                activeFusionPrunedSchema == _activeFusionPrunedSchema)
            {
                _activeFusionPrunedSchema = newActiveFusionPrunedSchema;
                break;
            }
        }
    }
}

void
IndexMaintainer::deactivateDiskIndexes(vespalib::string indexDir)
{
    _disk_indexes->notActive(indexDir);
    removeOldDiskIndexes();
}

IDiskIndex::SP
IndexMaintainer::loadDiskIndex(const string &indexDir)
{
    // Called by a flush worker thread OR CTOR (in document db init executor thread)
    if (LOG_WOULD_LOG(event)) {
        EventLogger::diskIndexLoadStart(indexDir);
    }
    vespalib::Timer timer;
    auto index = _operations.loadDiskIndex(indexDir);
    auto stats = index->getSearchableStats();
    _disk_indexes->setActive(indexDir, stats.sizeOnDisk());
    auto retval = std::make_shared<DiskIndexWithDestructorCallback>(
            std::move(index),
            makeSharedLambdaCallback([this, indexDir]() { deactivateDiskIndexes(indexDir); }),
            _layout, *_disk_indexes);
    if (LOG_WOULD_LOG(event)) {
        EventLogger::diskIndexLoadComplete(indexDir, vespalib::count_ms(timer.elapsed()));
    }
    return retval;
}

IDiskIndex::SP
IndexMaintainer::reloadDiskIndex(const IDiskIndex &oldIndex)
{
    // Called by a flush worker thread OR document db executor thread
    const string indexDir = oldIndex.getIndexDir();
    if (LOG_WOULD_LOG(event)) {
        EventLogger::diskIndexLoadStart(indexDir);
    }
    vespalib::Timer timer;
    const IDiskIndex &wrappedDiskIndex = (dynamic_cast<const DiskIndexWithDestructorCallback &>(oldIndex)).getWrapped();
    auto index = _operations.reloadDiskIndex(wrappedDiskIndex);
    auto stats = index->getSearchableStats();
    _disk_indexes->setActive(indexDir, stats.sizeOnDisk());
    auto retval = std::make_shared<DiskIndexWithDestructorCallback>(
            std::move(index),
            makeSharedLambdaCallback([this, indexDir]() { deactivateDiskIndexes(indexDir); }),
            _layout, *_disk_indexes);
    if (LOG_WOULD_LOG(event)) {
        EventLogger::diskIndexLoadComplete(indexDir, vespalib::count_ms(timer.elapsed()));
    }
    return retval;
}

IDiskIndex::SP
IndexMaintainer::flushMemoryIndex(IMemoryIndex &memoryIndex,
                                  uint32_t indexId,
                                  uint32_t docIdLimit,
                                  SerialNum serialNum,
                                  FixedSourceSelector::SaveInfo &saveInfo)
{
    // Called by a flush worker thread
    const string flushDir = getFlushDir(indexId);
    memoryIndex.flushToDisk(flushDir, docIdLimit, serialNum);
    Schema::SP prunedSchema(memoryIndex.getPrunedSchema());
    if (prunedSchema) {
        updateDiskIndexSchema(flushDir, *prunedSchema, noSerialNumHigh);
    }
    IndexWriteUtilities::writeSourceSelector(saveInfo, indexId, getAttrTune(),
                                             _ctx.getFileHeaderContext(), serialNum);
    IndexWriteUtilities::writeSerialNum(serialNum, flushDir, _ctx.getFileHeaderContext());
    return loadDiskIndex(flushDir);
}

ISearchableIndexCollection::UP
IndexMaintainer::loadDiskIndexes(const FusionSpec &spec, ISearchableIndexCollection::UP sourceList)
{
    // Called by CTOR (in document db init executor thread)
    uint32_t fusion_id = spec.last_fusion_id;
    if (fusion_id != 0) {
        sourceList->append(0, loadDiskIndex(getFusionDir(fusion_id)));
    }
    for (size_t i = 0; i < spec.flush_ids.size(); ++i) {
        const uint32_t id = spec.flush_ids[i];
        const uint32_t relative_id = id - fusion_id;
        sourceList->append(relative_id, loadDiskIndex(getFlushDir(id)));
    }
    return sourceList;
}

namespace {

    using LockGuard = std::lock_guard<std::mutex>;

ISearchableIndexCollection::SP
getLeaf(const LockGuard &newSearchLock, const ISearchableIndexCollection::SP & is, bool warn=false)
{
    if (dynamic_cast<const WarmupIndexCollection *>(is.get()) != nullptr) {
        if (warn) {
            LOG(info, "Already warming up an index '%s'. Start using it immediately."
                      " This is an indication that you have configured your warmup interval too long.",
                      is->toString().c_str());
        }
        const WarmupIndexCollection & wic(dynamic_cast<const WarmupIndexCollection &>(*is));
        return getLeaf(newSearchLock, wic.getNextIndexCollection(), warn);
    } else {
        return is;
    }
}

}

/*
 * Caller must hold _state_lock (SL).
 */
void
IndexMaintainer::replaceSource(uint32_t sourceId, const IndexSearchable::SP &source)
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    LockGuard lock(_new_search_lock);
    ISearchableIndexCollection::UP indexes = createNewSourceCollection(lock);
    indexes->replace(sourceId, source);
    swapInNewIndex(lock, std::move(indexes), *source);
}

/*
 * Caller must hold _state_lock (SL) and _new_search_lock (NSL), the latter
 * passed as guard.
 */
void
IndexMaintainer::swapInNewIndex(LockGuard & guard,
                                ISearchableIndexCollection::SP indexes,
                                IndexSearchable & source)
{
    assert(indexes->valid());
    (void) guard;
    if (_warmupConfig.getDuration() > vespalib::duration::zero()) {
        if (dynamic_cast<const IDiskIndex *>(&source) != nullptr) {
            LOG(debug, "Warming up a disk index.");
            indexes = std::make_shared<WarmupIndexCollection>
                      (_warmupConfig, getLeaf(guard, _source_list, true), indexes,
                       static_cast<IDiskIndex &>(source), _ctx.getWarmupExecutor(),
                       _ctx.getThreadingService().clock(), *this);
        } else {
            LOG(debug, "No warmup needed as it is a memory index that is mapped in.");
        }
    }
    LOG(debug, "Replacing indexcollection :\n%s\nwith\n%s", _source_list->toString().c_str(), indexes->toString().c_str());
    assert(indexes->valid());
    _source_list = std::move(indexes);
}

/*
 * Caller must hold _state_lock (SL).
 */
void
IndexMaintainer::appendSource(uint32_t sourceId, const IndexSearchable::SP &source)
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    LockGuard lock(_new_search_lock);
    ISearchableIndexCollection::UP indexes = createNewSourceCollection(lock);
    indexes->append(sourceId, source);
    swapInNewIndex(lock, std::move(indexes), *source);
}

ISearchableIndexCollection::UP
IndexMaintainer::createNewSourceCollection(const LockGuard &newSearchLock)
{
    ISearchableIndexCollection::SP currentLeaf(getLeaf(newSearchLock, _source_list));
    return std::make_unique<IndexCollection>(_selector, *currentLeaf);
}

IndexMaintainer::FlushArgs::FlushArgs()
    : old_index(),
      old_absolute_id(0),
      old_source_list(),
      save_info(),
      flush_serial_num(),
      stats(nullptr),
      _skippedEmptyLast(false),
      _extraIndexes(),
      _changeGens(),
      _prunedSchema()
{
}
IndexMaintainer::FlushArgs::~FlushArgs() = default;
IndexMaintainer::FlushArgs::FlushArgs(FlushArgs &&) = default;
IndexMaintainer::FlushArgs & IndexMaintainer::FlushArgs::operator=(FlushArgs &&) = default;

bool
IndexMaintainer::doneInitFlush(FlushArgs *args, IMemoryIndex::SP *new_index)
{
    // Called by initFlush via reconfigurer
    assert(_ctx.getThreadingService().master().isCurrentThread());
    LockGuard state_lock(_state_lock);
    args->old_index = _current_index;
    args->old_absolute_id = _current_index_id + _last_fusion_id;
    args->old_source_list = _source_list;
    string selector_name = IndexDiskLayout::getSelectorFileName(getFlushDir(args->old_absolute_id));
    args->flush_serial_num = current_serial_num();
    {
        LockGuard lock(_index_update_lock);
        // Handover of extra memory indexes to flush
        args->_extraIndexes = _frozenMemoryIndexes;
        _frozenMemoryIndexes.clear();
    }

    LOG(debug, "Flushing. Id = %u. Serial num = %llu",
        args->old_absolute_id, (unsigned long long) args->flush_serial_num);
    {
        LockGuard lock(_index_update_lock);
        if (!_current_index->hasReceivedDocumentInsert() &&
            _source_selector_changes == 0 &&
            !_flush_empty_current_index)
        {
            args->_skippedEmptyLast = true; // Skip flush of empty memory index
        }

        if (!args->_skippedEmptyLast) {
            // Keep on using same source selector with extended valid range
            args->save_info = getSourceSelector().extractSaveInfo(selector_name);
            // XXX: Overflow issue in source selector
            _current_index_id = getNewAbsoluteId() - _last_fusion_id;
            assert(_current_index_id < ISourceSelector::SOURCE_LIMIT);
            _selector->setDefaultSource(_current_index_id);
            _source_selector_changes = 0;
        }
        _current_index = *new_index;
        _flush_empty_current_index = false;
    }
    if (args->_skippedEmptyLast) {
        replaceSource(_current_index_id, _current_index);
    } else {
        appendSource(_current_index_id, _current_index);
    }
    _source_list->setCurrentIndex(_current_index_id);
    return true;
}

void
IndexMaintainer::doFlush(FlushArgs args)
{
    // Called by a flush worker thread
    FlushIds flushIds; // Absolute ids of flushed indexes

    flushFrozenMemoryIndexes(args, flushIds);

    if (!args._skippedEmptyLast) {
        flushLastMemoryIndex(args, flushIds);
    }

    assert(!flushIds.empty());
    if (args.stats != nullptr) {
        updateFlushStats(args);
    }

    scheduleFusion(flushIds);
}

void
IndexMaintainer::flushFrozenMemoryIndexes(FlushArgs &args, FlushIds &flushIds)
{
    // Called by a flush worker thread
    for (FrozenMemoryIndexRef & frozen : args._extraIndexes) {
        assert(frozen._absoluteId < args.old_absolute_id);
        assert(flushIds.empty() || flushIds.back() < frozen._absoluteId);

        FlushArgs eArgs;
        eArgs.old_index = frozen._index;
        eArgs.flush_serial_num = frozen._serialNum;
        eArgs.old_absolute_id = frozen._absoluteId;
        const uint32_t docIdLimit = frozen._saveInfo->getHeader()._docIdLimit;

        flushMemoryIndex(eArgs, docIdLimit, *frozen._saveInfo, flushIds);

        // Drop references to old memory index and old save info.
        frozen._index.reset();
        frozen._saveInfo.reset();
    }
}

void
IndexMaintainer::flushLastMemoryIndex(FlushArgs &args, FlushIds &flushIds)
{
    // Called by a flush worker thread
    const uint32_t docIdLimit = args.save_info->getHeader()._docIdLimit;
    flushMemoryIndex(args, docIdLimit, *args.save_info, flushIds);
}

void
IndexMaintainer::updateFlushStats(const FlushArgs &args)
{
    // Called by a flush worker thread
    vespalib::string flushDir;
    if (!args._skippedEmptyLast) {
        flushDir = getFlushDir(args.old_absolute_id);
    } else {
        assert(!args._extraIndexes.empty());
        flushDir = getFlushDir(args._extraIndexes.back()._absoluteId);
    }
    args.stats->setPath(flushDir);
}

void
IndexMaintainer::flushMemoryIndex(FlushArgs &args,
                                  uint32_t docIdLimit,
                                  FixedSourceSelector::SaveInfo &saveInfo,
                                  FlushIds &flushIds)
{
    // Called by a flush worker thread
    ChangeGens changeGens = getChangeGens();
    IMemoryIndex &memoryIndex = *args.old_index;
    Schema::SP prunedSchema = memoryIndex.getPrunedSchema();
    IDiskIndex::SP diskIndex = flushMemoryIndex(memoryIndex, args.old_absolute_id,
                                                docIdLimit, args.flush_serial_num,
                                                saveInfo);
    // Post processing after memory index has been written to disk and
    // opened as disk index.
    args._changeGens = changeGens;
    args._prunedSchema = prunedSchema;
    reconfigureAfterFlush(args, diskIndex);

    flushIds.push_back(args.old_absolute_id);
}


void
IndexMaintainer::reconfigureAfterFlush(FlushArgs &args, IDiskIndex::SP &diskIndex)
{
    // Called by a flush worker thread
    for (;;) {
        // Call reconfig closure for this change
        auto configure = makeLambdaConfigure([this, argsP=&args, diskIndexP=&diskIndex]() {
            return doneFlush(argsP, diskIndexP);
        });
        if (reconfigure(std::move(configure))) {
            return;
        }
        ChangeGens changeGens = getChangeGens();
        Schema::SP prunedSchema = args.old_index->getPrunedSchema();
        const string indexDir = getFlushDir(args.old_absolute_id);
        if (prunedSchema) {
            updateDiskIndexSchema(indexDir, *prunedSchema, noSerialNumHigh);
        }
        IDiskIndex::SP reloadedDiskIndex = reloadDiskIndex(*diskIndex);
        diskIndex = reloadedDiskIndex;
        args._changeGens = changeGens;
        args._prunedSchema = prunedSchema;
    }
}


bool
IndexMaintainer::doneFlush(FlushArgs *args, IDiskIndex::SP *disk_index) {
    // Called by doFlush via reconfigurer
    assert(_ctx.getThreadingService().master().isCurrentThread());
    LockGuard state_lock(_state_lock);
    IMemoryIndex &memoryIndex = *args->old_index;
    if (args->_changeGens != getChangeGens()) {
        return false;    // Must retry operation
    }
    if (args->_prunedSchema != memoryIndex.getPrunedSchema()) {
        return false;    // Must retry operation
    }
    set_flush_serial_num(std::max(flush_serial_num(), args->flush_serial_num));
    vespalib::system_time timeStamp = search::FileKit::getModificationTime((*disk_index)->getIndexDir());
    _lastFlushTime = timeStamp > _lastFlushTime ? timeStamp : _lastFlushTime;
    const uint32_t old_id = args->old_absolute_id - _last_fusion_id;
    replaceSource(old_id, *disk_index);
    return true;
}

void
IndexMaintainer::scheduleFusion(const FlushIds &flushIds)
{
    // Called by a flush worker thread
    LOG(debug, "Scheduled fusion for id %u.", flushIds.back());
    LockGuard guard(_fusion_lock);
    for (uint32_t id : flushIds) {
        _fusion_spec.flush_ids.push_back(id);
    }
}

bool
IndexMaintainer::canRunFusion(const FusionSpec &spec) const
{
    return spec.flush_ids.size() > 1 ||
        (spec.flush_ids.size() > 0 && spec.last_fusion_id != 0);
}

bool
IndexMaintainer::doneFusion(FusionArgs *args, IDiskIndex::SP *new_index)
{
    // Called by runFusion via reconfigurer
    assert(_ctx.getThreadingService().master().isCurrentThread());
    LockGuard state_lock(_state_lock);
    if (args->_changeGens != getChangeGens()) {
        return false;    // Must retry operation
    }
    if (args->_prunedSchema != getActiveFusionPrunedSchema()) {
        return false;    // Must retry operation
    }
    args->_old_source_list = _source_list; // delays destruction
    uint32_t id_diff = args->_new_fusion_id - _last_fusion_id;
    ostringstream ost;
    ost << "sourceselector_fusion(" << args->_new_fusion_id << ")";
    {
        LockGuard lock(_index_update_lock);

        // make new source selector with shifted values.
        _selector = getSourceSelector().cloneAndSubtract(ost.str(), id_diff);
        _source_selector_changes = 0;
        _current_index_id -= id_diff;
        _last_fusion_id = args->_new_fusion_id;
        _selector->setBaseId(_last_fusion_id);
        _activeFusionSchema.reset();
        _activeFusionPrunedSchema.reset();
    }

    ISearchableIndexCollection::SP currentLeaf;
    {
        LockGuard lock(_new_search_lock);
        currentLeaf = getLeaf(lock, _source_list);
    }
    ISearchableIndexCollection::UP fsc =
        IndexCollection::replaceAndRenumber(_selector, *currentLeaf, id_diff, *new_index);
    fsc->setCurrentIndex(_current_index_id);

    {
        LockGuard lock(_new_search_lock);
        swapInNewIndex(lock, std::move(fsc), **new_index);
    }
    return true;
}

bool
IndexMaintainer::makeSureAllRemainingWarmupIsDone(std::shared_ptr<WarmupIndexCollection> keepAlive)
{
    // called by warmupDone via reconfigurer, warmupDone() doesn't wait for us
    assert(_ctx.getThreadingService().master().isCurrentThread());
    ISearchableIndexCollection::SP warmIndex;
    {
        LockGuard state_lock(_state_lock);
        if (keepAlive == _source_list) {
            LockGuard lock(_new_search_lock);
            warmIndex = (getLeaf(lock, _source_list, false));
            _source_list = warmIndex;
        }
    }
    if (warmIndex) {
        LOG(info, "New index warmed up and switched in : %s", warmIndex->toString().c_str());
    }
    LOG(info, "Sync warmupExecutor.");
    keepAlive->drainPending();
    LOG(info, "Now the keep alive of the warmupindexcollection should be gone.");
    return true;
}

void
IndexMaintainer::warmupDone(std::shared_ptr<WarmupIndexCollection> current)
{
    // Called by a search thread
    LockGuard lock(_new_search_lock);
    if (current == _source_list) {
        auto makeSure = makeLambdaConfigure([this, collection=std::move(current)]() {
            return makeSureAllRemainingWarmupIsDone(std::move(collection));
        });
        auto task = std::make_unique<ReconfigRunnableTask>(_ctx.getReconfigurer(), std::move(makeSure));
        _ctx.getThreadingService().master().execute(std::move(task));
    } else {
        LOG(warning, "There has arrived a new IndexCollection while replacing the active index. "
                     "It can theoretically happen, but not very likely, so logging this as a warning.");
    }
}

namespace {

bool
has_matching_interleaved_features(const Schema& old_schema, const Schema& new_schema)
{
    for (SchemaUtil::IndexIterator itr(new_schema); itr.isValid(); ++itr) {
        if (itr.hasMatchingOldFields(old_schema) &&
                !itr.has_matching_use_interleaved_features(old_schema))
        {
            return false;
        }
    }
    return true;
}

}


void
IndexMaintainer::doneSetSchema(SetSchemaArgs &args, IMemoryIndex::SP &newIndex)
{
    assert(_ctx.getThreadingService().master().isCurrentThread()); // with idle index executor
    LockGuard state_lock(_state_lock);
    typedef FixedSourceSelector::SaveInfo SaveInfo;
    args._oldSchema = _schema;		// Delay destruction
    args._oldIndex = _current_index;	// Delay destruction
    args._oldSourceList = _source_list; // Delay destruction
    uint32_t oldAbsoluteId = _current_index_id + _last_fusion_id;
    string selectorName = IndexDiskLayout::getSelectorFileName(getFlushDir(oldAbsoluteId));
    SerialNum freezeSerialNum = current_serial_num();
    bool dropEmptyLast = false;
    SaveInfo::UP saveInfo;

    LOG(info, "Making new schema. Id = %u. Serial num = %llu", oldAbsoluteId, (unsigned long long) freezeSerialNum);
    {
        LockGuard lock(_index_update_lock);
        _schema = args._newSchema;
        if (!_current_index->hasReceivedDocumentInsert()) {
            dropEmptyLast = true; // Skip flush of empty memory index
        }

        if (!dropEmptyLast) {
            // Keep on using same source selector with extended valid range
            saveInfo = getSourceSelector().extractSaveInfo(selectorName);
            // XXX: Overflow issue in source selector
            _current_index_id = getNewAbsoluteId() - _last_fusion_id;
            assert(_current_index_id < ISourceSelector::SOURCE_LIMIT);
            _selector->setDefaultSource(_current_index_id);
            // Extra index to flush next time flushing is performed
            _frozenMemoryIndexes.emplace_back(args._oldIndex, freezeSerialNum, std::move(saveInfo), oldAbsoluteId);
        }
        _current_index = newIndex;
        // Non-matching interleaved features in schemas means that we need to
        // reconstruct or drop interleaved features in posting lists.
        // If so, we must flush the new index to disk even if it is empty.
        // This ensures that 2x triggerFlush will run fusion
        // to reconstruct or drop interleaved features in the posting lists.
        _flush_empty_current_index = !has_matching_interleaved_features(args._oldSchema, args._newSchema);
    }
    if (dropEmptyLast) {
        replaceSource(_current_index_id, _current_index);
    } else {
        appendSource(_current_index_id, _current_index);
    }
    _source_list->setCurrentIndex(_current_index_id);
}


Schema
IndexMaintainer::getSchema(void) const
{
    LockGuard lock(_index_update_lock);
    return _schema;
}

Schema::SP
IndexMaintainer::getActiveFusionPrunedSchema(void) const
{
    LockGuard lock(_index_update_lock);
    return _activeFusionPrunedSchema;
}

TuneFileAttributes
IndexMaintainer::getAttrTune(void)
{
    return _tuneFileAttributes;
}

IndexMaintainer::ChangeGens
IndexMaintainer::getChangeGens(void)
{
    LockGuard lock(_index_update_lock);
    return _changeGens;
}

bool
IndexMaintainer::reconfigure(std::unique_ptr<Configure> configure)
{
    // Called by a flush engine worker thread
    bool result = false;
    ReconfigRunnable runnable(result, _ctx.getReconfigurer(), std::move(configure));
    _ctx.getThreadingService().master().run(runnable);
    return result;
}

IndexMaintainer::IndexMaintainer(const IndexMaintainerConfig &config,
                                 const IndexMaintainerContext &ctx,
                                 IIndexMaintainerOperations &operations)
    : _base_dir(config.getBaseDir()),
      _warmupConfig(config.getWarmup()),
      _disk_indexes(std::make_shared<DiskIndexes>()),
      _layout(config.getBaseDir()),
      _schema(config.getSchema()),
      _activeFusionSchema(),
      _activeFusionPrunedSchema(),
      _source_selector_changes(0),
      _selector(),
      _source_list(),
      _last_fusion_id(),
      _next_id(),
      _current_index_id(),
      _current_index(),
      _flush_empty_current_index(false),
      _current_serial_num(0),
      _flush_serial_num(0),
      _lastFlushTime(),
      _frozenMemoryIndexes(),
      _state_lock(),
      _index_update_lock(),
      _new_search_lock(),
      _remove_lock(),
      _fusion_spec(),
      _fusion_lock(),
      _maxFlushed(config.getMaxFlushed()),
      _maxFrozen(10),
      _changeGens(),
      _schemaUpdateLock(),
      _tuneFileAttributes(config.getTuneFileAttributes()),
      _ctx(ctx),
      _operations(operations)
{
    // Called by document db init executor thread
    _changeGens.bumpPruneGen();
    DiskIndexCleaner::clean(_base_dir, *_disk_indexes);
    FusionSpec spec = IndexReadUtilities::readFusionSpec(_base_dir);
    _next_id = 1 + (spec.flush_ids.empty() ? spec.last_fusion_id : spec.flush_ids.back());
    _last_fusion_id = spec.last_fusion_id;

    if (_next_id > 1) {
        string latest_index_dir = spec.flush_ids.empty()
                                  ? getFusionDir(_next_id - 1)
                                  : getFlushDir(_next_id - 1);

        set_flush_serial_num(IndexReadUtilities::readSerialNum(latest_index_dir));
        _lastFlushTime = search::FileKit::getModificationTime(latest_index_dir);
        set_current_serial_num(flush_serial_num());
        const string selector = IndexDiskLayout::getSelectorFileName(latest_index_dir);
        _selector = FixedSourceSelector::load(selector, _next_id - 1);
    } else {
        set_flush_serial_num(0);
        _selector = std::make_shared<FixedSourceSelector>(0, "sourceselector", 1);
    }
    uint32_t baseId(_selector->getBaseId());
    if (_last_fusion_id != baseId) {
        assert(_last_fusion_id > baseId);
        uint32_t id_diff = _last_fusion_id - baseId;
        ostringstream ost;
        ost << "sourceselector_fusion(" << _last_fusion_id << ")";
        _selector = getSourceSelector().cloneAndSubtract(ost.str(), id_diff);
        assert(_last_fusion_id == _selector->getBaseId());
    }
    _current_index_id = getNewAbsoluteId() - _last_fusion_id;
    assert(_current_index_id < ISourceSelector::SOURCE_LIMIT);
    _selector->setDefaultSource(_current_index_id);
    auto sourceList = loadDiskIndexes(spec, std::make_unique<IndexCollection>(_selector));
    _current_index = operations.createMemoryIndex(_schema, *sourceList, current_serial_num());
    LOG(debug, "Index manager created with flushed serial num %" PRIu64, flush_serial_num());
    sourceList->append(_current_index_id, _current_index);
    sourceList->setCurrentIndex(_current_index_id);
    _source_list = std::move(sourceList);
    _fusion_spec = spec;
    _ctx.getThreadingService().master().execute(makeLambdaTask([this,&config]() {
        pruneRemovedFields(_schema, config.getSerialNum());
    }));
    _ctx.getThreadingService().master().sync();
}

IndexMaintainer::~IndexMaintainer()
{
    _source_list.reset();
    _frozenMemoryIndexes.clear();
    _selector.reset();
}

FlushTask::UP
IndexMaintainer::initFlush(SerialNum serialNum, searchcorespi::FlushStats * stats)
{
    assert(_ctx.getThreadingService().master().isCurrentThread()); // while flush engine scheduler thread waits
    {
        LockGuard lock(_index_update_lock);
        set_current_serial_num(std::max(current_serial_num(), serialNum));
    }

    IMemoryIndex::SP new_index(_operations.createMemoryIndex(getSchema(), *_current_index, current_serial_num()));
    FlushArgs args;
    args.stats = stats;
    // Ensure that all index thread tasks accessing memory index have completed.
    commit_and_wait();
    // Call reconfig closure for this change
    auto configure = makeLambdaConfigure([this, argsP=&args, indexP=&new_index]() {
        return doneInitFlush(argsP, indexP);
    });
    bool success = _ctx.getReconfigurer().reconfigure(std::move(configure));
    assert(success);
    (void) success;
    if (args._skippedEmptyLast && args._extraIndexes.empty()) {
        // No memory index to flush, it was empty
        LockGuard lock(_state_lock);
        set_flush_serial_num(current_serial_num());
        _lastFlushTime = vespalib::system_clock::now();
        LOG(debug, "No memory index to flush. Update serial number and flush time to current: "
            "flushSerialNum(%" PRIu64 "), lastFlushTime(%f)",
            flush_serial_num(), vespalib::to_s(_lastFlushTime.time_since_epoch()));
        return FlushTask::UP();
    }
    SerialNum realSerialNum = args.flush_serial_num;
    return makeLambdaFlushTask([this, myargs=std::move(args)]() mutable { doFlush(std::move(myargs)); }, realSerialNum);
}

FusionSpec
IndexMaintainer::getFusionSpec()
{
    // Only called by unit test
    LockGuard guard(_fusion_lock);
    return _fusion_spec;
}

string
IndexMaintainer::doFusion(SerialNum serialNum, std::shared_ptr<search::IFlushToken> flush_token)
{
    // Called by a flush engine worker thread

    // Make sure to update serial num in case it is something that does not receive any data.
    // XXX: Wrong, and will cause data loss.
    // XXX: Missing locking.
    // XXX: Claims to have flushed memory index when starting fusion.
    {
        LockGuard lock(_index_update_lock);
        set_current_serial_num(std::max(current_serial_num(), serialNum));
    }

    FusionSpec spec;
    {
        LockGuard guard(_fusion_lock);
        if (!canRunFusion(_fusion_spec))
            return "";
        spec = _fusion_spec;
        _fusion_spec.flush_ids.clear();
    }

    uint32_t new_fusion_id = runFusion(spec, flush_token);

    LockGuard lock(_fusion_lock);
    if (new_fusion_id == spec.last_fusion_id) {  // Error running fusion.
        string fail_dir = getFusionDir(spec.flush_ids.back());
        if (flush_token->stop_requested()) {
            LOG(info, "Fusion stopped for id %u, fusion dir \"%s\".", spec.flush_ids.back(), fail_dir.c_str());
        } else {
            LOG(warning, "Fusion failed for id %u, fusion dir \"%s\".", spec.flush_ids.back(), fail_dir.c_str());
        }
        // Restore fusion spec.
        copy(_fusion_spec.flush_ids.begin(), _fusion_spec.flush_ids.end(), back_inserter(spec.flush_ids));
        _fusion_spec.flush_ids.swap(spec.flush_ids);
    } else {
        _fusion_spec.last_fusion_id = new_fusion_id;
    }
    return getFusionDir(new_fusion_id);
}

namespace {

class RemoveFusionIndexGuard {
    DiskIndexes*       _disk_indexes;
    IndexDiskDir       _index_disk_dir;
public:
    RemoveFusionIndexGuard(DiskIndexes& disk_indexes, IndexDiskDir index_disk_dir)
        : _disk_indexes(&disk_indexes),
          _index_disk_dir(index_disk_dir)
    {
        _disk_indexes->add_not_active(index_disk_dir);
    }
    ~RemoveFusionIndexGuard() {
        if (_disk_indexes != nullptr) {
            (void) _disk_indexes->remove(_index_disk_dir);
        }
    }
    void reset() { _disk_indexes = nullptr; }
};

}

uint32_t
IndexMaintainer::runFusion(const FusionSpec &fusion_spec, std::shared_ptr<search::IFlushToken> flush_token)
{
    // Called by a flush engine worker thread
    FusionArgs args;
    TuneFileAttributes tuneFileAttributes(getAttrTune());
    {
        LockGuard slock(_state_lock);
        LockGuard ilock(_index_update_lock);
        _activeFusionSchema = std::make_shared<Schema>(_schema);
        _activeFusionPrunedSchema.reset();
        args._schema = _schema;
    }
    FastOS_StatInfo statInfo;
    string lastFlushDir(getFlushDir(fusion_spec.flush_ids.back()));
    string lastSerialFile = IndexDiskLayout::getSerialNumFileName(lastFlushDir);
    SerialNum serialNum = 0;
    if (FastOS_File::Stat(lastSerialFile.c_str(), &statInfo)) {
        serialNum = IndexReadUtilities::readSerialNum(lastFlushDir);
    }
    IndexDiskDir fusion_index_disk_dir(fusion_spec.flush_ids.back(), true);
    RemoveFusionIndexGuard remove_fusion_index_guard(*_disk_indexes, fusion_index_disk_dir);
    FusionRunner fusion_runner(_base_dir, args._schema, tuneFileAttributes, _ctx.getFileHeaderContext());
    uint32_t new_fusion_id = fusion_runner.fuse(fusion_spec, serialNum, _operations, flush_token);
    bool ok = (new_fusion_id != 0);
    if (ok) {
        ok = IndexWriteUtilities::copySerialNumFile(getFlushDir(fusion_spec.flush_ids.back()),
                                                    getFusionDir(new_fusion_id));
    }
    if (!ok) {
        string fail_dir = getFusionDir(fusion_spec.flush_ids.back());
        if (flush_token->stop_requested()) {
            LOG(info, "Fusion stopped, fusion dir \"%s\".", fail_dir.c_str());
        } else {
            LOG(error, "Fusion failed, fusion dir \"%s\".", fail_dir.c_str());
        }
        std::filesystem::remove_all(std::filesystem::path(fail_dir));
        {
            LockGuard slock(_state_lock);
            LockGuard ilock(_index_update_lock);
            _activeFusionSchema.reset();
            _activeFusionPrunedSchema.reset();
        }
        vespalib::File::sync(vespalib::dirname(fail_dir));
        return fusion_spec.last_fusion_id;
    }

    const string new_fusion_dir = getFusionDir(new_fusion_id);
    Schema::SP prunedSchema = getActiveFusionPrunedSchema();
    if (prunedSchema) {
        updateDiskIndexSchema(new_fusion_dir, *prunedSchema, noSerialNumHigh);
    }
    ChangeGens changeGens = getChangeGens();
    IDiskIndex::SP new_index(loadDiskIndex(new_fusion_dir));
    remove_fusion_index_guard.reset();

    // Post processing after fusion operation has completed and new disk
    // index has been opened.

    args._new_fusion_id = new_fusion_id;
    args._changeGens = changeGens;
    args._prunedSchema = prunedSchema;
    for (;;) {
        // Call reconfig closure for this change
        bool success = reconfigure(makeLambdaConfigure([this,argsP=&args,indexP=&new_index]() {
            return doneFusion(argsP, indexP);
        }));
        if (success) {
            break;
        }
        changeGens = getChangeGens();
        prunedSchema = getActiveFusionPrunedSchema();
        if (prunedSchema) {
            updateDiskIndexSchema(new_fusion_dir, *prunedSchema, noSerialNumHigh);
        }
        IDiskIndex::SP diskIndex2;
        diskIndex2 = reloadDiskIndex(*new_index);
        new_index = diskIndex2;
        args._changeGens = changeGens;
        args._prunedSchema = prunedSchema;
    }
    removeOldDiskIndexes();

    return new_fusion_id;
}

void
IndexMaintainer::removeOldDiskIndexes()
{
    LockGuard slock(_remove_lock);
    DiskIndexCleaner::removeOldIndexes(_base_dir, *_disk_indexes);
}

IndexMaintainer::FlushStats
IndexMaintainer::getFlushStats() const
{
    // Called by flush engine scheduler thread (from getFlushTargets())
    FlushStats stats;
    uint64_t source_selector_bytes;
    uint32_t source_selector_changes;
    uint32_t numFrozen = 0;
    {
        LockGuard lock(_index_update_lock);
        source_selector_bytes = _selector->getDocIdLimit() * sizeof(Source);
        stats.memory_before_bytes += _current_index->getMemoryUsage().allocatedBytes() + source_selector_bytes;
        stats.memory_after_bytes += _current_index->getStaticMemoryFootprint() + source_selector_bytes;
        numFrozen = _frozenMemoryIndexes.size();
        for (const FrozenMemoryIndexRef & frozen : _frozenMemoryIndexes) {
            stats.memory_before_bytes += frozen._index->getMemoryUsage().allocatedBytes() + source_selector_bytes;
        }
        source_selector_changes = _source_selector_changes;
    }

    if (!source_selector_changes && stats.memory_after_bytes >= stats.memory_before_bytes) {
        // Nothing is written if the index is empty.
        stats.disk_write_bytes = 0;
        stats.cpu_time_required = 0;
    } else {
        stats.disk_write_bytes = stats.memory_before_bytes  + source_selector_bytes - stats.memory_after_bytes;
        stats.cpu_time_required = source_selector_bytes * 3 * (1 + numFrozen) + stats.disk_write_bytes;
    }
    return stats;
}

IndexMaintainer::FusionStats
IndexMaintainer::getFusionStats() const
{
    // Called by flush engine scheduler thread (from getFlushTargets())
    FusionStats stats;
    IndexSearchable::SP source_list;

    {
        LockGuard lock(_new_search_lock);
        source_list = _source_list;
        stats.maxFlushed = _maxFlushed;
    }
    stats.diskUsage = source_list->getSearchableStats().sizeOnDisk();
    {
        LockGuard guard(_fusion_lock);
        stats.numUnfused = _fusion_spec.flush_ids.size() + ((_fusion_spec.last_fusion_id != 0) ? 1 : 0);
        stats._canRunFusion = canRunFusion(_fusion_spec);
    }
    LOG(debug, "Get fusion stats. Disk usage: %" PRIu64 ", maxflushed: %d", stats.diskUsage, stats.maxFlushed);
    return stats;
}

uint32_t
IndexMaintainer::getNumFrozenMemoryIndexes(void) const
{
    // Called by flush engine scheduler thread (from getFlushTargets())
    LockGuard state_lock(_index_update_lock);
    return _frozenMemoryIndexes.size();
}

void
IndexMaintainer::putDocument(uint32_t lid, const Document &doc, SerialNum serialNum, OnWriteDoneType on_write_done)
{
    assert(_ctx.getThreadingService().index().isCurrentThread());
    LockGuard lock(_index_update_lock);
    try {
        _current_index->insertDocument(lid, doc, on_write_done);
    } catch (const vespalib::IllegalStateException & e) {
        vespalib::string s = "Failed inserting document :\n"  + doc.toXml("  ") + "\n";
        LOG(error, "%s", s.c_str());
        throw vespalib::IllegalStateException(s, e, VESPA_STRLOC);
    }
    _selector->setSource(lid, _current_index_id);
    _source_list->setSource(lid);
    ++_source_selector_changes;
    set_current_serial_num(serialNum);
}

void
IndexMaintainer::removeDocuments(LidVector lids, SerialNum serialNum)
{
    assert(_ctx.getThreadingService().index().isCurrentThread());
    LockGuard lock(_index_update_lock);
    for (uint32_t lid : lids) {
        _selector->setSource(lid, _current_index_id);
        _source_list->setSource(lid);
    }
    _source_selector_changes += lids.size();
    set_current_serial_num(serialNum);
    _current_index->removeDocuments(std::move(lids));
}

void
IndexMaintainer::commit_and_wait()
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    vespalib::Gate gate;
    _ctx.getThreadingService().index().execute(makeLambdaTask([this, &gate]() { commit(gate); }));
    // Ensure that all index thread tasks accessing memory index have completed.
    gate.await();
}

void
IndexMaintainer::commit(vespalib::Gate& gate)
{
    // only triggered via commit_and_wait()
    assert(_ctx.getThreadingService().index().isCurrentThread());
    LockGuard lock(_index_update_lock);
    _current_index->commit(std::make_shared<vespalib::GateCallback>(gate), current_serial_num());
}

void
IndexMaintainer::commit(SerialNum serialNum, OnWriteDoneType onWriteDone)
{
    assert(_ctx.getThreadingService().index().isCurrentThread());
    LockGuard lock(_index_update_lock);
    set_current_serial_num(serialNum);
    _current_index->commit(onWriteDone, serialNum);
}

void
IndexMaintainer::heartBeat(SerialNum serialNum)
{
    assert(_ctx.getThreadingService().index().isCurrentThread());
    LockGuard lock(_index_update_lock);
    set_current_serial_num(serialNum);
}

void
IndexMaintainer::compactLidSpace(uint32_t lidLimit, SerialNum serialNum)
{
    assert(_ctx.getThreadingService().index().isCurrentThread());
    LOG(info, "compactLidSpace(%u, %" PRIu64 ")", lidLimit, serialNum);
    LockGuard lock(_index_update_lock);
    set_current_serial_num(serialNum);
    _selector->compactLidSpace(lidLimit);
}

IFlushTarget::List
IndexMaintainer::getFlushTargets()
{
    // Called by flush engine scheduler thread
    IFlushTarget::List ret;
    ret.reserve(2);
    ret.push_back(std::make_shared<IndexFlushTarget>(*this));
    ret.push_back(std::make_shared<IndexFusionTarget>(*this));
    return ret;
}

void
IndexMaintainer::setSchema(const Schema & schema, SerialNum serialNum)
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    pruneRemovedFields(schema, serialNum);
    IMemoryIndex::SP new_index(_operations.createMemoryIndex(schema, *_current_index, current_serial_num()));
    SetSchemaArgs args;

    args._newSchema = schema;
    // Ensure that all index thread tasks accessing memory index have completed.
    commit_and_wait();
    // Everything should be quiet now.
    doneSetSchema(args, new_index);
    // Source collection has now changed, caller must reconfigure further
    // as appropriate.
}

void
IndexMaintainer::pruneRemovedFields(const Schema &schema, SerialNum serialNum)
{
    assert(_ctx.getThreadingService().master().isCurrentThread());
    ISearchableIndexCollection::SP new_source_list;
    IIndexCollection::SP coll = getSourceCollection();
    updateIndexSchemas(*coll, schema, serialNum);
    updateActiveFusionPrunedSchema(schema);
    {
        LockGuard state_lock(_state_lock);
        LockGuard lock(_index_update_lock);
        _changeGens.bumpPruneGen();
    }
    {
        LockGuard state_lock(_state_lock);
        new_source_list = std::make_shared<IndexCollection>(_selector, *_source_list);
    }
    if (reopenDiskIndexes(*new_source_list)) {
        commit_and_wait();
        // Everything should be quiet now.
        LockGuard state_lock(_state_lock);
        LockGuard lock(_new_search_lock);
        _source_list = new_source_list;
    }
}

void
IndexMaintainer::setMaxFlushed(uint32_t maxFlushed)
{
    LockGuard lock(_new_search_lock);
    _maxFlushed = maxFlushed;
}

}
