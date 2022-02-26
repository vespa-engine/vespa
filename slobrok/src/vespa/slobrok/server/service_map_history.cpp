// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "service_map_history.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.service_map_history");

namespace slobrok {

ServiceMapHistory::UpdateLog::UpdateLog()
  : startGeneration(1),
    currentGeneration(1),
    updates(keep_items + 1)
{}
        
ServiceMapHistory::UpdateLog::~UpdateLog() = default;

void ServiceMapHistory::UpdateLog::add(const vespalib::string &name) {
    currentGeneration.add();
    updates.push(name);
    while (updates.size() > keep_items) {
        startGeneration.add();
        updates.pop();
    }
}
        
bool ServiceMapHistory::UpdateLog::isInRange(const Generation &gen) const {
    return gen.inRangeInclusive(startGeneration, currentGeneration);
}

std::vector<vespalib::string>
ServiceMapHistory::UpdateLog::updatedSince(const Generation &gen) const {
    std::vector<vespalib::string> result;
    uint32_t skip = startGeneration.distance(gen);
    uint32_t last = startGeneration.distance(currentGeneration);
    for (uint32_t idx = skip; idx < last; ++idx) {
        result.push_back(updates.peek(idx));
    }
    return result;
}


//-----------------------------------------------------------------------------

ServiceMapHistory::ServiceMapHistory()
  : _map(),
    _waitList(),
    _log()
{}


ServiceMapHistory::~ServiceMapHistory() {
    notify_updated();
}

void ServiceMapHistory::notify_updated() {
    WaitList waitList;
    std::swap(waitList, _waitList);
    for (auto & [ handler, gen ] : waitList) {
        handler->handle(makeDiffFrom(gen));
    }
}

void ServiceMapHistory::asyncGenerationDiff(DiffCompletionHandler *handler, const Generation &fromGen) {
    if (fromGen == myGen()) {
        _waitList.emplace_back(handler, fromGen);
        return;
    }
    handler->handle(makeDiffFrom(fromGen));
}

bool ServiceMapHistory::cancel(DiffCompletionHandler *handler) {
    size_t removed = std::erase_if(_waitList, [=](const Waiter &elem) noexcept { return elem.first == handler; });
    return (removed > 0);
}

void ServiceMapHistory::remove(const ServiceMapping &mapping) {
    auto iter = _map.find(mapping.name);
    if (iter == _map.end()) {
        LOG(debug, "already removed: %s", mapping.name.c_str());
        return; // already removed
    }
    LOG_ASSERT(iter->second == mapping.spec);
    _map.erase(iter);
    _log.add(mapping.name);
    notify_updated();
}

void ServiceMapHistory::add(const ServiceMapping &mapping) {
    auto iter = _map.find(mapping.name);
    if (iter != _map.end() && iter->second == mapping.spec) {
        // already ok
        return;
    }
    _map.insert_or_assign(mapping.name, mapping.spec);
    _log.add(mapping.name);
    notify_updated();
}

MapDiff ServiceMapHistory::makeDiffFrom(const Generation &fromGen) const {
    if (_log.isInRange(fromGen)) {
        std::vector<vespalib::string> removes;
        ServiceMappingList updates;
        auto changes = _log.updatedSince(fromGen);
        for (const vespalib::string & name : changes) {
            if (_map.contains(name)) {
                updates.emplace_back(name, _map.at(name));
            } else {
                removes.push_back(name);
            }
        }
        return MapDiff(fromGen, removes, updates, myGen());
    } else {
        ServiceMappingList mappings;
        for (const auto & [ name, spec ] : _map) {
            mappings.emplace_back(name, spec);
        }
        return MapDiff(mappings, myGen());
    }
}

//-----------------------------------------------------------------------------

} // namespace slobrok
