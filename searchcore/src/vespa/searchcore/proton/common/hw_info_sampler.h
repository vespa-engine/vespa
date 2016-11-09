// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hw_info.h"
#include <vespa/vespalib/stllike/string.h>
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
        double _diskWriteSpeedOverride;
        double _slowWriteSpeedLimit;
        uint64_t _diskSampleWriteSize;

        Config(double diskWriteSpeedOverride,
               double slowWriteSpeedLimit,
               double diskSampleWriteSize)
            : _diskWriteSpeedOverride(diskWriteSpeedOverride),
              _slowWriteSpeedLimit(slowWriteSpeedLimit),
              _diskSampleWriteSize(diskSampleWriteSize)
        {
        }
    };

private:
    HwInfo _hwInfo;
    using Clock = std::chrono::system_clock;
    Clock::time_point _sampleTime;
    double _diskWriteSpeed;

    void setup(const Config &config);
    void sample(const vespalib::string &path, const Config &config);
public:
    HwInfoSampler(const vespalib::string &path, const Config &config);
    ~HwInfoSampler();

    const HwInfo &hwInfo() const { return _hwInfo; }
    std::chrono::time_point<Clock> sampleTime() const { return _sampleTime; }
    double diskWriteSpeed() const { return _diskWriteSpeed; }
};

}
