// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::slotfile::MemFileEnvironment
 * \ingroup memfile
 *
 * \brief Keeps environment for MemFile operations
 *
 * The memfile layer needs quite a lot of stuff set up in order to work. Rather
 * than passing all these bits around when creating new slotfiles, we rather
 * have an environment where all the static pieces not related to single files
 * will be kept.
 */

#pragma once

#include "options.h"
#include "types.h"
#include "config_lock_guard.h"
#include "config_aliases.h"
#include <vespa/memfilepersistence/device/mountpointlist.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/config/helper/configfetcher.h>


namespace storage {
namespace memfile {

class MemFileMapper;
class MemFileCache;

struct Environment : public Types {
    class LazyFileFactory {
    public:
        virtual ~LazyFileFactory() {};

        virtual vespalib::LazyFile::UP
                    createFile(const std::string& fileName) const = 0;
    };
    using UP = std::unique_ptr<Environment>;

    const framework::Clock& _clock;
    MemFileCache& _cache;
    MemFileMapper& _memFileMapper;
    MountPointList::UP _mountPoints;
    document::BucketIdFactory _bucketFactory;
    std::unique_ptr<LazyFileFactory> _lazyFileFactory;
    vespalib::Lock _modifiedBucketsLock;
    document::BucketId::List _modifiedBuckets;

    Environment(const config::ConfigUri & configUri,
                MemFileCache&,
                MemFileMapper&,
                const document::DocumentTypeRepo&,
                const framework::Clock&,
                bool ignoreDisks = false);
    ~Environment();

    String calculatePathInDir(const Types::BucketId& id, Directory& dir);

    vespalib::LazyFile::UP createFile(const std::string& fileName) const {
        return _lazyFileFactory->createFile(fileName);
    }

    Directory& getDirectory(uint16_t disk = 0);

    void addModifiedBucket(const document::BucketId&);
    void swapModifiedBuckets(document::BucketId::List &);

    ConfigReadLockGuard acquireConfigReadLock() const {
        return ConfigReadLockGuard(*this);
    }

    ConfigWriteLockGuard acquireConfigWriteLock() {
        return ConfigWriteLockGuard(*this);
    }

    /**
     * Get the currently assigned document repo in a data race free manner.
     * Forms a release/acquire pair with setRepo()
     */
    const document::DocumentTypeRepo& repo() const noexcept {
        return *_repo.load(std::memory_order_acquire);
    }
    /**
     * Sets the currently assigned document repo in a data race free manner.
     * Forms a release/acquire pair with repo()
     */
    void setRepo(const document::DocumentTypeRepo* typeRepo) noexcept {
        _repo.store(typeRepo, std::memory_order_release);
    }
private:
    mutable vespalib::RWLock _configRWLock;
    /**
     * For simplicity, repos are currently kept alive for the duration of the
     * process. This means we don't have to care about lifetime management, but
     * we still have to ensure writes that set the repo are release/acquired
     * paired with their reads. Repos are provided through the SPI and _not_
     * through regular provider-level config subscription, so we therefore do
     * not require the config lock to be held when reading/writing.
     */
    std::atomic<const document::DocumentTypeRepo*> _repo;
    /**
     * Configs are kept as shared_ptrs to allow lock window to remain as small
     * as possible while still retaining thread safety during pointer
     * reassignments.
     */
    std::shared_ptr<const MemFilePersistenceConfig> _config;
    std::shared_ptr<const PersistenceConfig> _persistenceConfig;
    std::shared_ptr<const DevicesConfig> _devicesConfig;
    /**
     * Options is not a true config as per se, but is an aggregate of multiple
     * other configs and must thus be protected as if it were.
     */
    std::shared_ptr<const Options> _options;
    // We entrust the config guards with access to our internals.
    friend class ConfigLockGuardBase;
    friend class ConfigWriteLockGuard;
    friend class ConfigReadLockGuard;
};

struct DefaultLazyFileFactory
    : public Environment::LazyFileFactory
{
    int _flags;

    DefaultLazyFileFactory(int flags) : _flags(flags) {}

    vespalib::LazyFile::UP createFile(const std::string& fileName) const override;
};

} // storage
} // memfile

