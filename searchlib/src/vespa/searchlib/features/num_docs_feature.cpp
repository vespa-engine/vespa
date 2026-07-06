// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "num_docs_feature.h"

#include "valuefeature.h"

#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/vespalib/util/stash.h>

using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::IDumpFeatureVisitor;
using search::fef::IIndexEnvironment;
using search::fef::IQueryEnvironment;
using search::fef::ParameterList;

namespace search::features {

NumDocsBlueprint::NumDocsBlueprint() : Blueprint("numDocs") {
}

void NumDocsBlueprint::visitDumpFeatures(const IIndexEnvironment&, IDumpFeatureVisitor&) const {
}

bool NumDocsBlueprint::setup(const IIndexEnvironment&, const ParameterList&) {
    describeOutput("out",
                   "The local document count used as BM25's total document count when no per-term document-frequency "
                   "override is present.");
    return true;
}

Blueprint::UP NumDocsBlueprint::createInstance() const {
    return std::make_unique<NumDocsBlueprint>();
}

FeatureExecutor& NumDocsBlueprint::createExecutor(const IQueryEnvironment& env, vespalib::Stash& stash) const {
    return stash.create<SingleValueExecutor>(env.get_num_docs());
}

} // namespace search::features
