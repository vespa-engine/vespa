// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#define _NEED_SUMMARY_CONFIG_IMPL 1
#include "SummaryConfig.h"
#include "rpinterface.h"
#include "result.h"
#include "juniperparams.h"
#include "Matcher.h"
#include "config.h"
#include "appender.h"
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".juniper.result");

namespace juniper {

/** Actual implementation of Juniper generated summaries. */
class SummaryImpl : public Summary
{
public:
    explicit SummaryImpl() : _text() {}
    explicit SummaryImpl(const std::string& t) : _text(t) {}
    ~SummaryImpl() override = default;
    const char* Text() const override { return _text.c_str(); }
    size_t Length() const override { return _text.size(); }
    std::string _text;
};


Result::Result(const Config& config, QueryHandle& qhandle,
               const char* docsum, size_t docsum_len, uint32_t langid) :
    _qhandle(&qhandle),
    _mo(qhandle.MatchObj(langid)),
    _docsum(docsum),
    _docsum_len(docsum_len),
    _langid(langid),
    _config(&config),
    _matcher(),
    _tokenizer(),
    _summaries(),
    _scan_done(false),
    _dynsum_len(-1),
    _max_matches(-1),
    _surround_max(-1),
    _stem_min(0),
    _stem_extend(0),
    _winsize(0),
    _winsize_fallback_multiplier(10.0),
    _max_match_candidates(1000)
{
    if (!_mo) return; // The empty result..

    const MatcherParams& mp = _config->_matcherparams;
    const Fast_WordFolder* wordfolder = mp.WordFolder();

    if (_qhandle->_stem_min < 0)
        _stem_min = mp.StemMinLength();
    else
        _stem_min = _qhandle->_stem_min;

    if (_qhandle->_stem_extend < 0)
        _stem_extend = mp.StemMaxExtend();
    else
        _stem_extend = _qhandle->_stem_extend;

    if (_qhandle->_winsize < 0)
        _winsize = mp.MatchWindowSize();
    else
        _winsize = _qhandle->_winsize;

    if (_qhandle->_winsize_fallback_multiplier < 0)
        _winsize_fallback_multiplier = mp.MatchWindowSizeFallbackMultiplier();
    else
        _winsize_fallback_multiplier = _qhandle->_winsize_fallback_multiplier;

    if (_qhandle->_max_match_candidates < 0) {
        _max_match_candidates = mp.MaxMatchCandidates();
    } else {
        _max_match_candidates = _qhandle->_max_match_candidates;
    }

    /* Create the new pipeline */
    _tokenizer = std::make_unique<JuniperTokenizer>(wordfolder, nullptr, 0, nullptr, nullptr);

    _matcher = std::make_unique<Matcher>(this);
    _matcher->SetProximityFactor(mp.ProximityFactor());

    _registry = std::make_unique<SpecialTokenRegistry>(_matcher->getQuery());

    if (qhandle._log_mask)
        _matcher->set_log(qhandle._log_mask);

    _tokenizer->SetSuccessor(_matcher.get());
    if (!_registry->getSpecialTokens().empty()) {
        _tokenizer->setRegistry(_registry.get());
    }
}

Result::~Result() = default;


long Result::GetRelevancy()
{
    if (!_mo) return PROXIMITYBOOST_NOCONSTRAINT_OFFSET;
    if (!_mo->Query()) return PROXIMITYBOOST_NOCONSTRAINT_OFFSET;
    Scan();
    long retval = _matcher->GlobalRank();
    LOG(debug, "juniper::GetRelevancy(%lu)", retval);
    return retval;
}

Summary* Result::GetTeaser(const Config* alt_config)
{
    LOG(debug, "juniper::GetTeaser");
    const Config* cfg = (alt_config ? alt_config : _config);
    const DocsumParams& dsp = cfg->_docsumparams;
    if (_qhandle->_dynsum_len < 0)
        _dynsum_len = dsp.Length();
    else
        _dynsum_len = _qhandle->_dynsum_len;
    std::unique_ptr<SummaryImpl> sum;
    // Avoid overhead when being called with an empty stack
    if (_mo && _mo->Query()) {
        Scan();
        if (_qhandle->_max_matches < 0)
            _max_matches = dsp.MaxMatches();
        else
            _max_matches = _qhandle->_max_matches;
        if (_qhandle->_surround_max < 0)
            _surround_max = dsp.SurroundMax();
        else
            _surround_max = _qhandle->_surround_max;

        SummaryDesc* sdesc =
            _matcher->CreateSummaryDesc(_dynsum_len, dsp.MinLength(), _max_matches, _surround_max);

        if (sdesc) {
            size_t char_size;
            sum = std::make_unique<SummaryImpl>(BuildSummary(_docsum, _docsum_len, sdesc, cfg->_sumconf, char_size));
            DeleteSummaryDesc(sdesc);
        }
    }

    if (!sum) {
        sum = std::make_unique<SummaryImpl>();
    }

    if (sum->_text.empty() && dsp.Fallback() == DocsumParams::FALLBACK_PREFIX)
    {
        std::vector<char> text;
        Appender         a(cfg->_sumconf);
        ucs4_t           buf[TOKEN_DSTLEN];
        const char      *src      = _docsum;
        const char      *src_end  = _docsum + _docsum_len;
        ucs4_t          *dst      = buf;
        ucs4_t          *dst_end  = dst + TOKEN_DSTLEN;
        const Fast_WordFolder *folder   = _config->_matcherparams.WordFolder();

        text.reserve(std::min(4_Ki, size_t(_dynsum_len*2)));
        if (src_end - src <= _dynsum_len) {
            a.append(text, src, src_end - src);
            src = src_end; // ensure while loop not run
        }
        while (src < src_end) {
            const char *startpos;
            size_t tokenLen;
            const char *old_src = src;
            size_t old_sum_len = text.size();
            src = folder->UCS4Tokenize(src, src_end, dst, dst_end,
                                       startpos, tokenLen);
            if (dst[0] == 0) {
                a.append(text, old_src, src_end - old_src);
                src = src_end; // ensure loop termination
            } else {
                a.append(text, old_src, src - old_src);
            }
            if (text.size() > (size_t) _dynsum_len) {
                text.resize(old_sum_len);
                text.insert(text.end(), cfg->_sumconf->dots().begin(), cfg->_sumconf->dots().end());
                break;
            }
        }
        sum->_text = std::string(&text[0], text.size());
    }
    _summaries.emplace_back(std::move(sum));
    return _summaries.back().get();
}


Summary* Result::GetLog()
{
    // Avoid overhead when being called with an empty stack
    std::unique_ptr<Summary> sum;
    if (_mo && _mo->Query())
    {
        LOG(debug, "juniper::GetLog");
        Scan();
        sum = std::make_unique<SummaryImpl>(_matcher->GetLog());
    }
    else {
        sum = std::make_unique<SummaryImpl>();
    }
    _summaries.emplace_back(std::move(sum));
    return _summaries.back().get();
}


}  // end namespace juniper
