// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "content_node_message_stats_tracker.h"

namespace storage::distributor {

// TODO doc
class ContentNodeStatsProvider {
public:
    virtual ~ContentNodeStatsProvider() = default;

    [[nodiscard]] virtual ContentNodeMessageStatsTracker::NodeStats content_node_stats() const = 0;
};

}
