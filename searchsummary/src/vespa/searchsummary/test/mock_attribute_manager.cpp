// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_manager.h"

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/array_bool_attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>

#include <cassert>

using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::attribute::QuantizationParams;
using search::attribute::SingleRawAttribute;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace search::docsummary::test {

template <typename AttributeType>
std::shared_ptr<AttributeType> MockAttributeManager::create_and_register_attribute(const std::string&       name,
                                                                                   const attribute::Config& cfg) {
    auto attr_base = AttributeFactory::createAttribute(name, cfg);
    assert(attr_base);
    auto attr = std::dynamic_pointer_cast<AttributeType>(attr_base);
    assert(attr);
    attr->addReservedDoc();
    _mgr.add(attr);
    return attr;
}

template <typename AttributeType, typename ValueType>
void MockAttributeManager::build_attribute(const std::string& name, BasicType type, CollectionType col_type,
                                           const std::vector<std::vector<ValueType>>& values,
                                           std::optional<bool>                        uncased) {
    Config cfg(type, col_type);
    if (uncased.has_value()) {
        cfg.set_match(uncased.value() ? Config::Match::UNCASED : Config::Match::CASED);
    }
    auto attr = create_and_register_attribute<AttributeType>(name, cfg);
    for (const auto& docValues : values) {
        uint32_t docId = 0;
        attr->addDoc(docId);
        attr->clearDoc(docId);
        if (attr->hasMultiValue()) {
            for (const auto& value : docValues) {
                attr->append(docId, value, 1);
            }
        } else if (!docValues.empty()) {
            assert(docValues.size() == 1);
            attr->update(docId, docValues[0]);
        }
        attr->commit();
    }
}

MockAttributeManager::MockAttributeManager() = default;
MockAttributeManager::~MockAttributeManager() = default;

void MockAttributeManager::build_string_attribute(const std::string&                           name,
                                                  const std::vector<std::vector<std::string>>& values,
                                                  CollectionType col_type, std::optional<bool> uncased) {
    build_attribute<StringAttribute, std::string>(name, BasicType::Type::STRING, col_type, values, uncased);
}

void MockAttributeManager::build_float_attribute(const std::string&                      name,
                                                 const std::vector<std::vector<double>>& values,
                                                 CollectionType                          col_type) {
    build_attribute<FloatingPointAttribute, double>(name, BasicType::Type::DOUBLE, col_type, values, std::nullopt);
}

void MockAttributeManager::build_int_attribute(const std::string& name, BasicType type,
                                               const std::vector<std::vector<int64_t>>& values,
                                               CollectionType                           col_type) {
    build_attribute<IntegerAttribute, int64_t>(name, type, col_type, values, std::nullopt);
}

void MockAttributeManager::build_raw_attribute(const std::string&                                 name,
                                               const std::vector<std::vector<std::vector<char>>>& values) {
    build_attribute<SingleRawAttribute, std::vector<char>>(name, BasicType::Type::RAW, CollectionType::SINGLE, values,
                                                           std::nullopt);
}

void MockAttributeManager::build_bool_attribute(const std::string&                      name,
                                                const std::vector<std::vector<int8_t>>& values) {
    Config cfg(BasicType::BOOL, CollectionType::ARRAY);
    auto   bool_attr = create_and_register_attribute<attribute::ArrayBoolAttribute>(name, cfg);
    for (const auto& doc_values : values) {
        uint32_t docId = 0;
        bool_attr->addDoc(docId);
        if (!doc_values.empty()) {
            bool_attr->set_bools(docId, doc_values);
        }
        bool_attr->commit();
    }
}

void MockAttributeManager::build_tensor_attribute(const std::string& name, const std::string& tensor_spec,
                                                  bool                                       quantized,
                                                  const std::vector<std::unique_ptr<Value>>& tensors) {
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    if (!quantized) {
        cfg.setTensorType(ValueType::from_spec(tensor_spec));
    } else {
        constexpr uint64_t seed = 0x1337cafe;
        QuantizationParams qp(seed, QuantizationParams::QuantizationMode::MSE, 4);
        cfg.set_tensor_type_with_quantization(ValueType::from_spec(tensor_spec), qp);
    }
    auto attr = create_and_register_attribute<tensor::TensorAttribute>(name, cfg);
    for (const auto& t : tensors) {
        uint32_t doc_id = 0;
        attr->addDoc(doc_id);
        if (!quantized) {
            attr->setTensor(doc_id, *t);
        } else {
            attr->setTensor(doc_id, *attr->make_quantizer()->quantize(*t));
        }
        attr->commit();
    }
}

} // namespace search::docsummary::test
