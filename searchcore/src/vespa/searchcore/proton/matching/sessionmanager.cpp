// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sessionmanager.h"
#include <vespa/vespalib/stllike/lrucache_map.hpp>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <mutex>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".sessionmanager");

using search::grouping::GroupingSession;

namespace proton::matching {

namespace {
using Stats = SessionManager::Stats;
struct SessionCacheBase {
protected:
    Stats _stats;
    mutable std::mutex _lock;

    void entryDropped(const SessionId &id);
    ~SessionCacheBase() {}
};

template <typename T>
struct SessionCache : SessionCacheBase {
    using EntryUP = typename T::UP;
    vespalib::lrucache_map<vespalib::LruParam<SessionId, EntryUP> > _cache;

    SessionCache(uint32_t max_size) : _cache(max_size) {}

    void insert(EntryUP session) {
        std::lock_guard<std::mutex> guard(_lock);
        const SessionId &id(session->getSessionId());
        if (_cache.size() >= _cache.capacity()) {
            entryDropped(id);
        }
        _cache.insert(id, std::move(session));
        _stats.numInsert++;
    }
    EntryUP pick(const SessionId & id) {
        std::lock_guard<std::mutex> guard(_lock);
        EntryUP ret;
        if (_cache.hasKey(id)) {
            _stats.numPick++;
            ret = std::move(_cache[id]);
            _cache.erase(id);
        }
        return ret;
    }
    void pruneTimedOutSessions(vespalib::steady_time currentTime) {
        std::vector<EntryUP> toDestruct = stealTimedOutSessions(currentTime);
        toDestruct.clear();
    }
    std::vector<EntryUP> stealTimedOutSessions(vespalib::steady_time currentTime) {
        std::vector<EntryUP> toDestruct;
        std::lock_guard<std::mutex> guard(_lock);
        toDestruct.reserve(_cache.size());
        for (auto it(_cache.begin()), mt(_cache.end()); it != mt;) {
            auto &session = *it;
            if (session->getTimeOfDoom() < currentTime) {
                toDestruct.push_back(std::move(session));
                it = _cache.erase(it);
                _stats.numTimedout++;
            } else {
                it++;
            }
        }
        return toDestruct;
    }
    Stats getStats() {
        std::lock_guard<std::mutex> guard(_lock);
        Stats stats = _stats;
        stats.numCached = _cache.size();
        _stats = Stats();
        return stats;
    }
    bool empty() const {
        std::lock_guard<std::mutex> guard(_lock);
        return _cache.empty();
    }
};

template <typename T>
struct SessionMap : SessionCacheBase {
    using EntrySP = typename T::SP;
    vespalib::hash_map<SessionId, EntrySP> _map;

    void insert(EntrySP session) {
        std::lock_guard<std::mutex> guard(_lock);
        const SessionId &id(session->getSessionId());
        _map.insert(std::make_pair(id, session));
        _stats.numInsert++;
    }
    EntrySP pick(const SessionId & id) {
        std::lock_guard<std::mutex> guard(_lock);
        auto it = _map.find(id);
        if (it != _map.end()) {
            _stats.numPick++;
            return it->second;
        }
        return EntrySP();
    }
    void pruneTimedOutSessions(vespalib::steady_time currentTime) {
        std::vector<EntrySP> toDestruct = stealTimedOutSessions(currentTime);
        toDestruct.clear();
    }
    std::vector<EntrySP> stealTimedOutSessions(vespalib::steady_time currentTime) {
        std::vector<EntrySP> toDestruct;
        std::vector<SessionId> keys;
        std::lock_guard<std::mutex> guard(_lock);
        keys.reserve(_map.size());
        toDestruct.reserve(_map.size());
        for (auto & it : _map) {
            EntrySP &session = it.second;
            if (session->getTimeOfDoom() < currentTime) {
                keys.push_back(it.first);
                toDestruct.push_back(EntrySP());
                toDestruct.back().swap(session);
            }
        }
        for (auto key : keys) {
            _map.erase(key);
            _stats.numTimedout++;
        }
        return toDestruct;
    }
    Stats getStats() {
        std::lock_guard<std::mutex> guard(_lock);
        Stats stats = _stats;
        stats.numCached = _map.size();
        _stats = Stats();
        return stats;
    }
    size_t size() const {
        std::lock_guard<std::mutex> guard(_lock);
        return _map.size();
    }
    bool empty() const {
        std::lock_guard<std::mutex> guard(_lock);
        return _map.empty();
    }
    template <typename F>
    void each(F f) const {
        std::lock_guard<std::mutex> guard(_lock);
        for (const auto &entry: _map) {
            f(*entry.second);
        }
    }
};

void SessionCacheBase::entryDropped(const SessionId &id) {
    LOG(debug, "Session cache is full, dropping entry to fit session '%s'", id.c_str());
    _stats.numDropped++;
}

}

struct GroupingSessionCache : public SessionCache<search::grouping::GroupingSession> {
    using Parent = SessionCache<search::grouping::GroupingSession>;
    using Parent::Parent;
};

struct SearchSessionCache : public SessionMap<SearchSession> {

};


SessionManager::SessionManager(uint32_t maxSize)
    : _grouping_cache(std::make_unique<GroupingSessionCache>(maxSize)),
      _search_map(std::make_unique<SearchSessionCache>()) {
}

SessionManager::~SessionManager() {
    pruneTimedOutSessions(vespalib::steady_time::max());
    assert(_grouping_cache->empty());
    assert(_search_map->empty());
}

void SessionManager::insert(search::grouping::GroupingSession::UP session) {
    _grouping_cache->insert(std::move(session));
}

void SessionManager::insert(SearchSession::SP session) {
    _search_map->insert(std::move(session));
}

GroupingSession::UP SessionManager::pickGrouping(const SessionId &id) {
    return _grouping_cache->pick(id);
}

SearchSession::SP SessionManager::pickSearch(const SessionId &id) {
    return _search_map->pick(id);
}

std::vector<SessionManager::SearchSessionInfo>
SessionManager::getSortedSearchSessionInfo() const
{
    std::vector<SearchSessionInfo> sessions;
    _search_map->each([&sessions](const SearchSession &session)
                     {
                         sessions.emplace_back(session.getSessionId(),
                                 session.getCreateTime(),
                                 session.getTimeOfDoom());
                     });
    std::sort(sessions.begin(), sessions.end(),
              [](const SearchSessionInfo &a,
                 const SearchSessionInfo &b)
              {
                  return (a.created < b.created);
              });
    return sessions;
}

void SessionManager::pruneTimedOutSessions(vespalib::steady_time currentTime) {
    _grouping_cache->pruneTimedOutSessions(currentTime);
    _search_map->pruneTimedOutSessions(currentTime);
}

SessionManager::Stats SessionManager::getGroupingStats() {
    return _grouping_cache->getStats();
}
SessionManager::Stats SessionManager::getSearchStats() {
    return _search_map->getStats();
}
size_t SessionManager::getNumSearchSessions() const {
    return _search_map->size();
}

}
