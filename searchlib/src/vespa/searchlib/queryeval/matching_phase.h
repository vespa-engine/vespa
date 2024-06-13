// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::queryeval {

/*
 * The different matching phases when evaluating a query.
 */
enum class MatchingPhase {
    FIRST_PHASE,
    SECOND_PHASE,
    MATCH_FEATURES,
    SUMMARY_FEATURES,
    DUMP_FEATURES
};

}
