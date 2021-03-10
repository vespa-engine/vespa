// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_from_weighted_set_feature.h"
#include "constant_tensor_executor.h"
#include "utils.h"
#include "tensor_from_attribute_executor.h"
#include "weighted_set_parser.hpp"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_type.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.tensor_from_weighted_set_feature");

using namespace search::fef;
using search::attribute::IAttributeVector;
using search::attribute::WeightedConstCharContent;
using search::attribute::WeightedStringContent;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::ValueType;
using vespalib::eval::CellType;
using search::fef::FeatureType;

namespace search {
namespace features {

namespace {

struct WeightedStringVector
{
    std::vector<IAttributeVector::WeightedString> _data;
    void insert(vespalib::stringref key, vespalib::stringref weight) {
        _data.emplace_back(key, util::strToNum<int32_t>(weight));
    }
};

}

TensorFromWeightedSetBlueprint::TensorFromWeightedSetBlueprint()
    : TensorFactoryBlueprint("tensorFromWeightedSet")
{
}

bool
TensorFromWeightedSetBlueprint::setup(const search::fef::IIndexEnvironment &env,
                                      const search::fef::ParameterList &params)
{
    (void) env;
    // _params[0] = source ('attribute(name)' OR 'query(param)');
    // _params[1] = dimension (optional);
    bool validSource = extractSource(params[0].getValue());
    if (params.size() == 2) {
        _dimension = params[1].getValue();
    } else {
        _dimension = _sourceParam;
    }
    describeOutput("tensor",
                   "The tensor created from the given weighted set source (attribute field or query parameter)",
                   FeatureType::object(ValueType::make_type(CellType::DOUBLE, {{_dimension}})));
    return validSource;
}

namespace {

FeatureExecutor &
createAttributeExecutor(const search::fef::IQueryEnvironment &env,
                        const vespalib::string &attrName,
                        const vespalib::string &dimension,
                        vespalib::Stash &stash)
{
    const IAttributeVector *attribute = env.getAttributeContext().getAttribute(attrName);
    if (attribute == NULL) {
        LOG(warning, "The attribute vector '%s' was not found in the attribute manager."
                " Returning empty tensor.", attrName.c_str());
        return ConstantTensorExecutor::createEmpty(ValueType::make_type(CellType::DOUBLE, {{dimension}}), stash);
    }
    if (attribute->getCollectionType() != search::attribute::CollectionType::WSET ||
            attribute->isFloatingPointType()) {
        LOG(warning, "The attribute vector '%s' is NOT of type weighted set of string or integer."
                " Returning empty tensor.", attrName.c_str());
        return ConstantTensorExecutor::createEmpty(ValueType::make_type(CellType::DOUBLE, {{dimension}}), stash);
    }
    if (attribute->isIntegerType()) {
        // Using WeightedStringContent ensures that the integer values are converted
        // to strings while extracting them from the attribute.
        return stash.create<TensorFromAttributeExecutor<WeightedStringContent>>(attribute, dimension);
    }
    // When the underlying attribute is of type string we can reference these values
    // using WeightedConstCharContent.
    return stash.create<TensorFromAttributeExecutor<WeightedConstCharContent>>(attribute, dimension);
}

FeatureExecutor &
createQueryExecutor(const search::fef::IQueryEnvironment &env,
                    const vespalib::string &queryKey,
                    const vespalib::string &dimension, vespalib::Stash &stash)
{
    ValueType type = ValueType::make_type(CellType::DOUBLE, {{dimension}});
    search::fef::Property prop = env.getProperties().lookup(queryKey);
    if (prop.found() && !prop.get().empty()) {
        WeightedStringVector vector;
        WeightedSetParser::parse(prop.get(), vector);
        auto factory = FastValueBuilderFactory::get();
        size_t sz = vector._data.size();
        auto builder = factory.create_value_builder<double>(type, 1, 1, sz);
        std::vector<vespalib::stringref> addr_ref;
        for (const auto &elem : vector._data) {
            addr_ref.clear();
            addr_ref.push_back(elem.value());
            auto cell_array = builder->add_subspace(addr_ref);
            cell_array[0] = elem.weight();
        }
        return ConstantTensorExecutor::create(builder->build(std::move(builder)), stash);
    }
    return ConstantTensorExecutor::createEmpty(type, stash);
}

}

FeatureExecutor &
TensorFromWeightedSetBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_sourceType == ATTRIBUTE_SOURCE) {
        return createAttributeExecutor(env, _sourceParam, _dimension, stash);
    } else if (_sourceType == QUERY_SOURCE) {
        return createQueryExecutor(env, _sourceParam, _dimension, stash);
    }
    return ConstantTensorExecutor::createEmpty(ValueType::make_type(CellType::DOUBLE, {{_dimension}}), stash);
}

} // namespace features
} // namespace search
