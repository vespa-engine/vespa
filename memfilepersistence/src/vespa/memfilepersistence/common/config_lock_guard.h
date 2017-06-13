// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "config_aliases.h"
#include "options.h"
#include <vespa/vespalib/util/rwlock.h>

namespace storage {
namespace memfile {

class Environment;

/**
 * Shared guard base allowing read access to existing configs via both
 * read and write guard subclasses.
 */
class ConfigLockGuardBase {
public:
    explicit ConfigLockGuardBase(const Environment& e)
        : _env(&e)
    {
    }

    ConfigLockGuardBase(ConfigLockGuardBase&& other)
        : _env(other._env)
    {
        // If the source is attempted used after the move, ensure it nukes
        // itself with a SIGSEGV.
        other._env = nullptr;
    }

    // To avoid circular dependencies, all access of Environment internals
    // must be in separate .cpp file.

    bool hasPersistenceConfig() const noexcept;
    std::shared_ptr<const PersistenceConfig> persistenceConfig() const noexcept;

    bool hasMemFilePersistenceConfig() const noexcept;
    std::shared_ptr<const MemFilePersistenceConfig>
            memFilePersistenceConfig() const noexcept;

    bool hasDevicesConfig() const noexcept;
    std::shared_ptr<const DevicesConfig> devicesConfig() const noexcept;

    bool hasOptions() const noexcept;
    std::shared_ptr<const Options> options() const noexcept;

    ConfigLockGuardBase(const ConfigLockGuardBase&) = delete;
    ConfigLockGuardBase& operator=(const ConfigLockGuardBase&) = delete;

private:
    const Environment* _env;
};

class ConfigWriteLockGuard : public ConfigLockGuardBase {
public:
    explicit ConfigWriteLockGuard(Environment& e);
    /**
     * Moving a guard transfers ownership of the lock to the move target. It
     * is illegal and undefined behavior to attempt to access the environment
     * configuration through a guard whose lock has been transferred away.
     */
    ConfigWriteLockGuard(ConfigWriteLockGuard&& other);

    // By definition, configs can only be mutated when the writer lock
    // is held.
    void setPersistenceConfig(std::unique_ptr<PersistenceConfig> cfg) noexcept;
    void setMemFilePersistenceConfig(
            std::unique_ptr<MemFilePersistenceConfig> cfg) noexcept;
    void setDevicesConfig(std::unique_ptr<DevicesConfig> cfg) noexcept;
    void setOptions(std::unique_ptr<Options> opts);

private:
    vespalib::RWLockWriter _lock;
    // This points to the exact same object as the const ref in the base
    // and basically serves as an alternative to const_cast.
    Environment* _mutableEnv;

    // Hide the fact that we're storing duplicate information to other
    // methods.
    Environment& mutableEnv() { return *_mutableEnv; }
};

class ConfigReadLockGuard : public ConfigLockGuardBase {
public:
    explicit ConfigReadLockGuard(const Environment& e);
    ConfigReadLockGuard(ConfigReadLockGuard&& other);

    // Config reader methods already implemented in base.

private:
    vespalib::RWLockReader _lock;
};


} // memfile
} // storage

