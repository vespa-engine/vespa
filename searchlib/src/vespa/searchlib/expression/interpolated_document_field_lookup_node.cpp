// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "interpolated_document_field_lookup_node.h"
#include "simple_interpolate.h"
#include <vespa/document/fieldvalue/document.h>

using vespalib::Serializer;
using vespalib::Deserializer;

namespace search::expression {

namespace {

class InterpolateHandler : public InterpolatedDocumentFieldLookupNode::Handler {
    std::vector<double>& _values;
public:
    InterpolateHandler(std::vector<double>& values);
    ~InterpolateHandler() override;
    void reset() override;
    void onPrimitive(uint32_t fid, const Content& c) override;
};

InterpolateHandler::InterpolateHandler(std::vector<double>& values)
    : InterpolatedDocumentFieldLookupNode::Handler(),
      _values(values)
{
}

InterpolateHandler::~InterpolateHandler() = default;

void
InterpolateHandler::reset()
{
}

void
InterpolateHandler::onPrimitive(uint32_t, const Content& c)
{
    _values.push_back(c.getValue().getAsDouble());
}

}

IMPLEMENT_EXPRESSIONNODE(InterpolatedDocumentFieldLookupNode, DocumentFieldNode);

InterpolatedDocumentFieldLookupNode::InterpolatedDocumentFieldLookupNode() noexcept
    : DocumentFieldNode(),
      _lookup_expression(),
      _values(),
      _float_result()
{
}

InterpolatedDocumentFieldLookupNode::InterpolatedDocumentFieldLookupNode(vespalib::stringref name, std::unique_ptr<ExpressionNode> arg)
    : DocumentFieldNode(name),
      _lookup_expression(std::move(arg)),
      _values(),
      _float_result()
{
}

InterpolatedDocumentFieldLookupNode::InterpolatedDocumentFieldLookupNode(const InterpolatedDocumentFieldLookupNode &rhs) = default;

InterpolatedDocumentFieldLookupNode::~InterpolatedDocumentFieldLookupNode() = default;

InterpolatedDocumentFieldLookupNode&
InterpolatedDocumentFieldLookupNode::operator=(const InterpolatedDocumentFieldLookupNode &rhs) = default;

Serializer &
InterpolatedDocumentFieldLookupNode::onSerialize(Serializer & os) const
{
    os << _value;
    os << uint32_t(1) << _lookup_expression;
    os << _fieldName;
    return os;
}

Deserializer &
InterpolatedDocumentFieldLookupNode::onDeserialize(Deserializer & is)
{
    is >> _value;
    uint32_t count(0);
    is >> count;
    if (count > 0) {
        is >> _lookup_expression;
    } else {
        _lookup_expression.reset();
    }
    is >> _fieldName;
    return is;
}

void
InterpolatedDocumentFieldLookupNode::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    DocumentFieldNode::visitMembers(visitor);
    visit(visitor, "index", *_lookup_expression);
}

void
InterpolatedDocumentFieldLookupNode::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    DocumentFieldNode::selectMembers(predicate, operation);
    if (_lookup_expression) {
        _lookup_expression->select(predicate, operation);
    }
}

void
InterpolatedDocumentFieldLookupNode::onPrepare(bool)
{
    _handler = std::make_unique<InterpolateHandler>(_values);
    _value = std::make_unique<FloatResultNode>();
}

bool
InterpolatedDocumentFieldLookupNode::onExecute() const
{
    if (_lookup_expression) {
        _values.clear();
        _doc->iterateNested(_fieldPath.getFullRange(), *_handler);
        _lookup_expression->execute();
        auto lookup = _lookup_expression->getResult()->getFloat();
        auto result = simple_interpolate(_values, lookup);
        _float_result.set(result);
    } else {
        _float_result.set(0.0);
    }
    _value->set(_float_result);
    return true;
}

}
