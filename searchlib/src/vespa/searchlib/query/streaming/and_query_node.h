// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_connector.h"

namespace search::streaming {

class QueryVisitor;

/**
   N-ary Or operator that simply ANDs all the nodes together.
*/
class AndQueryNode : public QueryConnector
{
public:
    AndQueryNode() noexcept : QueryConnector("AND") { }
    explicit AndQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    ~AndQueryNode() override;
    bool evaluate() override;
    bool isFlattenable(ParseItem::ItemType type) const override { return type == ParseItem::ITEM_AND; }
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void accept(QueryVisitor &visitor);
};

}
