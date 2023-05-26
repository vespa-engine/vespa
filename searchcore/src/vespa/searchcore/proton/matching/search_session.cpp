// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "search_session.h"
#include "match_tools.h"
#include "match_context.h"

namespace proton::matching {

SearchSession::SearchSession(const SessionId &id, vespalib::steady_time create_time, vespalib::steady_time time_of_doom,
                             std::unique_ptr<MatchToolsFactory> match_tools_factory,
                             OwnershipBundle &&owned_objects)
    : _session_id(id),
      _create_time(create_time),
      _time_of_doom(time_of_doom),
      _owned_objects(std::move(owned_objects)),
      _match_tools_factory(std::move(match_tools_factory))
{
}

void
SearchSession::releaseEnumGuards() {
    _owned_objects.context.releaseEnumGuards();
}

SearchSession::~SearchSession() = default;

SearchSession::OwnershipBundle::OwnershipBundle(MatchContext && match_context,
                                                std::shared_ptr<const ISearchHandler> searchHandler) noexcept
    : search_handler(std::move(searchHandler)),
      context(std::move(match_context)),
      feature_overrides(),
      readGuard()
{}

SearchSession::OwnershipBundle::OwnershipBundle() noexcept = default;
SearchSession::OwnershipBundle::~OwnershipBundle() = default;

}
