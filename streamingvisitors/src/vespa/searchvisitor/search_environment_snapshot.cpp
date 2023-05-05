// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_environment_snapshot.h"

namespace streaming {

SearchEnvironmentSnapshot::SearchEnvironmentSnapshot(const RankManager& rank_manager, const vsm::VSMAdapter& vsm_adapter)
    : _rank_manager_snapshot(rank_manager.getSnapshot()),
      _vsm_fields_cfg(vsm_adapter.getFieldsConfig()),
      _docsum_tools(vsm_adapter.getDocsumTools())
{
}

SearchEnvironmentSnapshot::~SearchEnvironmentSnapshot() = default;

}
