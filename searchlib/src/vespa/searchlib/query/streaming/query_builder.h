// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/query_normalization.h>
#include <memory>

namespace search { class SimpleQueryStackDumpIterator; }

namespace search::streaming {

class MultiTerm;
class QueryNode;
class QueryNodeResultFactory;

/*
 * Class used to build a query from a query stack.
 */
class QueryBuilder {
    std::unique_ptr<QueryNode> build_nearest_neighbor_query_node(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
    void populate_multi_term(Normalizing string_normalize_mode, MultiTerm& mt, SimpleQueryStackDumpIterator& queryRep);
    std::unique_ptr<QueryNode> build_dot_product_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
    std::unique_ptr<QueryNode> build_wand_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
    std::unique_ptr<QueryNode> build_weighted_set_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
    std::unique_ptr<QueryNode> build_phrase_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
    std::unique_ptr<QueryNode> build_equiv_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep, bool allow_rewrite);
    std::unique_ptr<QueryNode> build_same_element_term(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
    static void skip_unknown(SimpleQueryStackDumpIterator& queryRep);

public:
    QueryBuilder();
    ~QueryBuilder();
    std::unique_ptr<QueryNode> build(const QueryNode * parent, const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator & queryRep, bool allowRewrite);
};

}
