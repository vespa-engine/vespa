// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryvisitor.h"

namespace search::query {

template <typename T, typename Base>
struct QueryNodeMixin : Base {
    using QueryNodeMixinType = QueryNodeMixin<T, Base>;

    ~QueryNodeMixin() = 0;
    void accept(QueryVisitor &visitor) override {
        visitor.visit(static_cast<T &>(*this));
    }

protected:
    using Base::Base;
};

template <typename T, typename Base>
QueryNodeMixin<T, Base>::~QueryNodeMixin() = default;

}
