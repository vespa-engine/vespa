// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryvisitor.h"

namespace search {
namespace query {

template <typename T, typename Base>
struct QueryNodeMixin : Base {
    typedef QueryNodeMixin<T, Base> QueryNodeMixinType;

    ~QueryNodeMixin() = 0;
    void accept(QueryVisitor &visitor) override {
        visitor.visit(static_cast<T &>(*this));
    }

protected:
    using Base::Base;
};

template <typename T, typename Base>
QueryNodeMixin<T, Base>::~QueryNodeMixin() {}

}  // namespace query
}  // namespace search

