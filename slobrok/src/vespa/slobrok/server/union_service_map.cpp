// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "union_service_map.h"
#include <vespa/log/log.h>

LOG_SETUP(".slobrok.server.union_service_map");

namespace slobrok {

UnionServiceMap::UnionServiceMap() = default;
UnionServiceMap::~UnionServiceMap() = default;

void UnionServiceMap::add(const ServiceMapping &mapping)
{
    const vespalib::string &key = mapping.name;
    auto iter = _mappings.find(key);
    if (iter == _mappings.end()) {
        _mappings[key].emplace_back(mapping.spec, 1u);
        _proxy.add(mapping);
        LOG(debug, "add new %s->%s", mapping.name.c_str(), mapping.spec.c_str());
    } else {
        Mappings &values = iter->second;
        for (CountedSpec &old : values) {
            if (old.spec == mapping.spec) {
                LOG(debug, "add ref to existing %s->%s", mapping.name.c_str(), mapping.spec.c_str());
                ++old.count;
                return;
            }
        }
        if (values.size() == 1u) {
            LOG(warning, "Multiple specs seen for name '%s', un-publishing", key.c_str());
            _proxy.remove(ServiceMapping{key, values[0].spec});
        }
        values.emplace_back(mapping.spec, 1u);
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
    std::erase_if(values, [] (const CountedSpec &v) { return v.count == 0; });
    if (values.size() == 1u) {
        LOG_ASSERT(old_size == 2u);
        LOG(info, "Had multiple mappings for %s, but now only %s remains",
            key.c_str(), values[0].spec.c_str());
        _proxy.add(ServiceMapping{key, values[0].spec});
    }
    if (values.size() == 0u) {
        LOG_ASSERT(old_size == 1u);
        LOG(debug, "Last reference for %s -> %s removed",
            key.c_str(), mapping.spec.c_str());
        _proxy.remove(mapping);
        _mappings.erase(iter);
    }
}

void UnionServiceMap::update(const ServiceMapping &old_mapping,
                             const ServiceMapping &new_mapping)
{
    LOG_ASSERT(old_mapping.name == new_mapping.name);
    remove(old_mapping);
    add(new_mapping);
}

void UnionServiceMap::registerListener(MapListener &listener)
{
    _proxy.registerListener(listener);
}

void UnionServiceMap::unregisterListener(MapListener &listener)
{
    _proxy.unregisterListener(listener);
}

} // namespace slobrok

