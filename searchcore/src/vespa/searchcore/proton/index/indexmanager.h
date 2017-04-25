// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/iindexmaintaineroperations.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcorespi/index/indexmaintainer.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>

namespace proton {

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
        const size_t _cacheSize;
        const search::common::FileHeaderContext &_fileHeaderContext;
        const search::TuneFileIndexing _tuneFileIndexing;
        const search::TuneFileSearch _tuneFileSearch;
        searchcorespi::index::IThreadingService &_threadingService;

    public:
        MaintainerOperations(const search::common::FileHeaderContext &fileHeaderContext,
                             const search::TuneFileIndexManager &tuneFileIndexManager,
                             size_t cacheSize,
                             searchcorespi::index::IThreadingService &
                             threadingService);

        virtual searchcorespi::index::IMemoryIndex::SP
        createMemoryIndex(const search::index::Schema &schema,
                          SerialNum serialNum) override;
        virtual searchcorespi::index::IDiskIndex::SP
            loadDiskIndex(const vespalib::string &indexDir) override;
        virtual searchcorespi::index::IDiskIndex::SP
            reloadDiskIndex(const searchcorespi::index::IDiskIndex &oldIndex) override;
        virtual bool runFusion(const search::index::Schema &schema,
                               const vespalib::string &outputDir,
                               const std::vector<vespalib::string> &sources,
                               const search::diskindex::SelectorArray &docIdSelector,
                               search::SerialNum lastSerialNum) override;
    };

private:
    MaintainerOperations     _operations;
    searchcorespi::index::IndexMaintainer _maintainer;

public:
    IndexManager(const IndexManager &) = delete;
    IndexManager & operator = (const IndexManager &) = delete;
    IndexManager(const vespalib::string &baseDir,
                 const searchcorespi::index::WarmupConfig & warmup,
                 size_t maxFlushed,
                 size_t cacheSize,
                 const Schema &schema,
                 Reconfigurer &reconfigurer,
                 searchcorespi::index::IThreadingService &threadingService,
                 vespalib::ThreadExecutor & warmupExecutor,
                 const search::TuneFileIndexManager &tuneFileIndexManager,
                 const search::TuneFileAttributes &tuneFileAttributes,
                 const search::common::FileHeaderContext &fileHeaderContext);
    ~IndexManager();

    searchcorespi::index::IndexMaintainer &getMaintainer() {
        return _maintainer;
    }

    /**
     * Implements searchcorespi::IIndexManager
     **/
    virtual void putDocument(uint32_t lid, const Document &doc,
                             SerialNum serialNum) override {
        _maintainer.putDocument(lid, doc, serialNum);
    }

    virtual void removeDocument(uint32_t lid, SerialNum serialNum) override {
        _maintainer.removeDocument(lid, serialNum);
    }

    virtual void commit(SerialNum serialNum,
                        OnWriteDoneType onWriteDone) override {
        _maintainer.commit(serialNum, onWriteDone);
    }

    virtual void heartBeat(SerialNum serialNum) override {
        _maintainer.heartBeat(serialNum);
    }

    virtual SerialNum getCurrentSerialNum() const override {
        return _maintainer.getCurrentSerialNum();
    }

    virtual SerialNum getFlushedSerialNum() const override {
        return _maintainer.getFlushedSerialNum();
    }

    virtual searchcorespi::IndexSearchable::SP getSearchable() const override {
        return _maintainer.getSearchable();
    }

    virtual search::SearchableStats getSearchableStats() const override {
        return _maintainer.getSearchableStats();
    }

    virtual searchcorespi::IFlushTarget::List getFlushTargets() override {
        return _maintainer.getFlushTargets();
    }

    virtual void setSchema(const Schema &schema, SerialNum serialNum) override {
        _maintainer.setSchema(schema, serialNum);
    }
};

} // namespace proton

