// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".sessionmanager");
#include "sessionmanager.h"

using search::grouping::GroupingSession;

namespace proton {
namespace matching {

void SessionManager::SessionCacheBase::entryDropped(const SessionId &id) {
    LOG(debug, "Session cache is full, dropping entry to fit "
        "session '%s'", id.c_str());
    _stats.numDropped++;
}

SessionManager::SessionManager(uint32_t maxSize)
    : _grouping_cache(maxSize),
      _search_map() {
}

void SessionManager::insert(search::grouping::GroupingSession::UP session) {
    _grouping_cache.insert(std::move(session));
}

void SessionManager::insert(SearchSession::SP session) {
    _search_map.insert(std::move(session));
}

GroupingSession::UP SessionManager::pickGrouping(const SessionId &id) {
    return _grouping_cache.pick(id);
}

SearchSession::SP SessionManager::pickSearch(const SessionId &id) {
    return _search_map.pick(id);
}

std::vector<SessionManager::SearchSessionInfo>
SessionManager::getSortedSearchSessionInfo() const
{
    std::vector<SearchSessionInfo> sessions;
    _search_map.each([&sessions](const SearchSession &session)
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

void SessionManager::pruneTimedOutSessions(fastos::TimeStamp currentTime) {
    _grouping_cache.pruneTimedOutSessions(currentTime);
    _search_map.pruneTimedOutSessions(currentTime);
}

void SessionManager::close() {
    pruneTimedOutSessions(fastos::TimeStamp::FUTURE);
    assert(_grouping_cache.empty());
    assert(_search_map.empty());
}

}  // namespace proton::matching
}  // namespace proton
