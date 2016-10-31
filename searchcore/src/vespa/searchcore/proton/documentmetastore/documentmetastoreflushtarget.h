// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include "documentmetastore.h"

namespace search
{

namespace common
{

class FileHeaderContext;

}

}

namespace proton
{

class ITlsSyncer;

using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;

/**
 * Implementation of IFlushTarget interface for document meta store.
 **/
class DocumentMetaStoreFlushTarget : public IFlushTarget
{
private:
    /**
     * Task performing the actual flushing to disk.
     **/
    class Flusher : public Task {
    private:
        DocumentMetaStoreFlushTarget     &_dmsft;
        std::unique_ptr<search::AttributeSaver> _saver;
        uint64_t                          _syncToken;
        vespalib::string                  _flushDir;

        bool saveDocumentMetaStore(); // not updating snap info.
    public:
        Flusher(DocumentMetaStoreFlushTarget &dmsft, uint64_t syncToken);
        ~Flusher();
        uint64_t getSyncToken() const { return _syncToken; }
        bool saveSnapInfo();
        bool flush();
        void updateStats();
        bool cleanUp();
        // Implements vespalib::Executor::Task
        virtual void run();

        virtual SerialNum
        getFlushSerial(void) const
        {
            return _syncToken;
        }
    };

    DocumentMetaStore::SP       _dms;
    ITlsSyncer                 &_tlsSyncer;
    vespalib::string            _baseDir;
    search::IndexMetaInfo       _snapInfo;
    vespalib::Lock		_snapInfoLock;
    vespalib::Lock              _flusherLock;
    bool                        _cleanUpAfterFlush;
    FlushStats                  _lastStats;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    fastos::TimeStamp           _lastFlushTime;
    
    static vespalib::string
    getSnapshotName(uint64_t syncToken);

    vespalib::string
    getSnapshotDir(uint64_t syncToken);

public:
    typedef std::shared_ptr<DocumentMetaStoreFlushTarget> SP;

    /**
     * Creates a new instance using the given attribute vector and the
     * given base dir where all attribute vectors are located.
     **/
    DocumentMetaStoreFlushTarget(const DocumentMetaStore::SP dms,
                                 ITlsSyncer &tlsSyncer,
                                 const vespalib::string &baseDir,
                                 const search::TuneFileAttributes &
                                 tuneFileAttributes,
                                 const search::common::FileHeaderContext &
                                 fileHeaderContext);

    virtual
    ~DocumentMetaStoreFlushTarget();

    void setCleanUpAfterFlush(bool cleanUp) { _cleanUpAfterFlush = cleanUp; }

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const;
    virtual DiskGain getApproxDiskGain() const;
    virtual Time getLastFlushTime() const;
    virtual SerialNum getFlushedSerialNum() const;
    virtual Task::UP initFlush(SerialNum currentSerial);
    virtual FlushStats getLastFlushStats() const { return _lastStats; }

    static void initCleanup(const vespalib::string &baseDir);
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

