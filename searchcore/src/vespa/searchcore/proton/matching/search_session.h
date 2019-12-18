// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/time.h>
#include <memory>

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

    SessionId             _session_id;
    vespalib::steady_time _create_time;
    vespalib::steady_time _time_of_doom;
    OwnershipBundle       _owned_objects;
    std::unique_ptr<MatchToolsFactory> _match_tools_factory;

public:
    typedef std::shared_ptr<SearchSession> SP;

    SearchSession(const SessionId &id, vespalib::steady_time create_time, vespalib::steady_time time_of_doom,
                  std::unique_ptr<MatchToolsFactory> match_tools_factory,
                  OwnershipBundle &&owned_objects);
    ~SearchSession();

    const SessionId &getSessionId() const { return _session_id; }
    void releaseEnumGuards();

    /**
     * Gets this session's create time.
     */
    vespalib::steady_time getCreateTime() const { return _create_time; }

    /**
     * Gets this session's timeout.
     */
    vespalib::steady_time getTimeOfDoom() const { return _time_of_doom; }

    MatchToolsFactory &getMatchToolsFactory() { return *_match_tools_factory; }
};

}
