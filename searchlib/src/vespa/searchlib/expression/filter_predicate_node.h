// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/identifiable.h>
#include <vespa/vespalib/objects/identifiable.hpp>

#include "expressiontree.h"

namespace search::expression {

class FilterPredicateNode : public vespalib::Identifiable
{
public:
    DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, expression, FilterPredicateNode);
    virtual FilterPredicateNode * clone() const = 0;
    //virtual bool valid() const = 0;
    virtual bool allow(DocId docId, HitRank rank) = 0;
    bool allow(const document::Document &, HitRank) { return true; }
};

class TruePredicateNode : public FilterPredicateNode {
public:
    // DECLARE_IDENTIFIABLE_NS2(search, expression, TruePredicateNode);
    TruePredicateNode * clone() const override { return new TruePredicateNode(); }
    //bool valid() const override { return true; }
    bool allow(DocId, HitRank) override { return true; }
    static TruePredicateNode instance;
};

}
