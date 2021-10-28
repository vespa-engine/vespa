// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::fef {

/**
 * Details on which match data that should be available in a TermFieldMatchData instance.
 *
 * Normal:
 *   Full match data positions should be available. This is the default.
 *
 * Interleaved:
 *   Interleaved match data ('number of occurrences' and 'field length') should be available.
 */
enum class MatchDataDetails {
    Normal = 1,
    Interleaved = 2
};

}
