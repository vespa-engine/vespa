// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "search_session.h"
#include <vespa/searchcore/grouping/groupingsession.h>
#include <vespa/searchcore/grouping/sessionid.h>
#include <vespa/vespalib/stllike/lrucache_map.h>

namespace vespalib { class ThreadExecutor; }
namespace proton::matching {

using SessionId = vespalib::string;

struct GroupingSessionCache;
struct SearchSessionCache;

class SessionManager {
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
        vespalib::steady_time created;
        vespalib::steady_time doom;
        SearchSessionInfo(const vespalib::string &id_in,
                          vespalib::steady_time created_in,
                          vespalib::steady_time doom_in) noexcept
            : id(id_in), created(created_in), doom(doom_in) {}
    };

private:
    std::unique_ptr<GroupingSessionCache> _grouping_cache;
    std::unique_ptr<SearchSessionCache> _search_map;

public:
    SessionManager(uint32_t maxSizeGrouping);
    ~SessionManager();

    void insert(search::grouping::GroupingSession::UP session);
    search::grouping::GroupingSession::UP pickGrouping(const SessionId &id);
    Stats getGroupingStats();

    void insert(SearchSession::SP session);
    SearchSession::SP pickSearch(const SessionId &id);
    Stats getSearchStats();
    size_t getNumSearchSessions() const;
    std::vector<SearchSessionInfo> getSortedSearchSessionInfo() const;

    void pruneTimedOutSessions(vespalib::steady_time currentTime, vespalib::ThreadExecutor & executor);
    // Only used for testing
    void pruneTimedOutSessions(vespalib::steady_time currentTime);
};

}
