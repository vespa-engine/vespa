// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchcore/proton/common/hw_info.h>

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
class AttributeDiskLayout;
class AttributeDirectory;
class DocumentMetaStore;

/**
 * Implementation of IFlushTarget interface for document meta store.
 **/
class DocumentMetaStoreFlushTarget : public searchcorespi::IFlushTarget
{
private:
    /**
     * Task performing the actual flushing to disk.
     **/
    class Flusher;
    using DocumentMetaStoreSP = std::shared_ptr<DocumentMetaStore>;
    using FlushStats = searchcorespi::FlushStats;

    DocumentMetaStoreSP         _dms;
    ITlsSyncer                 &_tlsSyncer;
    vespalib::string            _baseDir;
    bool                        _cleanUpAfterFlush;
    FlushStats                  _lastStats;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    HwInfo                      _hwInfo;
    std::shared_ptr<AttributeDiskLayout> _diskLayout;
    std::shared_ptr<AttributeDirectory> _dmsDir;

public:
    typedef std::shared_ptr<DocumentMetaStoreFlushTarget> SP;

    /**
     * Creates a new instance using the given attribute vector and the
     * given base dir where all attribute vectors are located.
     **/
    DocumentMetaStoreFlushTarget(const DocumentMetaStoreSP dms,
                                 ITlsSyncer &tlsSyncer,
                                 const vespalib::string &baseDir,
                                 const search::TuneFileAttributes &
                                 tuneFileAttributes,
                                 const search::common::FileHeaderContext &
                                 fileHeaderContext,
                                 const HwInfo &hwInfo);

    virtual
    ~DocumentMetaStoreFlushTarget();

    void setCleanUpAfterFlush(bool cleanUp) { _cleanUpAfterFlush = cleanUp; }

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual DiskGain getApproxDiskGain() const override;
    virtual Time getLastFlushTime() const override;
    virtual SerialNum getFlushedSerialNum() const override;
    virtual Task::UP initFlush(SerialNum currentSerial) override;
    virtual FlushStats getLastFlushStats() const override { return _lastStats; }

    static void initCleanup(const vespalib::string &baseDir);
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

