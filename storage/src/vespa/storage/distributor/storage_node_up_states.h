// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::distributor {

/**
 * Returns the states in which the distributors consider storage nodes to be up.
 */
constexpr const char* storage_node_up_states() noexcept {
    return "uri";
}

}
