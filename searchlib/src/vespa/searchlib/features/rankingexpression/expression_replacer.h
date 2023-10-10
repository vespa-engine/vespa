// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include "intrinsic_expression.h"

namespace vespalib::eval { class Function; }
namespace search::fef { class IIndexEnvironment; }

namespace search::features::rankingexpression {

/**
 * Interface used to replace the calculation of a ranking expression
 * (including calculating all its inputs) with a single intrinsic
 * operation directly producing the final result without exposing
 * intermediate results.
 **/
struct ExpressionReplacer {
    using UP = std::unique_ptr<ExpressionReplacer>;
    using SP = std::shared_ptr<ExpressionReplacer>;
    virtual IntrinsicExpression::UP maybe_replace(const vespalib::eval::Function &function,
                                                  const search::fef::IIndexEnvironment &env) const = 0;
    virtual ~ExpressionReplacer();
};

/**
 * Expression Replacer never replacing anything.
 **/
struct NullExpressionReplacer : public ExpressionReplacer {
    IntrinsicExpression::UP maybe_replace(const vespalib::eval::Function &function,
                                          const search::fef::IIndexEnvironment &env) const override;
    ~NullExpressionReplacer();
};

/**
 * Expression Replacer that keeps a list of expression replacers and
 * forwards the replace calls to each of them in order until the
 * expression has been replaced or all of them have been tried.
 **/
class ListExpressionReplacer : public ExpressionReplacer
{
private:
    std::vector<ExpressionReplacer::UP> _list;
public:
    ListExpressionReplacer();
    void add(ExpressionReplacer::UP replacer);
    IntrinsicExpression::UP maybe_replace(const vespalib::eval::Function &function,
                                          const search::fef::IIndexEnvironment &env) const override;
    ~ListExpressionReplacer();
};

} // namespace search::features::rankingexpression
