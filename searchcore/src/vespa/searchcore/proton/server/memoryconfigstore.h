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
    std::map<search::SerialNum,
             search::index::Schema::SP> histories;
    std::set<search::SerialNum> _valid;
};

class MemoryConfigStore : public ConfigStore {
    ConfigMaps::SP _maps;

public:
    MemoryConfigStore() : _maps(new ConfigMaps) {}
    MemoryConfigStore(ConfigMaps::SP maps) : _maps(maps) {}

    virtual SerialNum getBestSerialNum() const {
        return _maps->_valid.empty() ? 0 : *_maps->_valid.rbegin();
    }
    virtual SerialNum getOldestSerialNum() const {
        return _maps->_valid.empty() ? 0 : *_maps->_valid.begin();
    }
    virtual bool hasValidSerial(SerialNum serial) const {
        return _maps->_valid.find(serial) != _maps->_valid.end();
    }
    virtual SerialNum getPrevValidSerial(SerialNum serial) const {
        if (_maps->_valid.empty() ||
            *_maps->_valid.begin() >= serial) {
            return 0;
        }
        return *(--(_maps->_valid.lower_bound(serial)));
    }
    virtual void saveConfig(const DocumentDBConfig &config,
                            const search::index::Schema &history,
                            SerialNum serial) {
        _maps->configs[serial].reset(new DocumentDBConfig(config));
        _maps->histories[serial].reset(new search::index::Schema(history));
        _maps->_valid.insert(serial);
    }
    virtual void loadConfig(const DocumentDBConfig &, SerialNum serial,
                            DocumentDBConfig::SP &loaded_config,
                            search::index::Schema::SP &history_schema) {
        assert(hasValidSerial(serial));
        loaded_config = _maps->configs[serial];
        history_schema = _maps->histories[serial];
    }
    virtual void removeInvalid()
    {
        // Note: Depends on C++11 semantics for erase
        for (auto it = _maps->configs.begin(); it != _maps->configs.end();) {
            if (!hasValidSerial(it->first)) {
                it = _maps->configs.erase(it);
                continue;
            }
            ++it;
        }
        for (auto it = _maps->histories.begin();
             it != _maps->histories.end();) {
            if (!hasValidSerial(it->first)) {
                it = _maps->histories.erase(it);
                continue;
            }
            ++it;
        }
    }
    void prune(SerialNum serial) {
        _maps->configs.erase(_maps->configs.begin(),
                             _maps->configs.upper_bound(serial));
        _maps->histories.erase(_maps->histories.begin(),
                               _maps->histories.upper_bound(serial));
        _maps->_valid.erase(_maps->_valid.begin(),
                            _maps->_valid.upper_bound(serial));
    }
    virtual void saveWipeHistoryConfig(SerialNum serial,
                                       fastos::TimeStamp wipeTimeLimit)
    {
        if (hasValidSerial(serial)) {
            return;
        }
        SerialNum prev = getPrevValidSerial(serial);
        search::index::Schema::SP schema(new search::index::Schema);
        if (wipeTimeLimit != 0) {
            search::index::Schema::SP oldHistorySchema(_maps->histories[prev]);
            search::index::Schema::UP wipeSchema;
            wipeSchema = oldHistorySchema->getOldFields(wipeTimeLimit);
            schema.reset(search::index::Schema::
                         set_difference(*oldHistorySchema,
                                        *wipeSchema).release());
            
        }
        _maps->histories[serial] = schema;
        _maps->configs[serial] = _maps->configs[prev];
        _maps->_valid.insert(serial);
    }
    virtual void serializeConfig(SerialNum, vespalib::nbostream &) {
        LOG(info, "Serialization of config not implemented.");
    }
    virtual void deserializeConfig(SerialNum, vespalib::nbostream &) {
        assert(!"Not implemented");
    }
    virtual void setProtonConfig(const ProtonConfigSP &) override { }
};

// Holds the state of a set of MemoryConfigStore objects, making stored
// state available between different instantiations.
class MemoryConfigStores {
    std::map<std::string, ConfigMaps::SP> _config_maps;

public:
    ConfigStore::UP getConfigStore(const std::string &type) {
        if (!_config_maps[type].get()) {
            _config_maps[type].reset(new ConfigMaps);
        }
        return ConfigStore::UP(new MemoryConfigStore(_config_maps[type]));
    }
};

}  // namespace proton

