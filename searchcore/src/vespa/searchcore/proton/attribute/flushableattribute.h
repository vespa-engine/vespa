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

/**
 * Implementation of IFlushTarget interface for attribute vectors.
 */
class FlushableAttribute : public IFlushTarget
{
private:
    /**
     * Task performing the actual flushing to disk.
     **/
    class Flusher : public Task {
    private:
        FlushableAttribute              & _fattr;
        search::AttributeMemorySaveTarget      _saveTarget;
        std::unique_ptr<search::AttributeSaver> _saver;
        uint64_t                          _syncToken;
        search::AttributeVector::BaseName _flushFile;

        bool saveAttribute(); // not updating snap info.
    public:
        Flusher(FlushableAttribute & fattr, uint64_t syncToken);
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

    search::AttributeVector::SP _attr;
    vespalib::string            _baseDir;
    search::IndexMetaInfo       _snapInfo;
    vespalib::Lock		_snapInfoLock;
    vespalib::Lock              _flusherLock;
    bool                        _cleanUpAfterFlush;
    FlushStats                  _lastStats;
    const search::TuneFileAttributes _tuneFileAttributes;
    const search::common::FileHeaderContext &_fileHeaderContext;
    fastos::TimeStamp           _lastFlushTime;
    search::ISequencedTaskExecutor &_attributeFieldWriter;
    HwInfo                       _hwInfo;

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
                       const vespalib::string & baseDir,
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

