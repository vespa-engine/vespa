// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributedfw.h"
#include "docsumstate.h"
#include "docsumwriter.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/common/matching_elements_fields.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/objects/nbostream.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.attributedfw");

using namespace search;
using search::attribute::BasicType;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using vespalib::Memory;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vespalib::eval::Value;

namespace search::docsummary {

AttrDFW::AttrDFW(const vespalib::string & attrName) :
    _attrName(attrName)
{
}

const attribute::IAttributeVector &
AttrDFW::get_attribute(const GetDocsumsState& s) const {
    return *s.getAttribute(getIndex());
}

//-----------------------------------------------------------------------------

class SingleAttrDFW : public AttrDFW
{
public:
    explicit SingleAttrDFW(const vespalib::string & attrName) :
        AttrDFW(attrName)
    { }
    void insertField(uint32_t docid, GetDocsumsState *state, ResType type, Inserter &target) override;
    bool isDefaultValue(uint32_t docid, const GetDocsumsState * state) const override;
};

bool SingleAttrDFW::isDefaultValue(uint32_t docid, const GetDocsumsState * state) const
{
    return get_attribute(*state).isUndefined(docid);
}

void
SingleAttrDFW::insertField(uint32_t docid, GetDocsumsState * state, ResType type, Inserter &target)
{
    const auto& v = get_attribute(*state);
    switch (type) {
    case RES_INT: {
        uint32_t val = v.getInt(docid);
        target.insertLong(val);
        break;
    }
    case RES_SHORT: {
        uint16_t val = v.getInt(docid);
        target.insertLong(val);
        break;
    }
    case RES_BYTE: {
        uint8_t val = v.getInt(docid);
        target.insertLong(val);
        break;
    }
    case RES_BOOL: {
        uint8_t val = v.getInt(docid);
        target.insertBool(val != 0);
        break;
    }
    case RES_FLOAT: {
        float val = v.getFloat(docid);
        target.insertDouble(val);
        break;
    }
    case RES_DOUBLE: {
        double val = v.getFloat(docid);
        target.insertDouble(val);
        break;
    }
    case RES_INT64: {
        uint64_t val = v.getInt(docid);
        target.insertLong(val);
        break;
    }
    case RES_TENSOR: {
        BasicType::Type t = v.getBasicType();
        switch (t) {
        case BasicType::TENSOR: {
            const tensor::ITensorAttribute *tv = v.asTensorAttribute();
            assert(tv != nullptr);
            const auto tensor = tv->getTensor(docid);
            if (tensor) {
                vespalib::nbostream str;
                encode_value(*tensor, str);
                target.insertData(vespalib::Memory(str.peek(), str.size()));
            }
        }
        default:
            ;
        }
    }
        break;
    case RES_JSONSTRING:
    case RES_XMLSTRING:
    case RES_FEATUREDATA:
    case RES_LONG_STRING:
    case RES_STRING: {
        const char *s = v.getString(docid, nullptr, 0); // no need to pass in a buffer, this attribute has a string storage.
        target.insertString(vespalib::Memory(s));
        break;
    }
    case RES_LONG_DATA:
    case RES_DATA: {
        const char *s = v.getString(docid, nullptr, 0); // no need to pass in a buffer, this attribute has a string storage.
        target.insertData(vespalib::Memory(s));
        break;
    }
    default:
        // unknown type, will be missing, should not happen
        return;
    }
}


//-----------------------------------------------------------------------------

template <typename DataType>
class MultiAttrDFW : public AttrDFW {
private:
    bool _is_weighted_set;
    bool _filter_elements;
    std::shared_ptr<MatchingElementsFields> _matching_elems_fields;

public:
    explicit MultiAttrDFW(const vespalib::string& attr_name, bool is_weighted_set,
                          bool filter_elements, std::shared_ptr<MatchingElementsFields> matching_elems_fields)
        : AttrDFW(attr_name),
          _is_weighted_set(is_weighted_set),
          _filter_elements(filter_elements),
          _matching_elems_fields(std::move(matching_elems_fields))
    {
        if (filter_elements && _matching_elems_fields) {
            _matching_elems_fields->add_field(attr_name);
        }
    }
    void insertField(uint32_t docid, GetDocsumsState* state, ResType type, Inserter& target) override;
};

void
insert_element(const std::vector<IAttributeVector::WeightedString>& elements,
               size_t idx, bool is_weighted_set, Cursor& arr)
{
    const vespalib::string& sv = elements[idx].getValue();
    Memory value(sv.c_str(), sv.size());
    if (is_weighted_set) {
        Cursor& elem = arr.addObject();
        elem.setString("item", value);
        elem.setLong("weight", elements[idx].getWeight());
    } else {
        arr.addString(value);
    }
}

void
insert_element(const std::vector<IAttributeVector::WeightedInt>& elements,
               size_t idx, bool is_weighted_set, Cursor& arr)
{
    if (is_weighted_set) {
        Cursor& elem = arr.addObject();
        elem.setLong("item", elements[idx].getValue());
        elem.setLong("weight", elements[idx].getWeight());
    } else {
        arr.addLong(elements[idx].getValue());
    }
}

void
insert_element(const std::vector<IAttributeVector::WeightedFloat>& elements,
               size_t idx, bool is_weighted_set, Cursor& arr)
{
    if (is_weighted_set) {
        Cursor& elem = arr.addObject();
        elem.setDouble("item", elements[idx].getValue());
        elem.setLong("weight", elements[idx].getWeight());
    } else {
        arr.addDouble(elements[idx].getValue());
    }
}

template <typename DataType>
void
MultiAttrDFW<DataType>::insertField(uint32_t docid, GetDocsumsState* state, ResType, Inserter& target)
{
    const auto& attr = get_attribute(*state);
    uint32_t entries = attr.getValueCount(docid);
    if (entries == 0) {
        return;  // Don't insert empty fields
    }

    Cursor &arr = target.insertArray();
    std::vector<DataType> elements(entries);
    entries = std::min(entries, attr.get(docid, elements.data(), entries));

    if (_filter_elements) {
        const auto& matching_elems = state->get_matching_elements(*_matching_elems_fields)
                .get_matching_elements(docid, getAttributeName());
        if (!matching_elems.empty() && matching_elems.back() < entries) {
            for (uint32_t id_to_keep : matching_elems) {
                insert_element(elements, id_to_keep, _is_weighted_set, arr);
            }
        }
    } else {
        for (uint32_t i = 0; i < entries; ++i) {
            insert_element(elements, i, _is_weighted_set, arr);
        }
    }
}

//-----------------------------------------------------------------------------

namespace {

std::unique_ptr<IDocsumFieldWriter>
create_multi_writer(const IAttributeVector& attr,
                    bool filter_elements,
                    std::shared_ptr<MatchingElementsFields> matching_elems_fields)
{
    auto type = attr.getBasicType();
    bool is_weighted_set = attr.hasWeightedSetType();
    switch (type) {
    case BasicType::NONE:
    case BasicType::STRING: {
        return std::make_unique<MultiAttrDFW<IAttributeVector::WeightedString>>(attr.getName(), is_weighted_set,
                                                                                filter_elements, std::move(matching_elems_fields));
    }
    case BasicType::BOOL:
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
    case BasicType::INT16:
    case BasicType::INT32:
    case BasicType::INT64: {
        return std::make_unique<MultiAttrDFW<IAttributeVector::WeightedInt>>(attr.getName(), is_weighted_set,
                                                                             filter_elements, std::move(matching_elems_fields));
    }
    case BasicType::FLOAT:
    case BasicType::DOUBLE: {
        return std::make_unique<MultiAttrDFW<IAttributeVector::WeightedFloat>>(attr.getName(), is_weighted_set,
                                                                               filter_elements, std::move(matching_elems_fields));
    }
    default:
        // should not happen
        LOG(error, "Bad value for attribute type: %u", type);
        LOG_ASSERT(false);
    }
}

}

std::unique_ptr<IDocsumFieldWriter>
AttributeDFWFactory::create(IAttributeManager& attr_mgr,
                            const vespalib::string& attr_name,
                            bool filter_elements,
                            std::shared_ptr<MatchingElementsFields> matching_elems_fields)
{
    auto ctx = attr_mgr.createContext();
    const auto* attr = ctx->getAttribute(attr_name);
    if (attr == nullptr) {
        LOG(warning, "No valid attribute vector found: '%s'", attr_name.c_str());
        return std::unique_ptr<IDocsumFieldWriter>();
    }
    if (attr->hasMultiValue()) {
        return create_multi_writer(*attr, filter_elements, std::move(matching_elems_fields));
    } else {
        return std::make_unique<SingleAttrDFW>(attr->getName());
    }
}

}
