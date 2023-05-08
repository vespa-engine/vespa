// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search { class AttributeVector; }

namespace search::common { class FileHeaderContext; }
namespace vespalib { class ISequencedTaskExecutor; }

namespace proton {

class AttributeDirectory;
class TransientResourceUsage;

/**
 * Implementation of IFlushTarget interface for attribute vectors.
 */
class FlushableAttribute : public searchcorespi::LeafFlushTarget
{
private:
    /**
     * Task performing the actual flushing to disk.
     **/
    class Flusher;
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    using FlushStats = searchcorespi::FlushStats;

    AttributeVectorSP                        _attr;
    bool                                     _cleanUpAfterFlush;
    FlushStats                               _lastStats;
    const search::TuneFileAttributes         _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    vespalib::ISequencedTaskExecutor        &_attributeFieldWriter;
    HwInfo                                   _hwInfo;
    std::shared_ptr<AttributeDirectory>      _attrDir;
    double                                   _replay_operation_cost;

    Task::UP internalInitFlush(SerialNum currentSerial);

public:
    using SP = std::shared_ptr<FlushableAttribute>;

    /**
     * Creates a new instance using the given attribute vector and the
     * given base dir where all attribute vectors are located.
     *
     * fileHeaderContext must be kept alive by caller.
     **/
    FlushableAttribute(AttributeVectorSP attr,
                       const std::shared_ptr<AttributeDirectory> &attrDir,
                       const search::TuneFileAttributes &tuneFileAttributes,
                       const search::common::FileHeaderContext &
                       fileHeaderContext,
                       vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                       const HwInfo &hwInfo);

    virtual ~FlushableAttribute();

    void setCleanUpAfterFlush(bool cleanUp) { _cleanUpAfterFlush = cleanUp; }

    TransientResourceUsage get_transient_resource_usage() const;

    // Implements IFlushTarget
    virtual MemoryGain getApproxMemoryGain() const override;
    virtual DiskGain getApproxDiskGain() const override;
    virtual Time getLastFlushTime() const override;
    virtual SerialNum getFlushedSerialNum() const override;
    virtual Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
    virtual FlushStats getLastFlushStats() const override { return _lastStats; }
    virtual uint64_t getApproxBytesToWriteToDisk() const override;
    virtual double get_replay_operation_cost() const override;
};

} // namespace proton

