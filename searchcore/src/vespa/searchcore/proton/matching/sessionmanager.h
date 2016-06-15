// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "search_session.h"
#include "isessioncachepruner.h"
#include <vespa/searchcore/grouping/groupingsession.h>
#include <vespa/searchcore/grouping/sessionid.h>
#include <vespa/vespalib/stllike/lrucache_map.h>

namespace proton {
namespace matching {

typedef vespalib::string SessionId;

class SessionManager : public ISessionCachePruner {
public:
    struct Stats {
        Stats()
            : numInsert(0),
              numPick(0),
              numDropped(0),
              numCached(0),
              numTimedout(0)
        {}
        uint32_t numInsert;
        uint32_t numPick;
        uint32_t numDropped;
        uint32_t numCached;
        uint32_t numTimedout;
    };

    struct SearchSessionInfo {
        vespalib::string id;
        fastos::TimeStamp created;
        fastos::TimeStamp doom;
        SearchSessionInfo(const vespalib::string &id_in,
                          fastos::TimeStamp created_in,
                          fastos::TimeStamp doom_in)
            : id(id_in), created(created_in), doom(doom_in) {}
    };

private:
    struct SessionCacheBase {
    protected:
        Stats _stats;
        vespalib::Lock _lock;

        void entryDropped(const SessionId &id);
        ~SessionCacheBase() {}
    };

    template <typename T>
    struct SessionCache : SessionCacheBase {
        typedef typename T::LP EntryLP;
        typedef typename T::UP EntryUP;
        vespalib::lrucache_map<vespalib::LruParam<SessionId, EntryLP> > _cache;

        SessionCache(uint32_t max_size) : _cache(max_size) {}

        void insert(EntryUP session) {
            vespalib::LockGuard guard(_lock);
            const SessionId &id(session->getSessionId());
            if (_cache.size() >= _cache.capacity()) {
                entryDropped(id);
            }
            _cache.insert(id, EntryLP(session.release()));
            _stats.numInsert++;
        }
        EntryUP pick(const SessionId & id) {
            vespalib::LockGuard guard(_lock);
            EntryUP ret;
            if (_cache.hasKey(id)) {
                _stats.numPick++;
                EntryLP session(_cache.get(id));
                _cache.erase(id);
                ret.reset(session.release());
            }
            return ret;
        }
        void pruneTimedOutSessions(fastos::TimeStamp currentTime) {
            std::vector<EntryLP> toDestruct = stealTimedOutSessions(currentTime);
            toDestruct.clear();
        }
        std::vector<EntryLP> stealTimedOutSessions(fastos::TimeStamp currentTime) {
            std::vector<EntryLP> toDestruct;
            vespalib::LockGuard guard(_lock);
            toDestruct.reserve(_cache.size());
            for (auto it(_cache.begin()), mt(_cache.end()); it != mt;) {
                EntryLP session = *it;
                if (session->getTimeOfDoom() < currentTime) {
                    toDestruct.push_back(session);
                    it = _cache.erase(it);
                    _stats.numTimedout++;
                } else {
                    it++;
                }
            }
            return toDestruct;
        }
        Stats getStats() {
            vespalib::LockGuard guard(_lock);
            Stats stats = _stats;
            stats.numCached = _cache.size();
            _stats = Stats();
            return stats;
        }
        bool empty() const {
            vespalib::LockGuard guard(_lock);
            return _cache.empty();
        }
    };

    template <typename T>
    struct SessionMap : SessionCacheBase {
        typedef typename T::SP EntrySP;
        vespalib::hash_map<SessionId, EntrySP> _map;

        void insert(EntrySP session) {
            vespalib::LockGuard guard(_lock);
            const SessionId &id(session->getSessionId());
            _map.insert(std::make_pair(id, session));
            _stats.numInsert++;
        }
        EntrySP pick(const SessionId & id) {
            vespalib::LockGuard guard(_lock);
            auto it = _map.find(id);
            if (it != _map.end()) {
                _stats.numPick++;
                return it->second;
            }
            return EntrySP();
        }
        void pruneTimedOutSessions(fastos::TimeStamp currentTime) {
            std::vector<EntrySP> toDestruct = stealTimedOutSessions(currentTime);
            toDestruct.clear();
        }
        std::vector<EntrySP> stealTimedOutSessions(fastos::TimeStamp currentTime) {
            std::vector<EntrySP> toDestruct;
            std::vector<SessionId> keys;
            vespalib::LockGuard guard(_lock);
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
            vespalib::LockGuard guard(_lock);
            Stats stats = _stats;
            stats.numCached = _map.size();
            _stats = Stats();
            return stats;
        }
        size_t size() const {
            vespalib::LockGuard guard(_lock);
            return _map.size();
        }
        bool empty() const {
            vespalib::LockGuard guard(_lock);
            return _map.empty();
        }
        template <typename F>
        void each(F f) const {
            vespalib::LockGuard guard(_lock);
            for (const auto &entry: _map) {
                f(*entry.second);
            }
        }
    };

    SessionCache<search::grouping::GroupingSession> _grouping_cache;
    SessionMap<SearchSession> _search_map;

public:
    typedef std::unique_ptr<SessionManager> UP;
    typedef std::shared_ptr<SessionManager> SP;

    SessionManager(uint32_t maxSizeGrouping);

    void insert(search::grouping::GroupingSession::UP session);
    search::grouping::GroupingSession::UP pickGrouping(const SessionId &id);
    Stats getGroupingStats() { return _grouping_cache.getStats(); }

    void insert(SearchSession::SP session);
    SearchSession::SP pickSearch(const SessionId &id);
    Stats getSearchStats() { return _search_map.getStats(); }
    size_t getNumSearchSessions() const { return _search_map.size(); }
    std::vector<SearchSessionInfo> getSortedSearchSessionInfo() const;

    void pruneTimedOutSessions(fastos::TimeStamp currentTime);
    void close();
};

}  // namespace proton::matching
}  // namespace proton

