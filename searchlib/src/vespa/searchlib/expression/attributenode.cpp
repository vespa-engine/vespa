// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributenode.h"
#include <vespa/searchlib/attribute/singleenumattribute.h>

namespace search {
namespace expression {

using namespace vespalib;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::BasicType;

IMPLEMENT_EXPRESSIONNODE(AttributeNode, FunctionNode);
IMPLEMENT_RESULTNODE(AttributeResult, ResultNode);

namespace {

class EnumAttributeResult : public AttributeResult
{
public:
    DECLARE_RESULTNODE(EnumAttributeResult);
    EnumAttributeResult(const attribute::IAttributeVector * attribute, DocId docId) :
        AttributeResult(attribute, docId),
        _enumAttr(dynamic_cast<const SingleValueEnumAttributeBase *>(attribute))
    {
    }
private:
    EnumAttributeResult() :
        AttributeResult(),
        _enumAttr(NULL)
    { }
    int64_t onGetEnum(size_t index) const override { (void) index; return (static_cast<int64_t>(_enumAttr->getE(getDocId()))); }
    const SingleValueEnumAttributeBase * _enumAttr;
};

IMPLEMENT_RESULTNODE(EnumAttributeResult, AttributeResult);

AttributeResult::UP createResult(const IAttributeVector * attribute)
{
    return (dynamic_cast<const SingleValueEnumAttributeBase *>(attribute) != NULL)
        ? AttributeResult::UP(new EnumAttributeResult(attribute, 0))
        : AttributeResult::UP(new AttributeResult(attribute, 0));
}

}

AttributeNode::AttributeNode() :
    FunctionNode(),
    _scratchResult(new AttributeResult()),
    _hasMultiValue(false),
    _useEnumOptimization(false),
    _handler(),
    _attributeName()
{
}

AttributeNode::AttributeNode(const vespalib::stringref &name) :
    FunctionNode(),
    _scratchResult(new AttributeResult()),
    _hasMultiValue(false),
    _useEnumOptimization(false),
    _handler(),
    _attributeName(name)
{
}
AttributeNode::AttributeNode(const IAttributeVector & attribute) :
    FunctionNode(),
    _scratchResult(createResult(&attribute)),
    _hasMultiValue(attribute.hasMultiValue()),
    _useEnumOptimization(false),
    _handler(),
    _attributeName(attribute.getName())
{
}

AttributeNode::AttributeNode(const AttributeNode & attribute) :
    FunctionNode(attribute),
    _scratchResult(attribute._scratchResult->clone()),
    _hasMultiValue(attribute._hasMultiValue),
    _useEnumOptimization(attribute._useEnumOptimization),
    _handler(),
    _attributeName(attribute._attributeName)
{
    _scratchResult->setDocId(0);
}

AttributeNode & AttributeNode::operator = (const AttributeNode & attr)
{
    if (this != &attr) {
        FunctionNode::operator = (attr);
        _attributeName = attr._attributeName;
        _hasMultiValue = attr._hasMultiValue;
        _useEnumOptimization = attr._useEnumOptimization;
        _scratchResult.reset(attr._scratchResult->clone());
        _scratchResult->setDocId(0);
    }
    return *this;
}

void AttributeNode::onPrepare(bool preserveAccurateTypes)
{
    const IAttributeVector * attribute = _scratchResult->getAttribute();
    if (attribute != NULL) {
        BasicType::Type basicType = attribute->getBasicType();
        if (attribute->isIntegerType()) {
            if (_hasMultiValue) {
                if (preserveAccurateTypes) {
                    switch (basicType) {
                      case BasicType::INT8:
                        setResultType(std::unique_ptr<ResultNode>(new Int8ResultNodeVector()));
                        break;
                      case BasicType::INT16:
                        setResultType(std::unique_ptr<ResultNode>(new Int16ResultNodeVector()));
                        break;
                      case BasicType::INT32:
                        setResultType(std::unique_ptr<ResultNode>(new Int32ResultNodeVector()));
                        break;
                      case BasicType::INT64:
                        setResultType(std::unique_ptr<ResultNode>(new Int64ResultNodeVector()));
                        break;
                      default:
                        throw std::runtime_error("This is no valid integer attribute " + attribute->getName());
                        break;
                    }
                } else {
                    setResultType(std::unique_ptr<ResultNode>(new IntegerResultNodeVector()));
                }
               _handler.reset(new IntegerHandler(updateResult()));
            } else {
                if (preserveAccurateTypes) {
                    switch (basicType) {
                      case BasicType::INT8:
                        setResultType(std::unique_ptr<ResultNode>(new Int8ResultNode()));
                        break;
                      case BasicType::INT16:
                        setResultType(std::unique_ptr<ResultNode>(new Int16ResultNode()));
                        break;
                      case BasicType::INT32:
                        setResultType(std::unique_ptr<ResultNode>(new Int32ResultNode()));
                        break;
                      case BasicType::INT64:
                        setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode()));
                        break;
                      default:
                        throw std::runtime_error("This is no valid integer attribute " + attribute->getName());
                        break;
                    }
                } else {
                    setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode()));
                }
            }
        } else if (attribute->isFloatingPointType()) {
            if (_hasMultiValue) {
                setResultType(std::unique_ptr<ResultNode>(new FloatResultNodeVector()));
               _handler.reset(new FloatHandler(updateResult()));
            } else {
                setResultType(std::unique_ptr<ResultNode>(new FloatResultNode()));
            }
        } else if (attribute->isStringType()) {
            if (_hasMultiValue) {
                if (_useEnumOptimization) {
                    setResultType(std::unique_ptr<ResultNode>(new EnumResultNodeVector()));
                    _handler.reset(new EnumHandler(updateResult()));
                } else {
                    setResultType(std::unique_ptr<ResultNode>(new StringResultNodeVector()));
                   _handler.reset(new StringHandler(updateResult()));
                }
            } else {
                if (_useEnumOptimization) {
                    setResultType(std::unique_ptr<ResultNode>(new EnumResultNode()));
                } else {
                    setResultType(std::unique_ptr<ResultNode>(new StringResultNode()));
                }
            }
        } else {
            throw std::runtime_error(make_string("Can not deduce correct resultclass for attribute vector '%s'",
                                                 attribute->getName().c_str()));
        }
    }
}

void AttributeNode::IntegerHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), &_wVector[0], _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

void AttributeNode::FloatHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), &_wVector[0], _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

void AttributeNode::StringHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), &_wVector[0], _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

void AttributeNode::EnumHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), &_wVector[0], _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

bool AttributeNode::onExecute() const
{
    if (_hasMultiValue) {
        _handler->handle(*_scratchResult);
    } else {
        updateResult().set(*_scratchResult);
    }
    return true;
}

void AttributeNode::wireAttributes(const IAttributeContext & attrCtx)
{
    const IAttributeVector * attribute(_scratchResult ? _scratchResult->getAttribute() : nullptr);
    if (attribute == NULL) {
        if (_useEnumOptimization) {
            attribute = attrCtx.getAttributeStableEnum(_attributeName);
        } else {
            attribute = attrCtx.getAttribute(_attributeName);
        }
        if (attribute == NULL) {
            throw std::runtime_error(make_string("Failed locating attribute vector '%s'", _attributeName.c_str()));
        }
        _hasMultiValue = attribute->hasMultiValue();
        _scratchResult = createResult(attribute);
    }
}

void AttributeNode::cleanup()
{
    _scratchResult.reset();
}

Serializer & AttributeNode::onSerialize(Serializer & os) const
{
    FunctionNode::onSerialize(os);
    return os << _attributeName;
}

Deserializer & AttributeNode::onDeserialize(Deserializer & is)
{
    FunctionNode::onDeserialize(is);

    return is >> _attributeName;
}

void
AttributeNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "attributeName", _attributeName);
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_attributenode() {}
