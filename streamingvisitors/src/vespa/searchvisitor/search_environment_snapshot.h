// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankmanager.h"

namespace streaming {

/*
 * Snapshot of SearchEnvironment used by SearchVisitor. The snapshot
 * is created as part of applying config to the search environment and
 * references classes based on the same config snapshot.
 */
class SearchEnvironmentSnapshot
{
    std::shared_ptr<const RankManager::Snapshot> _rank_manager_snapshot;
    std::shared_ptr<VsmfieldsConfig>             _vsm_fields_cfg;
    std::shared_ptr<const vsm::DocsumTools>      _docsum_tools;

public:
    SearchEnvironmentSnapshot(const RankManager& rank_manager, const vsm::VSMAdapter& vsm_adapter);
    ~SearchEnvironmentSnapshot();
    const std::shared_ptr<const RankManager::Snapshot>& get_rank_manager_snapshot() const noexcept { return _rank_manager_snapshot; }
    const std::shared_ptr<VsmfieldsConfig>& get_vsm_fields_config() const noexcept { return _vsm_fields_cfg; }
    const std::shared_ptr<const vsm::DocsumTools>& get_docsum_tools() const noexcept { return _docsum_tools; }
};

}
