// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class LoadTypeSet
 * \ingroup loadtype
 *
 * \brief Class containing all the various load types that have been configured.
 *
 * The load type set makes configured load types available in an easy way for
 * different parts of Vespa to access it.
 */
#pragma once

#include <vespa/config/config.h>
#include <vespa/documentapi/loadtypes/loadtype.h>
#include <vespa/metrics/loadmetric.h>
#include <vector>
#include <vespa/config-load-type.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace documentapi {

class LoadTypeSet
{
    vespalib::hash_map<uint32_t, LoadType::LP> _types;
        // Want order to be ~ alphabetical.
    std::map<string, LoadType*> _nameMap;

        // This object cannot be copied
    LoadTypeSet(const LoadTypeSet&);
    LoadTypeSet& operator=(const LoadTypeSet&);

    void configure(const vespa::config::content::LoadTypeConfig& config) {
        // This configure does not support live reconfig
        if (!_types.empty()) return;

        addLoadType(0, LoadType::DEFAULT.getName(), LoadType::DEFAULT.getPriority());

        for (uint32_t i=0; i<config.type.size(); ++i) {
            addLoadType(config.type[i].id, config.type[i].name, Priority::getPriority(config.type[i].priority));
        }
    }

public:
    typedef std::unique_ptr<LoadTypeSet> UP;
    typedef std::shared_ptr<LoadTypeSet> SP;

    LoadTypeSet() {
        addLoadType(0, LoadType::DEFAULT.getName(), LoadType::DEFAULT.getPriority());
    }

    LoadTypeSet(const config::ConfigUri & configUri) {
        std::unique_ptr<vespa::config::content::LoadTypeConfig> cfg =
            config::ConfigGetter<vespa::config::content::LoadTypeConfig>::getConfig(configUri.getConfigId(), configUri.getContext());
        configure(*cfg);
    }

    LoadTypeSet(const vespa::config::content::LoadTypeConfig& config) {
        configure(config);
    }

    void addLoadType(uint32_t id, const string& name, Priority::Value priority) {
        vespalib::hash_map<uint32_t, LoadType::LP>::iterator it(
                _types.find(id));
        if (it != _types.end()) {
            throw config::InvalidConfigException(
                    "Load type identifiers need to be non-overlapping, 1+ "
                    "and without gaps.\n", VESPA_STRLOC);
        }
        if (_nameMap.find(name) != _nameMap.end()) {
            throw config::InvalidConfigException(
                    "Load type names need to be unique and different from "
                    "the reserved name \"default\".", VESPA_STRLOC);
        }
        _types[id] = LoadType::LP(new LoadType(id, name, priority));
        _nameMap[name] = _types[id].get();
    }

    const std::map<string, LoadType*>& getLoadTypes() const
        { return _nameMap; }
    metrics::LoadTypeSet getMetricLoadTypes() const {
        metrics::LoadTypeSet result;
        for (vespalib::hash_map<uint32_t, LoadType::LP>::const_iterator it
                = _types.begin(); it != _types.end(); ++it)
        {
            result.push_back(metrics::LoadType(
                        it->first, it->second->getName()));
        }
        return result;
    }

    const LoadType& operator[](uint32_t id) const {
        vespalib::hash_map<uint32_t, LoadType::LP>::const_iterator it(
                _types.find(id));
        return (it == _types.end() ? LoadType::DEFAULT : *it->second);
    }
    const LoadType& operator[](const string& name) const {
        std::map<string, LoadType*>::const_iterator it(
                _nameMap.find(name));

        return (it == _nameMap.end() ? LoadType::DEFAULT : *it->second);
    }

    uint32_t size() const { return uint32_t(_types.size()); }

    /**
     * Attempts to locate a load type with given name. Returns 0 if none found.
     */
    const LoadType* findLoadType(const string& name) const {
        std::map<string, LoadType*>::const_iterator it(
                _nameMap.find(name));
        return (it == _nameMap.end() ? 0 : it->second);
    }
};

} // documentapi

