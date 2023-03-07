// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributenode.h"
#include "enumattributeresult.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>

namespace search::expression {

using namespace vespalib;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::BasicType;

IMPLEMENT_EXPRESSIONNODE(AttributeNode, FunctionNode);

template <typename V>
class AttributeNode::IntegerHandler : public AttributeNode::Handler
{
public:
    IntegerHandler(ResultNode & result) :
        Handler(),
        _vector(((V &)result).getVector()),
        _wVector()
    { }
    void handle(const AttributeResult & r) override;
private:
    typename V::Vector & _vector;
    std::vector<search::attribute::IAttributeVector::WeightedInt> _wVector;
};

class AttributeNode::FloatHandler : public AttributeNode::Handler
{
public:
    FloatHandler(ResultNode & result) :
        Handler(),
        _vector(((FloatResultNodeVector &)result).getVector()),
        _wVector()
    { }
    void handle(const AttributeResult & r) override;
private:
    FloatResultNodeVector::Vector & _vector;
    std::vector<search::attribute::IAttributeVector::WeightedFloat> _wVector;
};

class AttributeNode::StringHandler : public AttributeNode::Handler
{
public:
    StringHandler(ResultNode & result) :
        Handler(),
        _vector(((StringResultNodeVector &)result).getVector()),
        _wVector()
    { }
    void handle(const AttributeResult & r) override;
private:
    StringResultNodeVector::Vector & _vector;
    std::vector<search::attribute::IAttributeVector::WeightedConstChar> _wVector;
};

class AttributeNode::EnumHandler : public AttributeNode::Handler
{
public:
    EnumHandler(ResultNode & result) :
        Handler(),
        _vector(((EnumResultNodeVector &)result).getVector()),
        _wVector()
    { }
    void handle(const AttributeResult & r) override;
private:
    EnumResultNodeVector::Vector &_vector;
    std::vector<search::attribute::IAttributeVector::WeightedEnum> _wVector;
};

namespace {

std::unique_ptr<AttributeResult>
createResult(const IAttributeVector * attribute)
{
    IAttributeVector::EnumRefs enumRefs = attribute->make_enum_read_view();
    if (enumRefs.empty()) {
        if (attribute->isIntegerType()) return std::make_unique<IntegerAttributeResult>(attribute, 0);
        if (attribute->isFloatingPointType()) return std::make_unique<FloatAttributeResult>(attribute, 0);
        return std::make_unique<AttributeResult>(attribute, 0);
    }
    return std::make_unique<EnumAttributeResult>(enumRefs, attribute, 0);
}

}

AttributeNode::AttributeNode() :
    FunctionNode(),
    _scratchResult(std::make_unique<AttributeResult>()),
    _hasMultiValue(false),
    _useEnumOptimization(false),
    _handler(),
    _attributeName()
{}

AttributeNode::~AttributeNode() = default;

AttributeNode::AttributeNode(vespalib::stringref name) :
    FunctionNode(),
    _scratchResult(std::make_unique<AttributeResult>()),
    _hasMultiValue(false),
    _useEnumOptimization(false),
    _handler(),
    _attributeName(name)
{}
AttributeNode::AttributeNode(const IAttributeVector & attribute) :
    FunctionNode(),
    _scratchResult(createResult(&attribute)),
    _hasMultiValue(attribute.hasMultiValue()),
    _useEnumOptimization(false),
    _handler(),
    _attributeName(attribute.getName())
{}

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

AttributeNode &
AttributeNode::operator = (const AttributeNode & attr)
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

void
AttributeNode::onPrepare(bool preserveAccurateTypes)
{
    const IAttributeVector * attribute = _scratchResult->getAttribute();
    if (attribute != nullptr) {
        BasicType::Type basicType = attribute->getBasicType();
        if (attribute->isIntegerType()) {
            if (_hasMultiValue) {
                if (basicType == BasicType::BOOL) {
                    setResultType(std::make_unique<BoolResultNodeVector>());
                    _handler = std::make_unique<IntegerHandler<BoolResultNodeVector>>(updateResult());
                } else if (preserveAccurateTypes) {
                    switch (basicType) {
                      case BasicType::INT8:
                        setResultType(std::make_unique<Int8ResultNodeVector>());
                        _handler = std::make_unique<IntegerHandler<Int8ResultNodeVector>>(updateResult());
                        break;
                      case BasicType::INT16:
                        setResultType(std::make_unique<Int16ResultNodeVector>());
                        _handler = std::make_unique<IntegerHandler<Int16ResultNodeVector>>(updateResult());
                        break;
                      case BasicType::INT32:
                        setResultType(std::make_unique<Int32ResultNodeVector>());
                        _handler = std::make_unique<IntegerHandler<Int32ResultNodeVector>>(updateResult());
                        break;
                      case BasicType::INT64:
                        setResultType(std::make_unique<Int64ResultNodeVector>());
                        _handler = std::make_unique<IntegerHandler<Int64ResultNodeVector>>(updateResult());
                        break;
                      default:
                        throw std::runtime_error("This is no valid integer attribute " + attribute->getName());
                        break;
                    }
                } else {
                    setResultType(std::make_unique<IntegerResultNodeVector>());
                    _handler = std::make_unique<IntegerHandler<IntegerResultNodeVector>>(updateResult());
                }
            } else {
                if (basicType == BasicType::BOOL) {
                    setResultType(std::make_unique<BoolResultNode>());
                } else if (preserveAccurateTypes) {
                    switch (basicType) {
                      case BasicType::INT8:
                        setResultType(std::make_unique<Int8ResultNode>());
                        break;
                      case BasicType::INT16:
                        setResultType(std::make_unique<Int16ResultNode>());
                        break;
                      case BasicType::INT32:
                        setResultType(std::make_unique<Int32ResultNode>());
                        break;
                      case BasicType::INT64:
                        setResultType(std::make_unique<Int64ResultNode>());
                        break;
                      default:
                        throw std::runtime_error("This is no valid integer attribute " + attribute->getName());
                        break;
                    }
                } else {
                    setResultType(std::make_unique<Int64ResultNode>());
                }
            }
        } else if (attribute->isFloatingPointType()) {
            if (_hasMultiValue) {
                setResultType(std::make_unique<FloatResultNodeVector>());
                _handler = std::make_unique<FloatHandler>(updateResult());
            } else {
                setResultType(std::make_unique<FloatResultNode>());
            }
        } else if (attribute->isStringType()) {
            if (_hasMultiValue) {
                if (_useEnumOptimization) {
                    setResultType(std::make_unique<EnumResultNodeVector>());
                    _handler = std::make_unique<EnumHandler>(updateResult());
                } else {
                    setResultType(std::make_unique<StringResultNodeVector>());
                    _handler = std::make_unique<StringHandler>(updateResult());
                }
            } else {
                if (_useEnumOptimization) {
                    setResultType(std::make_unique<EnumResultNode>());
                } else {
                    setResultType(std::make_unique<StringResultNode>());
                }
            }
        } else if (attribute->is_raw_type()) {
            if (_hasMultiValue) {
                throw std::runtime_error(make_string("Does not support multivalue raw attribute vector '%s'",
                                                     attribute->getName().c_str()));
            } else {
                setResultType(std::make_unique<RawResultNode>());
            }
        } else {
            throw std::runtime_error(make_string("Can not deduce correct resultclass for attribute vector '%s'",
                                                 attribute->getName().c_str()));
        }
    }
}

template <typename V>
void
AttributeNode::IntegerHandler<V>::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), _wVector.data(), _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

void
AttributeNode::FloatHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), _wVector.data(), _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

void
AttributeNode::StringHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), _wVector.data(), _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

void AttributeNode::EnumHandler::handle(const AttributeResult & r)
{
    size_t numValues = r.getAttribute()->getValueCount(r.getDocId());
    _vector.resize(numValues);
    _wVector.resize(numValues);
    r.getAttribute()->get(r.getDocId(), _wVector.data(), _wVector.size());
    for(size_t i(0); i < numValues; i++) {
        _vector[i] = _wVector[i].getValue();
    }
}

bool AttributeNode::onExecute() const
{
    if (_handler) {
        _handler->handle(*_scratchResult);
    } else {
        updateResult().set(*_scratchResult);
    }
    return true;
}

void
AttributeNode::wireAttributes(const IAttributeContext & attrCtx)
{
    const IAttributeVector * attribute(_scratchResult ? _scratchResult->getAttribute() : nullptr);
    if (attribute == nullptr) {
        if (_useEnumOptimization) {
            attribute = attrCtx.getAttributeStableEnum(_attributeName);
        } else {
            attribute = attrCtx.getAttribute(_attributeName);
        }
        if (attribute == nullptr) {
            throw std::runtime_error(make_string("Failed locating attribute vector '%s'", _attributeName.c_str()));
        }
        _hasMultiValue = attribute->hasMultiValue();
        _scratchResult = createResult(attribute);
    }
}

void
AttributeNode::cleanup()
{
    _scratchResult.reset();
}

Serializer &
AttributeNode::onSerialize(Serializer & os) const
{
    FunctionNode::onSerialize(os);
    return os << _attributeName;
}

Deserializer &
AttributeNode::onDeserialize(Deserializer & is)
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

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_attributenode() {}
