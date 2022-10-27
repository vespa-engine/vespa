// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_builder.h"
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
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
fill_helper(AttributeVector& attr, const std::vector<ValueType>& values)
{
    assert(attr.getConfig().collectionType() == CollectionType::SINGLE);
    add_docs(attr, values.size());
    auto& real = dynamic_cast<AttrType&>(attr);
    for (size_t i = 0; i < values.size(); ++i) {
        uint32_t docid = (i + 1);
        real.update(docid, values[i]);
    }
    attr.commit(true);
}

template <typename AttrType, typename ValueType>
void
fill_array_helper(AttributeVector& attr, const std::vector<std::vector<ValueType>>& values)
{
    assert((attr.getConfig().collectionType() == CollectionType::ARRAY) ||
           (attr.getConfig().collectionType() == CollectionType::WSET));
    add_docs(attr, values.size());
    auto& real = dynamic_cast<AttrType&>(attr);
    for (size_t i = 0; i < values.size(); ++i) {
        uint32_t docid = (i + 1);
        for (auto value : values[i]) {
            real.append(docid, value, 1);
        }
    }
    attr.commit(true);
}

template <typename AttrType, typename ValueType>
void
fill_wset_helper(AttributeVector& attr, const std::vector<std::vector<std::pair<ValueType, int32_t>>>& values)
{
    assert(attr.getConfig().collectionType() == CollectionType::WSET);
    add_docs(attr, values.size());
    auto& real = dynamic_cast<AttrType&>(attr);
    for (size_t i = 0; i < values.size(); ++i) {
        uint32_t docid = (i + 1);
        for (auto value : values[i]) {
            real.append(docid, value.first, value.second);
        }
    }
    attr.commit(true);
}

}

AttributeBuilder&
AttributeBuilder::fill(const std::vector<int64_t>& values)
{
    fill_helper<IntegerAttribute, int64_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_array(const std::vector<std::vector<int64_t>>& values)
{
    fill_array_helper<IntegerAttribute, int64_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(const std::vector<std::vector<std::pair<int64_t, int32_t>>>& values)
{
    fill_wset_helper<IntegerAttribute, int64_t>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill(const std::vector<vespalib::string>& values)
{
    fill_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_array(const std::vector<std::vector<vespalib::string>>& values)
{
    fill_array_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

AttributeBuilder&
AttributeBuilder::fill_wset(const std::vector<std::vector<std::pair<vespalib::string, int32_t>>>& values)
{
    fill_wset_helper<StringAttribute, vespalib::string>(_attr, values);
    return *this;
}

}

