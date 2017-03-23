// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include "documentmetastore.h"
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
    class Flusher;

    DocumentMetaStore::SP       _dms;
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
    DocumentMetaStoreFlushTarget(const DocumentMetaStore::SP dms,
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

