// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/hw_info.h>
#include <chrono>

namespace proton {

/*
 * Class detecting some hardware characteristics on the machine, e.g.
 * speed of sequential write to file.
 */
class HwInfoSampler
{
public:
    struct Config {
        uint64_t diskSizeBytes;
        double diskWriteSpeedOverride;
        double slowWriteSpeedLimit;
        uint64_t diskSampleWriteSize;
        bool diskShared;
        uint64_t memorySizeBytes;
        uint32_t cpuCores;

        Config(uint64_t diskSizeBytes_,
               double diskWriteSpeedOverride_,
               double slowWriteSpeedLimit_,
               double diskSampleWriteSize_,
               bool diskShared_,
               uint64_t memorySizeBytes_,
               uint32_t cpuCores_)
            : diskSizeBytes(diskSizeBytes_),
              diskWriteSpeedOverride(diskWriteSpeedOverride_),
              slowWriteSpeedLimit(slowWriteSpeedLimit_),
              diskSampleWriteSize(diskSampleWriteSize_),
              diskShared(diskShared_),
              memorySizeBytes(memorySizeBytes_),
              cpuCores(cpuCores_)
        {
        }
    };

private:
    vespalib::HwInfo _hwInfo;
    using Clock = std::chrono::system_clock;
    Clock::time_point _sampleTime;
    double _diskWriteSpeed;

    void setup(const vespalib::HwInfo::Disk &disk, const vespalib::HwInfo::Memory &memory, const vespalib::HwInfo::Cpu &cpu);
    void setDiskWriteSpeed(const vespalib::string &path, const Config &config);
    void sampleDiskWriteSpeed(const vespalib::string &path, const Config &config);
public:
    HwInfoSampler(const vespalib::string &path, const Config &config);
    ~HwInfoSampler();

    const vespalib::HwInfo &hwInfo() const { return _hwInfo; }
    std::chrono::time_point<Clock> sampleTime() const { return _sampleTime; }
    double diskWriteSpeed() const { return _diskWriteSpeed; }
};

}
