// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "search_session.h"
#include "isessioncachepruner.h"
#include <vespa/searchcore/grouping/groupingsession.h>
#include <vespa/searchcore/grouping/sessionid.h>
#include <vespa/vespalib/stllike/lrucache_map.h>

namespace proton::matching {

typedef vespalib::string SessionId;

struct GroupingSessionCache;
struct SearchSessionCache;

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
        fastos::SteadyTimeStamp created;
        fastos::SteadyTimeStamp doom;
        SearchSessionInfo(const vespalib::string &id_in,
                          fastos::SteadyTimeStamp created_in,
                          fastos::SteadyTimeStamp doom_in)
            : id(id_in), created(created_in), doom(doom_in) {}
    };

private:
    std::unique_ptr<GroupingSessionCache> _grouping_cache;
    std::unique_ptr<SearchSessionCache> _search_map;

public:
    typedef std::unique_ptr<SessionManager> UP;
    typedef std::shared_ptr<SessionManager> SP;

    SessionManager(uint32_t maxSizeGrouping);
    ~SessionManager() override;

    void insert(search::grouping::GroupingSession::UP session);
    search::grouping::GroupingSession::UP pickGrouping(const SessionId &id);
    Stats getGroupingStats();

    void insert(SearchSession::SP session);
    SearchSession::SP pickSearch(const SessionId &id);
    Stats getSearchStats();
    size_t getNumSearchSessions() const;
    std::vector<SearchSessionInfo> getSortedSearchSessionInfo() const;

    void pruneTimedOutSessions(fastos::SteadyTimeStamp currentTime) override;
    void close();
};

}
