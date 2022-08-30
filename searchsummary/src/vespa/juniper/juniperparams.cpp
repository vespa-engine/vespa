// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "juniperparams.h"
#include <cstring>

// DocsumParams implementation:
// ---------------------------------------------------------------

DocsumParams::DocsumParams() :
    _enabled(false), _length(256), _min_length(128), _max_matches(3),
    _surround_max(80), _fallback(FALLBACK_NONE)
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
int DocsumParams::Fallback() const { return _fallback; }

// MatcherParams implementation:
// ---------------------------------------------------------------


MatcherParams::MatcherParams() :
    _match_winsize(200),
    _match_winsize_fallback_multiplier(10.0),
    _max_match_candidates(1000),
    _stem_min(0), _stem_extend(0),
    _wordfolder(NULL), _proximity_factor(1.0)
{ }


MatcherParams& MatcherParams::SetMatchWindowSize(size_t winsize)
{
    _match_winsize = winsize;
    return *this;
}

MatcherParams& MatcherParams::SetMaxMatchCandidates(size_t max_match_candidates)
{
    _max_match_candidates = max_match_candidates;
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

size_t MatcherParams::MatchWindowSize() const { return _match_winsize; }
double MatcherParams::MatchWindowSizeFallbackMultiplier() const { return _match_winsize_fallback_multiplier; }
size_t MatcherParams::MaxMatchCandidates() const { return _max_match_candidates; }
size_t MatcherParams::StemMinLength() const { return _stem_min; }
size_t MatcherParams::StemMaxExtend() const { return _stem_extend; }


MatcherParams& MatcherParams::SetWordFolder(const Fast_WordFolder* wordfolder)
{
    _wordfolder = wordfolder;
    return *this;
}

MatcherParams& MatcherParams::SetProximityFactor(double factor)
{
    _proximity_factor = factor;
    return *this;
}

bool operator==(MatcherParams& mp1, MatcherParams& mp2)
{
    return memcmp(&mp1, &mp2, sizeof(MatcherParams)) == 0;
}
