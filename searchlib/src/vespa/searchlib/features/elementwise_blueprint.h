// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

#include <map>

namespace search::features {

/**
 * Blueprint for the elementwise ranking feature. It manages an inner blueprint that creates tensor values as output.
 *
 * Example usage: elementwise(bm25(i),x,float) will calculate bm25 feature per element in the field 'i', creating
 * a tensor with a single mapped dimension 'x' that contains an elementwise aggregated float bm25 score for each term
 * matching the field. The dimension and cell type are passed as extra parameters to the inner blueprint and calls to
 * prepareSharedState() and createExecutor() are proxied to the inner blueprint.
 *
 * Inner feature name and dimension name are mandatory arguments. Cell type is optional, and the default depends
 * on the inner feature (see below). The resolved cell type is always passed explicitly to the inner blueprint,
 * e.g. both elementwise(bm25(i),x) and elementwise(bm25(i),x,double) pass (i,x,double) to the inner elementwise
 * bm25 blueprint. Rank property keys used for tuning must therefore always contain the cell type name.
 *
 * Supported inner features:
 *   - bm25: elementwise bm25 score per element of index field i, e.g. elementwise(bm25(i),x).
 *     Default cell type is 'double'.
 *   - matches: 1.0 for each element id matched by sameElement in an array of struct or map field f,
 *     e.g. elementwise(matches(f),x). Default cell type is 'float', since the values are always 1.0.
 */
class ElementwiseBlueprint : public fef::Blueprint {
public:
    using NestedBlueprints = std::shared_ptr<std::map<std::string, std::shared_ptr<fef::Blueprint>>>;

private:
    std::unique_ptr<fef::Blueprint> _inner_blueprint;
    NestedBlueprints _nested_blueprints; // known blueprints that can be first argument to elementwise blueprint

    static NestedBlueprints make_default_nested_blueprints();

public:
    ElementwiseBlueprint();
    ElementwiseBlueprint(NestedBlueprints nested_blueprints);
    ~ElementwiseBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

} // namespace search::features
