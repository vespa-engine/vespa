// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "loadtypeset.h"
#include <vespa/config-load-type.h>
#include <vespa/config/config.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/config/helper/configgetter.hpp>


namespace documentapi {

void LoadTypeSet::configure(const LoadTypeConfig& config) {
    // This configure does not support live reconfig
    if (!_types.empty()) return;

    addLoadType(0, LoadType::DEFAULT.getName(), LoadType::DEFAULT.getPriority());

    for (uint32_t i=0; i<config.type.size(); ++i) {
        addLoadType(config.type[i].id, config.type[i].name, Priority::getPriority(config.type[i].priority));
    }
}

LoadTypeSet::LoadTypeSet()
{
    addLoadType(0, LoadType::DEFAULT.getName(), LoadType::DEFAULT.getPriority());
}

LoadTypeSet::LoadTypeSet(const config::ConfigUri & configUri)
{
    std::unique_ptr<LoadTypeConfig> cfg = config::ConfigGetter<LoadTypeConfig>::getConfig(configUri.getConfigId(), configUri.getContext());
    configure(*cfg);
}

LoadTypeSet::LoadTypeSet(const LoadTypeConfig& config)
{
    configure(config);
}

LoadTypeSet::~LoadTypeSet() = default;

void
LoadTypeSet::addLoadType(uint32_t id, const string& name, Priority::Value priority) {
    auto it(_types.find(id));
    if (it != _types.end()) {
        throw config::InvalidConfigException("Load type identifiers need to be non-overlapping, 1+ and without gaps.\n", VESPA_STRLOC);
    }
    if (_nameMap.find(name) != _nameMap.end()) {
        throw config::InvalidConfigException("Load type names need to be unique and different from the reserved name \"default\".", VESPA_STRLOC);
    }
    _types[id] = std::make_unique<LoadType>(id, name, priority);
    _nameMap[name] = _types[id].get();
}

metrics::LoadTypeSet
LoadTypeSet::getMetricLoadTypes() const {
    metrics::LoadTypeSet result;
    for (const auto & entry : _types) {
        result.push_back(metrics::LoadType(entry.first, entry.second->getName()));
    }
    return result;
}

const LoadType&
LoadTypeSet::operator[](uint32_t id) const {
    auto it(_types.find(id));
    return (it == _types.end() ? LoadType::DEFAULT : *it->second);
}

const LoadType&
LoadTypeSet::operator[](const string& name) const {
    auto it(_nameMap.find(name));

    return (it == _nameMap.end() ? LoadType::DEFAULT : *it->second);
}

const LoadType*
LoadTypeSet::findLoadType(const string& name) const {
    auto it(_nameMap.find(name));
    return (it == _nameMap.end() ? 0 : it->second);
}

}
