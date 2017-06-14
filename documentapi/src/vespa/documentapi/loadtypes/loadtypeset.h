// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include "loadtype.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <map>

namespace config {
    class ConfigUri;
}

namespace vespa {
namespace config {
namespace content {
namespace internal {
    class InternalLoadTypeType;
}
}
}
}

namespace documentapi {

class LoadTypeSet
{
    using LoadTypeConfig = const vespa::config::content::internal::InternalLoadTypeType;
    vespalib::hash_map<uint32_t, std::unique_ptr<LoadType>> _types;
    // Want order to be ~ alphabetical.
    std::map<string, LoadType*> _nameMap;

    void configure(const LoadTypeConfig& config);
public:
    typedef std::unique_ptr<LoadTypeSet> UP;
    typedef std::shared_ptr<LoadTypeSet> SP;

    LoadTypeSet(const LoadTypeSet&) = delete;
    LoadTypeSet& operator=(const LoadTypeSet&) = delete;

    LoadTypeSet();
    LoadTypeSet(const config::ConfigUri & configUri);
    LoadTypeSet(const LoadTypeConfig& config);
    ~LoadTypeSet();

    void addLoadType(uint32_t id, const string& name, Priority::Value priority);

    const std::map<string, LoadType*>& getLoadTypes() const { return _nameMap; }
    metrics::LoadTypeSet getMetricLoadTypes() const;

    const LoadType& operator[](uint32_t id) const;
    const LoadType& operator[](const string& name) const;
    uint32_t size() const { return uint32_t(_types.size()); }

    /**
     * Attempts to locate a load type with given name. Returns 0 if none found.
     */
    const LoadType* findLoadType(const string& name) const;
};

} // documentapi

