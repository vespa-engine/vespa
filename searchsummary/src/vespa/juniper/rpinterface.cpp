// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpinterface.h"
#include "config.h"
#include "juniperparams.h"
#include "propreader.h"
#include "queryhandle.h"
#include "queryvisitor.h"
#include "result.h"
#include <cassert>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP(".juniper.rpinterface");

/* Implementation of the interface between Juniper and the content/query provider */

namespace juniper {

bool AnalyseCompatible(Config* conf1, Config* conf2) {
    return conf1 == conf2 || (conf1 && conf2 && conf1->_matcherparams == conf2->_matcherparams &&
                              conf1->_docsumparams.Length() == conf2->_docsumparams.Length());
}

void SetDebug(unsigned int mask) {
    // Make sure we do not get 200 of these warnings per query..
    static bool warning_printed = false;
    if (mask && !warning_printed) {
        LOG(warning, "Juniper debug mode requested in binary compiled without debug support!");
        warning_printed = true;
    }
}

Juniper::Juniper(IJuniperProperties* props, const Fast_WordFolder* wordfolder, int api_version)
  : _props(props),
    _wordfolder(wordfolder) {
    if (api_version != JUNIPER_RP_ABI_VERSION) {
        // This can happen if fsearch and juniper is not compiled with the same version of the
        // Juniper API header files.
        LOG(error,
            "FATAL: "
            "juniper::Init: incompatible ABI versions between Juniper(%d) and caller (%d)!",
            JUNIPER_RP_ABI_VERSION, api_version);
    }

    assert(props);
    assert(wordfolder);

    LOG(debug, "Juniper result processor (interface v.%d)", JUNIPER_RP_ABI_VERSION);

    unsigned int debug_mask = strtol(_props->GetProperty("juniper.debug_mask", "0"), nullptr, 0);
    if (debug_mask) SetDebug(debug_mask);
}

Juniper::~Juniper() {}

std::unique_ptr<Config> Juniper::CreateConfig(const char* config_name) const {
    return std::unique_ptr<Config>(new Config(config_name, *this));
}

std::unique_ptr<QueryHandle> Juniper::CreateQueryHandle(const IQuery& fquery, const char* juniperoptions) const {
    return std::make_unique<QueryHandle>(fquery, juniperoptions);
}

std::unique_ptr<Result> Analyse(const Config& config, QueryHandle& qhandle, const char* docsum, size_t docsum_len,
                                uint32_t docid) {
    LOG(debug, "juniper::Analyse(): docId(%u), docsumLen(%zu), docsum(%s)", docid, docsum_len, docsum);
    return std::make_unique<Result>(config, qhandle, docsum, docsum_len);
}

long GetRelevancy(Result& result_handle) {
    return result_handle.GetRelevancy();
}

Summary* GetTeaser(Result& result_handle, const Config* alt_config) {
    return result_handle.GetTeaser(alt_config);
}

Summary* GetLog(Result& result_handle) {
    return result_handle.GetLog();
}

} // end namespace juniper
