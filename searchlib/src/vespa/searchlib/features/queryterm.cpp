// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryterm.h"
#include "utils.h"

using namespace search::fef;
using search::feature_t;

namespace search::features {

QueryTerm::QueryTerm() :
    _termData(nullptr),
    _handle(IllegalHandle),
    _significance(0),
    _connectedness(0)
{
}

QueryTerm::QueryTerm(const ITermData * td, feature_t sig, feature_t con) :
    _termData(td),
    _handle(IllegalHandle),
    _significance(sig),
    _connectedness(con)
{
}

QueryTerm
QueryTermFactory::create(const IQueryEnvironment & env,
                         uint32_t termIdx,
                         bool lookupSignificance,
                         bool lookupConnectedness)
{
    const ITermData *termData = env.getTerm(termIdx);
    feature_t significance = 0;
    if (lookupSignificance) {
        feature_t fallback = util::getSignificance(*termData);
        significance = util::lookupSignificance(env, termIdx, fallback);
    }
    feature_t connectedness = 0;
    if (lookupConnectedness) {
        connectedness = search::features::util::lookupConnectedness(env, termIdx);
    }
    return QueryTerm(termData, significance, connectedness);
}


}
