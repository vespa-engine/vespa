// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config.h"
#include "rpinterface.h"
#include "juniperdebug.h"
#include "juniper_separators.h"
#define _NEED_SUMMARY_CONFIG_IMPL
#include "SummaryConfig.h"
#include <vespa/vespalib/locale/c.h>

namespace juniper
{

Config::Config(const char* config_name, const Juniper & juniper) :
    _docsumparams(),
    _matcherparams(),
    _sumconf(nullptr),
    _config_name(config_name),
    _juniper(juniper)
{
    std::string separators = "";
    separators += separators::unit_separator_string;
    separators += separators::group_separator_string;

    const char* high_on  = GetProp("dynsum.highlight_on", "<b>");
    const char* high_off = GetProp("dynsum.highlight_off", "</b>");
    const char* contsym  = GetProp("dynsum.continuation", "...");
    const char* fallback = GetProp("dynsum.fallback", "none");
    size_t summarylength = atoi(GetProp("dynsum.length", "256"));
    size_t sum_minlength = atoi(GetProp("dynsum.min_length", "128"));
    size_t stem_min      = atoi(GetProp("stem.min_length", "5"));
    size_t stem_extend   = atoi(GetProp("stem.max_extend", "3"));
    size_t surround_max  = atoi(GetProp("dynsum.surround_max", "128"));
    size_t max_matches   = atoi(GetProp("dynsum.max_matches", "3"));
    const char* escape_markup  = GetProp("dynsum.escape_markup", "auto");
    const char* preserve_white_space  = GetProp("dynsum.preserve_white_space", "off");
    size_t match_winsize = strtol(GetProp("matcher.winsize", "200"), NULL, 0);
    size_t max_match_candidates = atoi(GetProp("matcher.max_match_candidates", "1000"));
    const char* seps = GetProp("dynsum.separators", separators.c_str());
    const unsigned char* cons =
        reinterpret_cast<const unsigned char*>(GetProp("dynsum.connectors", separators.c_str()));
    double proximity_factor = vespalib::locale::c::strtod(GetProp("proximity.factor", "0.25"), NULL);
    // Silently convert to something sensible
    if (proximity_factor > 1E8 || proximity_factor < 0) proximity_factor = 0.25;

    _sumconf = CreateSummaryConfig(high_on, high_off, contsym, seps, cons,
				   StringToConfigFlag(escape_markup),
				   StringToConfigFlag(preserve_white_space));
    _docsumparams.SetEnabled(true)
        .SetLength(summarylength).SetMinLength(sum_minlength)
        .SetMaxMatches(max_matches)
        .SetSurroundMax(surround_max)
        .SetFallback(fallback);
    _matcherparams
        .SetStemMinLength(stem_min).SetStemMaxExtend(stem_extend)
        .SetMatchWindowSize(match_winsize)
        .SetMaxMatchCandidates(max_match_candidates)
        .SetWordFolder(& _juniper.getWordFolder())
        .SetProximityFactor(proximity_factor);
}

Config::~Config()
{
    DeleteSummaryConfig(_sumconf);
}

const char* Config::GetProp(const char* name, const char* def)
{
    std::string propstr = _config_name.c_str();
    propstr += '.';
    propstr.append(name);
    if (_config_name == "juniper") {
        return _juniper.getProp().GetProperty(propstr.c_str(), def);
    } else {
        const char* p = _juniper.getProp().GetProperty(propstr.c_str(), NULL);
        if (p == NULL) {
            propstr = "juniper.";
            propstr.append(name);
            p = _juniper.getProp().GetProperty(propstr.c_str(), def);
        }
        return p;
    }
}

} // end namespace juniper
