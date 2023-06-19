// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributenode.h"
#include "resultvector.h"
#include "enumattributeresult.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <cassert>

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
    IntegerHandler(ResultNode & result) noexcept
        : Handler(),
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
    FloatHandler(ResultNode & result) noexcept
        : Handler(),
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
    StringHandler(ResultNode & result) noexcept
        : Handler(),
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
    EnumHandler(ResultNode & result) noexcept
        : Handler(),
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

template<typename T>
std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
createSingle() {
    return { std::make_unique<T>(), std::unique_ptr<AttributeNode::Handler>()};
}

template<typename T, typename H>
std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
createMulti() {
    auto result = std::make_unique<T>();
    auto handler = std::make_unique<H>(*result);
    return { std::move(result), std::move(handler)};
}

}

AttributeNode::AttributeNode() :
    FunctionNode(),
    _scratchResult(std::make_unique<AttributeResult>()),
    _index(nullptr),
    _keepAliveForIndexLookups(),
    _hasMultiValue(false),
    _useEnumOptimization(false),
    _needExecute(true),
    _handler(),
    _attributeName()
{}

AttributeNode::~AttributeNode() = default;

AttributeNode::AttributeNode(vespalib::stringref name)
    : FunctionNode(),
      _scratchResult(std::make_unique<AttributeResult>()),
      _index(nullptr),
      _keepAliveForIndexLookups(),
      _hasMultiValue(false),
      _useEnumOptimization(false),
      _needExecute(true),
      _handler(),
      _attributeName(name)
{}

AttributeNode::AttributeNode(const IAttributeVector & attribute)
    : FunctionNode(),
      _scratchResult(createResult(&attribute)),
      _index(nullptr),
      _keepAliveForIndexLookups(),
      _hasMultiValue(attribute.hasMultiValue()),
      _useEnumOptimization(false),
      _needExecute(true),
      _handler(),
      _attributeName(attribute.getName())
{}

AttributeNode::AttributeNode(const AttributeNode & attribute)
    : FunctionNode(attribute),
      _scratchResult(attribute._scratchResult->clone()),
      _index(nullptr),
      _keepAliveForIndexLookups(),
      _hasMultiValue(attribute._hasMultiValue),
      _useEnumOptimization(attribute._useEnumOptimization),
      _needExecute(true),
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
        _handler.reset();
        _keepAliveForIndexLookups.reset();
        _needExecute = true;
    }
    return *this;
}

std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
AttributeNode::createResultHandler(bool preserveAccurateTypes, const attribute::IAttributeVector & attribute) const {
    BasicType::Type basicType = attribute.getBasicType();
    if (attribute.isIntegerType()) {
        if (_hasMultiValue) {
            if (basicType == BasicType::BOOL) {
                return createMulti<BoolResultNodeVector, IntegerHandler<BoolResultNodeVector>>();
            } else if (preserveAccurateTypes) {
                switch (basicType) {
                    case BasicType::INT8:
                        return createMulti<Int8ResultNodeVector, IntegerHandler<Int8ResultNodeVector>>();
                    case BasicType::INT16:
                        return createMulti<Int16ResultNodeVector, IntegerHandler<Int16ResultNodeVector>>();
                    case BasicType::INT32:
                        return createMulti<Int32ResultNodeVector, IntegerHandler<Int32ResultNodeVector>>();
                    case BasicType::INT64:
                        return createMulti<Int64ResultNodeVector, IntegerHandler<Int64ResultNodeVector>>();
                    default:
                        throw std::runtime_error("This is no valid integer attribute " + attribute.getName());
                }
            } else {
                return createMulti<IntegerResultNodeVector, IntegerHandler<IntegerResultNodeVector>>();
            }
        } else {
            if (basicType == BasicType::BOOL) {
                return createSingle<BoolResultNode>();
            } else if (preserveAccurateTypes) {
                switch (basicType) {
                    case BasicType::INT8:
                        return createSingle<Int8ResultNode>();
                    case BasicType::INT16:
                        return createSingle<Int16ResultNode>();
                    case BasicType::INT32:
                        return createSingle<Int32ResultNode>();
                    case BasicType::INT64:
                        return createSingle<Int64ResultNode>();
                    default:
                        throw std::runtime_error("This is no valid integer attribute " + attribute.getName());
                }
            } else {
                return createSingle<Int64ResultNode>();
            }
        }
    } else if (attribute.isFloatingPointType()) {
        return (_hasMultiValue)
                ? createMulti<FloatResultNodeVector, FloatHandler>()
                : createSingle<FloatResultNode>();
    } else if (attribute.isStringType()) {
        if (_hasMultiValue) {
            return (_useEnumOptimization)
                    ? createMulti<EnumResultNodeVector, EnumHandler>()
                    : createMulti<StringResultNodeVector, StringHandler>();
        } else {
            return (_useEnumOptimization)
                    ? createSingle<EnumResultNode>()
                    : createSingle<StringResultNode>();
        }
    } else if (attribute.is_raw_type()) {
        if (_hasMultiValue) {
            throw std::runtime_error(make_string("Does not support multivalue raw attribute vector '%s'",
                                                 attribute.getName().c_str()));
        } else {
            return createSingle<RawResultNode>();
        }
    } else {
        throw std::runtime_error(make_string("Can not deduce correct resultclass for attribute vector '%s'",
                                             attribute.getName().c_str()));
    }
}

void
AttributeNode::onPrepare(bool preserveAccurateTypes)
{
    const IAttributeVector * attribute = getAttribute();
    if (attribute != nullptr) {
        auto[result, handler] = createResultHandler(preserveAccurateTypes, *attribute);
        _handler = std::move(handler);
        if (_index == nullptr) {
            setResultType(std::move(result));
        } else {
            assert(_hasMultiValue);
            assert(_handler);
            setResultType(result->createBaseType());
            assert(result->inherits(ResultNodeVector::classId));
            _keepAliveForIndexLookups.reset(dynamic_cast<ResultNodeVector *>(result.release()));
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

void
AttributeNode::EnumHandler::handle(const AttributeResult & r)
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
AttributeNode::setDocId(DocId docId) {
    _scratchResult->setDocId(docId);
    _needExecute = true;
}

bool
AttributeNode::onExecute() const
{
    if (_handler) {
        if (_needExecute) {
            _handler->handle(*_scratchResult);
            _needExecute = false;
        }
        if ((_index != nullptr) && !_keepAliveForIndexLookups->empty()) {
            assert(_hasMultiValue);
            size_t idx = std::min(size_t(_index->get()), _keepAliveForIndexLookups->size() - 1);
            updateResult().set(_keepAliveForIndexLookups->get(idx));
        }
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
