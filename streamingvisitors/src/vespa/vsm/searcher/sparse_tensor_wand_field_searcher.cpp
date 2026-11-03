// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_field_searcher.h"

#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/query_value.h>
#include <vespa/vespalib/util/issue.h>

using search::fef::QueryValue;
using search::queryeval::SparseTensorWandSearch;
using search::streaming::SparseTensorWandQueryNode;

namespace vsm {

SparseTensorWandFieldSearcher::NodeAndDotProduct::NodeAndDotProduct(SparseTensorWandQueryNode*   node_in,
                                                                    const vespalib::eval::Value& query_tensor)
    : node(node_in), dot_product(query_tensor) {
}

SparseTensorWandFieldSearcher::NodeAndDotProduct::~NodeAndDotProduct() = default;

SparseTensorWandFieldSearcher::SparseTensorWandFieldSearcher(FieldIdT fid) : FieldSearcher(fid), _nodes() {
}

SparseTensorWandFieldSearcher::~SparseTensorWandFieldSearcher() = default;

std::unique_ptr<FieldSearcher> SparseTensorWandFieldSearcher::duplicate() const {
    return std::make_unique<SparseTensorWandFieldSearcher>(field());
}

void SparseTensorWandFieldSearcher::prepare(search::streaming::QueryTermList& qtl, const SharedSearcherBuf& buf,
                                            const vsm::FieldPathMapT&       field_paths,
                                            search::fef::IQueryEnvironment& query_env) {
    FieldSearcher::prepare(qtl, buf, field_paths, query_env);
    _nodes.clear();
    const auto* tensor_data_type = field_paths[field()].back().getDataType().cast_tensor();
    if (tensor_data_type == nullptr) {
        vespalib::Issue::report("Data type for field %u is '%s', but expected it to be a tensor type", field(),
                                field_paths[field()].back().getDataType().toString().c_str());
        return;
    }
    const auto& field_type = tensor_data_type->getTensorType();
    if (!SparseTensorWandSearch::is_supported_type(field_type)) {
        vespalib::Issue::report("Tensor type for field %u is '%s', but expected it to be a sparse tensor with one "
                                "mapped dimension",
                                field(), field_type.to_spec().c_str());
        return;
    }
    for (auto term : qtl) {
        auto* wand_term = term->as_sparse_tensor_wand_query_node();
        if (wand_term == nullptr) {
            vespalib::Issue::report("Query term (%s) searching field %u is NOT a SparseTensorWandQueryNode",
                                    term->getClassName().c_str(), field());
            continue;
        }
        auto query_value =
            QueryValue::from_config(wand_term->get_query_tensor_name(), query_env.getIndexEnvironment());
        query_value.prepare_shared_state(query_env, query_env.getObjectStore());
        const auto* tensor_value = query_value.lookup_value(query_env.getObjectStore());
        if (tensor_value == nullptr) {
            vespalib::Issue::report("Could not find query tensor for SparseTensorWandQueryNode(%s, %s)",
                                    wand_term->index().c_str(), wand_term->get_query_tensor_name().c_str());
            continue;
        }
        if (tensor_value->type() != field_type) {
            vespalib::Issue::report("SparseTensorWandQueryNode(%s, %s): Field tensor type (%s) and query tensor "
                                    "type (%s) differ",
                                    wand_term->index().c_str(), wand_term->get_query_tensor_name().c_str(),
                                    field_type.to_spec().c_str(), tensor_value->type().to_spec().c_str());
            continue;
        }
        _nodes.push_back(std::make_unique<NodeAndDotProduct>(wand_term, *tensor_value));
    }
}

void SparseTensorWandFieldSearcher::onValue(const document::FieldValue& fv) {
    if (fv.isA(document::FieldValue::Type::TENSOR)) {
        const auto* tfv = dynamic_cast<const document::TensorFieldValue*>(&fv);
        if (tfv && tfv->getAsTensorPtr()) {
            for (auto& elem : _nodes) {
                double score = elem->dot_product.eval(*tfv->getAsTensorPtr());
                if (score > elem->node->get_min_score()) {
                    elem->node->add_field_score(field(), score);
                }
            }
        }
    }
}

} // namespace vsm
