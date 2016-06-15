// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "match_context.h"
#include "match_tools.h"
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace proton {
namespace matching {

/**
 * Holds enough data to perform a GetDocSum request. Makes sure the
 * data is kept alive.
 */
class SearchSession {
public:
    struct OwnershipBundle {
        ISearchHandler::SP search_handler;
        search::fef::Properties::UP feature_overrides;
        MatchContext::UP context;
    };
private:
    typedef vespalib::string SessionId;

    SessionId _session_id;
    fastos::TimeStamp _create_time;
    fastos::TimeStamp _time_of_doom;
    OwnershipBundle _owned_objects;
    MatchToolsFactory::UP _match_tools_factory;

public:
    typedef std::shared_ptr<SearchSession> SP;

    SearchSession(const SessionId &id, fastos::TimeStamp time_of_doom,
                  MatchToolsFactory::UP match_tools_factory,
                  OwnershipBundle &&owned_objects)
        : _session_id(id),
          _create_time(fastos::ClockSystem::now()),
          _time_of_doom(time_of_doom),
          _owned_objects(std::move(owned_objects)),
          _match_tools_factory(std::move(match_tools_factory)) {
    }

    const SessionId &getSessionId() const { return _session_id; }

    void releaseEnumGuards() { _owned_objects.context->releaseEnumGuards(); }

    /**
     * Gets this session's create time.
     */
    fastos::TimeStamp getCreateTime() const { return _create_time; }

    /**
     * Gets this session's timeout.
     */
    fastos::TimeStamp getTimeOfDoom() const { return _time_of_doom; }

    MatchToolsFactory &getMatchToolsFactory() { return *_match_tools_factory; }
};

}  // namespace proton::matching
}  // namespace proton

