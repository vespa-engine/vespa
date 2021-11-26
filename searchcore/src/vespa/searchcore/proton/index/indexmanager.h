// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/iindexmaintaineroperations.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcorespi/index/indexmaintainer.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchcorespi/index/warmupconfig.h>

namespace proton::index {

struct IndexConfig {
    using WarmupConfig = searchcorespi::index::WarmupConfig;
    IndexConfig() : IndexConfig(WarmupConfig(), 2, 0) { }
    IndexConfig(WarmupConfig warmup_, size_t maxFlushed_, size_t cacheSize_)
        : warmup(warmup_),
          maxFlushed(maxFlushed_),
          cacheSize(cacheSize_)
    { }

    const WarmupConfig warmup;
    const size_t       maxFlushed;
    const size_t       cacheSize;
};

/**
 * The IndexManager provides a holistic view of a set of disk and
 * memory indexes. It allows updating the active index, enables search
 * across all indexes, and manages the set of indexes through flushing
 * of memory indexes and fusion of disk indexes.
 */
class IndexManager : public searchcorespi::IIndexManager
{
public:
    class MaintainerOperations : public searchcorespi::index::IIndexMaintainerOperations {
    private:
        using IDiskIndex = searchcorespi::index::IDiskIndex;
        using IMemoryIndex = searchcorespi::index::IMemoryIndex;
        const size_t _cacheSize;
        const search::common::FileHeaderContext &_fileHeaderContext;
        const search::TuneFileIndexing _tuneFileIndexing;
        const search::TuneFileSearch _tuneFileSearch;
        searchcorespi::index::IThreadingService &_threadingService;

    public:
        MaintainerOperations(const search::common::FileHeaderContext &fileHeaderContext,
                             const search::TuneFileIndexManager &tuneFileIndexManager,
                             size_t cacheSize,
                             searchcorespi::index::IThreadingService &threadingService);

        IMemoryIndex::SP createMemoryIndex(const Schema& schema,
                                           const IFieldLengthInspector& inspector,
                                           SerialNum serialNum) override;
        IDiskIndex::SP loadDiskIndex(const vespalib::string &indexDir) override;
        IDiskIndex::SP reloadDiskIndex(const IDiskIndex &oldIndex) override;
        bool runFusion(const Schema &schema, const vespalib::string &outputDir,
                       const std::vector<vespalib::string> &sources,
                       const SelectorArray &docIdSelector,
                       search::SerialNum lastSerialNum,
                       std::shared_ptr<search::IFlushToken> flush_token) override;
    };

private:
    MaintainerOperations     _operations;
    searchcorespi::index::IndexMaintainer _maintainer;

public:
    IndexManager(const IndexManager &) = delete;
    IndexManager & operator = (const IndexManager &) = delete;
    IndexManager(const vespalib::string &baseDir,
                 const IndexConfig & indexConfig,
                 const Schema &schema,
                 SerialNum serialNum,
                 Reconfigurer &reconfigurer,
                 searchcorespi::index::IThreadingService &threadingService,
                 vespalib::Executor & warmupExecutor,
                 const search::TuneFileIndexManager &tuneFileIndexManager,
                 const search::TuneFileAttributes &tuneFileAttributes,
                 const search::common::FileHeaderContext &fileHeaderContext);
    ~IndexManager() override;

    searchcorespi::index::IndexMaintainer &getMaintainer() {
        return _maintainer;
    }

    /**
     * Implements searchcorespi::IIndexManager
     **/
    void putDocument(uint32_t lid, const Document &doc, SerialNum serialNum, OnWriteDoneType on_write_done) override {
        _maintainer.putDocument(lid, doc, serialNum, on_write_done);
    }

    void removeDocuments(LidVector lids, SerialNum serialNum) override {
        _maintainer.removeDocuments(std::move(lids), serialNum);
    }

    void commit(SerialNum serialNum, OnWriteDoneType onWriteDone) override {
        _maintainer.commit(serialNum, onWriteDone);
    }

    void heartBeat(SerialNum serialNum) override {
        _maintainer.heartBeat(serialNum);
    }
    void compactLidSpace(uint32_t lidLimit, SerialNum serialNum) override;

    SerialNum getCurrentSerialNum() const override {
        return _maintainer.getCurrentSerialNum();
    }

    SerialNum getFlushedSerialNum() const override {
        return _maintainer.getFlushedSerialNum();
    }

    searchcorespi::IndexSearchable::SP getSearchable() const override {
        return _maintainer.getSearchable();
    }

    search::SearchableStats getSearchableStats() const override {
        return _maintainer.getSearchableStats();
    }

    searchcorespi::IFlushTarget::List getFlushTargets() override {
        return _maintainer.getFlushTargets();
    }

    void setSchema(const Schema &schema, SerialNum serialNum) override {
        _maintainer.setSchema(schema, serialNum);
    }
    void setMaxFlushed(uint32_t maxFlushed) override {
        _maintainer.setMaxFlushed(maxFlushed);
    }
};

} // namespace proton

