// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryterm.h"
#include "utils.h"

using search::feature_t;
using namespace search::fef;

namespace search::features {

QueryTerm
QueryTermFactory::create(const IQueryEnvironment & env, uint32_t termIdx, bool lookupConnectedness)
{
    const ITermData *termData = env.getTerm(termIdx);
    feature_t fallback = util::getSignificance(*termData);
    feature_t significance = features::util::lookupSignificance(env, termIdx, fallback);
    feature_t connectedness = 0;
    if (lookupConnectedness) {
        connectedness = util::lookupConnectedness(env, termIdx);
    }
    return QueryTerm(termData, significance, connectedness);
}

QueryTermHelper::QueryTermHelper(const IQueryEnvironment &env)
    : _fallBack(),
      _queryTerms(lookupQueryTerms(env))
{
    if (_queryTerms == nullptr) {
        _fallBack = createQueryTermvector(env);
        _queryTerms = & _fallBack;
    }
}

namespace {

using QueryTermVectorWrapper = AnyWrapper<QueryTermVector>;
const vespalib::string QUERY_TERMS_KEY("querytermhelper.queryterms");

}
const QueryTermVector &
QueryTermHelper::lookupAndStoreQueryTerms(const IQueryEnvironment &env, IObjectStore & store)
{
    const Anything * obj = store.get(QUERY_TERMS_KEY);
    if (obj == nullptr) {
        store.add(QUERY_TERMS_KEY, std::make_unique<QueryTermVectorWrapper>(createQueryTermvector(env)));
        obj = store.get(QUERY_TERMS_KEY);
    }
    return static_cast<const QueryTermVectorWrapper *>(obj)->getValue();
}

const QueryTermVector *
QueryTermHelper::lookupQueryTerms(const IQueryEnvironment & env)
{
    const Anything * obj = env.getObjectStore().get(QUERY_TERMS_KEY);
    return (obj != nullptr) ? & QueryTermVectorWrapper::getValue(*obj) : nullptr;
}

QueryTermVector
QueryTermHelper::createQueryTermvector(const IQueryEnvironment &env) {
    QueryTermVector vector;
    vector.reserve(env.getNumTerms());
    for (size_t i(0); i < env.getNumTerms(); i++) {
        vector.push_back(QueryTermFactory::create(env, i));
    }
    return vector;
}

}
