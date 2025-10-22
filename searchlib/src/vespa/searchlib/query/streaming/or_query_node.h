// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_connector.h"

namespace search::streaming {

class QueryVisitor;

/**
   N-ary Or operator that simply ORs all the nodes together.
*/
class OrQueryNode : public QueryConnector
{
public:
    OrQueryNode() noexcept : QueryConnector("OR") { }
    explicit OrQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    ~OrQueryNode() override;
    bool evaluate() override;
    bool isFlattenable(ParseItem::ItemType type) const override {
        return (type == ParseItem::ITEM_OR) ||
               (type == ParseItem::ITEM_WEAK_AND);
    }
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void accept(QueryVisitor &visitor);
};

}
