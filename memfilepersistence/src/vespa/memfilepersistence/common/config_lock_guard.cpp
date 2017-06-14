// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config_lock_guard.h"
#include "environment.h"

namespace storage {
namespace memfile {

bool
ConfigLockGuardBase::hasPersistenceConfig() const noexcept
{
    return (_env->_persistenceConfig.get() != nullptr);
}

std::shared_ptr<const PersistenceConfig>
ConfigLockGuardBase::persistenceConfig() const noexcept
{
    return _env->_persistenceConfig;
}

bool
ConfigLockGuardBase::hasMemFilePersistenceConfig() const noexcept
{
    return (_env->_config.get() != nullptr);
}

std::shared_ptr<const MemFilePersistenceConfig>
ConfigLockGuardBase::memFilePersistenceConfig() const noexcept
{
    return _env->_config;
}

bool
ConfigLockGuardBase::hasDevicesConfig() const noexcept
{
    return (_env->_devicesConfig.get() != nullptr);
}

std::shared_ptr<const DevicesConfig>
ConfigLockGuardBase::devicesConfig() const noexcept
{
    return _env->_devicesConfig;
}

bool
ConfigLockGuardBase::hasOptions() const noexcept
{
    return (_env->_options.get() != nullptr);
}

std::shared_ptr<const Options>
ConfigLockGuardBase::options() const noexcept
{
    return _env->_options;
}

ConfigWriteLockGuard::ConfigWriteLockGuard(Environment& e)
    : ConfigLockGuardBase(e),
      _lock(e._configRWLock),
      _mutableEnv(&e)
{
}

ConfigWriteLockGuard::ConfigWriteLockGuard(ConfigWriteLockGuard&& other)
    : ConfigLockGuardBase(std::move(other)),
      _lock(other._lock), // Implicit lock stealing, no explicit moving
      _mutableEnv(other._mutableEnv)
{
    other._mutableEnv = nullptr;
}

void
ConfigWriteLockGuard::setPersistenceConfig(
        std::unique_ptr<PersistenceConfig> cfg) noexcept
{
    mutableEnv()._persistenceConfig = std::move(cfg);
}

void
ConfigWriteLockGuard::setMemFilePersistenceConfig(
        std::unique_ptr<MemFilePersistenceConfig> cfg) noexcept
{
    mutableEnv()._config = std::move(cfg);
}

void
ConfigWriteLockGuard::setDevicesConfig(
        std::unique_ptr<DevicesConfig> cfg) noexcept
{
    mutableEnv()._devicesConfig = std::move(cfg);
}

void
ConfigWriteLockGuard::setOptions(std::unique_ptr<Options> opts)
{
    mutableEnv()._options = std::move(opts);
}

ConfigReadLockGuard::ConfigReadLockGuard(const Environment& e)
    : ConfigLockGuardBase(e),
      _lock(e._configRWLock)
{
}

ConfigReadLockGuard::ConfigReadLockGuard(ConfigReadLockGuard&& other)
    : ConfigLockGuardBase(std::move(other)),
      _lock(other._lock) // Implicit lock stealing, no explicit moving
{
}

} // memfile
} // storage

