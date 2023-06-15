// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "interpolatedlookupfunctionnode.h"
#include "floatresultnode.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::expression {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(InterpolatedLookup, AttributeNode);

InterpolatedLookup::InterpolatedLookup() noexcept
    : AttributeNode(),
      _lookupExpression()
{
}

InterpolatedLookup::~InterpolatedLookup() = default;

InterpolatedLookup::InterpolatedLookup(const vespalib::string &attribute, ExpressionNode::UP arg)
    : AttributeNode(attribute),
      _lookupExpression(std::move(arg))
{
}

InterpolatedLookup::InterpolatedLookup(const attribute::IAttributeVector &attr, ExpressionNode::UP arg)
    : AttributeNode(attr),
      _lookupExpression(std::move(arg))
{
}


InterpolatedLookup::InterpolatedLookup(const InterpolatedLookup &rhs) = default;
InterpolatedLookup & InterpolatedLookup::operator= (const InterpolatedLookup &rhs) = default;

namespace {

double
simpleInterpolate(const std::vector<double> & v, double lookup) {
    if (v.empty() || lookup < v[0])
        return 0;
    for (size_t i = 1; i < v.size(); ++i) {
        if (lookup < v[i]) {
            double total = v[i] - v[i - 1];
            double above = lookup - v[i - 1];
            double result = i - 1;
            result += (above / total);
            return result;
        }
    }
    return v.size() - 1;
}

class InterpolateHandler : public AttributeNode::Handler {
public:
    InterpolateHandler(FloatResultNode & result, const ExpressionNode * lookupExpression) noexcept
        : AttributeNode::Handler(),
          _lookupExpression(lookupExpression),
          _result(result),
          _values()
    { }
    void handle(const AttributeResult & r) override;
private:
    const ExpressionNode *_lookupExpression;
    FloatResultNode      &_result;
    std::vector<double>   _values;
};

void
InterpolateHandler::handle(const AttributeResult &r) {
    _lookupExpression->execute();
    double lookup = _lookupExpression->getResult()->getFloat();
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _values.resize(numValues);
    r.getAttribute()->get(r.getDocId(), _values.data(), _values.size());
    _result.set(simpleInterpolate(_values, lookup));
}

}

std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
InterpolatedLookup::createResultHandler(bool, const attribute::IAttributeVector &) const {
    auto result = std::make_unique<FloatResultNode>();
    auto handler = std::make_unique<InterpolateHandler>(*result, _lookupExpression.get());
    return { std::move(result), std::move(handler) };
}

Serializer &
InterpolatedLookup::onSerialize(Serializer & os) const
{
    // Here we are doing a dirty skipping AttributeNode in the inheritance.
    // This is due to refactoring and the need to keep serialization the same.
    FunctionNode::onSerialize(os);
    os << uint32_t(1u) << _lookupExpression; // Simulating a single element vector.
    os << _attributeName;
    return os;
}

Deserializer &
InterpolatedLookup::onDeserialize(Deserializer & is)
{
    // See comment in onSerialize method.
    FunctionNode::onDeserialize(is);
    uint32_t count(0);
    is >> count;
    if (count > 0) {
        is >> _lookupExpression;
    } else {
        _lookupExpression.reset();
    }
    is >> _attributeName;
    return is;
}

void
InterpolatedLookup::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeNode::visitMembers(visitor);
    visit(visitor, "index", *_lookupExpression);
}

void
InterpolatedLookup::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    AttributeNode::selectMembers(predicate, operation);
    if (_lookupExpression) {
        _lookupExpression->select(predicate, operation);
    }
}

}
