// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchcore/proton/common/hw_info.h>


namespace search {

class ISequencedTaskExecutor;
class AttributeVector;

namespace common { class FileHeaderContext; }

}

namespace proton {


class AttributeDirectory;

/**
 * Implementation of IFlushTarget interface for attribute vectors.
 */
class FlushableAttribute : public searchcorespi::IFlushTarget
{
private:
    /**
     * Task performing the actual flushing to disk.
     **/
    class Flusher;
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    using FlushStats = searchcorespi::FlushStats;

    AttributeVectorSP           _attr;
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
    FlushableAttribute(const AttributeVectorSP attr,
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
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual DiskGain getApproxDiskGain() const override;
    virtual Time getLastFlushTime() const override;
    virtual SerialNum getFlushedSerialNum() const override;
    virtual Task::UP initFlush(SerialNum currentSerial) override;
    virtual FlushStats getLastFlushStats() const override { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
};

} // namespace proton

