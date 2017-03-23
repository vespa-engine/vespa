// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/attribute/attributememorysavetarget.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchcore/proton/common/hw_info.h>


namespace search {

class ISequencedTaskExecutor;

namespace common { class FileHeaderContext; }

}

namespace proton {

using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;

class AttributeDirectory;

/**
 * Implementation of IFlushTarget interface for attribute vectors.
 */
class FlushableAttribute : public IFlushTarget
{
private:
    /**
     * Task performing the actual flushing to disk.
     **/
    class Flusher;

    search::AttributeVector::SP _attr;
    bool                        _cleanUpAfterFlush;
    FlushStats                  _lastStats;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    search::ISequencedTaskExecutor &_attributeFieldWriter;
    HwInfo                       _hwInfo;
    std::shared_ptr<AttributeDirectory> _attrDir;

    Task::UP internalInitFlush(SerialNum currentSerial);

public:
    typedef std::shared_ptr<FlushableAttribute> SP;

    /**
     * Creates a new instance using the given attribute vector and the
     * given base dir where all attribute vectors are located.
     *
     * fileHeaderContext must be kept alive by caller.
     **/
    FlushableAttribute(const search::AttributeVector::SP attr,
                       const std::shared_ptr<AttributeDirectory> &attrDir,
                       const search::TuneFileAttributes &tuneFileAttributes,
                       const search::common::FileHeaderContext &
                       fileHeaderContext,
                       search::ISequencedTaskExecutor &attributeFieldWriter,
                       const HwInfo &hwInfo);

    virtual
    ~FlushableAttribute();

    void setCleanUpAfterFlush(bool cleanUp) { _cleanUpAfterFlush = cleanUp; }

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const;
    virtual DiskGain getApproxDiskGain() const;
    virtual Time getLastFlushTime() const;
    virtual SerialNum getFlushedSerialNum() const;
    virtual Task::UP initFlush(SerialNum currentSerial);
    virtual FlushStats getLastFlushStats() const { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

