// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Blueprint for the per-term document frequency of the query terms searching a given index field,
 * exposed as a mapped tensor(term{}) with one cell per query term. Labels are query-term indexes.
 * Cell values are the document frequency BM25 would use: a query-provided override
 * (documentFrequency annotation / significance model) if present, otherwise the local index statistic.
 */
class QueryTermDocumentFrequencyBlueprint : public fef::Blueprint {
    uint32_t                  _field_id;
    vespalib::eval::ValueType _value_type;

public:
    QueryTermDocumentFrequencyBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
    }
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
};

} // namespace search::features
