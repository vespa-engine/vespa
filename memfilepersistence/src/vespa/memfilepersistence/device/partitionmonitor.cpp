// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/device/partitionmonitor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.device.partition.monitor");

namespace storage {

namespace memfile {

namespace {

    uint32_t getBlockSize(struct statvfs& info) {
            // f_bsize have a strange name in man page, but as far as we've seen
            // on actual file systems, it seems to correspond to block size.
        return info.f_bsize;
    }

    float calcRootOnlyRatio(struct statvfs& info) {
        return (static_cast<uint64_t>(info.f_bfree)
                   - static_cast<uint64_t>(info.f_bavail))
               / info.f_blocks;
    }

    struct RealStatter : public PartitionMonitor::Statter {
        virtual void statFileSystem(const std::string& file, struct statvfs& info) override
        {
            if (statvfs(file.c_str(), &info) != 0) {
                vespalib::asciistream ost;
                ost << "Failed to run statvfs to find data on disk containing "
                    << "file " << file << ": errno(" << errno << ") - "
                    << vespalib::getLastErrorString() << ".";
                throw vespalib::IoException(
                        ost.str(), vespalib::IoException::getErrorType(errno),
                        VESPA_STRLOC);
            }
        }
    };

}

uint64_t
PartitionMonitor::calcTotalSpace(struct statvfs& info) const {
    // Ignore the part of the filesystem only root can write to.
    uint64_t nonRootBlocksExisting(
            static_cast<uint64_t>(info.f_blocks)
            - static_cast<uint64_t>(info.f_bfree)
            + static_cast<uint64_t>(info.f_bavail));
    return nonRootBlocksExisting * _blockSize;
}

uint64_t
PartitionMonitor::calcUsedSpace(struct statvfs& info) const {
    return (_partitionSize - info.f_bavail * _blockSize);
}

float
PartitionMonitor::calcInodeFillRatio(struct statvfs& info) const {
    uint64_t freeForRootOnly = info.f_ffree - info.f_favail;
    uint64_t nonRootInodes = info.f_files - freeForRootOnly;
    float freeInodesRatio = static_cast<float>(info.f_favail) / nonRootInodes;
    return float(1.0) - freeInodesRatio;
}

uint64_t
PartitionMonitor::calcDynamicPeriod() const
{
    uint32_t lastFillRate = (100 * _usedSpace / _partitionSize);
    uint32_t maxFillRate = static_cast<uint32_t>(100 * _maxFillRate);
    if (lastFillRate >= maxFillRate) {
        return 1;
    } else {
        uint32_t fillDiff = (maxFillRate - lastFillRate);
        return _period * fillDiff * fillDiff;
    }
}

PartitionMonitor::PartitionMonitor(const std::string& file)
    : _fileOnPartition(file),
      _fileSystemId(0),
      _policy(STAT_PERIOD),
      _blockSize(0),
      _partitionSize(0),
      _usedSpace(0),
      _period(100),
      _queriesSinceStat(0),
      _maxFillRate(0.98),
      _rootOnlyRatio(0),
      _inodeFillRate(0),
      _statter()
{
    setStatter(std::unique_ptr<Statter>(new RealStatter));
    LOG(debug, "%s: Monitor created with default setting of period at 100.",
        _fileOnPartition.c_str());
}

void
PartitionMonitor::setPolicy(vespa::config::storage::StorDevicesConfig::StatfsPolicy policy,
                            uint32_t period)
{
    switch (policy) {
        case vespa::config::storage::StorDevicesConfig::STAT_ALWAYS:
            setAlwaysStatPolicy(); break;
        case vespa::config::storage::StorDevicesConfig::STAT_ONCE:
            setStatOncePolicy(); break;
        case vespa::config::storage::StorDevicesConfig::STAT_PERIOD:
            if (period == 0) {
                setStatPeriodPolicy();
            } else {
                setStatPeriodPolicy(period);
            }
            break;
        case vespa::config::storage::StorDevicesConfig::STAT_DYNAMIC:
            if (period == 0) {
                setStatDynamicPolicy();
            } else {
                setStatDynamicPolicy(period);
            }
            break;
    }
}

void
PartitionMonitor::setAlwaysStatPolicy()
{
    _policy = ALWAYS_STAT;
    LOG(debug, "%s: Set stat policy to always stat.", _fileOnPartition.c_str());
}

void
PartitionMonitor::setStatOncePolicy()
{
    _policy = STAT_ONCE;
    LOG(debug, "%s: Set stat policy to stat once.", _fileOnPartition.c_str());
}

void
PartitionMonitor::setStatPeriodPolicy(uint32_t period)
{
    _policy = STAT_PERIOD;
    _period = period;
    LOG(debug, "%s: Set stat policy to stat every %u attempt.",
        _fileOnPartition.c_str(), _period);
}

void
PartitionMonitor::setStatDynamicPolicy(uint32_t basePeriod)
{
    _policy = STAT_DYNAMIC;
    _period = basePeriod;
    LOG(debug, "%s: Set stat policy to stat dynamicly with base %u.",
        _fileOnPartition.c_str(), _period);
}

void
PartitionMonitor::setStatter(std::unique_ptr<Statter> statter)
{
    vespalib::LockGuard lock(_updateLock);
    _statter = std::move(statter);
    struct statvfs info;
    _statter->statFileSystem(_fileOnPartition, info);
    _blockSize = getBlockSize(info);
    _partitionSize = calcTotalSpace(info);
        // Calculations further down assumes total size can be held within
        // a signed 64 bit.
    assert(_partitionSize
                < static_cast<uint64_t>(std::numeric_limits<int64_t>::max()));
    _usedSpace = calcUsedSpace(info);
    _rootOnlyRatio = calcRootOnlyRatio(info);
    _inodeFillRate = calcInodeFillRatio(info);
    _fileSystemId = info.f_fsid;
    LOG(debug, "FileSystem(%s): Total size: %" PRIu64 ", used: %" PRIu64
               ", root only %f, max fill rate %f, fill rate %f.",
        _fileOnPartition.c_str(),
        _partitionSize,
        _usedSpace,
        _rootOnlyRatio,
        _maxFillRate,
        static_cast<double>(_usedSpace) / _partitionSize);
}

void
PartitionMonitor::updateIfNeeded() const
{
    uint32_t period = 0;
    switch (_policy) {
        case STAT_ONCE: period = std::numeric_limits<uint32_t>::max(); break;
        case ALWAYS_STAT: period = 1; break;
        case STAT_PERIOD: period = _period; break;
        case STAT_DYNAMIC: period = calcDynamicPeriod(); break;
    }
    if (++_queriesSinceStat >= period) {
        struct statvfs info;
        try{
            _statter->statFileSystem(_fileOnPartition, info);
            _usedSpace = calcUsedSpace(info);
            _inodeFillRate = calcInodeFillRatio(info);
            _queriesSinceStat = 0;
        } catch (vespalib::Exception& e) {
            LOG(warning, "Failed to stat filesystem with file %s. Using "
                         "last stored used space of %" PRIu64 ".",
                _fileOnPartition.c_str(), _usedSpace);
        }
    }
}
uint64_t
PartitionMonitor::getUsedSpace() const
{
    vespalib::LockGuard lock(_updateLock);
    updateIfNeeded();
    return _usedSpace;
}

float
PartitionMonitor::getFillRate(int64_t afterAdding) const
{
    vespalib::LockGuard lock(_updateLock);
    updateIfNeeded();
    float fillRate;
    if (static_cast<int64_t>(_usedSpace) + afterAdding
            >= static_cast<int64_t>(_partitionSize))
    {
        fillRate = 1;
    } else if (static_cast<int64_t>(_usedSpace) + afterAdding < 0) {
        fillRate = 0;
    } else {
        fillRate = (static_cast<double>(_usedSpace) + afterAdding)
                 / _partitionSize;
    }
    if (fillRate < _inodeFillRate) {
        fillRate = _inodeFillRate;
        LOG(spam, "Inode fill rate is now %f. %u requests since last stat.",
            fillRate, _queriesSinceStat);
    } else {
        LOG(spam, "Fill rate is now %f. %u requests since last stat.",
            fillRate, _queriesSinceStat);
    }
    return fillRate;
}

void
PartitionMonitor::setMaxFillness(float maxFill)
{
    if (maxFill <= 0 || maxFill > 1.0) {
        vespalib::asciistream ost;
        ost << "Max fill rate must be in the range <0,1]. Value of "
            << maxFill << " is not legal.";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    _maxFillRate = maxFill;
}

void
PartitionMonitor::addingData(uint64_t dataSize)
{
    vespalib::LockGuard lock(_updateLock);
    _usedSpace = std::max(_usedSpace, _usedSpace + dataSize);
}

void
PartitionMonitor::removingData(uint64_t dataSize)
{
    vespalib::LockGuard lock(_updateLock);
    _usedSpace = (_usedSpace > dataSize ? _usedSpace - dataSize : 0);
}

uint64_t
PartitionMonitor::getPartitionId(const std::string& fileOnPartition)
{
    RealStatter realStatter;
    struct statvfs info;
    realStatter.statFileSystem(fileOnPartition, info);
    return info.f_fsid;
}

namespace {
    void printSize(std::ostream& out, uint64_t size) {
        std::string s;
        if (size < 10 * 1024) {
            s = "B";
        } else {
            size = size / 1024;
            if (size < 10 * 1024) {
                s = "kB";
            } else {
                size = size / 1024;
                if (size < 10 * 1024) {
                    s = "MB";
                } else {
                    size = size / 1024;
                    if (size < 10 * 1024) {
                        s = "GB";
                    } else {
                        size = size / 1024;
                        s = "TB";
                    }
                }
            }
        }
        out << " (" << size << " " << s << ")";
    }
}

void
PartitionMonitor::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    vespalib::LockGuard lock(_updateLock);
    out << "PartitionMonitor(" << _fileOnPartition;
    if (verbose) {
        out << ") {"
            << "\n" << indent << "  Fill rate: "
                    << (100.0 * _usedSpace / _partitionSize)
                    << " %"
            << "\n" << indent << "  Inode fill rate: " << (100 * _inodeFillRate)
                    << " %"
            << "\n" << indent << "  Detected block size: " << _blockSize
            << "\n" << indent << "  File system id: " << _fileSystemId
            << "\n" << indent << "  Total size: " << _partitionSize;
        printSize(out, _partitionSize);
        out << "\n" << indent << "  Used size: " << _usedSpace;
        printSize(out, _usedSpace);
        out << "\n" << indent << "  Queries since last stat: "
                              << _queriesSinceStat
            << "\n" << indent << "  Monitor policy: ";
    } else {
        out << ", ";
    }
    switch (_policy) {
        case STAT_ONCE: out << "STAT_ONCE"; break;
        case ALWAYS_STAT: out << "ALWAYS_STAT"; break;
        case STAT_PERIOD: out << "STAT_PERIOD(" << _period << ")"; break;
        case STAT_DYNAMIC: out << "STAT_DYNAMIC(" << calcDynamicPeriod() << ")";
                           break;
    }
    if (verbose) {
        if (_policy == STAT_DYNAMIC) {
            out << "\n" << indent << "  Period at current fillrate "
                                  << calcDynamicPeriod();
        }
        out << "\n" << indent << "  Root only ratio " << _rootOnlyRatio
            << "\n" << indent << "  Max fill rate " << (100 * _maxFillRate)
                              << " %"
            << "\n" << indent << "}";
    } else {
        bool inodesFill = false;
        double fillRate = static_cast<double>(_usedSpace) / _partitionSize;
        if (_inodeFillRate > fillRate) {
            inodesFill = true;
            fillRate = _inodeFillRate;
        }

        out << ", " << _usedSpace << "/" << _partitionSize << " used - "
            << (100 * fillRate) << " % full" << (inodesFill ? " (inodes)" : "")
            << ")";
    }
}

void
PartitionMonitor::printXml(vespalib::XmlOutputStream& xos) const
{
    using namespace vespalib::xml;
    xos << XmlTag("partitionmonitor")
        << XmlContent(toString(true))
        << XmlEndTag();
}

void
PartitionMonitor::overrideRealStat(uint32_t blockSize, uint32_t totalBlocks,
                                   uint32_t blocksUsed, float inodeFillRate)
{
    vespalib::LockGuard lock(_updateLock);
    if (_policy != STAT_ONCE) {
        throw vespalib::IllegalStateException(
                "Makes no sense to override real stat if policy isnt set to "
                "STAT_ONCE. Values will just be set back to real values again.",
                VESPA_STRLOC);
    }
    _blockSize = blockSize;
    _partitionSize = totalBlocks * blockSize;
    _usedSpace = blocksUsed * blockSize;
    _inodeFillRate = inodeFillRate;
}

}

} // storage
