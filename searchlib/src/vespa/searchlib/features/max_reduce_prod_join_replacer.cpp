// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "max_reduce_prod_join_replacer.h"
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/searchlib/features/rankingexpression/intrinsic_blueprint_adapter.h>
#include <vespa/searchlib/fef/featurenameparser.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.max_reduce_prod_join_replacer");

namespace search::features {

using fef::Blueprint;
using fef::FeatureNameParser;
using fef::IIndexEnvironment;
using rankingexpression::ExpressionReplacer;
using rankingexpression::IntrinsicBlueprintAdapter;
using rankingexpression::IntrinsicExpression;
using vespalib::eval::Aggr;
using vespalib::eval::Function;
using vespalib::eval::nodes::Mul;
using vespalib::eval::nodes::Node;
using vespalib::eval::nodes::Symbol;
using vespalib::eval::nodes::TensorJoin;
using vespalib::eval::nodes::TensorReduce;
using vespalib::eval::nodes::as;

namespace {

bool match_params(const Node &a, const Node &b) {
    bool first = false;
    bool second = false;
    for (int i = 0; i < 2; ++i) {
        const Node &node = (i == 0) ? a : b;
        if (auto symbol = as<Symbol>(node)) {
            if (symbol->id() == 0) {
                first = true;
            } else if (symbol->id() == 1) {
                second = true;
            }
        }
    }
    return (first && second);
};

bool match_prod_join(const Node &node) {
    if (auto join = as<TensorJoin>(node)) {
        const Node &root = join->lambda().root();
        if (as<Mul>(root)) {
            return match_params(root.get_child(0), root.get_child(1));
        }
    }
    return false;
}

bool match_max_reduce(const Node &node, vespalib::string &reduce_dim) {
    auto reduce = as<TensorReduce>(node);
    if (!reduce || (reduce->aggr() != Aggr::MAX) || (reduce->dimensions().size() > 1)) {
        return false;
    }
    if (reduce->dimensions().size() == 1) {
        reduce_dim = reduce->dimensions()[0];
    }
    return true;
}

bool match_function(const Function &function, vespalib::string &reduce_dim) {
    const Node &expect_max = function.root();
    if ((function.num_params() == 2) && match_max_reduce(expect_max, reduce_dim)) {
        const Node &expect_mul = expect_max.get_child(0);
        if (as<Mul>(expect_mul) || match_prod_join(expect_mul)) {
            return match_params(expect_mul.get_child(0), expect_mul.get_child(1));
        }
    }
    return false;
}

void try_extract_param(const vespalib::string &feature, const vespalib::string &wanted_wrapper,
                       vespalib::string &param, vespalib::string &dim)
{
    FeatureNameParser parser(feature);
    if (parser.valid() &&
        (parser.parameters().size() >= 1) &&
        (parser.parameters().size() <= 2))
    {
        vespalib::string wrapper;
        vespalib::string body;
        vespalib::string error;
        if (Function::unwrap(parser.parameters()[0], wrapper, body, error) &&
            (wrapper == wanted_wrapper))
        {
            param = body;
            if (parser.parameters().size() == 2) {
                dim = parser.parameters()[1];
            } else {
                dim = param;
            }
        }
    }
}

struct MatchInputs {
    vespalib::string attribute;
    vespalib::string attribute_dim;
    vespalib::string query;
    vespalib::string query_dim;
    MatchInputs() : attribute(), attribute_dim(), query(), query_dim() {}
    void process(const vespalib::string &param) {
        if (starts_with(param, "tensorFromLabels")) {
            try_extract_param(param, "attribute", attribute, attribute_dim);
        } else if (starts_with(param, "tensorFromWeightedSet")) {
            try_extract_param(param, "query", query, query_dim);
        }
    }
    bool matched() const {
        return (!attribute.empty() && !query.empty() && (attribute_dim == query_dim));
    }
};

struct MaxReduceProdJoinReplacerImpl : ExpressionReplacer {
    Blueprint::UP proto;
    MaxReduceProdJoinReplacerImpl(Blueprint::UP proto_in)
        : proto(std::move(proto_in)) {}
    IntrinsicExpression::UP maybe_replace(const Function &function,
                                          const IIndexEnvironment &env) const override
    {
        vespalib::string reduce_dim;
        if (match_function(function, reduce_dim)) {
            MatchInputs match_inputs;
            match_inputs.process(function.param_name(0));
            match_inputs.process(function.param_name(1));
            if (match_inputs.matched() && (reduce_dim.empty() || (reduce_dim == match_inputs.attribute_dim))) {
                return IntrinsicBlueprintAdapter::try_create(*proto, env, {match_inputs.attribute, match_inputs.query});
            }
        }
        return IntrinsicExpression::UP(nullptr);
    }
};

} // namespace search::features::<unnamed>

ExpressionReplacer::UP
MaxReduceProdJoinReplacer::create(Blueprint::UP proto)
{
    return std::make_unique<MaxReduceProdJoinReplacerImpl>(std::move(proto));
}

} // namespace search::features
