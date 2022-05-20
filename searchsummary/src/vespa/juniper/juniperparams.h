// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vespa/fastlib/text/wordfolder.h>

class SummaryConfig;

class DocsumParams
{
public:
    enum {
        FALLBACK_NONE,
        FALLBACK_PREFIX
    };

    DocsumParams();

    DocsumParams& SetEnabled(bool en);
    bool Enabled() const;

    DocsumParams& SetLength(size_t length);
    size_t Length() const;

    DocsumParams& SetMinLength(size_t length);
    size_t MinLength() const;

    DocsumParams& SetMaxMatches(size_t matches);
    size_t MaxMatches() const;

    DocsumParams& SetSurroundMax(size_t length);
    size_t SurroundMax() const;

    DocsumParams& SetSpaceChars(const char* spacechars);
    const char* SpaceChars() const;

    DocsumParams& SetFallback(const char* fallback);
    int Fallback() const;

private:
    bool _enabled;
    size_t _length;
    size_t _min_length;
    size_t _max_matches;
    size_t _surround_max;
    std::string _space_chars;
    int _fallback;
};


class MatcherParams
{
public:
    MatcherParams();

    MatcherParams& SetPrefixExtendLength(size_t extend_length);
    size_t PrefixExtendLength() const;

    MatcherParams& SetPrefixMinLength(size_t min_length);
    size_t PrefixMinLength() const;

    MatcherParams& SetMatchWindowSize(size_t winsize);
    size_t MatchWindowSize() const;

    MatcherParams& SetMatchWindowSizeFallbackMultiplier(double winsize);
    double MatchWindowSizeFallbackMultiplier() const;

    MatcherParams& SetMaxMatchCandidates(size_t max_match_candidates);
    size_t MaxMatchCandidates() const;

    MatcherParams& SetWantGlobalRank(bool global_rank);
    bool WantGlobalRank() const;

    MatcherParams& SetStemMinLength(size_t stem_min);
    size_t StemMinLength() const;

    MatcherParams& SetStemMaxExtend(size_t stem_extend);
    size_t StemMaxExtend() const;

    MatcherParams& SetWordFolder(Fast_WordFolder* wordfolder);
    Fast_WordFolder* WordFolder();

    MatcherParams& SetProximityFactor(double factor);
    double ProximityFactor();

private:
    size_t _prefix_extend_length;
    size_t _prefix_min_length;
    size_t _match_winsize;
    double _match_winsize_fallback_multiplier;
    size_t _max_match_candidates;
    bool _want_global_rank;
    size_t _stem_min;
    size_t _stem_extend;
    Fast_WordFolder* _wordfolder; // The wordfolder object needed as 1st parameter to folderfun
    double _proximity_factor;

    MatcherParams(MatcherParams &);
    MatcherParams &operator=(MatcherParams &);
};


bool operator==(MatcherParams& mp1, MatcherParams& mp2);

