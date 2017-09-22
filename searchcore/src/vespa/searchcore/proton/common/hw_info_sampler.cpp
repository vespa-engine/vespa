// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hw_info_sampler.h"
#include <vespa/config/config.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/file/filesource.h>
#include <vespa/searchcore/config/config-hwinfo.h>
#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/fastos/file.h>

using config::ConfigHandle;
using config::ConfigSubscriber;
using config::FileSpec;
using vespa::config::search::core::HwinfoConfig;
using vespa::config::search::core::HwinfoConfigBuilder;
using vespalib::alloc::Alloc;

using Clock = std::chrono::system_clock;

namespace proton {

namespace {

std::unique_ptr<HwinfoConfig> readConfig(const vespalib::string &path) {
    FileSpec spec(path + "/" + "hwinfo.cfg");
    ConfigSubscriber s(spec);
    std::unique_ptr<ConfigHandle<HwinfoConfig>> handle = s.subscribe<HwinfoConfig>("hwinfo");
    s.nextConfig(0);
    return std::move(handle->getConfig());
}


void writeConfig(const vespalib::string &path,
                 double diskWriteSpeed, Clock::time_point sampleTime)
{
    HwinfoConfigBuilder builder;
    builder.disk.writespeed = diskWriteSpeed;
    builder.disk.sampletime = std::chrono::duration_cast<std::chrono::seconds>(sampleTime.time_since_epoch()).count();
    config::FileConfigWriter writer(path + "/hwinfo.cfg");
    if (!writer.write(builder)) {
        abort();
    }
}

double measureDiskWriteSpeed(const vespalib::string &path,
                             size_t diskWriteLen)
{
    FastOS_File testFile;
    vespalib::string fileName = path + "/hwinfo-writespeed";
    size_t bufferLen = 1024 * 1024;
    Alloc buffer(Alloc::allocMMap(bufferLen));
    memset(buffer.get(), 0, buffer.size());
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
    testFile.Close();
    vespalib::unlink(fileName);
    double elapsed = std::chrono::duration<double>(after - before).count();
    double diskWriteSpeed = diskWriteLen / elapsed / 1024 / 1024;
    return diskWriteSpeed;
}

}

HwInfoSampler::HwInfoSampler(const vespalib::string &path,
                             const Config &config)
    : _hwInfo(),
      _sampleTime(),
      _diskWriteSpeed(0.0)
{
    if (config._diskWriteSpeedOverride != 0) {
        _diskWriteSpeed = config._diskWriteSpeedOverride;
        _sampleTime = Clock::now();
    } else {
        auto cfg = readConfig(path);
        if (cfg && cfg->disk.sampletime != 0.0) {
            _sampleTime = std::chrono::time_point<Clock>(std::chrono::seconds(cfg->disk.sampletime));
            _diskWriteSpeed = cfg->disk.writespeed;
        } else {
            sample(path, config);
        }
    }
    setup(config);
}

HwInfoSampler::~HwInfoSampler()
{
}

void
HwInfoSampler::setup(const Config &config)
{
    bool slowDisk = _diskWriteSpeed < config._slowWriteSpeedLimit;
    _hwInfo = HwInfo(slowDisk);
}

void
HwInfoSampler::sample(const vespalib::string &path, const Config &config)
{
    size_t minDiskWriteLen = 1024u * 1024u;
    size_t diskWriteLen = config._diskSampleWriteSize;
    diskWriteLen = std::max(diskWriteLen, minDiskWriteLen);
    _sampleTime = Clock::now();
    _diskWriteSpeed = measureDiskWriteSpeed(path, diskWriteLen);
    writeConfig(path, _diskWriteSpeed, _sampleTime);
}

}
