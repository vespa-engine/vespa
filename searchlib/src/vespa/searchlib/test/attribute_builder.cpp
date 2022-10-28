// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_builder.h"
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <cassert>

namespace search::attribute::test {

AttributeBuilder::AttributeBuilder(const vespalib::string& name, const Config& cfg)
    : _attr_ptr(AttributeFactory::createAttribute(name, cfg)),
      _attr(*_attr_ptr)
{
}

AttributeBuilder::AttributeBuilder(AttributeVector& attr)
    : _attr_ptr(),
      _attr(attr)
{
}

namespace {

void
add_docs(AttributeVector& attr, size_t num_docs)
{
    attr.addReservedDoc();
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
    assert((attr.getConfig().collectionType() == CollectionType::ARRAY) ||
           (attr.getConfig().collectionType() == CollectionType::WSET));
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
    assert(attr.getConfig().collectionType() == CollectionType::WSET);
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
AttributeBuilder::fill(std::initializer_list<int32_t> values)
{
    fill_helper<IntegerAttribute, int32_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_array(std::initializer_list<std::initializer_list<int32_t>> values)
{
    fill_array_helper<IntegerAttribute, int32_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(std::initializer_list<std::initializer_list<std::pair<int32_t, int32_t>>> values)
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
AttributeBuilder::fill_array(std::initializer_list<std::initializer_list<double>> values)
{
    fill_array_helper<FloatingPointAttribute, double>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(std::initializer_list<std::initializer_list<std::pair<double, int32_t>>> values)
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
AttributeBuilder::fill_array(std::initializer_list<std::initializer_list<vespalib::string>> values)
{
    fill_array_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(std::initializer_list<std::initializer_list<std::pair<vespalib::string, int32_t>>> values)
{
    fill_wset_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

}

