// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_connector.h"

namespace search::streaming {

class QueryVisitor;

/**
   N-ary RankWith operator
*/
class RankWithQueryNode : public QueryConnector
{
public:
    RankWithQueryNode() noexcept : QueryConnector("RANK") { }
    explicit RankWithQueryNode(const char * opName) noexcept : QueryConnector(opName) { }
    ~RankWithQueryNode() override;
    bool evaluate() override;
    void get_element_ids(std::vector<uint32_t>& element_ids) override;
    void accept(QueryVisitor &visitor);
};

}
