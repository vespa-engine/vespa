// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_keyed_node.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/common/undefinedvalues.h>

using search::attribute::IAttributeVector;
using search::attribute::BasicType;

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
    static uint32_t noKey() { return std::numeric_limits<uint32_t>::max(); }
    virtual ~KeyHandler() = default;
    virtual uint32_t handle(DocId docId) = 0;
};

template <typename T, typename KT>
class AttributeKeyedNode::KeyHandlerT : public KeyHandler
{
protected:
    std::vector<T> _values;
    KT _key;

    KeyHandlerT(const IAttributeVector &attribute)
        : KeyHandler(attribute),
          _values(),
          _key()
    {
    }
    ~KeyHandlerT() override;
    uint32_t handle(DocId docId) override {
        size_t numValues = _attribute.get(docId, &_values[0], _values.size());
        while (numValues > _values.size()) {
            _values.resize(numValues);
            numValues = _attribute.get(docId, &_values[0], _values.size());
        }
        for (uint32_t i = 0; i < numValues; ++i) {
            if (_key == _values[i]) {
                return i;
            }
        }
        return noKey();
    }
};

template <typename T, typename KT>
AttributeKeyedNode::KeyHandlerT<T,KT>::~KeyHandlerT()
{
}

class AttributeKeyedNode::IntegerKeyHandler : public KeyHandlerT<IAttributeVector::largeint_t>
{
public:
    IntegerKeyHandler(const IAttributeVector &attribute, const vespalib::string &key)
        : KeyHandlerT<IAttributeVector::largeint_t>(attribute)
    {
        vespalib::asciistream is(key);
        try {
            is >> _key;
        } catch (const vespalib::IllegalArgumentException &) {
        }
    }
};

class AttributeKeyedNode::FloatKeyHandler : public KeyHandlerT<double>
{
public:
    FloatKeyHandler(const IAttributeVector &attribute, const vespalib::string &key)
        : KeyHandlerT<double>(attribute)
    {
        vespalib::asciistream is(key);
        try {
            is >> _key;
        } catch (const vespalib::IllegalArgumentException &) {
        }
    }
};

class AttributeKeyedNode::StringKeyHandler : public KeyHandlerT<const char *, vespalib::string>
{
public:
    StringKeyHandler(const IAttributeVector &attribute, const vespalib::string &key)
        : KeyHandlerT<const char *, vespalib::string>(attribute)
    {
        _key = key;
    }
};

class AttributeKeyedNode::EnumKeyHandler : public AttributeKeyedNode::KeyHandlerT<IAttributeVector::EnumHandle>
{
public:
    EnumKeyHandler(const IAttributeVector &attribute, const vespalib::string &key)
        : KeyHandlerT<IAttributeVector::EnumHandle>(attribute)
    {
        attribute.findEnum(key.c_str(), _key);
    }
};

class AttributeKeyedNode::ValueHandler : public AttributeNode::Handler
{
protected:
    std::unique_ptr<KeyHandler> _keyHandler;
    const IAttributeVector &_attribute;
    ValueHandler(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute)
        : _keyHandler(std::move(keyHandler)),
          _attribute(attribute)
    {
    }
};

template <typename T, typename RN>
class AttributeKeyedNode::ValueHandlerT : public ValueHandler
{
    std::vector<T> _values;
    RN &_result;
    T _undefinedValue;
protected:
    ValueHandlerT(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, RN &result, T undefinedValue)
        : ValueHandler(std::move(keyHandler), attribute),
          _values(),
          _result(result),
          _undefinedValue(undefinedValue)
    {
    }
    void handle(const AttributeResult & r) override {
        uint32_t docId = r.getDocId();
        uint32_t keyIdx  = _keyHandler->handle(docId);
        if (keyIdx != KeyHandler::noKey()) {
            size_t numValues = _attribute.get(docId, &_values[0], _values.size());
            while (numValues > _values.size()) {
                _values.resize(numValues);
                numValues = _attribute.get(docId, &_values[0], _values.size());
            }
            if (keyIdx < numValues) {
                _result = _values[keyIdx];
                return;
            }
        }
        _result = _undefinedValue;
    }
};


template <typename RN>
class AttributeKeyedNode::IntegerValueHandler : public ValueHandlerT<IAttributeVector::largeint_t, RN>
{
public:
    IntegerValueHandler(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, RN &result, IAttributeVector::largeint_t undefinedValue)
        : ValueHandlerT<IAttributeVector::largeint_t, RN>(std::move(keyHandler), attribute, result, undefinedValue)
    { }
};

class AttributeKeyedNode::FloatValueHandler : public ValueHandlerT<double, FloatResultNode>
{
public:
    FloatValueHandler(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, FloatResultNode &result)
        : ValueHandlerT<double, FloatResultNode>(std::move(keyHandler), attribute, result, search::attribute::getUndefined<double>())
    { }
};

class AttributeKeyedNode::StringValueHandler : public ValueHandlerT<const char *, StringResultNode>
{
public:
    StringValueHandler(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, StringResultNode &result)
        : ValueHandlerT<const char *, StringResultNode>(std::move(keyHandler), attribute, result, "")
    { }
};

class AttributeKeyedNode::EnumValueHandler : public ValueHandlerT<IAttributeVector::EnumHandle, EnumResultNode>
{
public:
    EnumValueHandler(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, EnumResultNode &result)
        : ValueHandlerT<IAttributeVector::EnumHandle, EnumResultNode>(std::move(keyHandler), attribute, result, IAttributeVector::EnumHandle())
    { }
};

namespace {

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
        return search::attribute::getUndefined<int8_t>();
    case BasicType::INT16:
        return search::attribute::getUndefined<int16_t>();
    case BasicType::INT32:
        return search::attribute::getUndefined<int32_t>();
    case BasicType::INT64:
        return search::attribute::getUndefined<int64_t>();
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

template <typename RN>
void
AttributeKeyedNode::prepareIntValues(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, IAttributeVector::largeint_t undefinedValue)
{
    auto resultNode = std::make_unique<RN>();
    _handler = std::make_unique<IntegerValueHandler<RN>>(std::move(keyHandler), attribute, *resultNode, undefinedValue);
    setResultType(std::move(resultNode));
}

std::unique_ptr<AttributeKeyedNode::KeyHandler>
AttributeKeyedNode::makeKeyHandler()
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
        throw std::runtime_error(vespalib::make_string("Can not deduce correct key handler for attribute vector '%s'",
                                                       attribute.getName().c_str()));
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
            _handler = std::make_unique<FloatValueHandler>(std::move(keyHandler), *attribute, *resultNode);
            setResultType(std::move(resultNode));
        } else if (attribute->isStringType()) {
            if (_useEnumOptimization) {
                auto resultNode = std::make_unique<EnumResultNode>();
                _handler = std::make_unique<EnumValueHandler>(std::move(keyHandler), *attribute, *resultNode);
                setResultType(std::move(resultNode));
            } else {
                auto resultNode = std::make_unique<StringResultNode>();
                _handler = std::make_unique<StringValueHandler>(std::move(keyHandler), *attribute, *resultNode);
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
