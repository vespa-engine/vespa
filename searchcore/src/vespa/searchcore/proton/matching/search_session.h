// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "match_context.h"
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
        OwnershipBundle() noexcept;
        OwnershipBundle(MatchContext && matchContext, std::shared_ptr<const ISearchHandler> searchHandler) noexcept;
        OwnershipBundle(OwnershipBundle &&) noexcept = default;
        OwnershipBundle & operator = (OwnershipBundle &&) noexcept = delete;
        ~OwnershipBundle();
        // Note that SearchHandler must above the other members due to life time guarantees.
        std::shared_ptr<const ISearchHandler> search_handler;
        MatchContext context;
        std::unique_ptr<search::fef::Properties> feature_overrides;
        IDocumentMetaStoreContext::IReadGuard::SP readGuard;
    };
private:
    using SessionId = vespalib::string;

    SessionId             _session_id;
    vespalib::steady_time _create_time;
    vespalib::steady_time _time_of_doom;
    OwnershipBundle       _owned_objects;
    std::unique_ptr<MatchToolsFactory> _match_tools_factory;

public:
    using SP = std::shared_ptr<SearchSession>;

    SearchSession(const SessionId &id, vespalib::steady_time create_time, vespalib::steady_time time_of_doom,
                  std::unique_ptr<MatchToolsFactory> match_tools_factory, OwnershipBundle &&owned_objects);
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
