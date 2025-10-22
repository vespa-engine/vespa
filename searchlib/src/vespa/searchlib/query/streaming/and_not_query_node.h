// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_connector.h"

namespace search::streaming {

class QueryVisitor;

/**
   N-ary special AndNot operator. n[0] & !n[1] & !n[2] .. & !n[j].
*/
class AndNotQueryNode : public QueryConnector
{
public:
    AndNotQueryNode() noexcept : QueryConnector("ANDNOT") { }
    ~AndNotQueryNode() override;
    bool evaluate() override;
    bool isFlattenable(ParseItem::ItemType) const override { return false; }
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void accept(QueryVisitor &visitor);
};

}
