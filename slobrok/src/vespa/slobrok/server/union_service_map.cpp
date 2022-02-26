// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "union_service_map.h"
#include <vespa/log/log.h>

LOG_SETUP(".slobrok.server.union_service_map");

namespace slobrok {

UnionServiceMap::UnionServiceMap() = default;
UnionServiceMap::~UnionServiceMap() = default;

ServiceMappingList UnionServiceMap::currentConsensus() const {
    ServiceMappingList result;
    for (const auto & [ name, list ] : _mappings) {
        if (list.size() == 1u) {
            result.emplace_back(name, list[0].spec);
        }
    }
    return result;
}

bool UnionServiceMap::wouldConflict(const ServiceMapping &mapping) const {
    const vespalib::string &key = mapping.name;
    auto iter = _mappings.find(key);
    if (iter == _mappings.end()) {
        return false;
    }
    const Mappings &values = iter->second;
    if (values.size() != 1) {
        return true;
    }
    return (values[0].spec != mapping.spec);
}

void UnionServiceMap::add(const ServiceMapping &mapping)
{
    const vespalib::string &key = mapping.name;
    auto iter = _mappings.find(key);
    if (iter == _mappings.end()) {
        _mappings[key].emplace_back(mapping.spec, 1u);
        LOG(debug, "add new %s->%s", mapping.name.c_str(), mapping.spec.c_str());
        ProxyMapSource::add(mapping);
    } else {
        Mappings &values = iter->second;
        for (CountedSpec &old : values) {
            if (old.spec == mapping.spec) {
                LOG(debug, "add ref to existing %s->%s", mapping.name.c_str(), mapping.spec.c_str());
                ++old.count;
                return;
            }
        }
        values.emplace_back(mapping.spec, 1u);
        if (values.size() == 2u) {
            ServiceMapping toRemove{key, values[0].spec};
            LOG(warning, "Multiple specs seen for name '%s', un-publishing %s",
                toRemove.name.c_str(), toRemove.spec.c_str());
            ProxyMapSource::remove(toRemove);
        }
    }
}

void UnionServiceMap::remove(const ServiceMapping &mapping)
{
    const vespalib::string &key = mapping.name;
    auto iter = _mappings.find(key);
    if (iter == _mappings.end()) {
        LOG(error, "Broken invariant: did not find %s in mappings", key.c_str());
        return;
    }
    LOG(debug, "remove ref from %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    Mappings &values = iter->second;
    bool found = false;
    for (CountedSpec &old : values) {
        if (old.spec == mapping.spec) {
            if (--old.count > 0u) return;
            found = true;
        }
    }
    if (! found) {
        LOG(error, "Broken invariant: did not find %s->%s in mappings",
            key.c_str(), mapping.spec.c_str());
        return;
    }
    size_t old_size = values.size();
    std::erase_if(values, [] (const CountedSpec &v) noexcept { return v.count == 0; });
    if (values.size() == 1u) {
        LOG_ASSERT(old_size == 2u);
        ServiceMapping toAdd{key, values[0].spec};
        LOG(info, "Had multiple mappings for %s, but now only %s remains",
            toAdd.name.c_str(), toAdd.spec.c_str());
        ProxyMapSource::add(toAdd);
    } else if (values.size() == 0u) {
        LOG_ASSERT(old_size == 1u);
        LOG(debug, "Last reference for %s -> %s removed",
            key.c_str(), mapping.spec.c_str());
        _mappings.erase(iter);
        ProxyMapSource::remove(mapping);
    }
}

void UnionServiceMap::update(const ServiceMapping &old_mapping,
                             const ServiceMapping &new_mapping)
{
    LOG_ASSERT(old_mapping.name == new_mapping.name);
    remove(old_mapping);
    add(new_mapping);
}

} // namespace slobrok
