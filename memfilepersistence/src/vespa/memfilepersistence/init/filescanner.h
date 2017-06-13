// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::FileScanner
 * \ingroup memfile
 *
 * \brief Scans a directory for memfiles.
 *
 * When storage starts up, we need to know what data already exist. This process
 * will identify what buckets we have data for.
 */

#pragma once

#include <vespa/memfilepersistence/device/mountpointlist.h>
#include <vespa/memfilepersistence/mapper/bucketdirectorymapper.h>
#include <vespa/metrics/metrics.h>
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageframework/generic/clock/timer.h>

namespace document {
    class BucketId;
}

namespace storage::memfile {

class FileScanner : private framework::Component {
public:
    typedef std::unique_ptr<FileScanner> UP;

    struct Config {
        uint32_t _maxAlienFilesLogged;
        Config()
            : _maxAlienFilesLogged(10) {}
    };
    struct Metrics : public metrics::MetricSet {
        std::vector<std::string> _alienFiles;
        metrics::LongCountMetric _alienFileCounter;
        metrics::LongCountMetric _temporaryFilesDeleted;
        metrics::LongCountMetric _multipleBucketsSameDisk;
        metrics::LongCountMetric _wrongDir;
        metrics::LongCountMetric _wrongDisk;
        metrics::LongCountMetric _dirsListed;
        framework::MilliSecTimer _startTime;
        metrics::LongAverageMetric _listLatency;

        Metrics(framework::Clock&);
        ~Metrics();
    };

private:
    struct Context {
        const Directory& _dir;
        Metrics _metrics;

        Context(const Directory& d, framework::Clock& c)
            : _dir(d), _metrics(c) {}
    };

    BucketDirectoryMapper _directoryMapper;
    const MountPointList& _mountPoints;
    Config _config;
    uint32_t _dirLevels;
    uint32_t _dirSpread;
        // As there is only one FileScanner instance in storage, we need a
        // lock to let multiple threads update global data in the scanner.
        // Each operation will typically keep a Context object it can use
        // without locking and then grab lock to update global data after
        // completion.
    vespalib::Lock _globalLock;
    Metrics _globalMetrics;

public:
    FileScanner(framework::ComponentRegister&, const MountPointList&,
                uint32_t dirLevels, uint32_t dirSpread);
    ~FileScanner();

    void buildBucketList(document::BucketId::List & list,
                         uint16_t partition,
                         uint16_t part, uint16_t totalParts);

    const Metrics& getMetrics() const { return _globalMetrics; }


private:
    void buildBucketList(document::BucketId::List & list,
                         Context&,
                         std::vector<uint32_t>& path,
                         uint32_t dirLevel);
    std::string getPathName(Context&, std::vector<uint32_t>& path,
                            const document::BucketId* bucket = 0) const;
    void buildBucketList(document::BucketId::List & list,
                         Context&,
                         std::vector<uint32_t>& path);
    bool processFile(document::BucketId::List & list,
                     Context&,
                     std::vector<uint32_t>& path,
                     const std::string& pathName,
                     const std::string& name);
    document::BucketId extractBucketId(const std::string& name) const;
    bool handleBadLocation(const document::BucketId& bucket,
                           Context&,
                           std::vector<uint32_t>& path);
};

}
