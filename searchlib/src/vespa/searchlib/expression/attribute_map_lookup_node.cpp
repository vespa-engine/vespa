// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_map_lookup_node.h"
#include "resultvector.h"
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/multi_value_read_view_traits.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <format>

using search::attribute::ArrayReadViewType_t;
using search::attribute::BasicType;
using search::attribute::IAttributeVector;
using search::attribute::IMultiValueAttribute;
using search::attribute::getUndefined;
using vespalib::Deserializer;
using vespalib::Serializer;
using vespalib::Stash;
using vespalib::datastore::AtomicEntryRef;
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
convertKey(const IAttributeVector &, const std::string &key)
{
    KeyType ret;
    vespalib::asciistream is(key);
    is >> ret;
    return ret;
}

template <>
std::string
convertKey<std::string>(const IAttributeVector &, const std::string &key)
{
    return key;
}

template <>
EnumHandle
convertKey<EnumHandle>(const IAttributeVector &attribute, const std::string &key)
{
    EnumHandle ret;
    if (!attribute.findEnum(key.c_str(), ret)) {
        ret = EnumHandle();
    }
    return ret;
}

namespace {

template <typename T>
struct UnwrappedType {
    using type = T;
};

template <>
struct UnwrappedType<AtomicEntryRef> {
    using type = EnumHandle;
};

template <typename T>
using UnwrappedType_t = typename UnwrappedType<T>::type;

template <typename KeyType, typename T>
bool matching_direct_key(const KeyType& lhs, const T& rhs) {
    return lhs == rhs;
}

template <>
bool matching_direct_key(const EnumHandle& lhs, const AtomicEntryRef& rhs) {
    return lhs == rhs.load_relaxed().ref();
}

template <typename T>
UnwrappedType_t<T> unwrap_value(const T& value) {
    return value;
}

template <>
EnumHandle unwrap_value(const AtomicEntryRef& value) {
    return value.load_relaxed().ref();
}

}

template <typename T, typename KeyType>
class KeyHandlerT : public AttributeMapLookupNode::KeyHandler
{
    using ReadView = ArrayReadViewType_t<T>;
    Stash           _stash;
    const ReadView* _read_view;
    KeyType         _key;

public:
    KeyHandlerT(const IAttributeVector &attribute, const std::string &key)
        : KeyHandler(attribute),
          _stash(),
          _read_view(nullptr),
          _key(convertKey<KeyType>(attribute, key))
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<T>(), _stash);
        }
    }
    ~KeyHandlerT() override;
    uint32_t handle(DocId docId) override {
        if (_read_view != nullptr) {
            auto keys = _read_view->get_values(docId);
            for (uint32_t i = 0; i < keys.size(); ++i) {
                if (matching_direct_key(_key, keys[i])) {
                    return i;
                }
            }
        }
        return noKeyIdx();
    }
};

template <typename T, typename KeyType>
KeyHandlerT<T,KeyType>::~KeyHandlerT() = default;

template <typename T>
using IntegerKeyHandler = KeyHandlerT<T, T>;
template <typename T>
using FloatKeyHandler   = KeyHandlerT<T, T>;
using StringKeyHandler  = KeyHandlerT<const char *, std::string>;
using EnumKeyHandler    = KeyHandlerT<AtomicEntryRef, EnumHandle>;

template <typename WT, typename T>
bool
matchingKey(WT lhs, T rhs) {
    return lhs == rhs;
}

template <>
bool
matchingKey<const char*, const char*>(const char *lhs, const char *rhs) {
    return (strcmp(lhs, rhs) == 0);
}

template <typename WT, typename T>
class IndirectKeyHandlerT : public AttributeMapLookupNode::KeyHandler
{
    using ReadView = ArrayReadViewType_t<T>;
    const IAttributeVector& _keySourceAttribute;
    Stash                   _stash;
    const ReadView*         _read_view;

public:
    IndirectKeyHandlerT(const IAttributeVector &attribute, const IAttributeVector &keySourceAttribute)
        : KeyHandler(attribute),
          _keySourceAttribute(keySourceAttribute),
          _stash(),
          _read_view(nullptr)
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<T>(), _stash);
        }
    }
    ~IndirectKeyHandlerT() override;
    uint32_t handle(DocId docId) override {
        if (_read_view != nullptr) {
            WT key = WT();
            if constexpr (std::is_same_v<WT, IAttributeVector::largeint_t>) {
                key = _keySourceAttribute.getInt(docId);
            } else if constexpr (std::is_same_v<WT, double>) {
                key = _keySourceAttribute.getFloat(docId);
            } else if constexpr (std::is_same_v<WT, const char*>) {
                auto raw = _keySourceAttribute.get_raw(docId);
                if (raw.data() == nullptr) {
                    return noKeyIdx();
                }
                key = raw.data();
            } else {
                static_assert(false, "Unexepected WT template argument");
            }
            auto keys = _read_view->get_values(docId);
            for (uint32_t i = 0; i < keys.size(); ++i) {
                if (matchingKey(key, keys[i])) {
                    return i;
                }
            }
        }
        return noKeyIdx();
    }
};

template <typename WT, typename T>
IndirectKeyHandlerT<WT, T>::~IndirectKeyHandlerT() = default;

template <typename T>
using IndirectIntegerKeyHandler = IndirectKeyHandlerT<IAttributeVector::largeint_t, T>;
template <typename T>
using IndirectFloatKeyHandler = IndirectKeyHandlerT<double, T>;
using IndirectStringKeyHandler = IndirectKeyHandlerT<const char*, const char*>;

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
    using ReadView = ArrayReadViewType_t<T>;
    using UndefinedType = UnwrappedType_t<T>;
    Stash           _stash;
    const ReadView* _read_view;
    ResultNodeType& _result;
    UndefinedType   _undefinedValue;
public:
    ValueHandlerT(std::unique_ptr<AttributeMapLookupNode::KeyHandler> keyHandler, const IAttributeVector &attribute, ResultNodeType &result, UnwrappedType_t<T> undefinedValue) noexcept
        : ValueHandler(std::move(keyHandler), attribute),
          _stash(),
          _read_view(nullptr),
          _result(result),
          _undefinedValue(undefinedValue)
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<T>(), _stash);
        }
    }
    void handle(const AttributeResult & r) override {
        uint32_t docId = r.getDocId();
        uint32_t keyIdx  = _keyHandler->handle(docId);
        if (keyIdx != AttributeMapLookupNode::KeyHandler::noKeyIdx() && _read_view != nullptr) {
            auto values = _read_view->get_values(docId);
            if (keyIdx < values.size()) {
                _result = unwrap_value(values[keyIdx]);
                return;
            }
        }
        _result = _undefinedValue;
    }
};

template <typename T, typename ResultNodeType>
using IntegerValueHandler = ValueHandlerT<T, ResultNodeType>;
template <typename T>
using FloatValueHandler   = ValueHandlerT<T, FloatResultNode>;
using StringValueHandler  = ValueHandlerT<const char *, StringResultNode>;
using EnumValueHandler    = ValueHandlerT<AtomicEntryRef, EnumResultNode>;

const IAttributeVector *
findAttribute(const search::attribute::IAttributeContext &attrCtx, bool useEnumOptimization, const std::string &name)
{
    const IAttributeVector *attribute = useEnumOptimization ? attrCtx.getAttributeStableEnum(name) : attrCtx.getAttribute(name);
    if (attribute == nullptr) {
        throw std::runtime_error(std::format("Failed locating attribute vector '{}' for attribute map lookup", name));
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

template <typename T, typename ResultNodeType>
std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<AttributeNode::Handler>>
prepareIntValues(std::unique_ptr<AttributeMapLookupNode::KeyHandler> keyHandler, const IAttributeVector &attribute, IAttributeVector::largeint_t undefinedValue)
{
    auto resultNode = std::make_unique<ResultNodeType>();
    auto handler = std::make_unique<IntegerValueHandler<T, ResultNodeType>>(std::move(keyHandler), attribute, *resultNode, undefinedValue);
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

AttributeMapLookupNode::AttributeMapLookupNode(std::string_view name, std::string_view keyAttributeName, std::string_view valueAttributeName, std::string_view key, std::string_view keySourceAttributeName)
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
            switch (attribute.getBasicType()) {
                case BasicType::BOOL:
                    return std::make_unique<IndirectIntegerKeyHandler<bool>>(attribute, keySourceAttribute);
                case BasicType::INT8:
                    return std::make_unique<IndirectIntegerKeyHandler<int8_t>>(attribute, keySourceAttribute);
                case BasicType::INT16:
                    return std::make_unique<IndirectIntegerKeyHandler<int16_t>>(attribute, keySourceAttribute);
                case BasicType::INT32:
                    return std::make_unique<IndirectIntegerKeyHandler<int32_t>>(attribute, keySourceAttribute);
                case BasicType::INT64:
                    return std::make_unique<IndirectIntegerKeyHandler<int64_t>>(attribute, keySourceAttribute);
                default:
                    return std::make_unique<BadKeyHandler>(attribute);
            }
        } else if (attribute.isFloatingPointType() && keySourceAttribute.isFloatingPointType()) {
            switch (attribute.getBasicType()) {
                case BasicType::FLOAT:
                    return std::make_unique<IndirectFloatKeyHandler<float>>(attribute, keySourceAttribute);
                case BasicType::DOUBLE:
                    return std::make_unique<IndirectFloatKeyHandler<double>>(attribute, keySourceAttribute);
                default:
                    return std::make_unique<BadKeyHandler>(attribute);
            }
        } else if (attribute.isStringType() && keySourceAttribute.isStringType()) {
            return std::make_unique<IndirectStringKeyHandler>(attribute, keySourceAttribute);
        } else {
            return std::make_unique<BadKeyHandler>(attribute);
        }
    }
    if (attribute.hasEnum() && useEnumOptimization()) {
        return std::make_unique<EnumKeyHandler>(attribute, _key);
    } else if (attribute.isIntegerType()) {
        switch (attribute.getBasicType()) {
            case BasicType::BOOL:
                return std::make_unique<IntegerKeyHandler<bool>>(attribute, _key);
            case BasicType::INT8:
                return std::make_unique<IntegerKeyHandler<int8_t>>(attribute, _key);
            case BasicType::INT16:
                return std::make_unique<IntegerKeyHandler<int16_t>>(attribute, _key);
            case BasicType::INT32:
                return std::make_unique<IntegerKeyHandler<int32_t>>(attribute, _key);
            case BasicType::INT64:
                return std::make_unique<IntegerKeyHandler<int64_t>>(attribute, _key);
            default:
                return std::make_unique<BadKeyHandler>(attribute);
        }
    } else if (attribute.isFloatingPointType()) {
        switch (attribute.getBasicType()) {
            case BasicType::FLOAT:
                return std::make_unique<FloatKeyHandler<float>>(attribute, _key);
            case BasicType::DOUBLE:
                return std::make_unique<FloatKeyHandler<double>>(attribute, _key);
            default:
                return std::make_unique<BadKeyHandler>(attribute);
        }
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
                case BasicType::BOOL:
                    return prepareIntValues<bool, BoolResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT8:
                    return prepareIntValues<int8_t, Int8ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT16:
                    return prepareIntValues<int16_t, Int16ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT32:
                    return prepareIntValues<int32_t, Int32ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT64:
                    return prepareIntValues<int64_t, Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                default:
                    throw std::runtime_error(std::format("'{}' is not a valid integer attribute"
                                                         " for attribute map lookup result", attribute.getName()));
            }
        } else {
            switch (basicType) {
                case BasicType::BOOL:
                    return prepareIntValues<bool, Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT8:
                    return prepareIntValues<int8_t, Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT16:
                    return prepareIntValues<int16_t, Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT32:
                    return prepareIntValues<int32_t, Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                case BasicType::INT64:
                    return prepareIntValues<int64_t, Int64ResultNode>(std::move(keyHandler), attribute, undefinedValue);
                default:
                    throw std::runtime_error(std::format("'{}' is not a valid integer attribute",
                                                         " for attribute map lookup result", attribute.getName()));
            }
        }
    } else if (attribute.isFloatingPointType()) {
        auto resultNode = std::make_unique<FloatResultNode>();
        switch (basicType) {
            case BasicType::FLOAT:
            {
                auto handler = std::make_unique<FloatValueHandler<float>>(std::move(keyHandler), attribute, *resultNode, getUndefined<double>());
                return { std::move(resultNode), std::move(handler) };
            }
            case BasicType::DOUBLE:
            {
                auto handler = std::make_unique<FloatValueHandler<double>>(std::move(keyHandler), attribute, *resultNode, getUndefined<double>());
                return { std::move(resultNode), std::move(handler) };
            }
            default:
                throw std::runtime_error(std::format("'{}' is not a valid float attribute"
                                                     " for attribute map lookup result", attribute.getName()));
        }
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
        throw std::runtime_error(std::format("Can not deduce correct resultclass for attribute vector '{}'"
                                             " for attribute map lookup result", attribute.getName()));
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
