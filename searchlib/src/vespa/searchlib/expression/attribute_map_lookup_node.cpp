// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_map_lookup_node.h"
#include "resultvector.h"
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

using search::attribute::AttributeContent;
using search::attribute::IAttributeVector;
using search::attribute::BasicType;
using search::attribute::getUndefined;
using vespalib::Deserializer;
using vespalib::Serializer;
using EnumHandle = IAttributeVector::EnumHandle;

namespace search::expression {

IMPLEMENT_EXPRESSIONNODE(AttributeMapLookupNode, AttributeNode);

class AttributeMapLookupNode::KeyHandler
{
protected:
    const IAttributeVector &_attribute;

    KeyHandler(const IAttributeVector &attribute) noexcept : _attribute(attribute) { }
public:
    static uint32_t noKeyIdx() { return std::numeric_limits<uint32_t>::max(); }
    virtual ~KeyHandler() = default;
    virtual uint32_t handle(DocId docId) = 0;
};

namespace {

class BadKeyHandler : public AttributeMapLookupNode::KeyHandler
{
public:
    BadKeyHandler(const IAttributeVector &attribute) noexcept : KeyHandler(attribute) { }
    uint32_t handle(DocId) override { return noKeyIdx(); }
};

template <typename KeyType>
KeyType
convertKey(const IAttributeVector &, const vespalib::string &key)
{
    KeyType ret;
    vespalib::asciistream is(key);
    is >> ret;
    return ret;
}

template <>
vespalib::string
convertKey<vespalib::string>(const IAttributeVector &, const vespalib::string &key)
{
    return key;
}

template <>
EnumHandle
convertKey<EnumHandle>(const IAttributeVector &attribute, const vespalib::string &key)
{
    EnumHandle ret;
    if (!attribute.findEnum(key.c_str(), ret)) {
        ret = EnumHandle();
    }
    return ret;
}

template <typename T, typename KeyType = T>
class KeyHandlerT : public AttributeMapLookupNode::KeyHandler
{
    AttributeContent<T> _keys;
    KeyType _key;

public:
    KeyHandlerT(const IAttributeVector &attribute, const vespalib::string &key)
        : KeyHandler(attribute),
          _keys(),
          _key(convertKey<KeyType>(attribute, key))
    { }
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
KeyHandlerT<T,KeyType>::~KeyHandlerT() = default;

using IntegerKeyHandler = KeyHandlerT<IAttributeVector::largeint_t>;
using FloatKeyHandler   = KeyHandlerT<double>;
using StringKeyHandler  = KeyHandlerT<const char *, vespalib::string>;
using EnumKeyHandler    = KeyHandlerT<EnumHandle>;

template <typename T>
bool
matchingKey(T lhs, T rhs) {
    return lhs == rhs;
}

template <>
bool
matchingKey<const char *>(const char *lhs, const char *rhs) {
    return (strcmp(lhs, rhs) == 0);
}

template <typename T>
class IndirectKeyHandlerT : public AttributeMapLookupNode::KeyHandler
{
    const IAttributeVector &_keySourceAttribute;
    AttributeContent<T>     _keys;

public:
    IndirectKeyHandlerT(const IAttributeVector &attribute, const IAttributeVector &keySourceAttribute)
        : KeyHandler(attribute),
          _keySourceAttribute(keySourceAttribute),
          _keys()
    { }
    ~IndirectKeyHandlerT() override;
    uint32_t handle(DocId docId) override {
        T key = T();
        _keySourceAttribute.get(docId, &key, 1);
        _keys.fill(_attribute, docId);
        for (uint32_t i = 0; i < _keys.size(); ++i) {
            if (matchingKey(key, _keys[i])) {
                return i;
            }
        }
        return noKeyIdx();
    }
};

template <typename T>
IndirectKeyHandlerT<T>::~IndirectKeyHandlerT() = default;

using IndirectIntegerKeyHandler = IndirectKeyHandlerT<IAttributeVector::largeint_t>;
using IndirectFloatKeyHandler = IndirectKeyHandlerT<double>;
using IndirectStringKeyHandler = IndirectKeyHandlerT<const char *>;

class ValueHandler : public AttributeNode::Handler
{
protected:
    std::unique_ptr<AttributeMapLookupNode::KeyHandler> _keyHandler;
    const IAttributeVector &_attribute;
    ValueHandler(std::unique_ptr<AttributeMapLookupNode::KeyHandler> keyHandler, const IAttributeVector &attribute) noexcept
        : _keyHandler(std::move(keyHandler)),
          _attribute(attribute)
    { }
};

template <typename T, typename ResultNodeType>
class ValueHandlerT : public ValueHandler
{
    AttributeContent<T> _values;
    ResultNodeType &_result;
    T _undefinedValue;
public:
    ValueHandlerT(std::unique_ptr<AttributeMapLookupNode::KeyHandler> keyHandler, const IAttributeVector &attribute, ResultNodeType &result, T undefinedValue) noexcept
        : ValueHandler(std::move(keyHandler), attribute),
          _values(),
          _result(result),
          _undefinedValue(undefinedValue)
    { }
    void handle(const AttributeResult & r) override {
        uint32_t docId = r.getDocId();
        uint32_t keyIdx  = _keyHandler->handle(docId);
        if (keyIdx != AttributeMapLookupNode::KeyHandler::noKeyIdx()) {
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

const IAttributeVector *
findAttribute(const search::attribute::IAttributeContext &attrCtx, bool useEnumOptimization, const vespalib::string &name)
{
    const IAttributeVector *attribute = useEnumOptimization ? attrCtx.getAttributeStableEnum(name) : attrCtx.getAttribute(name);
    if (attribute == nullptr) {
        throw std::runtime_error(vespalib::make_string("Failed locating attribute vector '%s'", name.c_str()));
    }
    return attribute;
}

IAttributeVector::largeint_t
getUndefinedValue(BasicType::Type basicType)
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
    default:
        return 0;
    }
}

template <typename ResultNodeType>
std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
prepareIntValues(std::unique_ptr<AttributeMapLookupNode::KeyHandler> keyHandler, const IAttributeVector &attribute, IAttributeVector::largeint_t undefinedValue)
{
    auto resultNode = std::make_unique<ResultNodeType>();
    auto handler = std::make_unique<IntegerValueHandler<ResultNodeType>>(std::move(keyHandler), attribute, *resultNode, undefinedValue);
    return { std::move(resultNode), std::move(handler) };
}

}

AttributeMapLookupNode::AttributeMapLookupNode()
    : AttributeNode(),
      _keyAttributeName(),
      _valueAttributeName(),
      _key(),
      _keySourceAttributeName(),
      _keyAttribute(nullptr),
      _keySourceAttribute(nullptr)
{ }

AttributeMapLookupNode::AttributeMapLookupNode(const AttributeMapLookupNode &) = default;

AttributeMapLookupNode::AttributeMapLookupNode(vespalib::stringref name, vespalib::stringref keyAttributeName, vespalib::stringref valueAttributeName, vespalib::stringref key, vespalib::stringref keySourceAttributeName)
    : AttributeNode(name),
      _keyAttributeName(keyAttributeName),
      _valueAttributeName(valueAttributeName),
      _key(key),
      _keySourceAttributeName(keySourceAttributeName),
      _keyAttribute(nullptr),
      _keySourceAttribute(nullptr)
{ }

AttributeMapLookupNode::~AttributeMapLookupNode() = default;

AttributeMapLookupNode &
AttributeMapLookupNode::operator=(const AttributeMapLookupNode &rhs) = default;

std::unique_ptr<AttributeMapLookupNode::KeyHandler>
AttributeMapLookupNode::makeKeyHandlerHelper() const
{
    const IAttributeVector &attribute = *_keyAttribute;
    if (_keySourceAttribute != nullptr) {
        const IAttributeVector &keySourceAttribute = *_keySourceAttribute;
        if (attribute.isIntegerType() && keySourceAttribute.isIntegerType()) {
            return std::make_unique<IndirectIntegerKeyHandler>(attribute, keySourceAttribute);
        } else if (attribute.isFloatingPointType() && keySourceAttribute.isFloatingPointType()) {
            return std::make_unique<IndirectFloatKeyHandler>(attribute, keySourceAttribute);
        } else if (attribute.isStringType() && keySourceAttribute.isStringType()) {
            return std::make_unique<IndirectStringKeyHandler>(attribute, keySourceAttribute);
        } else {
            return std::make_unique<BadKeyHandler>(attribute);
        }
    }
    if (attribute.hasEnum() && useEnumOptimization()) {
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

std::unique_ptr<AttributeMapLookupNode::KeyHandler>
AttributeMapLookupNode::makeKeyHandler() const
{
    try {
        return makeKeyHandlerHelper();
    } catch (const vespalib::IllegalArgumentException &) {
        return std::make_unique<BadKeyHandler>(*_keyAttribute);
    }
}

std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
AttributeMapLookupNode::createResultHandler(bool preserveAccurateTypes, const attribute::IAttributeVector & attribute) const {
    auto keyHandler = makeKeyHandler();
    BasicType::Type basicType = attribute.getBasicType();
    if (attribute.isIntegerType()) {
        IAttributeVector::largeint_t undefinedValue = getUndefinedValue(basicType);
        if (preserveAccurateTypes) {
            switch (basicType) {
                case BasicType::INT8:
                    return prepareIntValues<Int8ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT16:
                    return prepareIntValues<Int16ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT32:
                    return prepareIntValues<Int32ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT64:
                    return prepareIntValues<Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                default:
                    throw std::runtime_error("This is no valid integer attribute " + attribute.getName());
            }
        } else {
            return prepareIntValues<Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
        }
    } else if (attribute.isFloatingPointType()) {
        auto resultNode = std::make_unique<FloatResultNode>();
        auto handler = std::make_unique<FloatValueHandler>(std::move(keyHandler), attribute, *resultNode, getUndefined<double>());
        return { std::move(resultNode), std::move(handler) };
    } else if (attribute.isStringType()) {
        if (useEnumOptimization()) {
            auto resultNode = std::make_unique<EnumResultNode>();
            const StringAttribute & sattr = dynamic_cast<const StringAttribute &>(attribute);
            EnumHandle undefined(0);
            bool found = attribute.findEnum(sattr.defaultValue(), undefined);
            assert(found);
            auto handler = std::make_unique<EnumValueHandler>(std::move(keyHandler), attribute, *resultNode, undefined);
            return { std::move(resultNode), std::move(handler) };
        } else {
            auto resultNode = std::make_unique<StringResultNode>();
            auto handler = std::make_unique<StringValueHandler>(std::move(keyHandler), attribute, *resultNode, "");
            return { std::move(resultNode), std::move(handler) };
        }
    } else {
        throw std::runtime_error(vespalib::make_string("Can not deduce correct resultclass for attribute vector '%s'",
                                                       attribute.getName().c_str()));
    }
}

void
AttributeMapLookupNode::cleanup()
{
    _keyAttribute = nullptr;
    _keySourceAttribute = nullptr;
    AttributeNode::cleanup();
}

void
AttributeMapLookupNode::wireAttributes(const search::attribute::IAttributeContext &attrCtx)
{
    auto valueAttribute = findAttribute(attrCtx, useEnumOptimization(), _valueAttributeName);
    setHasMultiValue(false);
    setScratchResult(std::make_unique<AttributeResult>(valueAttribute, 0));
    _keyAttribute = findAttribute(attrCtx, useEnumOptimization(), _keyAttributeName);
    if (!_keySourceAttributeName.empty()) {
        _keySourceAttribute = findAttribute(attrCtx, false, _keySourceAttributeName);
    }
}

Serializer &
AttributeMapLookupNode::onSerialize(Serializer & os) const
{
    AttributeNode::onSerialize(os);
    return os << _keyAttributeName << _valueAttributeName << _key << _keySourceAttributeName;
}

Deserializer &
AttributeMapLookupNode::onDeserialize(Deserializer & is)
{
    AttributeNode::onDeserialize(is);
    return is >> _keyAttributeName >> _valueAttributeName >> _key >> _keySourceAttributeName;
}

void
AttributeMapLookupNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeNode::visitMembers(visitor);
    visit(visitor, "keyAttributeName", _keyAttributeName);
    visit(visitor, "keySourceAttributeName", _keySourceAttributeName);
    visit(visitor, "valueAttributeName", _valueAttributeName);
    visit(visitor, "key", _key);
}

}
