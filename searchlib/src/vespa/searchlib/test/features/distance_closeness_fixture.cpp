// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_closeness_fixture.h"
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_fast_value_attribute.h>

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::DistanceMetric;
using search::fef::test::IndexEnvironment;
using search::fef::test::QueryEnvironment;
using search::tensor::DenseTensorAttribute;
using search::tensor::DirectTensorAttribute;
using search::tensor::SerializedFastValueAttribute;
using search::tensor::TensorAttribute;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::features::test {

namespace {

std::shared_ptr<TensorAttribute>
create_tensor_attribute(const vespalib::string& attr_name,
                        const vespalib::string& tensor_type,
                        DistanceMetric distance_metric,
                        bool direct_tensor,
                        uint32_t docid_limit)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(tensor_type));
    cfg.set_distance_metric(distance_metric);
    std::shared_ptr<TensorAttribute> result;
    if (cfg.tensorType().is_dense()) {
        result = std::make_shared<DenseTensorAttribute>(attr_name, cfg);
    } else if (direct_tensor) {
        result = std::make_shared<DirectTensorAttribute>(attr_name, cfg);
    } else {
        result = std::make_shared<SerializedFastValueAttribute>(attr_name, cfg);
    }
    result->addReservedDoc();
    result->addDocs(docid_limit-1);
    result->commit();
    return result;
}

}

FeatureDumpFixture::~FeatureDumpFixture() = default;

DistanceClosenessFixture::DistanceClosenessFixture(size_t fooCnt, size_t barCnt,
                                                   const Labels& labels,
                                                   const vespalib::string& featureName,
                                                   const vespalib::string& query_tensor,
                                                   DistanceMetric distance_metric)
    : DistanceClosenessFixture("tensor(x[2])", false, fooCnt, barCnt, labels, featureName, query_tensor, distance_metric)
{
}

DistanceClosenessFixture::DistanceClosenessFixture(const vespalib::string& tensor_type,
                                                   bool direct_tensor,
                                                   size_t fooCnt, size_t barCnt,
                                                   const Labels& labels,
                                                   const vespalib::string& featureName,
                                                   const vespalib::string& query_tensor,
                                                   DistanceMetric distance_metric)
    : queryEnv(&indexEnv), rankSetup(factory, indexEnv),
      mdl(), match_data(), rankProgram(), fooHandles(), barHandles(),
      tensor_attr(),
      docid_limit(11),
      _failed(false)
{
    for (size_t i = 0; i < fooCnt; ++i) {
        uint32_t fieldId = indexEnv.getFieldByName("foo")->id();
        fooHandles.push_back(mdl.allocTermField(fieldId));
        SimpleTermData term;
        term.setUniqueId(i + 1);
        term.addField(fieldId).setHandle(fooHandles.back());
        queryEnv.getTerms().push_back(term);
    }
    for (size_t i = 0; i < barCnt; ++i) {
        uint32_t fieldId = indexEnv.getFieldByName("bar")->id();
        barHandles.push_back(mdl.allocTermField(fieldId));
        SimpleTermData term;
        term.setUniqueId(fooCnt + i + 1);
        term.addField(fieldId).setHandle(barHandles.back());
        if (!query_tensor.empty()) {
            term.set_query_tensor_name("qbar");
        }
        queryEnv.getTerms().push_back(term);
    }
    if (!query_tensor.empty()) {
        tensor_attr = create_tensor_attribute("bar", tensor_type, distance_metric, direct_tensor, docid_limit);
        indexEnv.getAttributeMap().add(tensor_attr);
        search::fef::indexproperties::type::Attribute::set(indexEnv.getProperties(), "bar", tensor_type);
        set_query_tensor("qbar", "tensor(x[2])", TensorSpec::from_expr(query_tensor));
    }
    labels.inject(queryEnv.getProperties());
    rankSetup.setFirstPhaseRank(featureName);
    rankSetup.setIgnoreDefaultRankFeatures(true);
    EXPECT_TRUE(rankSetup.compile()) << (_failed = true, "");
    if (_failed) {
        return;
    }
    rankSetup.prepareSharedState(queryEnv, queryEnv.getObjectStore());
    match_data = mdl.createMatchData();
    rankProgram = rankSetup.create_first_phase_program();
    rankProgram->setup(*match_data, queryEnv);
}

DistanceClosenessFixture::~DistanceClosenessFixture() = default;

void
DistanceClosenessFixture::set_attribute_tensor(uint32_t docid, const vespalib::eval::TensorSpec& spec)
{
    auto tensor = SimpleValue::from_spec(spec);
    tensor_attr->setTensor(docid, *tensor);
    tensor_attr->commit();
}

void
DistanceClosenessFixture::set_query_tensor(const vespalib::string& query_tensor_name,
                                           const vespalib::string& tensor_type,
                                           const TensorSpec& spec)
{
    search::fef::indexproperties::type::QueryFeature::set(indexEnv.getProperties(), query_tensor_name, tensor_type);
    auto tensor = SimpleValue::from_spec(spec);
    vespalib::nbostream stream;
    vespalib::eval::encode_value(*tensor, stream);
    queryEnv.getProperties().add(query_tensor_name, vespalib::stringref(stream.peek(), stream.size()));
}

}

