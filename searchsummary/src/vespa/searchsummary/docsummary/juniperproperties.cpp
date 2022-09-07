// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperproperties.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchsummary/config/config-juniperrc.h>

using vespalib::make_string;

namespace search::docsummary {

JuniperProperties::JuniperProperties() :
    _properties()
{
    reset();
}

JuniperProperties::JuniperProperties(const JuniperrcConfig &cfg) :
     _properties()
{
    reset();
    configure(cfg);
}

JuniperProperties::~JuniperProperties() = default;

void
JuniperProperties::reset()
{
    _properties.clear();
    //_properties["juniper.debug_mask"]                         = "0";
    //_properties["juniper.dynsum.connectors"]                  = "\x1F\x1D";
    _properties["juniper.dynsum.continuation"]                  = "\x1E";
    _properties["juniper.dynsum.escape_markup"]                 = "off";
    _properties["juniper.dynsum.fallback"]                      = "prefix";
    _properties["juniper.dynsum.highlight_off"]                 = "\x1F";
    _properties["juniper.dynsum.highlight_on"]                  = "\x1F";
    _properties["juniper.dynsum.preserve_white_space"]          = "on";
    //_properties["juniper.dynsum.length"]                      = "256";
    //_properties["juniper.dynsum.max_matches"]                 = "3";
    //_properties["juniper.dynsum.min_length"]                  = "128";
    //_properties["juniper.dynsum.separators"]                  = "\x1F\x1D";
    //_properties["juniper.dynsum.surround_max"]                = "128";
    _properties["juniper.matcher.winsize"]                      = "200";
    _properties["juniper.matcher.winsize_fallback_multiplier"]  = "10.0";
    _properties["juniper.matcher.max_match_candidates"]         = "1000";
    //_properties["juniper.proximity.factor"]                   = "0.25";
    //_properties["juniper.stem.max_extend"]                    = "3";
    //_properties["juniper.stem.min_length"]                    = "5";
}

void
JuniperProperties::configure(const JuniperrcConfig &cfg)
{
    reset();
    _properties["juniper.dynsum.fallback"]      = cfg.prefix ? "prefix" : "none";
    _properties["juniper.dynsum.length"]        = make_string("%d", cfg.length);
    _properties["juniper.dynsum.max_matches"]   = make_string("%d", cfg.maxMatches);
    _properties["juniper.dynsum.min_length"]    = make_string("%d", cfg.minLength);
    _properties["juniper.dynsum.surround_max"]  = make_string("%d", cfg.surroundMax);
    _properties["juniper.matcher.winsize"]  = make_string("%d", cfg.winsize);
    _properties["juniper.matcher.winsize_fallback_multiplier"]  = make_string("%f", cfg.winsizeFallbackMultiplier);
    _properties["juniper.matcher.max_match_candidates"]  = make_string("%d", cfg.maxMatchCandidates);
    _properties["juniper.stem.min_length"]  = make_string("%d", cfg.stemMinLength);
    _properties["juniper.stem.max_extend"]  = make_string("%d", cfg.stemMaxExtend);

    for (const auto & override : cfg.override) {
        const vespalib::string keyDynsum = make_string("%s.dynsum.", override.fieldname.c_str());
        const vespalib::string keyMatcher = make_string("%s.matcher.", override.fieldname.c_str());
        const vespalib::string keyStem = make_string("%s.stem.", override.fieldname.c_str());

        _properties[keyDynsum + "fallback"]           = override.prefix ? "prefix" : "none";
        _properties[keyDynsum + "length"]             = make_string("%d", override.length);
        _properties[keyDynsum + "max_matches"]        = make_string("%d", override.maxMatches);
        _properties[keyDynsum + "min_length"]         = make_string("%d", override.minLength);
        _properties[keyDynsum + "surround_max"]       = make_string("%d", override.surroundMax);

        _properties[keyMatcher + "winsize"]                     = make_string("%d", override.winsize);
        _properties[keyMatcher + "winsize_fallback_multiplier"] = make_string("%f", override.winsizeFallbackMultiplier);
        _properties[keyMatcher + "max_match_candidates"] = make_string("%d", override.maxMatchCandidates);

        _properties[keyStem + "min_length"] = make_string("%d", override.stemMinLength);
        _properties[keyStem + "max_extend"] = make_string("%d", override.stemMaxExtend);
    }
}

const char *
JuniperProperties::GetProperty(const char *name, const char *def) const
{
    auto it = _properties.find(name);
    return it != _properties.end() ? it->second.c_str() : def;
}

}
