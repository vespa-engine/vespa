// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "interpolatedlookupfunctionnode.h"
#include "floatresultnode.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search {
namespace expression {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(InterpolatedLookup, UnaryFunctionNode);

InterpolatedLookup::InterpolatedLookup()
    : _attribute(0),
      _docId(0)
{
}

InterpolatedLookup::~InterpolatedLookup()
{
}

InterpolatedLookup::InterpolatedLookup(const vespalib::string &attribute, ExpressionNode::UP arg)
    : UnaryFunctionNode(std::move(arg)),
      _attributeName(attribute),
      _attribute(0),
      _docId(0)
{
}

InterpolatedLookup::InterpolatedLookup(const attribute::IAttributeVector &attr, ExpressionNode::UP lookupArg)
    : UnaryFunctionNode(std::move(lookupArg)),
      _attributeName(attr.getName()),
      _attribute(&attr),
      _docId(0)
{
}


InterpolatedLookup::InterpolatedLookup(const InterpolatedLookup &rhs) :
    UnaryFunctionNode(rhs),
    _attributeName(rhs._attributeName),
    _attribute(rhs._attribute),
    _docId(rhs._docId)
{
    // why?
    _docId = 0;
}

InterpolatedLookup &
InterpolatedLookup::operator= (const InterpolatedLookup &rhs)
{
    if (this != &rhs) {
        UnaryFunctionNode::operator =(rhs);
        _attributeName = rhs._attributeName;
        _attribute = rhs._attribute;
        // _docId = rhs._docId;
        _docId = 0;
    }
    return *this;
}

void InterpolatedLookup::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new FloatResultNode()));
}

static double
simpleInterpolate(size_t sz, std::vector<double> v, double lookup)
{
    if (sz == 0 || lookup < v[0])
        return 0;
    for (size_t i = 1; i < sz; ++i) {
        if (lookup < v[i]) {
            double total = v[i] - v[i-1];
            double above = lookup - v[i-1];
            double result = i - 1;
            result += (above / total);
            return result;
        }
    }
    return sz - 1;
}

bool InterpolatedLookup::onExecute() const
{
    getArg().execute();
    double lookup = getArg().getResult()->getFloat();
    // get attribute data
    size_t numValues = _attribute->getValueCount(_docId);
    std::vector<double> valueVector;
    valueVector.resize(numValues);
    _attribute->get(_docId, &valueVector[0], numValues);
    double result = simpleInterpolate(numValues, valueVector, lookup);
    static_cast<FloatResultNode &>(updateResult()).set(result);
    return true;
}

void InterpolatedLookup::wireAttributes(const search::attribute::IAttributeContext & attrCtx)
{
    _attribute = attrCtx.getAttribute(_attributeName);
    if (_attribute == NULL) {
        throw std::runtime_error(vespalib::make_string("Failed locating attribute vector '%s'", _attributeName.c_str()));
    }
}

Serializer & InterpolatedLookup::onSerialize(Serializer & os) const
{
    UnaryFunctionNode::onSerialize(os);
    os << _attributeName;
    return os;
}

Deserializer & InterpolatedLookup::onDeserialize(Deserializer & is)
{
    UnaryFunctionNode::onDeserialize(is);
    is >> _attributeName;
    return is;
}

} // namespace expression
} // namespace search
