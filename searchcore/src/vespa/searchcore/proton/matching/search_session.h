// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vespa/fastos/timestamp.h>

namespace search::fef { class Properties; }

namespace proton::matching {

class MatchToolsFactory;
class MatchContext;

/**
 * Holds enough data to perform a GetDocSum request. Makes sure the
 * data is kept alive.
 */
class SearchSession {
public:
    struct OwnershipBundle {
        OwnershipBundle();
        OwnershipBundle(OwnershipBundle &&) = default;
        OwnershipBundle & operator = (OwnershipBundle &&) = default;
        ~OwnershipBundle();
        ISearchHandler::SP search_handler;
        std::unique_ptr<search::fef::Properties> feature_overrides;
        std::unique_ptr<MatchContext> context;
        IDocumentMetaStoreContext::IReadGuard::UP readGuard;
    };
private:
    typedef vespalib::string SessionId;

    SessionId _session_id;
    fastos::TimeStamp _create_time;
    fastos::TimeStamp _time_of_doom;
    OwnershipBundle   _owned_objects;
    std::unique_ptr<MatchToolsFactory> _match_tools_factory;

public:
    typedef std::shared_ptr<SearchSession> SP;

    SearchSession(const SessionId &id, fastos::TimeStamp create_time, fastos::TimeStamp time_of_doom,
                  std::unique_ptr<MatchToolsFactory> match_tools_factory,
                  OwnershipBundle &&owned_objects);
    ~SearchSession();

    const SessionId &getSessionId() const { return _session_id; }
    void releaseEnumGuards();

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

}
