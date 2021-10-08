// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "juniperdebug.h"
#include "juniperparams.h"
#include "Matcher.h"
#include <cstring>

// DocsumParams implementation:
// ---------------------------------------------------------------

DocsumParams::DocsumParams() :
    _enabled(false), _length(256), _min_length(128), _max_matches(3),
    _surround_max(80), _space_chars(""), _fallback(FALLBACK_NONE)
{ }

DocsumParams& DocsumParams::SetEnabled(bool en)
{
    _enabled = en;
    return *this;
}

DocsumParams& DocsumParams::SetLength(size_t length)
{
    _length = length;
    return *this;
}

DocsumParams& DocsumParams::SetMinLength(size_t length)
{
    _min_length = length;
    return *this;
}

DocsumParams& DocsumParams::SetMaxMatches(size_t matches)
{
    _max_matches = matches;
    return *this;
}

DocsumParams& DocsumParams::SetSurroundMax(size_t length)
{
    _surround_max = length;
    return *this;
}

DocsumParams& DocsumParams::SetSpaceChars(const char* spacechars)
{
    _space_chars = spacechars;
    return *this;
}

DocsumParams& DocsumParams::SetFallback(const char* fallback)
{
    if (strcmp("prefix", fallback) == 0) {
        _fallback = FALLBACK_PREFIX;
    } else {
        _fallback = FALLBACK_NONE;
    }
    return *this;
}

size_t DocsumParams::Length() const      { return _length; }
size_t DocsumParams::MinLength() const   { return _min_length; }
size_t DocsumParams::MaxMatches() const  { return _max_matches; }
size_t DocsumParams::SurroundMax() const { return _surround_max; }
bool   DocsumParams::Enabled() const     { return _enabled; }
const char* DocsumParams::SpaceChars() const { return _space_chars.c_str(); }
int DocsumParams::Fallback() const { return _fallback; }

// MatcherParams implementation:
// ---------------------------------------------------------------


MatcherParams::MatcherParams() :
    _prefix_extend_length(3),
    _prefix_min_length(5),
    _match_winsize(200),
    _match_winsize_fallback_multiplier(10.0),
    _max_match_candidates(1000),
    _want_global_rank(false),
    _stem_min(0), _stem_extend(0),
    _wordfolder(NULL), _proximity_factor(1.0)
{ }


MatcherParams& MatcherParams::SetPrefixExtendLength(size_t extend_length)
{
    _prefix_extend_length = extend_length;
    return *this;
}

MatcherParams& MatcherParams::SetPrefixMinLength(size_t min_length)
{
    _prefix_min_length = min_length;
    return *this;
}


MatcherParams& MatcherParams::SetMatchWindowSize(size_t winsize)
{
    _match_winsize = winsize;
    return *this;
}

MatcherParams& MatcherParams::SetMatchWindowSizeFallbackMultiplier(double winsize)
{
    _match_winsize_fallback_multiplier = winsize;
    return *this;
}

MatcherParams& MatcherParams::SetMaxMatchCandidates(size_t max_match_candidates)
{
    _max_match_candidates = max_match_candidates;
    return *this;
}

MatcherParams& MatcherParams::SetWantGlobalRank(bool global_rank)
{
    _want_global_rank = global_rank;
    return *this;
}

MatcherParams& MatcherParams::SetStemMinLength(size_t stem_min)
{
    _stem_min = stem_min;
    return *this;
}


MatcherParams& MatcherParams::SetStemMaxExtend(size_t stem_extend)
{
    _stem_extend = stem_extend;
    return *this;
}

size_t MatcherParams::PrefixExtendLength() const { return _prefix_extend_length; }
size_t MatcherParams::PrefixMinLength() const { return _prefix_min_length; }
size_t MatcherParams::MatchWindowSize() const { return _match_winsize; }
double MatcherParams::MatchWindowSizeFallbackMultiplier() const { return _match_winsize_fallback_multiplier; }
size_t MatcherParams::MaxMatchCandidates() const { return _max_match_candidates; }
bool   MatcherParams::WantGlobalRank() const { return _want_global_rank; }
size_t MatcherParams::StemMinLength() const { return _stem_min; }
size_t MatcherParams::StemMaxExtend() const { return _stem_extend; }


MatcherParams& MatcherParams::SetWordFolder(Fast_WordFolder* wordfolder)
{
    _wordfolder = wordfolder;
    return *this;
}

Fast_WordFolder* MatcherParams::WordFolder() { return _wordfolder; }


MatcherParams& MatcherParams::SetProximityFactor(double factor)
{
    _proximity_factor = factor;
    return *this;
}

double MatcherParams::ProximityFactor() { return _proximity_factor; }


bool operator==(MatcherParams& mp1, MatcherParams& mp2)
{
    return memcmp(&mp1, &mp2, sizeof(MatcherParams)) == 0;
}
