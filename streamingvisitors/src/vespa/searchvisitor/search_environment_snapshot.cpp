// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_environment_snapshot.h"

namespace streaming {

SearchEnvironmentSnapshot::SearchEnvironmentSnapshot(const RankManager& rank_manager, const vsm::VSMAdapter& vsm_adapter, int64_t config_generation)
    : _rank_manager_snapshot(rank_manager.getSnapshot()),
      _vsm_fields_cfg(vsm_adapter.getFieldsConfig()),
      _docsum_tools(vsm_adapter.getDocsumTools()),
      _config_generation(config_generation)
{
}

SearchEnvironmentSnapshot::~SearchEnvironmentSnapshot() = default;

}
