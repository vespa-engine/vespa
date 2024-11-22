// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iindexmanager.h"
#include "fusionspec.h"
#include "iindexmaintaineroperations.h"
#include "indexdisklayout.h"
#include "indexmaintainercontext.h"
#include "warmupindexcollection.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchcorespi/flush/flushstats.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/common/serialnum.h>
#include <atomic>

namespace document { class Document; }

namespace search::common { class FileHeaderContext; }
namespace vespalib { class Gate; }

namespace searchcorespi::index {

class DiskIndexes;
class IndexMaintainerConfig;

/**
 * The IndexMaintainer provides a holistic view of a set of disk and
 * memory indexes. It allows updating the active memory index, enables search
 * across all indexes, and manages the set of indexes through flushing
 * of memory indexes and fusion of disk indexes.
 */
class IndexMaintainer : public IIndexManager,
                        public IWarmupDone {
    /**
     * Extra memory that is frozen but not yet flushed.
     */
    class FrozenMemoryIndexRef {
    public:
        using SaveInfo = search::FixedSourceSelector::SaveInfo;
        using SerialNum = search::SerialNum;

        std::shared_ptr<IMemoryIndex> _index;
        SerialNum                     _serialNum;
        std::shared_ptr<SaveInfo>     _saveInfo;
        uint32_t                      _absoluteId;

        FrozenMemoryIndexRef(const std::shared_ptr<IMemoryIndex> &index,
                             SerialNum serialNum,
                             std::unique_ptr<SaveInfo> saveInfo,
                             uint32_t absoluteId)
            : _index(index),
              _serialNum(serialNum),
              _saveInfo(std::move(saveInfo)),
              _absoluteId(absoluteId)
        { }
        ~FrozenMemoryIndexRef();
    };

    class ChangeGens {
    public:
        uint32_t _pruneGen;

        ChangeGens() : _pruneGen(0) { }
        void bumpPruneGen(void) { ++_pruneGen; }
        bool operator==(const ChangeGens &rhs) const { return _pruneGen == rhs._pruneGen; }
        bool operator!=(const ChangeGens &rhs) const { return _pruneGen != rhs._pruneGen; }
    };

    using FlushIds = std::vector<uint32_t>;
    using FrozenMemoryIndexRefs = std::vector<FrozenMemoryIndexRef>;
    using ISourceSelector = search::queryeval::ISourceSelector;
    using LockGuard = std::lock_guard<std::mutex>;

    const std::string _base_dir;
    const WarmupConfig     _warmupConfig;
    std::shared_ptr<DiskIndexes>  _disk_indexes;
    IndexDiskLayout        _layout;
    Schema                  _schema;             // Protected by SL + IUL
    std::shared_ptr<const Schema> _activeFusionSchema; // Protected by SL + IUL
    // Protected by SL + IUL
    std::shared_ptr<const Schema> _activeFusionPrunedSchema;
    uint32_t               _source_selector_changes; // Protected by IUL
    // _selector is protected by SL + IUL
    std::shared_ptr<ISourceSelector> _selector;
    std::shared_ptr<ISearchableIndexCollection> _source_list; // Protected by SL + NSL, only set by master thread
    uint32_t               _last_fusion_id;   // Protected by SL + IUL
    uint32_t               _next_id;          // Protected by SL + IUL
    uint32_t               _current_index_id; // Protected by SL + IUL
    uint32_t               _urgent_flush_id;  // Protected by SL + IUL
    std::shared_ptr<IMemoryIndex> _current_index;    // Protected by SL + IUL
    bool                   _flush_empty_current_index;
    std::atomic<SerialNum> _current_serial_num;// Writes protected by IUL
    std::atomic<SerialNum> _flush_serial_num;  // Writes protected by SL
    vespalib::system_time  _lastFlushTime; // Protected by SL
    // Extra frozen memory indexes.  This list is empty unless new
    // memory index has been added by force (due to config change or
    // data structure limitations).
    // Protected by SL + IUL
    FrozenMemoryIndexRefs _frozenMemoryIndexes;
    /*
     * Locks protecting state.
     *
     * Note about locking:
     *
     * A variable can be protected by multiple locks, e.g. SL + NSL.
     * To change the variable, all of these locks must be held.
     * To read the variable, holding any of these locks is sufficient.
     *
     * In the example above, getting NSL typically has lower latency, but
     * fewer variables can be retrieved.  Getting SL has a higher latency
     * and allows a snapshot of multiple variables depending on each other
     * to be retrieved.
     *
     * Flush threads typically performs some setup (take SL, copy a
     * relevant portion of variables to an args class (FlushArgs,
     * FusionArgs, SetSchemaArgs) and perform some disk io) that takes
     * time before scheduling a state change task performed by the
     * document db master thread.
     *
     * The scheduled state change task will fail if state changed too much
     * after setup by the flush thread.  The flush thread must then retry
     * with an updated setup.
     *
     * Things get more complicated when handling multiple kinds of overlapping
     * flush operations, e.g. dump from memory to disk, fusion, schema changes
     * and pruning of removed fields, since this will trigger more retries for
     * some of the operations.
     */
    std::mutex                       _state_lock;        // Outer lock (SL)
    mutable std::mutex               _index_update_lock; // Inner lock (IUL)
    mutable std::mutex               _new_search_lock;   // Inner lock (NSL)
    std::mutex                       _remove_lock;       // Lock for removing indexes.
    FusionSpec                       _fusion_spec;       // Protected by FL
    mutable std::mutex               _fusion_lock;       // Fusion spec lock (FL)
    uint32_t                         _maxFlushed;        // Protected by NSL
    const uint32_t                   _maxFrozen;
    ChangeGens                       _changeGens;        // Protected by SL + IUL
    std::mutex                       _schemaUpdateLock;  // Serialize rewrite of schema
    const search::TuneFileAttributes _tuneFileAttributes;
    const IndexMaintainerContext     _ctx;
    IIndexMaintainerOperations&      _operations;

    search::FixedSourceSelector & getSourceSelector() { return static_cast<search::FixedSourceSelector &>(*_selector); }
    const search::FixedSourceSelector & getSourceSelector() const { return static_cast<const search::FixedSourceSelector &>(*_selector); }
    // get absolute id of current memory index, caller holds SL or IUL
    uint32_t get_absolute_id() const noexcept { return _last_fusion_id + _current_index_id; }
    // set id for new memory index, other callers than constructor holds SL and IUL
    void set_id_for_new_memory_index();
    std::string getFlushDir(uint32_t sourceId) const;
    std::string getFusionDir(uint32_t sourceId) const;

    /**
     * Will reopen diskindexes if necessary due to schema changes.
     * @param coll Indexcollection that will be updated with reloaded index.
     * @return true if any reload has been performed
     */
    bool reopenDiskIndexes(ISearchableIndexCollection &coll);

    void updateDiskIndexSchema(const std::string &indexDir,
                               const Schema &schema,
                               SerialNum serialNum);

    void updateIndexSchemas(IIndexCollection &coll,
                            const Schema &schema,
                            SerialNum serialNum);

    void updateActiveFusionPrunedSchema(const Schema &schema);
    void deactivateDiskIndexes(std::string indexDir);
    std::shared_ptr<IDiskIndex> loadDiskIndex(const std::string &indexDir);
    std::shared_ptr<IDiskIndex> reloadDiskIndex(const IDiskIndex &oldIndex);

    std::shared_ptr<IDiskIndex> flushMemoryIndex(IMemoryIndex &memoryIndex,
                                                 uint32_t indexId,
                                                 uint32_t docIdLimit,
                                                 SerialNum serialNum,
                                                 search::FixedSourceSelector::SaveInfo &saveInfo);

    std::unique_ptr<ISearchableIndexCollection> loadDiskIndexes(const FusionSpec &spec, std::unique_ptr<ISearchableIndexCollection> sourceList);
    void replaceSource(uint32_t sourceId, const std::shared_ptr<IndexSearchable>& source);
    void appendSource(uint32_t sourceId, const std::shared_ptr<IndexSearchable>& source);
    void swapInNewIndex(LockGuard & guard, std::shared_ptr<ISearchableIndexCollection> indexes, IndexSearchable & source);
    std::unique_ptr<ISearchableIndexCollection> createNewSourceCollection(const LockGuard &newSearchLock);

    struct FlushArgs {
        std::shared_ptr<IMemoryIndex> old_index;	// Last memory index
        uint32_t         old_absolute_id;
        std::shared_ptr<ISearchableIndexCollection> old_source_list; // Delays destruction
        std::shared_ptr<search::FixedSourceSelector::SaveInfo> save_info;
        SerialNum        flush_serial_num;
        searchcorespi::FlushStats  * stats;
        bool             _skippedEmptyLast; // Don't flush empty memory index

        // Extra indexes to flush before flushing last frozen memory index
        // They are flushed before old_index.  This list is empty unless
        // new memory index has been added by force (due to config change
        // or data structure limitations).
        FrozenMemoryIndexRefs _extraIndexes;
        ChangeGens _changeGens;
        std::shared_ptr<const Schema> _prunedSchema;

        FlushArgs();
        FlushArgs(const FlushArgs &) = delete;
        FlushArgs & operator=(const FlushArgs &) = delete;
        FlushArgs(FlushArgs &&);
        FlushArgs & operator=(FlushArgs &&);
        ~FlushArgs();
    };

    bool doneInitFlush(FlushArgs *args, std::shared_ptr<IMemoryIndex> *new_index);
    void doFlush(FlushArgs args);
    void flushFrozenMemoryIndexes(FlushArgs &args, FlushIds &flushIds);
    void flushLastMemoryIndex(FlushArgs &args, FlushIds &flushIds);
    void updateFlushStats(const FlushArgs &args);
    void flushMemoryIndex(FlushArgs &args, uint32_t docIdLimit,
                          search::FixedSourceSelector::SaveInfo &saveInfo, FlushIds &flushIds);
    void reconfigureAfterFlush(FlushArgs &args, std::shared_ptr<IDiskIndex>& diskIndex);
    bool doneFlush(FlushArgs *args, std::shared_ptr<IDiskIndex> *disk_index);


    class FusionArgs {
    public:
        uint32_t   _new_fusion_id;
        ChangeGens _changeGens;
        Schema     _schema;
        std::shared_ptr<const Schema> _prunedSchema;
        std::shared_ptr<ISearchableIndexCollection> _old_source_list; // Delays destruction

        FusionArgs();
        ~FusionArgs();
    };

    void scheduleFusion(const FlushIds &flushIds);
    bool canRunFusion(const FusionSpec &spec) const;
    bool doneFusion(FusionArgs *args, std::shared_ptr<IDiskIndex> *new_index);

    class SetSchemaArgs {
    public:
        Schema           _newSchema;
        Schema           _oldSchema;
        std::shared_ptr<IMemoryIndex> _oldIndex;
        std::shared_ptr<ISearchableIndexCollection> _oldSourceList; // Delays destruction

        SetSchemaArgs();
        ~SetSchemaArgs();
    };

    void doneSetSchema(SetSchemaArgs &args, std::shared_ptr<IMemoryIndex>& newIndex, SerialNum serial_num);

    Schema getSchema(void) const;
    std::shared_ptr<const Schema> getActiveFusionPrunedSchema() const;
    search::TuneFileAttributes getAttrTune();
    ChangeGens getChangeGens();

    /*
     * Schedule document db executor task to use reconfigurer to
     * reconfigure index manager with closure as argument.  Wait for
     * result.
     */
    bool reconfigure(std::unique_ptr<Configure> configure);
    void warmupDone(std::shared_ptr<WarmupIndexCollection> current) override;
    bool makeSureAllRemainingWarmupIsDone(std::shared_ptr<WarmupIndexCollection> keepAlive);
    void commit_and_wait();
    void commit(vespalib::Gate& gate);
    void pruneRemovedFields(const Schema &schema, SerialNum serialNum);
    [[nodiscard]] SerialNum current_serial_num() const noexcept {
        return _current_serial_num.load(std::memory_order_relaxed);
    }
    void set_current_serial_num(SerialNum new_serial_num) noexcept {
        _current_serial_num.store(new_serial_num, std::memory_order_relaxed);
    }
    [[nodiscard]] SerialNum flush_serial_num() const noexcept {
        return _flush_serial_num.load(std::memory_order_relaxed);
    }
    void set_flush_serial_num(SerialNum new_serial_num) noexcept {
        _flush_serial_num.store(new_serial_num, std::memory_order_relaxed);
    }

public:
    IndexMaintainer(const IndexMaintainer &) = delete;
    IndexMaintainer & operator = (const IndexMaintainer &) = delete;
    IndexMaintainer(const IndexMaintainerConfig &config,
                    const IndexMaintainerContext &context,
                    IIndexMaintainerOperations &operations);
    ~IndexMaintainer() override;

    /**
     * Starts a new MemoryIndex, and dumps the previous one to disk.
     * Updates flush stats when finished if specified.
     **/
    std::unique_ptr<FlushTask> initFlush(SerialNum serialNum, FlushStats * stats);
    FusionSpec getFusionSpec();

    /**
     * Runs fusion for any available specs and return the output fusion directory.
     */
    std::string doFusion(SerialNum serialNum, std::shared_ptr<search::IFlushToken> flush_token);
    uint32_t runFusion(const FusionSpec &fusion_spec, std::shared_ptr<search::IFlushToken> flush_token);
    void removeOldDiskIndexes();

    struct FlushStats {
        explicit FlushStats(uint64_t memory_before=0) :
            memory_before_bytes(memory_before),
            memory_after_bytes(0),
            disk_write_bytes(0),
            cpu_time_required(0)
        { }

        uint64_t memory_before_bytes;
        uint64_t memory_after_bytes;
        uint64_t disk_write_bytes;
        uint64_t cpu_time_required;
    };

    struct FusionStats {
        FusionStats()
            : diskUsage(0),
              maxFlushed(0),
              numUnfused(0),
              _canRunFusion(false)
        { }

        uint64_t diskUsage;
        uint32_t maxFlushed;
        uint32_t numUnfused;
        bool _canRunFusion;
    };

    /**
     * Calculates an approximation of the cost of performing a flush.
     **/
    FlushStats getFlushStats() const;
    FusionStats getFusionStats() const;
    const std::string & getBaseDir() const { return _base_dir; }
    uint32_t getNumFrozenMemoryIndexes() const;
    uint32_t getMaxFrozenMemoryIndexes() const { return _maxFrozen; }

    vespalib::system_time getLastFlushTime() const { return _lastFlushTime; }

    // Implements IIndexManager
    void putDocument(uint32_t lid, const Document &doc, SerialNum serialNum, const OnWriteDoneType& on_write_done) override;
    void removeDocuments(LidVector lids, SerialNum serialNum) override;
    void commit(SerialNum serialNum, const OnWriteDoneType& onWriteDone) override;
    void heartBeat(search::SerialNum serialNum) override;
    void compactLidSpace(uint32_t lidLimit, SerialNum serialNum) override;

    SerialNum getCurrentSerialNum() const override {
        return current_serial_num();
    }

    SerialNum getFlushedSerialNum() const override {
        return flush_serial_num();
    }

    std::shared_ptr<IIndexCollection> getSourceCollection() const {
        LockGuard lock(_new_search_lock);
        return _source_list;
    }

    std::shared_ptr<searchcorespi::IndexSearchable> getSearchable() const override {
        LockGuard lock(_new_search_lock);
        return _source_list;
    }

    search::SearchableStats getSearchableStats(bool clear_disk_io_stats) const override {
        LockGuard lock(_new_search_lock);
        return _source_list->getSearchableStats(clear_disk_io_stats);
    }

    IFlushTarget::List getFlushTargets() override;
    void setSchema(const Schema & schema, SerialNum serialNum) override ;
    void setMaxFlushed(uint32_t maxFlushed) override;
    void consider_urgent_flush(const Schema& old_schema, const Schema& new_schema, uint32_t flush_id);
    void consider_initial_urgent_flush();
    uint32_t get_urgent_flush_id() const;
    bool urgent_memory_index_flush() const;
    bool urgent_disk_index_fusion() const;
    bool has_pending_urgent_flush() const override;
};

}
