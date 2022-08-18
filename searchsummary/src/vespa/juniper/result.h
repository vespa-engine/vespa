// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "queryhandle.h"
#include "tokenizer.h"
#include "juniperdebug.h"
#include <memory>

namespace juniper
{

#define PROXIMITYBOOST_NOCONSTRAINT_OFFSET 2

class Result
{
public:
    Result(const Config& config, QueryHandle& qhandle,
	   const char* docsum, size_t docsum_len, uint32_t langid);
    ~Result();

    inline void Scan()
    {
        if (!_scan_done)
        {
            _tokenizer->SetText(_docsum, _docsum_len);
            _tokenizer->scan();
            _scan_done = true;
        }
    }

    long GetRelevancy();
    size_t StemMin()  const { return _stem_min; }
    size_t StemExt()  const { return _stem_extend; }
    size_t WinSize()  const { return _winsize; }
    double WinSizeFallbackMultiplier() const { return _winsize_fallback_multiplier; }
    size_t MaxMatchCandidates()  const { return _max_match_candidates; }
    Summary* GetTeaser(const Config* alt_config);
    Summary* GetLog();

    QueryHandle* _qhandle;
    MatchObject* _mo;
    const char* _docsum;
    size_t _docsum_len;
    uint32_t _langid;
    const Config* _config;
    std::unique_ptr<Matcher> _matcher;
    std::unique_ptr<SpecialTokenRegistry> _registry;
    std::unique_ptr<JuniperTokenizer> _tokenizer;
private:
    std::vector<std::unique_ptr<Summary>> _summaries; // Active summaries for this result
    bool _scan_done;  // State of the result - is text scan done?

    /* Option storage */
    int _dynsum_len;  // Dynamic summary length
    int _max_matches; // Number of matches in summary
    int _surround_max; // Max surrounding characters of a keyword in a teaser
    size_t _stem_min; // Min.size of word to apply "fuzzy" matching
    // The max number of characters to allow a word to contain in addition to the
    // search keyword prefix for it to match (set this to 0 to disable stemming!)
    // default 3
    size_t _stem_extend;
    size_t _winsize;  // Window size to use when matching
    double _winsize_fallback_multiplier;
    size_t _max_match_candidates;

    Result(Result &);
    Result &operator=(Result &);
};

}  // end namespace juniper
