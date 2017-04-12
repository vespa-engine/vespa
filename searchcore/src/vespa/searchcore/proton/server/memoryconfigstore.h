// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "configstore.h"
#include "documentdbconfig.h"
#include <vespa/searchcommon/common/schema.h>
#include <map>

namespace proton {

struct ConfigMaps {
    typedef std::shared_ptr<ConfigMaps> SP;
    std::map<search::SerialNum, DocumentDBConfig::SP> configs;
    std::map<search::SerialNum, search::index::Schema::SP> histories;
    std::set<search::SerialNum> _valid;
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

    virtual SerialNum getBestSerialNum() const override;
    virtual SerialNum getOldestSerialNum() const override;
    virtual bool hasValidSerial(SerialNum serial) const override;
    virtual SerialNum getPrevValidSerial(SerialNum serial) const override;
    virtual void saveConfig(const DocumentDBConfig &config,
                            const Schema &history,
                            SerialNum serial) override;
    virtual void loadConfig(const DocumentDBConfig &, SerialNum serial,
                            DocumentDBConfig::SP &loaded_config,
                            Schema::SP &history_schema) override;
    virtual void removeInvalid() override;
    void prune(SerialNum serial) override;
    virtual void saveWipeHistoryConfig(SerialNum serial, fastos::TimeStamp wipeTimeLimit) override;
    virtual void serializeConfig(SerialNum, vespalib::nbostream &) override;
    virtual void deserializeConfig(SerialNum, vespalib::nbostream &) override;
    virtual void setProtonConfig(const ProtonConfigSP &) override;
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

