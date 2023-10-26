// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "service_map_mirror.h"
#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.service_map_mirror");

namespace slobrok {

ServiceMapMirror::ServiceMapMirror()
  : _map(),
    _currGen(0)
{}

ServiceMapMirror::~ServiceMapMirror() {
    LOG_ASSERT(_listeners.size() == 0);
}

void ServiceMapMirror::apply(const MapDiff &diff) {
    LOG(debug, "Applying diff from gen %u", diff.fromGen.getAsInt());
    LOG_ASSERT(diff.fromGen == _currGen);
    for (const auto & name : diff.removed) {
        auto iter = _map.find(name);
        if (iter != _map.end()) {
            LOG(debug, "Apply remove %s->%s", name.c_str(), iter->second.c_str());
            ServiceMapping mapping(name, iter->second);
            for (auto * listener : _listeners) {
                listener->remove(mapping);
            }
            _map.erase(iter);
        } else {
            LOG(debug, "Apply remove %s [already removed]", name.c_str());
        }
    }
    for (const auto & mapping : diff.updated) {
        LOG(debug, "Apply update %s->%s", mapping.name.c_str(), mapping.spec.c_str());
        auto iter = _map.find(mapping.name);
        if (iter != _map.end()) {
            ServiceMapping old{mapping.name, iter->second};
            iter->second = mapping.spec;
            for (auto * listener : _listeners) {
                listener->update(old, mapping);
            }
        } else {
            _map.emplace(mapping.name, mapping.spec);
            for (auto * listener : _listeners) {
                listener->add(mapping);
            }
        }
    }
    LOG(debug, "Apply diff complete to gen %u", diff.toGen.getAsInt());
    _currGen = diff.toGen;
}

void ServiceMapMirror::clear() {
    for (const auto & [ k, v ] : _map) {
        ServiceMapping mapping{k, v};
        for (auto * listener : _listeners) {
            listener->remove(mapping);
        }
    }
    _map.clear();
    _currGen.reset();
}

ServiceMappingList ServiceMapMirror::allMappings() const {
    ServiceMappingList result;
    result.reserve(_map.size());
    for (const auto & [ k, v ] : _map) {
        result.emplace_back(k, v);
    }
    return result;
}

void ServiceMapMirror::registerListener(MapListener &listener) {
    _listeners.insert(&listener);
}

void ServiceMapMirror::unregisterListener(MapListener &listener) {
    _listeners.erase(&listener);
}


} // namespace slobrok
