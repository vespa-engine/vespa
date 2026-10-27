// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldsearcher.h"

#include <vespa/searchlib/query/streaming/sparse_tensor_wand_query_node.h>
#include <vespa/searchlib/queryeval/sparse_tensor_wand_search.h>

namespace vsm {

/**
 * Class used to perform sparse tensor WAND search over the streamed values of a tensor field.
 *
 * The dot product between the field value and the query tensor is added to
 * SparseTensorWandQueryNode instances searching this field when it exceeds the
 * score threshold of the node, which the node raises using a heap of the best
 * scores of documents that became hits.
 */
class SparseTensorWandFieldSearcher : public FieldSearcher {
private:
    struct NodeAndDotProduct {
        search::streaming::SparseTensorWandQueryNode* node;
        search::queryeval::SparseTensorDotProduct     dot_product;
        NodeAndDotProduct(search::streaming::SparseTensorWandQueryNode* node_in,
                          const vespalib::eval::Value&                  query_tensor);
        ~NodeAndDotProduct();
    };
    std::vector<std::unique_ptr<NodeAndDotProduct>> _nodes;

public:
    explicit SparseTensorWandFieldSearcher(FieldIdT fid);
    ~SparseTensorWandFieldSearcher() override;

    std::unique_ptr<FieldSearcher> duplicate() const override;
    void prepare(search::streaming::QueryTermList& qtl, const SharedSearcherBuf& buf,
                 const vsm::FieldPathMapT& field_paths, search::fef::IQueryEnvironment& query_env) override;
    void onValue(const document::FieldValue& fv) override;
};

} // namespace vsm
