// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::fef { class IIndexEnvironment; }
namespace search::query { class Node; }

namespace proton::matching {

class HandleRecorder;

/**
 * Visits all terms of a node tree and register need for normal features due to query recall, i.e. when iterators
 * inspects unpacked data from children (e.g. equiv, near, onear, phrase and sameElement).
 */
void tag_needed_handles(search::query::Node& node, HandleRecorder& handle_recorder,
                        const search::fef::IIndexEnvironment& index_env);

}
