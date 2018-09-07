// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_keyed_node.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/common/undefinedvalues.h>

using search::attribute::AttributeContent;
using search::attribute::IAttributeVector;
using search::attribute::BasicType;
using search::attribute::getUndefined;
using EnumHandle = IAttributeVector::EnumHandle;

namespace search::expression {

class AttributeKeyedNode::KeyHandler
{
protected:
    const IAttributeVector &_attribute;

    KeyHandler(const IAttributeVector &attribute)
        : _attribute(attribute)
    {
    }
public:
    static uint32_t noKeyIdx() { return std::numeric_limits<uint32_t>::max(); }
    virtual ~KeyHandler() = default;
    virtual uint32_t handle(DocId docId) = 0;
};

namespace {

class BadKeyHandler : public AttributeKeyedNode::KeyHandler
{
public:
    BadKeyHandler(const IAttributeVector &attribute)
        : KeyHandler(attribute)
    {
    }
    uint32_t handle(DocId) override { return noKeyIdx(); }
};

template <typename KeyType>
KeyType convertKey(const IAttributeVector &, const vespalib::string &key)
{
    KeyType ret;
    vespalib::asciistream is(key);
    is >> ret;
    return ret;
}

template <>
vespalib::string convertKey<vespalib::string>(const IAttributeVector &, const vespalib::string &key)
{
    return key;
}

template <>
EnumHandle convertKey<EnumHandle>(const IAttributeVector &attribute, const vespalib::string &key)
{
    EnumHandle ret;
    if (!attribute.findEnum(key.c_str(), ret)) {
        ret = EnumHandle();
    }
    return ret;
}

template <typename T, typename KeyType = T>
class KeyHandlerT : public AttributeKeyedNode::KeyHandler
{
    AttributeContent<T> _keys;
    KeyType _key;

public:
    KeyHandlerT(const IAttributeVector &attribute, const vespalib::string &key)
        : KeyHandler(attribute),
          _keys(),
          _key(convertKey<KeyType>(attribute, key))
    {
    }
    ~KeyHandlerT() override;
    uint32_t handle(DocId docId) override {
        _keys.fill(_attribute, docId);
        for (uint32_t i = 0; i < _keys.size(); ++i) {
            if (_key == _keys[i]) {
                return i;
            }
        }
        return noKeyIdx();
    }
};

template <typename T, typename KeyType>
KeyHandlerT<T,KeyType>::~KeyHandlerT()
{
}

using IntegerKeyHandler = KeyHandlerT<IAttributeVector::largeint_t>;
using FloatKeyHandler   = KeyHandlerT<double>;
using StringKeyHandler  = KeyHandlerT<const char *, vespalib::string>;
using EnumKeyHandler    = KeyHandlerT<EnumHandle>;

class ValueHandler : public AttributeNode::Handler
{
protected:
    std::unique_ptr<AttributeKeyedNode::KeyHandler> _keyHandler;
    const IAttributeVector &_attribute;
    ValueHandler(std::unique_ptr<AttributeKeyedNode::KeyHandler> keyHandler, const IAttributeVector &attribute)
        : _keyHandler(std::move(keyHandler)),
          _attribute(attribute)
    {
    }
};

template <typename T, typename ResultNodeType>
class ValueHandlerT : public ValueHandler
{
    AttributeContent<T> _values;
    ResultNodeType &_result;
    T _undefinedValue;
public:
    ValueHandlerT(std::unique_ptr<AttributeKeyedNode::KeyHandler> keyHandler, const IAttributeVector &attribute, ResultNodeType &result, T undefinedValue)
        : ValueHandler(std::move(keyHandler), attribute),
          _values(),
          _result(result),
          _undefinedValue(undefinedValue)
    {
    }
    void handle(const AttributeResult & r) override {
        uint32_t docId = r.getDocId();
        uint32_t keyIdx  = _keyHandler->handle(docId);
        if (keyIdx != AttributeKeyedNode::KeyHandler::noKeyIdx()) {
            _values.fill(_attribute, docId);
            if (keyIdx < _values.size()) {
                _result = _values[keyIdx];
                return;
            }
        }
        _result = _undefinedValue;
    }
};

template <typename ResultNodeType>
using IntegerValueHandler = ValueHandlerT<IAttributeVector::largeint_t, ResultNodeType>;
using FloatValueHandler   = ValueHandlerT<double, FloatResultNode>;
using StringValueHandler  = ValueHandlerT<const char *, StringResultNode>;
using EnumValueHandler    = ValueHandlerT<EnumHandle, EnumResultNode>;

const IAttributeVector *findAttribute(const search::attribute::IAttributeContext &attrCtx, bool useEnumOptimization, const vespalib::string &name)
{
    const IAttributeVector *attribute = useEnumOptimization ? attrCtx.getAttributeStableEnum(name) : attrCtx.getAttribute(name);
    if (attribute == nullptr) {
        throw std::runtime_error(vespalib::make_string("Failed locating attribute vector '%s'", name.c_str()));
    }
    return attribute;
}

IAttributeVector::largeint_t getUndefinedValue(BasicType::Type basicType)
{
    switch (basicType) {
    case BasicType::INT8:
        return getUndefined<int8_t>();
    case BasicType::INT16:
        return getUndefined<int16_t>();
    case BasicType::INT32:
        return getUndefined<int32_t>();
    case BasicType::INT64:
        return getUndefined<int64_t>();
        break;
    default:
        return 0;
    }
}

}

AttributeKeyedNode::AttributeKeyedNode()
    : AttributeNode(),
      _keyAttributeName(),
      _valueAttributeName(),
      _key(),
      _keyAttribute(nullptr)
{
}

AttributeKeyedNode::AttributeKeyedNode(const AttributeKeyedNode &) = default;

AttributeKeyedNode::AttributeKeyedNode(vespalib::stringref name)
    : AttributeNode(name),
      _keyAttributeName(),
      _valueAttributeName(),
      _key(),
      _keyAttribute(nullptr)
{
    setupAttributeNames();
}

AttributeKeyedNode::~AttributeKeyedNode() = default;

AttributeKeyedNode &
AttributeKeyedNode::operator=(const AttributeKeyedNode &rhs) = default;

void
AttributeKeyedNode::setupAttributeNames()
{
    vespalib::asciistream keyName;
    vespalib::asciistream valueName;
    auto leftBracePos = _attributeName.find('{');
    auto leftQuotePos = _attributeName.find('"', leftBracePos + 1);
    auto rightQuotePos = _attributeName.find('"', leftQuotePos + 1);
    auto rightBracePos = _attributeName.find('}', rightQuotePos + 1);
    auto baseName = _attributeName.substr(0, leftBracePos);
    keyName << baseName << ".key";
    valueName << baseName << ".value" << _attributeName.substr(rightBracePos + 1);
    _keyAttributeName = keyName.str();
    _valueAttributeName = valueName.str();
    _key = _attributeName.substr(leftQuotePos + 1, rightQuotePos - leftQuotePos - 1);
}

template <typename ResultNodeType>
void
AttributeKeyedNode::prepareIntValues(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, IAttributeVector::largeint_t undefinedValue)
{
    auto resultNode = std::make_unique<ResultNodeType>();
    _handler = std::make_unique<IntegerValueHandler<ResultNodeType>>(std::move(keyHandler), attribute, *resultNode, undefinedValue);
    setResultType(std::move(resultNode));
}

std::unique_ptr<AttributeKeyedNode::KeyHandler>
AttributeKeyedNode::makeKeyHandlerHelper()
{
    const IAttributeVector &attribute = *_keyAttribute;
    if (attribute.hasEnum() && _useEnumOptimization) {
        return std::make_unique<EnumKeyHandler>(attribute, _key);
    } else if (attribute.isIntegerType()) {
        return std::make_unique<IntegerKeyHandler>(attribute, _key);
    } else if (attribute.isFloatingPointType()) {
        return std::make_unique<FloatKeyHandler>(attribute, _key);
    } else if (attribute.isStringType()) {
        return std::make_unique<StringKeyHandler>(attribute, _key);
    } else {
        return std::make_unique<BadKeyHandler>(attribute);
    }
}

std::unique_ptr<AttributeKeyedNode::KeyHandler>
AttributeKeyedNode::makeKeyHandler()
{
    try {
        return makeKeyHandlerHelper();
    } catch (const vespalib::IllegalArgumentException &) {
        return std::make_unique<BadKeyHandler>(*_keyAttribute);
    }
}

void
AttributeKeyedNode::onPrepare(bool preserveAccurateTypes)
{
    auto keyHandler = makeKeyHandler();
    const IAttributeVector * attribute = _scratchResult->getAttribute();
    if (attribute != nullptr) {
        BasicType::Type basicType = attribute->getBasicType();
        if (attribute->isIntegerType()) {
            IAttributeVector::largeint_t undefinedValue = getUndefinedValue(basicType);
            if (preserveAccurateTypes) {
                switch (basicType) {
                case BasicType::INT8:
                    prepareIntValues<Int8ResultNode>(std::move(keyHandler), *attribute, undefinedValue);
                    break;
                case BasicType::INT16:
                    prepareIntValues<Int16ResultNode>(std::move(keyHandler), *attribute, undefinedValue);
                    break;
                case BasicType::INT32:
                    prepareIntValues<Int32ResultNode>(std::move(keyHandler), *attribute, undefinedValue);
                    break;
                case BasicType::INT64:
                    prepareIntValues<Int64ResultNode>(std::move(keyHandler), *attribute, undefinedValue);
                    break;
                default:
                    throw std::runtime_error("This is no valid integer attribute " + attribute->getName());
                    break;
                }
            } else {
                prepareIntValues<Int64ResultNode>(std::move(keyHandler), *attribute, undefinedValue);
            }
        } else if (attribute->isFloatingPointType()) {
            auto resultNode = std::make_unique<FloatResultNode>();
            _handler = std::make_unique<FloatValueHandler>(std::move(keyHandler), *attribute, *resultNode, getUndefined<double>());
            setResultType(std::move(resultNode));
        } else if (attribute->isStringType()) {
            if (_useEnumOptimization) {
                auto resultNode = std::make_unique<EnumResultNode>();
                _handler = std::make_unique<EnumValueHandler>(std::move(keyHandler), *attribute, *resultNode, EnumHandle());
                setResultType(std::move(resultNode));
            } else {
                auto resultNode = std::make_unique<StringResultNode>();
                _handler = std::make_unique<StringValueHandler>(std::move(keyHandler), *attribute, *resultNode, "");
                setResultType(std::move(resultNode));
            }
        } else {
            throw std::runtime_error(vespalib::make_string("Can not deduce correct resultclass for attribute vector '%s'",
                                                           attribute->getName().c_str()));
        }
    }
}

void
AttributeKeyedNode::cleanup()
{
    _keyAttribute = nullptr;
    AttributeNode::cleanup();
}

void
AttributeKeyedNode::wireAttributes(const search::attribute::IAttributeContext &attrCtx)
{
    auto valueAttribute = findAttribute(attrCtx, _useEnumOptimization, _valueAttributeName);
    _hasMultiValue = false;
    _scratchResult = std::make_unique<AttributeResult>(valueAttribute, 0);
    _keyAttribute = findAttribute(attrCtx, _useEnumOptimization, _keyAttributeName);
}

void
AttributeKeyedNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeNode::visitMembers(visitor);
    visit(visitor, "keyAttributeName", _keyAttributeName);
    visit(visitor, "valueAttributeName", _valueAttributeName);
    visit(visitor, "key", _key);
}

}
