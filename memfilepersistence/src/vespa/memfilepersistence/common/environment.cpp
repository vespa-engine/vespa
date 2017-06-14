// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "environment.h"
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vespalib/stllike/asciistream.h>

using config::ConfigGetter;

namespace storage::memfile {

namespace {

template <typename ConfigT>
std::shared_ptr<ConfigT>
resolveConfig(const config::ConfigUri& configUri)
{
    return {ConfigGetter<ConfigT>::getConfig(
                configUri.getConfigId(), configUri.getContext())};
}

}

vespalib::LazyFile::UP
DefaultLazyFileFactory::createFile(const std::string& fileName) const
{
    return vespalib::LazyFile::UP(
            new vespalib::LazyFile(
                fileName, vespalib::File::DIRECTIO | _flags));
}

Environment::Environment(const config::ConfigUri & configUri,
                         MemFileCache& cache,
                         MemFileMapper& mapper,
                         const document::DocumentTypeRepo& typeRepo,
                         const framework::Clock& clock,
                         bool ignoreDisks)
    : _clock(clock),
      _cache(cache),
      _memFileMapper(mapper),
      _bucketFactory(),
      _lazyFileFactory(new DefaultLazyFileFactory(
            ignoreDisks ? vespalib::File::READONLY : 0)),
      _repo(&typeRepo),
      _config(resolveConfig<MemFilePersistenceConfig>(configUri)),
      _persistenceConfig(resolveConfig<PersistenceConfig>(configUri)),
      _devicesConfig(resolveConfig<DevicesConfig>(configUri)),
      _options(std::make_shared<Options>(*_config, *_persistenceConfig))
{
    DeviceManager::UP manager(
            new DeviceManager(DeviceMapper::UP(new SimpleDeviceMapper()),
                              _clock));

    manager->setPartitionMonitorPolicy(
            _devicesConfig->statfsPolicy, _devicesConfig->statfsPeriod);
    _mountPoints.reset(new MountPointList(_devicesConfig->rootFolder,
                                          _devicesConfig->diskPath,
                                          std::move(manager)));

    if (!ignoreDisks) {
        _mountPoints->init(0);

        // Update full disk setting for partition monitors
        for (uint32_t i=0; i<_mountPoints->getSize(); ++i) {
            Directory& dir(getDirectory(i));
            if (dir.getPartition().getMonitor() != 0) {
                dir.getPartition().getMonitor()->setMaxFillness(
                        _options->_diskFullFactor);
            }
        }
    }
}

Types::String
Environment::calculatePathInDir(const Types::BucketId& id, Directory& dir)
{
    vespalib::asciistream os;
    os << dir.getPath() << '/';
    // Directories created should only depend on bucket identifier.
    document::BucketId::Type seed = id.getId();
    seed = seed ^ (seed >> 32);
    vespalib::RandomGen randomizer(static_cast<uint32_t>(seed) ^ 0xba5eba11);

    for (uint32_t i = 1; i <= (uint32_t)_config->dirLevels; ++i) {
        os << vespalib::make_vespa_string(
                "%.4x/",
                randomizer.nextUint32() % _config->dirSpread);
    }

    os << vespalib::make_vespa_string("%.8" PRIx64 ".0", id.getId());
    return os.str();
}

Environment::~Environment()
{
}

Directory& Environment::getDirectory(uint16_t disk)
{
    return (*_mountPoints)[disk];
}

void
Environment::addModifiedBucket(const document::BucketId& bid)
{
    vespalib::LockGuard guard(_modifiedBucketsLock);
    _modifiedBuckets.push_back(bid);
}

void
Environment::swapModifiedBuckets(document::BucketId::List & ids)
{
    vespalib::LockGuard guard(_modifiedBucketsLock);
    _modifiedBuckets.swap(ids);
}

}
