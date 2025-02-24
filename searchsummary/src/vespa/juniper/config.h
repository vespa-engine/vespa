// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dpinterface.h"
#include "juniperparams.h"

class IJuniperProperties;

namespace juniper {

class IReducer;
class IExpander;
class Juniper;

class Config {
public:
    Config(const char* config_name, const Juniper& juniper);
    ~Config();
    const char* GetProp(const char* name, const char* def);

    DocsumParams   _docsumparams;
    MatcherParams  _matcherparams;
    SummaryConfig* _sumconf;

private:
    std::string    _config_name;
    const Juniper& _juniper;

    Config(Config&);
    Config& operator=(Config&);
};

} // end namespace juniper
