// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumwriter.h"
#include "attributedfw.h"
#include "docsumstate.h"
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.attributedfw");

using namespace search;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::BasicType;

namespace search {
namespace docsummary {

ResType inferType(const IAttributeVector & vec) {
    ResType retval;
    if (vec.hasMultiValue()) {
        retval = RES_STRING;
    } else {
        if (vec.isStringType()) {
            retval = RES_STRING;
        } else {
            size_t fw = vec.getFixedWidth();
            if (vec.isIntegerType()) {
                if (fw == sizeof(uint8_t)) {
                    retval = RES_BYTE;
                } else if (fw == sizeof(uint16_t)) {
                    retval = RES_SHORT;
                } else if (fw == sizeof(uint32_t)) {
                    retval = RES_INT;
                } else {
                    retval = RES_INT64;
                }
            } else if (vec.isFloatingPointType()) {
                retval = (fw == sizeof(float)) ? RES_FLOAT : RES_DOUBLE;
            } else {
                BasicType::Type t = vec.getBasicType();
                switch (t) {
                case BasicType::TENSOR:
                    retval = RES_TENSOR;
                    break;
                default:
                    retval = RES_STRING;
                }
            }
        }
    }
    return retval;
}

//-----------------------------------------------------------------------------

AttrDFW::AttrDFW(const vespalib::string & attrName) :
    _attrName(attrName)
{
}

const attribute::IAttributeVector &
AttrDFW::vec(const GetDocsumsState & s) const {
    return *s.getAttribute(getIndex());
}

//-----------------------------------------------------------------------------

class SingleAttrDFW : public AttrDFW
{
public:
    SingleAttrDFW(const vespalib::string & attrName) :
        AttrDFW(attrName)
    { }
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) override;
   virtual bool isDefaultValue(uint32_t docid, const GetDocsumsState * state) const override;
};

bool SingleAttrDFW::isDefaultValue(uint32_t docid, const GetDocsumsState * state) const
{
    return vec(*state).isUndefined(docid);
}

void
SingleAttrDFW::insertField(uint32_t docid,
                          GeneralResult *,
                          GetDocsumsState * state,
                          ResType type,
                          vespalib::slime::Inserter &target)
{
    const char *s="";
    const IAttributeVector & v = vec(*state);
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
                vespalib::tensor::TypedBinaryFormat::serialize(str, *tensor);
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
        s = v.getString(docid, NULL, 0); // no need to pass in a buffer, this attribute has a string storage.
        target.insertString(vespalib::Memory(s));
        break;
    }
    case RES_LONG_DATA:
    case RES_DATA: {
        s = v.getString(docid, NULL, 0); // no need to pass in a buffer, this attribute has a string storage.
        target.insertData(vespalib::Memory(s));
        break;
    }
    default:
        // unknown type, will be missing, should not happen
        return;
    }
}


//-----------------------------------------------------------------------------

class MultiAttrDFW : public AttrDFW
{
public:
    MultiAttrDFW(const vespalib::string & attrName) : AttrDFW(attrName) {}
    virtual void insertField(uint32_t docid,
                             GeneralResult *gres,
                             GetDocsumsState *state,
                             ResType type,
                             vespalib::slime::Inserter &target) override;

};

void
MultiAttrDFW::insertField(uint32_t docid,
                          GeneralResult *,
                          GetDocsumsState *state,
                          ResType,
                          vespalib::slime::Inserter &target)
{
    using vespalib::slime::Cursor;
    using vespalib::Memory;
    const IAttributeVector & v = vec(*state);
    uint32_t entries = v.getValueCount(docid);
    bool isWeightedSet = v.hasWeightedSetType();

    Cursor &arr = target.insertArray();
    BasicType::Type t = v.getBasicType();
    switch (t) {
    case BasicType::NONE:
    case BasicType::STRING: {
        std::vector<IAttributeVector::WeightedString> elements(entries);
        entries = std::min(entries, v.get(docid, &elements[0], entries));
        for (uint32_t i = 0; i < entries; ++i) {
            const vespalib::string &sv = elements[i].getValue();
            Memory value(sv.c_str(), sv.size());
            if (isWeightedSet) {
                Cursor &elem = arr.addObject();
                elem.setString("item", value);
                elem.setLong("weight", elements[i].getWeight());
            } else {
                arr.addString(value);
            }
        }
        return; }
    case BasicType::UINT1:
    case BasicType::UINT2:
    case BasicType::UINT4:
    case BasicType::INT8:
    case BasicType::INT16:
    case BasicType::INT32:
    case BasicType::INT64: {
        std::vector<IAttributeVector::WeightedInt> elements(entries);
        entries = std::min(entries, v.get(docid, &elements[0], entries));
        for (uint32_t i = 0; i < entries; ++i) {
            if (isWeightedSet) {
                Cursor &elem = arr.addObject();
                elem.setLong("item", elements[i].getValue());
                elem.setLong("weight", elements[i].getWeight());
            } else {
                arr.addLong(elements[i].getValue());
            }
        }
        return; }
    case BasicType::FLOAT:
    case BasicType::DOUBLE: {
        std::vector<IAttributeVector::WeightedFloat> elements(entries);
        entries = std::min(entries, v.get(docid, &elements[0], entries));
        for (uint32_t i = 0; i < entries; ++i) {
            if (isWeightedSet) {
                Cursor &elem = arr.addObject();
                elem.setDouble("item", elements[i].getValue());
                elem.setLong("weight", elements[i].getWeight());
            } else {
                arr.addDouble(elements[i].getValue());
            }
        }
        return; }
    default:
        // should not happen
        LOG(error, "bad value for type: %u\n", t);
        LOG_ASSERT(false);
    }
}

//-----------------------------------------------------------------------------

IDocsumFieldWriter *
AttributeDFWFactory::create(IAttributeManager & vecMan, const char *vecName)
{
    IAttributeContext::UP ctx = vecMan.createContext();
    const IAttributeVector * vec = ctx->getAttribute(vecName);
    if (vec == NULL) {
        LOG(warning, "No valid attribute vector found: %s", vecName);
        return NULL;
    }
    if (vec->hasMultiValue()) {
        return new MultiAttrDFW(vec->getName());
    } else {
        return new SingleAttrDFW(vec->getName());
    }
}

}
}
