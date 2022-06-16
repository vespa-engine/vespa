// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "arrayatlookupfunctionnode.h"
#include "floatresultnode.h"
#include "integerresultnode.h"
#include "stringresultnode.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search {
namespace expression {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(ArrayAtLookup, UnaryFunctionNode);

ArrayAtLookup::ArrayAtLookup()
{
}

ArrayAtLookup::~ArrayAtLookup()
{
}

ArrayAtLookup::ArrayAtLookup(const vespalib::string &attribute, ExpressionNode::UP arg)
    : UnaryFunctionNode(std::move(arg)),
      _attributeName(attribute)
{
}

ArrayAtLookup::ArrayAtLookup(const search::attribute::IAttributeVector &attr,
                             ExpressionNode::UP indexArg)
    : UnaryFunctionNode(std::move(indexArg)),
      _attributeName(attr.getName()),
      _attribute(&attr)
{
}


ArrayAtLookup::ArrayAtLookup(const ArrayAtLookup &rhs) :
    UnaryFunctionNode(rhs),
    _attributeName(rhs._attributeName),
    _attribute(rhs._attribute),
    _docId(rhs._docId),
    _basicAttributeType(rhs._basicAttributeType)
{
    // why?
    _docId = 0;
}

ArrayAtLookup & ArrayAtLookup::operator= (const ArrayAtLookup &rhs)
{
    if (this != &rhs) {
        UnaryFunctionNode::operator =(rhs);
        _attributeName = rhs._attributeName;
        _attribute = rhs._attribute;
        // _docId = rhs._docId;
        _docId = 0;
        _basicAttributeType = rhs._basicAttributeType;
    }
    return *this;
}

void ArrayAtLookup::onPrepareResult()
{
    if (_attribute->isIntegerType()) {
        _basicAttributeType = BAT_INT;
        setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode()));
    } else if (_attribute->isFloatingPointType()) {
        _basicAttributeType = BAT_FLOAT;
        setResultType(std::unique_ptr<ResultNode>(new FloatResultNode()));
    } else {
        _basicAttributeType = BAT_STRING;
        setResultType(std::unique_ptr<ResultNode>(new StringResultNode()));
    }
}

bool ArrayAtLookup::onExecute() const
{
    getArg().execute();
    int64_t idx = getArg().getResult()->getInteger();
    // get attribute data
    size_t numValues = _attribute->getValueCount(_docId);
    if (idx < 0) {
        idx = 0;
    }
    if (idx >= (int64_t)numValues) {
        idx = numValues - 1;
    }

    if (_basicAttributeType == BAT_FLOAT) {
        std::vector<search::attribute::IAttributeVector::WeightedFloat> wVector;
        wVector.resize(numValues);
        _attribute->get(_docId, &wVector[0], numValues);
        std::vector<double> tmp;
        tmp.resize(numValues);
        for (size_t i = 0; i < numValues; ++i) {
            tmp[i] = wVector[i].getValue();
        }
        double result = 0;
        if (idx >= 0 && idx < (int64_t)numValues) {
            result = tmp[idx];
        }
        static_cast<FloatResultNode &>(updateResult()).set(result);
    } else if (_basicAttributeType == BAT_INT) {
        std::vector<search::attribute::IAttributeVector::WeightedInt> wVector;
        wVector.resize(numValues);
        _attribute->get(_docId, &wVector[0], numValues);
        std::vector<int64_t> tmp;
        tmp.resize(numValues);
        for (size_t i = 0; i < numValues; ++i) {
            tmp[i] = wVector[i].getValue();
        }
        int64_t result = 0;
        if (idx >= 0 && idx < (int64_t)numValues) {
            result = tmp[idx];
        }
        static_cast<Int64ResultNode &>(updateResult()).set(result);
    } else {
        std::vector<search::attribute::IAttributeVector::WeightedString> wVector;
        wVector.resize(numValues);
        _attribute->get(_docId, &wVector[0], numValues);
        std::vector<vespalib::string> tmp;
        tmp.resize(numValues);
        for (size_t i = 0; i < numValues; ++i) {
            tmp[i] = wVector[i].getValue();
        }
        vespalib::string result;
        if (idx >= 0 && idx < (int64_t)numValues) {
            result = tmp[idx];
        }
        static_cast<StringResultNode &>(updateResult()).set(result);
    }
    return true;
}

void ArrayAtLookup::wireAttributes(const search::attribute::IAttributeContext & attrCtx)
{
    _attribute = attrCtx.getAttribute(_attributeName);
    if (_attribute == NULL) {
        throw std::runtime_error(vespalib::make_string("Failed locating attribute vector '%s'", _attributeName.c_str()));
    }
}

Serializer & ArrayAtLookup::onSerialize(Serializer & os) const
{
    UnaryFunctionNode::onSerialize(os);
    os << _attributeName;
    return os;
}

Deserializer & ArrayAtLookup::onDeserialize(Deserializer & is)
{
    UnaryFunctionNode::onDeserialize(is);
    is >> _attributeName;
    return is;
}

} // namespace expression
} // namespace search
