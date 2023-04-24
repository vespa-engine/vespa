// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_field_searcher.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/query_value.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/distance_function.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/searchlib/tensor/tensor_ext_attribute.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::fef::QueryValue;
using search::tensor::DistanceCalculator;
using search::tensor::TensorExtAttribute;
using search::tensor::make_distance_function;
using vespalib::eval::ValueType;

namespace {

constexpr uint32_t scratch_docid = 0;

std::unique_ptr<TensorExtAttribute>
make_attribute(const ValueType& tensor_type)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(tensor_type);
    auto result = std::make_unique<TensorExtAttribute>("nnfs_attr", cfg);
    uint32_t docid;
    result->addDoc(docid);
    assert(docid == scratch_docid);
    return result;
}

}

namespace vsm {

NearestNeighborFieldSearcher::NodeAndCalc::NodeAndCalc(search::streaming::NearestNeighborQueryNode* node_in,
                                                       std::unique_ptr<search::tensor::DistanceCalculator> calc_in)
    : node(node_in),
      calc(std::move(calc_in)),
      distance_threshold(calc->function().convert_threshold(node->get_distance_threshold()))
{
}

NearestNeighborFieldSearcher::NearestNeighborFieldSearcher(FieldIdT fid,
                                                           search::attribute::DistanceMetric metric)
    : FieldSearcher(fid),
      _metric(metric),
      _attr(),
      _calcs()
{
}

NearestNeighborFieldSearcher::~NearestNeighborFieldSearcher() = default;

std::unique_ptr<FieldSearcher>
NearestNeighborFieldSearcher::duplicate() const
{
    return std::make_unique<NearestNeighborFieldSearcher>(field(), _metric);
}

void
NearestNeighborFieldSearcher::prepare(search::streaming::QueryTermList& qtl,
                                      const SharedSearcherBuf& buf,
                                      const vsm::FieldPathMapT& field_paths,
                                      search::fef::IQueryEnvironment& query_env)
{
    FieldSearcher::prepare(qtl, buf, field_paths, query_env);
    const auto* tensor_type = field_paths[field()].back().getDataType().cast_tensor();
    if (tensor_type == nullptr) {
        vespalib::Issue::report("Data type for field %u is '%s', but expected it to be a tensor type",
                                field(), field_paths[field()].back().getDataType().toString().c_str());
    }
    _attr = make_attribute(tensor_type->getTensorType());
    _calcs.clear();
    for (auto term : qtl) {
        auto* nn_term = term->as_nearest_neighbor_query_node();
        if (nn_term == nullptr) {
            vespalib::Issue::report("Query term (%s) searching field %u is NOT a NearestNeighborQueryNode",
                                    term->getClassName().c_str(), field());
            continue;
        }
        auto query_value = QueryValue::from_config(nn_term->get_query_tensor_name(), query_env.getIndexEnvironment());
        query_value.prepare_shared_state(query_env, query_env.getObjectStore());
        const auto* tensor_value = query_value.lookup_value(query_env.getObjectStore());
        if (tensor_value == nullptr) {
            vespalib::Issue::report("Could not find query tensor for NearestNeighborQueryNode(%s, %s)",
                                    nn_term->index().c_str(), nn_term->get_query_tensor_name().c_str());
            continue;
        }
        try {
            auto calc = DistanceCalculator::make_with_validation(*_attr, *tensor_value);
            _calcs.emplace_back(nn_term, std::move(calc));
        } catch (const vespalib::IllegalArgumentException& ex) {
            vespalib::Issue::report("Could not create DistanceCalculator for NearestNeighborQueryNode(%s, %s): %s",
                                    nn_term->index().c_str(), nn_term->get_query_tensor_name().c_str(), ex.what());
        }
    }
}

void
NearestNeighborFieldSearcher::onValue(const document::FieldValue& fv)
{
    if (fv.isA(document::FieldValue::Type::TENSOR)) {
        const auto* tfv = dynamic_cast<const document::TensorFieldValue*>(&fv);
        if (tfv && tfv->getAsTensorPtr()) {
            _attr->add(*tfv->getAsTensorPtr(), 1);
            for (auto& elem : _calcs) {
                double distance = elem.calc->calc_with_limit(scratch_docid, elem.distance_threshold);
                if (distance <= elem.distance_threshold) {
                    double score = elem.calc->function().to_rawscore(distance);
                    elem.node->set_raw_score(score);
                }
            }
        }
    }
}

search::attribute::DistanceMetric
NearestNeighborFieldSearcher::distance_metric_from_string(const vespalib::string& value)
{
    using search::attribute::DistanceMetric;
    // Valid string values must match the definition of DistanceMetric in
    // config-model/src/main/java/com/yahoo/schema/document/Attribute.java
    if (value == "EUCLIDEAN") {
        return DistanceMetric::Euclidean;
    } else if (value == "ANGULAR") {
        return DistanceMetric::Angular;
    } else if (value == "GEODEGREES") {
        return DistanceMetric::GeoDegrees;
    } else if (value == "INNERPRODUCT") {
        return DistanceMetric::InnerProduct;
    } else if (value == "HAMMING") {
        return DistanceMetric::Hamming;
    }
    vespalib::Issue::report("Distance metric '%s' is not supported. Using 'euclidean' instead", value.c_str());
    return DistanceMetric::Euclidean;
}

}

