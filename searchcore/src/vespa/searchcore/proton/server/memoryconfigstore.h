// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "configstore.h"
#include <vespa/searchcommon/common/schema.h>
#include <map>
#include <set>

namespace proton {

struct ConfigMaps {
    typedef std::shared_ptr<ConfigMaps> SP;
    std::map<search::SerialNum, std::shared_ptr<DocumentDBConfig>> configs;
    std::set<search::SerialNum> _valid;
    ~ConfigMaps();
};

class MemoryConfigStore : public ConfigStore {
    using Schema = search::index::Schema;
    ConfigMaps::SP _maps;

public:
    MemoryConfigStore(const MemoryConfigStore &) = delete;
    MemoryConfigStore & operator = (const MemoryConfigStore &) = delete;
    MemoryConfigStore();
    MemoryConfigStore(ConfigMaps::SP maps);
    ~MemoryConfigStore();

    SerialNum getBestSerialNum() const override;
    SerialNum getOldestSerialNum() const override;
    bool hasValidSerial(SerialNum serial) const override;
    SerialNum getPrevValidSerial(SerialNum serial) const override;
    void saveConfig(const DocumentDBConfig &config, SerialNum serial) override;
    void loadConfig(const DocumentDBConfig &, SerialNum serial, std::shared_ptr<DocumentDBConfig> &loaded_config) override;
    void removeInvalid() override;
    void prune(SerialNum serial) override;
    void serializeConfig(SerialNum, vespalib::nbostream &) override;
    void deserializeConfig(SerialNum, vespalib::nbostream &) override;
    void setProtonConfig(const ProtonConfigSP &) override;
};

// Holds the state of a set of MemoryConfigStore objects, making stored
// state available between different instantiations.
class MemoryConfigStores {
    std::map<std::string, ConfigMaps::SP> _config_maps;

public:
    MemoryConfigStores(const MemoryConfigStores &) = delete;
    MemoryConfigStores & operator = (const MemoryConfigStores &) = delete;
    MemoryConfigStores();
    ~MemoryConfigStores();
    ConfigStore::UP getConfigStore(const std::string &type);
};

}  // namespace proton

