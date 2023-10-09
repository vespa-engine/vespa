// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "expression_replacer.h"

namespace search::features::rankingexpression {

ExpressionReplacer::~ExpressionReplacer()
{
}

//-----------------------------------------------------------------------------

IntrinsicExpression::UP
NullExpressionReplacer::maybe_replace(const vespalib::eval::Function &,
                                      const search::fef::IIndexEnvironment &) const
{
    return IntrinsicExpression::UP(nullptr);
}

NullExpressionReplacer::~NullExpressionReplacer()
{
}

//-----------------------------------------------------------------------------

ListExpressionReplacer::ListExpressionReplacer() = default;

void
ListExpressionReplacer::add(ExpressionReplacer::UP replacer)
{
    _list.push_back(std::move(replacer));
}

IntrinsicExpression::UP
ListExpressionReplacer::maybe_replace(const vespalib::eval::Function &function,
                                      const search::fef::IIndexEnvironment &env) const
{
    for (const auto &item: _list) {
        if (auto result = item.get()->maybe_replace(function, env)) {
            return result;
        }
    }
    return IntrinsicExpression::UP(nullptr);
}

ListExpressionReplacer::~ListExpressionReplacer()
{
}

} // namespace search::features::rankingexpression
