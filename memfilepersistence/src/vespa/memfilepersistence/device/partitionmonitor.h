// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::PartitionMonitor
 * \ingroup persistence
 *
 * \brief Monitors how full a file system is.
 *
 * This class is used by the persistence layer to monitor how full a disk is.
 * It remembers how full the disk is, and can also take hints, such that it
 * can give reasonable correct answers cheaply.
 */
#pragma once

#include <vespa/config-stor-devices.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/xmlserializable.h>
#include <vespa/vespalib/util/printable.h>
#include <sys/statvfs.h>


namespace storage::memfile {

class PartitionMonitorTest;

class PartitionMonitor : public vespalib::Printable,
                         public vespalib::XmlSerializable
{
public:
    using UP = std::unique_ptr<PartitionMonitor>;

    /**
     * Use an object to stat through, such that unit tests can fake stat
     * responses.
     */
    struct Statter {
        virtual ~Statter() {}
        virtual void statFileSystem(const std::string& file,
                                    struct statvfs& info) = 0;
    };

private:
    enum MonitorPolicy { ALWAYS_STAT, STAT_ONCE, STAT_PERIOD, STAT_DYNAMIC };

    vespalib::Lock _updateLock;
    std::string _fileOnPartition;
    uint64_t _fileSystemId;
    MonitorPolicy _policy;
    uint32_t _blockSize;
    uint64_t _partitionSize;
    mutable uint64_t _usedSpace;
    uint32_t _period;
    mutable uint32_t _queriesSinceStat;
    float _maxFillRate;
    float _rootOnlyRatio;
    mutable float _inodeFillRate;
    std::unique_ptr<Statter> _statter;

    void setStatter(std::unique_ptr<Statter> statter);
    uint64_t calcTotalSpace(struct statvfs& info) const;
    uint64_t calcUsedSpace(struct statvfs& info) const;
    uint64_t calcDynamicPeriod() const;
    float calcInodeFillRatio(struct statvfs& info) const;

    friend class PartitionMonitorTest;

public:
    /** Default policy is STAT_PERIOD(100). Default max fill rate 0.98. */
    PartitionMonitor(const std::string& fileOnFileSystem);
    ~PartitionMonitor();

    /** Set monitor policy from config. */
    void setPolicy(vespa::config::storage::StorDevicesConfig::StatfsPolicy, uint32_t period);

    /** Always stat on getFillRate() requests. */
    void setAlwaysStatPolicy();
    /**
     * Stat only once, then depend on addingData/removingData hints to provide
     * correct answers.
     */
    void setStatOncePolicy();
    /**
     * Run stat each period getFillRate() request. Depend on hints to keep value
     * sane within a period.
     */
    void setStatPeriodPolicy(uint32_t period = 100);
    /**
     * Run stat often when close to full, but seldom when there is lots of free
     * space. In current algorithm, we will check each percentage diff from full
     * multiplied itself times the baseperiod request.
     */
    void setStatDynamicPolicy(uint32_t basePeriod = 10);

    /** Get the file system id of this instance. */
    uint64_t getFileSystemId() const { return _fileSystemId; }

    float getRootOnlyRatio() const { return _rootOnlyRatio; }

    uint64_t getPartitionSize() const { return _partitionSize; }

    uint64_t getUsedSpace() const;

    /**
     * Get the fill rate of the file system. Where 0 is empty and 1 is 100%
     * full.
     */
    float getFillRate(int64_t afterAdding = 0) const;

    /** Set the limit where the file system is considered full. (0-1) */
    void setMaxFillness(float maxFill);

    /** Query whether disk fill rate is high enough to be considered full. */
    bool isFull(int64_t afterAdding = 0, double maxFillRate = -1) const
    {
        if (maxFillRate == -1) {
            maxFillRate = _maxFillRate;
        }
        return (getFillRate(afterAdding) >= maxFillRate);
    }

    /**
     * To keep the monitor more up to date without having to do additional stat
     * commands, give clues when you add or remove data from the file system.
     */
    void addingData(uint64_t dataSize);

    /**
     * To keep the monitor more up to date without having to do additional stat
     * commands, give clues when you add or remove data from the file system.
     */
    void removingData(uint64_t dataSize);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override ;

    /**
     * Calculate the file system id for a given file. Used when wanting an
     * instance for a new file, but you're unsure whether you already have a
     * tracker for that file system.
     */
    static uint64_t getPartitionId(const std::string& fileOnPartition);

    /** Used in unit testing only. */
    void overrideRealStat(uint32_t blockSize, uint32_t totalBlocks,
                          uint32_t blocksUsed, float inodeFillRate = 0.1);

    void printXml(vespalib::XmlOutputStream&) const override;
private:
    void updateIfNeeded() const;

};

} // memfile
