// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/tree/queryvisitor.h>

namespace search {
namespace query {

template <typename T, typename Base>
struct QueryNodeMixin : Base {
    typedef QueryNodeMixin<T, Base> QueryNodeMixinType;

    virtual ~QueryNodeMixin() = 0;
    virtual void accept(QueryVisitor &visitor) {
        visitor.visit(static_cast<T &>(*this));
    }

protected:
    using Base::Base;
};

template <typename T, typename Base>
QueryNodeMixin<T, Base>::~QueryNodeMixin() {}

}  // namespace query
}  // namespace search

