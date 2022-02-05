// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/source.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/configstate.h>
#include <map>

namespace config {

class ConfigInstance;
class IConfigHolder;

/**
 * Class for sending and receiving config request from a raw string.
 */
class ConfigSetSource : public Source {
public:
    typedef std::map<ConfigKey, ConfigInstance *> BuilderMap;
    typedef std::shared_ptr<BuilderMap> BuilderMapSP;
    ConfigSetSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key, BuilderMapSP builderMap);
    ConfigSetSource(const ConfigSetSource &) = delete;
    ConfigSetSource & operator =(const ConfigSetSource &) = delete;
    ~ConfigSetSource();

    void getConfig() override;
    void reload(int64_t generation) override;
    void close() override;
private:
    std::shared_ptr<IConfigHolder> _holder;
    const ConfigKey _key;
    int64_t         _generation;
    BuilderMapSP    _builderMap;
    ConfigState     _lastState;

    bool validRequest(const ConfigKey & key);
};

}

