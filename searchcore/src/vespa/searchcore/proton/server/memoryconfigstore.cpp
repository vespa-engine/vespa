// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memoryconfigstore.h"
#include "documentdbconfig.h"
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.memoryconfigstore");

namespace proton {

ConfigMaps::~ConfigMaps() = default;

MemoryConfigStore::MemoryConfigStore() : _maps(new ConfigMaps) {}
MemoryConfigStore::MemoryConfigStore(ConfigMaps::SP maps) : _maps(maps) {}
MemoryConfigStore::~MemoryConfigStore() = default;

ConfigStore::SerialNum
MemoryConfigStore::getBestSerialNum() const {
    return _maps->_valid.empty() ? 0 : *_maps->_valid.rbegin();
}
ConfigStore::SerialNum
MemoryConfigStore::getOldestSerialNum() const {
    return _maps->_valid.empty() ? 0 : *_maps->_valid.begin();
}
bool
MemoryConfigStore::hasValidSerial(SerialNum serial) const {
    return _maps->_valid.find(serial) != _maps->_valid.end();
}
ConfigStore::SerialNum
MemoryConfigStore::getPrevValidSerial(SerialNum serial) const {
    if (_maps->_valid.empty() ||
        *_maps->_valid.begin() >= serial) {
        return 0;
    }
    return *(--(_maps->_valid.lower_bound(serial)));
}
void
MemoryConfigStore::saveConfig(const DocumentDBConfig &config,
                              SerialNum serial)
{
    _maps->configs[serial] = std::make_shared<DocumentDBConfig>(config);
    _maps->_valid.insert(serial);
}
void
MemoryConfigStore::loadConfig(const DocumentDBConfig &, SerialNum serial,
                              std::shared_ptr<DocumentDBConfig> &loaded_config)
{
    assert(hasValidSerial(serial));
    loaded_config = _maps->configs[serial];
}
void
MemoryConfigStore::removeInvalid()
{
    // Note: Depends on C++11 semantics for erase
    for (auto it = _maps->configs.begin(); it != _maps->configs.end();) {
        if (!hasValidSerial(it->first)) {
            it = _maps->configs.erase(it);
            continue;
        }
        ++it;
    }
}

void
MemoryConfigStore::prune(SerialNum serial) {
    _maps->configs.erase(_maps->configs.begin(),
                         _maps->configs.upper_bound(serial));
    _maps->_valid.erase(_maps->_valid.begin(),
                        _maps->_valid.upper_bound(serial));
}

void
MemoryConfigStore::serializeConfig(SerialNum, vespalib::nbostream &) {
    LOG(info, "Serialization of config not implemented.");
}
void
MemoryConfigStore::deserializeConfig(SerialNum, vespalib::nbostream &) {
    assert(!"Not implemented");
}
void
MemoryConfigStore::setProtonConfig(const ProtonConfigSP &) { }

MemoryConfigStores::MemoryConfigStores() = default;
MemoryConfigStores::~MemoryConfigStores() = default;

ConfigStore::UP
MemoryConfigStores::getConfigStore(const std::string &type) {
    if (!_config_maps[type].get()) {
        _config_maps[type].reset(new ConfigMaps);
    }
    return std::make_unique<MemoryConfigStore>(_config_maps[type]);
}

}  // namespace proton

