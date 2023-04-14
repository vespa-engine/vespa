// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_builder.h"
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/single_raw_attribute.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <cassert>

using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;

namespace search::attribute::test {

AttributeBuilder::AttributeBuilder(const vespalib::string& name, const Config& cfg)
    : _attr_ptr(AttributeFactory::createAttribute(name, cfg)),
      _attr(*_attr_ptr)
{
    _attr.addReservedDoc();
}

namespace {

void
add_docs(AttributeVector& attr, size_t num_docs)
{
    attr.addDocs(num_docs);
}

template <typename AttrType, typename ValueType>
void
fill_helper(AttributeVector& attr, std::initializer_list<ValueType> values)
{
    add_docs(attr, values.size());
    auto& real = dynamic_cast<AttrType&>(attr);
    uint32_t docid = 1;
    for (auto value : values) {
        real.update(docid++, value);
    }
    attr.commit(true);
}

template <typename AttrType, typename ValueType>
void
fill_array_helper(AttributeVector& attr, std::initializer_list<std::initializer_list<ValueType>> values)
{
    assert(attr.hasMultiValue());
    add_docs(attr, values.size());
    auto& real = dynamic_cast<AttrType&>(attr);
    uint32_t docid = 1;
    for (auto value : values) {
        for (auto elem : value) {
            real.append(docid, elem, 1);
        }
        ++docid;
    }
    attr.commit(true);
}

template <typename AttrType, typename ValueType>
void
fill_wset_helper(AttributeVector& attr, std::initializer_list<std::initializer_list<std::pair<ValueType, int32_t>>> values)
{
    assert(attr.hasMultiValue());
    add_docs(attr, values.size());
    auto& real = dynamic_cast<AttrType&>(attr);
    uint32_t docid = 1;
    for (auto value : values) {
        for (auto elem : value) {
            real.append(docid, elem.first, elem.second);
        }
        ++docid;
    }
    attr.commit(true);
}

}

AttributeBuilder&
AttributeBuilder::docs(size_t num_docs)
{
    add_docs(_attr, num_docs);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill(std::initializer_list<int32_t> values)
{
    fill_helper<IntegerAttribute, int32_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill(std::initializer_list<int64_t> values)
{
    fill_helper<IntegerAttribute, int64_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_array(std::initializer_list<IntList> values)
{
    fill_array_helper<IntegerAttribute, int32_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(std::initializer_list<WeightedIntList> values)
{
    fill_wset_helper<IntegerAttribute, int32_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill(std::initializer_list<double> values)
{
    fill_helper<FloatingPointAttribute, double>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_array(std::initializer_list<DoubleList> values)
{
    fill_array_helper<FloatingPointAttribute, double>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(std::initializer_list<WeightedDoubleList> values)
{
    fill_wset_helper<FloatingPointAttribute, double>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill(std::initializer_list<vespalib::string> values)
{
    fill_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_array(std::initializer_list<StringList> values)
{
    fill_array_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(std::initializer_list<WeightedStringList> values)
{
    fill_wset_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill(std::initializer_list<vespalib::ConstArrayRef<char>> values)
{
    fill_helper<SingleRawAttribute, vespalib::ConstArrayRef<char>>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_tensor(const std::vector<vespalib::string>& values)
{
    add_docs(_attr, values.size());
    auto& real = dynamic_cast<search::tensor::TensorAttribute&>(_attr);
    vespalib::string tensor_type = real.getConfig().tensorType().to_spec();
    uint32_t docid = 1;
    for (const auto& value : values) {
        if (!value.empty()) {
            auto spec = TensorSpec::from_expr(tensor_type + ":" + value);
            auto tensor = SimpleValue::from_spec(spec);
            real.setTensor(docid, *tensor);
        }
        ++docid;
    }
    _attr.commit(true);
    return *this;
}

}

