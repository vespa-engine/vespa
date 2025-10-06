// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/query_normalization.h>
#include <memory>
#include <optional>

namespace search { class QueryStackIterator; }

namespace search::streaming {

class MultiTerm;
class QueryNode;
class QueryNodeResultFactory;

/*
 * Class used to build a query from a query stack.
 */
class QueryBuilder {
    std::unique_ptr<QueryNode> build_nearest_neighbor_query_node(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep);
    void populate_multi_term(Normalizing string_normalize_mode, MultiTerm& mt, QueryStackIterator& queryRep);
    std::unique_ptr<QueryNode> build_dot_product_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep);
    std::unique_ptr<QueryNode> build_wand_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep);
    std::unique_ptr<QueryNode> build_weighted_set_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep);
    std::unique_ptr<QueryNode> build_phrase_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep);
    std::unique_ptr<QueryNode> build_equiv_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep, bool allow_rewrite);
    std::unique_ptr<QueryNode> build_same_element_term(const QueryNodeResultFactory& factory, QueryStackIterator& queryRep);
    static void skip_unknown(QueryStackIterator& queryRep);

    std::optional<std::string> _same_element_view;
    void adjust_index(std::string& index);
public:
    QueryBuilder();
    ~QueryBuilder();
    std::unique_ptr<QueryNode> build(const QueryNode * parent, const QueryNodeResultFactory& factory, QueryStackIterator & queryRep, bool allowRewrite);
};

}
