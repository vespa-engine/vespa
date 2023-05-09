// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hw_info_sampler.h"
#include <vespa/config-hwinfo.h>
#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/fastos/file.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/resource_limits.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/alloc.h>
#include <filesystem>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".proton.common.hw_info_sampler");

using config::ConfigHandle;
using config::ConfigSubscriber;
using config::FileSpec;
using vespa::config::search::core::HwinfoConfig;
using vespa::config::search::core::HwinfoConfigBuilder;
using vespalib::alloc::Alloc;

using Clock = std::chrono::system_clock;

namespace proton {

namespace {

uint64_t
sampleDiskSizeBytes(const std::string &pathStr, const HwInfoSampler::Config &cfg)
{
    if (cfg.diskSizeBytes != 0) {
        return cfg.diskSizeBytes;
    }
    std::filesystem::path path(pathStr);
    auto space_info = std::filesystem::space(path);
    return space_info.capacity;
}

uint64_t
sampleMemorySizeBytes(const HwInfoSampler::Config &cfg, const vespalib::ResourceLimits& resource_limits)
{
    if (cfg.memorySizeBytes != 0) {
        return cfg.memorySizeBytes;
    }
    return resource_limits.memory();
}

uint32_t
sampleCpuCores(const HwInfoSampler::Config &cfg, const vespalib::ResourceLimits& resource_limits)
{
    if (cfg.cpuCores != 0) {
        return cfg.cpuCores;
    }
    return resource_limits.cpu();
}

std::unique_ptr<HwinfoConfig>
readConfig(const vespalib::string &path) {
    FileSpec spec(path + "/" + "hwinfo.cfg");
    ConfigSubscriber s(spec);
    std::unique_ptr<ConfigHandle<HwinfoConfig>> handle = s.subscribe<HwinfoConfig>("hwinfo");
    s.nextConfigNow();
    return handle->getConfig();
}


void writeConfig(const vespalib::string &path,
                 double diskWriteSpeed, Clock::time_point sampleTime)
{
    HwinfoConfigBuilder builder;
    builder.disk.writespeed = diskWriteSpeed;
    builder.disk.sampletime = std::chrono::duration_cast<std::chrono::seconds>(sampleTime.time_since_epoch()).count();
    config::FileConfigWriter writer(path + "/hwinfo.cfg");
    if (!writer.write(builder)) {
        LOG_ABORT("should not be reached");
    }
}

double measureDiskWriteSpeed(const vespalib::string &path,
                             size_t diskWriteLen)
{
    vespalib::string fileName = path + "/hwinfo-writespeed";
    size_t bufferLen = 1_Mi;
    Alloc buffer(Alloc::allocMMap(bufferLen));
    memset(buffer.get(), 0, buffer.size());
    double diskWriteSpeed;
    {
        FastOS_File testFile;
        testFile.EnableDirectIO();
        testFile.OpenWriteOnlyTruncate(fileName.c_str());
        sync();
        sleep(1);
        sync();
        sleep(1);
        Clock::time_point before = Clock::now();
        size_t residue = diskWriteLen;
        while (residue > 0) {
            size_t writeNow = std::min(residue, bufferLen);
            testFile.WriteBuf(buffer.get(), writeNow);
            residue -= writeNow;
        }
        Clock::time_point after = Clock::now();
        double elapsed = vespalib::to_s(after - before);
        diskWriteSpeed = diskWriteLen / elapsed / 1_Mi;
    }
    vespalib::unlink(fileName);
    return diskWriteSpeed;
}

}

HwInfoSampler::HwInfoSampler(const vespalib::string &path,
                             const Config &config)
    : _hwInfo(),
      _sampleTime(),
      _diskWriteSpeed(0.0)
{
    setDiskWriteSpeed(path, config);
    auto resource_limits = vespalib::ResourceLimits::create();
    setup(HwInfo::Disk(sampleDiskSizeBytes(path, config),
                       (_diskWriteSpeed < config.slowWriteSpeedLimit),
                       config.diskShared),
          HwInfo::Memory(sampleMemorySizeBytes(config, resource_limits)),
          HwInfo::Cpu(sampleCpuCores(config, resource_limits)));
}

HwInfoSampler::~HwInfoSampler() = default;

void
HwInfoSampler::setup(const HwInfo::Disk &disk, const HwInfo::Memory &memory, const HwInfo::Cpu &cpu)
{
    _hwInfo = HwInfo(disk, memory, cpu);
}

void
HwInfoSampler::setDiskWriteSpeed(const vespalib::string &path, const Config &config)
{
    if (config.diskWriteSpeedOverride != 0) {
        _diskWriteSpeed = config.diskWriteSpeedOverride;
        _sampleTime = Clock::now();
    } else {
        auto cfg = readConfig(path);
        if (cfg && cfg->disk.sampletime != 0.0) {
            _sampleTime = std::chrono::time_point<Clock>(std::chrono::seconds(cfg->disk.sampletime));
            _diskWriteSpeed = cfg->disk.writespeed;
        } else {
            sampleDiskWriteSpeed(path, config);
        }
    }
}

void
HwInfoSampler::sampleDiskWriteSpeed(const vespalib::string &path, const Config &config)
{
    size_t minDiskWriteLen = 1_Mi;
    size_t diskWriteLen = config.diskSampleWriteSize;
    diskWriteLen = std::max(diskWriteLen, minDiskWriteLen);
    _sampleTime = Clock::now();
    _diskWriteSpeed = measureDiskWriteSpeed(path, diskWriteLen);
    writeConfig(path, _diskWriteSpeed, _sampleTime);
}

}
