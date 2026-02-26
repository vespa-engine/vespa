// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributenode.h"
#include "resultvector.h"
#include "enumattributeresult.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/i_multi_value_attribute.h>
#include <vespa/searchcommon/attribute/multi_value_read_view_traits.h>
#include <vespa/vespalib/util/stash.h>
#include <cassert>
#include <format>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.expression.attributenode");

namespace search::expression {

using namespace vespalib;
using search::attribute::ArrayReadViewType_t;
using search::attribute::BasicType;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::IMultiValueAttribute;
using vespalib::Stash;
using vespalib::datastore::AtomicEntryRef;

IMPLEMENT_EXPRESSIONNODE(AttributeNode, FunctionNode);

template <typename V, typename T>
class AttributeNode::IntegerHandler : public AttributeNode::Handler
{
public:
    IntegerHandler(ResultNode & result, const IAttributeVector & attribute) noexcept
        : Handler(),
          _vector(((V &)result).getVector()),
          _stash(),
          _read_view(nullptr)
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<T>(), _stash);
        }
    }
    void handle(const AttributeResult & r) override;
private:
    using ReadView = ArrayReadViewType_t<T>;
    typename V::Vector& _vector;
    Stash               _stash;
    const ReadView*     _read_view;
};

template <typename T>
class AttributeNode::FloatHandler : public AttributeNode::Handler
{
public:
    FloatHandler(ResultNode & result, const IAttributeVector& attribute) noexcept
        : Handler(),
          _vector(((FloatResultNodeVector &)result).getVector()),
          _stash(),
          _read_view(nullptr)
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<T>(), _stash);
        }
    }
    void handle(const AttributeResult & r) override;
private:
    using ReadView = ArrayReadViewType_t<T>;
    FloatResultNodeVector::Vector& _vector;
    Stash                          _stash;
    const ReadView*                _read_view;
};

class AttributeNode::StringHandler : public AttributeNode::Handler
{
public:
    StringHandler(ResultNode & result, const IAttributeVector& attribute) noexcept
        : Handler(),
          _vector(((StringResultNodeVector &)result).getVector()),
          _stash(),
          _read_view(nullptr)
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<const char*>(), _stash);
        }
    }
    void handle(const AttributeResult & r) override;
private:
    using ReadView = ArrayReadViewType_t<const char*>;
    StringResultNodeVector::Vector& _vector;
    Stash                           _stash;
    const ReadView*                 _read_view;
};

class AttributeNode::EnumHandler : public AttributeNode::Handler
{
public:
    EnumHandler(ResultNode & result, const IAttributeVector& attribute) noexcept
        : Handler(),
          _vector(((EnumResultNodeVector &)result).getVector()),
          _stash(),
          _read_view(nullptr)
    {
        auto* mva = attribute.as_multi_value_attribute();
        if (mva != nullptr) {
            _read_view = mva->make_read_view(IMultiValueAttribute::ArrayTag<AtomicEntryRef>(), _stash);
        }
    }
    void handle(const AttributeResult & r) override;
private:
    using ReadView = ArrayReadViewType_t<AtomicEntryRef>;
    EnumResultNodeVector::Vector& _vector;
    Stash                         _stash;
    const ReadView*               _read_view;
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
createMulti(const IAttributeVector & attribute) {
    auto result = std::make_unique<T>();
    auto handler = std::make_unique<H>(*result, attribute);
    return { std::move(result), std::move(handler)};
}

}

AttributeNode::AttributeNode() :
    FunctionNode(),
    _scratchResult(std::make_unique<AttributeResult>()),
    _currentIndex(nullptr),
    _keepAliveForIndexLookups(),
    _hasMultiValue(false),
    _useEnumOptimization(false),
    _needExecute(true),
    _handler(),
    _attributeName()
{}

AttributeNode::~AttributeNode() = default;

AttributeNode::AttributeNode(std::string_view name)
    : FunctionNode(),
      _scratchResult(std::make_unique<AttributeResult>()),
      _currentIndex(nullptr),
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
      _currentIndex(nullptr),
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
      _currentIndex(nullptr),
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
AttributeNode::createResultHandler(bool preserveAccurateTypes, const IAttributeVector& attribute) const {
    BasicType::Type basicType = attribute.getBasicType();
    if (attribute.isIntegerType()) {
        if (_hasMultiValue) {
            if (basicType == BasicType::BOOL) {
                return createMulti<BoolResultNodeVector, IntegerHandler<BoolResultNodeVector, bool>>(attribute);
            } else if (preserveAccurateTypes) {
                switch (basicType) {
                    case BasicType::INT8:
                        return createMulti<Int8ResultNodeVector, IntegerHandler<Int8ResultNodeVector, int8_t>>(attribute);
                    case BasicType::INT16:
                        return createMulti<Int16ResultNodeVector, IntegerHandler<Int16ResultNodeVector, int16_t>>(attribute);
                    case BasicType::INT32:
                        return createMulti<Int32ResultNodeVector, IntegerHandler<Int32ResultNodeVector, int32_t>>(attribute);
                    case BasicType::INT64:
                        return createMulti<Int64ResultNodeVector, IntegerHandler<Int64ResultNodeVector, int64_t>>(attribute);
                    default:
                        ;
                }
            } else {
                switch (basicType) {
                    case BasicType::INT8:
                        return createMulti<IntegerResultNodeVector, IntegerHandler<IntegerResultNodeVector, int8_t>>(attribute);
                    case BasicType::INT16:
                        return createMulti<IntegerResultNodeVector, IntegerHandler<IntegerResultNodeVector, int16_t>>(attribute);
                    case BasicType::INT32:
                        return createMulti<IntegerResultNodeVector, IntegerHandler<IntegerResultNodeVector, int32_t>>(attribute);
                    case BasicType::INT64:
                        return createMulti<IntegerResultNodeVector, IntegerHandler<IntegerResultNodeVector, int64_t>>(attribute);
                    default:
                        ;
                }
            }
            throw std::runtime_error(std::format("'{}' is not a valid integer attribute for attribute node",
                                                 attribute.getName()));
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
                        throw std::runtime_error(std::format("'{}' is not a valid integer attribute for attribute node",
                                                             attribute.getName()));
                }
            } else {
                return createSingle<Int64ResultNode>();
            }
        }
    } else if (attribute.isFloatingPointType()) {
        if (_hasMultiValue) {
            switch (basicType) {
                case BasicType::FLOAT:
                    return createMulti<FloatResultNodeVector, FloatHandler<float>>(attribute);
                case BasicType::DOUBLE:
                    return createMulti<FloatResultNodeVector, FloatHandler<double>>(attribute);
                default:
                    throw std::runtime_error(std::format("'{}' is not a valid float attribute for attribute node",
                                                         attribute.getName()));
            }
        } else {
            return createSingle<FloatResultNode>();
        }
    } else if (attribute.isStringType()) {
        if (_hasMultiValue) {
            return (_useEnumOptimization)
                    ? createMulti<EnumResultNodeVector, EnumHandler>(attribute)
                    : createMulti<StringResultNodeVector, StringHandler>(attribute);
        } else {
            return (_useEnumOptimization)
                    ? createSingle<EnumResultNode>()
                    : createSingle<StringResultNode>();
        }
    } else if (attribute.is_raw_type()) {
        if (_hasMultiValue) {
            throw std::runtime_error(std::format("Does not support multivalue raw attribute vector '{}'"
                                                 " for attribute node",
                                                 attribute.getName()));
        } else {
            return createSingle<RawResultNode>();
        }
    } else {
        throw std::runtime_error(std::format("Can not deduce correct resultclass for attribute vector '{}'"
                                             " for attribute node",
                                             attribute.getName()));
    }
}

void
AttributeNode::onPrepare(bool preserveAccurateTypes)
{
    const IAttributeVector * attribute = getAttribute();
    if (attribute != nullptr) {
        auto[result, handler] = createResultHandler(preserveAccurateTypes, *attribute);
        _handler = std::move(handler);
        if (_currentIndex == nullptr) {
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

template <typename V, typename T>
void
AttributeNode::IntegerHandler<V, T>::handle(const AttributeResult & r)
{
    if (_read_view != nullptr) {
        auto values = _read_view->get_values(r.getDocId());
        _vector.resize(values.size());
        for(size_t i(0); i < values.size(); i++) {
            _vector[i] = values[i];
        }
    }
}

template <typename T>
void
AttributeNode::FloatHandler<T>::handle(const AttributeResult & r)
{
    if (_read_view != nullptr) {
        auto values = _read_view->get_values(r.getDocId());
        _vector.resize(values.size());
        for(size_t i(0); i < values.size(); i++) {
            _vector[i] = values[i];
        }
    }
}

void
AttributeNode::StringHandler::handle(const AttributeResult & r)
{
    if (_read_view != nullptr) {
        auto values = _read_view->get_values(r.getDocId());
        _vector.resize(values.size());
        for(size_t i(0); i < values.size(); i++) {
            _vector[i] = values[i];
        }
    }
}

void
AttributeNode::EnumHandler::handle(const AttributeResult & r)
{
    if (_read_view != nullptr) {
        auto values = _read_view->get_values(r.getDocId());
        _vector.resize(values.size());
        for(size_t i(0); i < values.size(); i++) {
            _vector[i] = values[i].load_relaxed().ref();
        }
    }
}

void
AttributeNode::setDocId(DocId docId) {
    if (_scratchResult->getDocId() != docId) {
        _scratchResult->setDocId(docId);
        _needExecute = true;
    }
}

bool
AttributeNode::onExecute() const
{
    if (_handler) {
        if (_needExecute) {
            _handler->handle(*_scratchResult);
            _needExecute = false;
        }
        if (_currentIndex != nullptr) {
            assert(_keepAliveForIndexLookups);
            assert(_hasMultiValue);
            size_t idx = _currentIndex->get();
            if (idx < _keepAliveForIndexLookups->size()) [[likely]] {
                updateResult().set(_keepAliveForIndexLookups->get(idx));
            } else if (_keepAliveForIndexLookups->size() == 0) {
                LOG(debug, "%s lookup %zd in [] -> NIL", _attributeName.c_str(), idx);
                updateResult().set(*_keepAliveForIndexLookups->createBaseType());
            } else {
                // XXX accessing outside array boundary returns last element
                idx = _keepAliveForIndexLookups->size() - 1;
                updateResult().set(_keepAliveForIndexLookups->get(idx));
            }
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
            throw std::runtime_error(std::format("Failed locating attribute vector '{}' for attribute node",
                                                 _attributeName));
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
