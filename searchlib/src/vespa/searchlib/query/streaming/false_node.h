// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_connector.h"

namespace search::streaming {

class QueryVisitor;

/** False operator. Matches nothing. */
class FalseNode : public QueryConnector
{
public:
    FalseNode() noexcept : QueryConnector("AND") { }
    ~FalseNode() override;
    bool evaluate() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void accept(QueryVisitor &visitor);
};

}
