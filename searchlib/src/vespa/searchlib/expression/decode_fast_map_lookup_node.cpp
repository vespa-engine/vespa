// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "decode_fast_map_lookup_node.h"

#include "attributenode.h"
#include "resultvector.h"

#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/objects/serializer.h>
#include <vespa/vespalib/text/excess_hex.h>

#include <format>

using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::expression {

IMPLEMENT_EXPRESSIONNODE(DecodeFastMapLookupNode, FunctionNode);

namespace {

template <typename T> bool decodeExcessHex(std::string_view hex, std::string& out) {
    auto value = vespalib::excess_hex::decode<T>(hex);
    if (!value) {
        return false;
    }
    out = std::format("{}", *value);
    return true;
}

bool decodeValue(std::string_view encoded, DecodeFastMapLookupNode::ValueType valueType, std::string& out) {
    using ValueType = DecodeFastMapLookupNode::ValueType;
    switch (valueType) {
    case ValueType::INT:
        return decodeExcessHex<int32_t>(encoded, out);
    case ValueType::LONG:
        return decodeExcessHex<int64_t>(encoded, out);
    case ValueType::FLOAT:
        return decodeExcessHex<float>(encoded, out);
    case ValueType::DOUBLE:
        return decodeExcessHex<double>(encoded, out);
    case ValueType::STRING:
        out = encoded;
        return true;
    case ValueType::UNKNOWN:
        break;
    }
    return false;
}

std::string_view toString(DecodeFastMapLookupNode::ValueType valueType) {
    using ValueType = DecodeFastMapLookupNode::ValueType;
    switch (valueType) {
    case ValueType::INT:
        return "int";
    case ValueType::LONG:
        return "long";
    case ValueType::FLOAT:
        return "float";
    case ValueType::DOUBLE:
        return "double";
    case ValueType::STRING:
        return "string";
    case ValueType::UNKNOWN:
        break;
    }
    return "unknown";
}

// Maps a serialized value type to the enum, where unknown values (for example from a newer container) become UNKNOWN.
DecodeFastMapLookupNode::ValueType toValueType(uint8_t value) {
    using ValueType = DecodeFastMapLookupNode::ValueType;
    switch (static_cast<ValueType>(value)) {
    case ValueType::INT:
    case ValueType::LONG:
    case ValueType::FLOAT:
    case ValueType::DOUBLE:
    case ValueType::STRING:
        return static_cast<ValueType>(value);
    case ValueType::UNKNOWN:
        break;
    }
    return ValueType::UNKNOWN;
}

} // namespace

DecodeFastMapLookupNode::DecodeFastMapLookupNode() noexcept
    : FunctionNode(), _attributeName(), _valueType(ValueType::UNKNOWN), _separator(default_separator), _input() {
}

DecodeFastMapLookupNode::DecodeFastMapLookupNode(std::string_view attributeName, ValueType valueType,
                                                 std::string_view separator)
    : FunctionNode(), _attributeName(attributeName), _valueType(valueType), _separator(separator), _input() {
    createInput();
}

DecodeFastMapLookupNode::DecodeFastMapLookupNode(const DecodeFastMapLookupNode& rhs)
    : FunctionNode(rhs),
      _attributeName(rhs._attributeName),
      _valueType(rhs._valueType),
      _separator(rhs._separator),
      _input(rhs._input ? std::make_unique<AttributeNode>(*rhs._input) : nullptr) {
}

DecodeFastMapLookupNode& DecodeFastMapLookupNode::operator=(const DecodeFastMapLookupNode& rhs) {
    if (this != &rhs) {
        FunctionNode::operator=(rhs);
        _attributeName = rhs._attributeName;
        _valueType = rhs._valueType;
        _separator = rhs._separator;
        _input = rhs._input ? std::make_unique<AttributeNode>(*rhs._input) : nullptr;
    }
    return *this;
}

DecodeFastMapLookupNode::~DecodeFastMapLookupNode() = default;

void DecodeFastMapLookupNode::createInput() {
    _input = std::make_unique<AttributeNode>(_attributeName);
}

std::string DecodeFastMapLookupNode::decode(std::string_view encoded, ValueType valueType,
                                            std::string_view separator) {
    auto pos = encoded.find(encoded_separator);
    if (pos == std::string_view::npos) {
        return std::string(encoded);
    }
    auto        key = encoded.substr(0, pos);
    auto        value = encoded.substr(pos + 1);
    std::string decoded;
    if (!decodeValue(value, valueType, decoded)) {
        decoded = value;
    }
    std::string result;
    result.reserve(key.size() + separator.size() + decoded.size());
    result.append(key).append(separator).append(decoded);
    return result;
}

void DecodeFastMapLookupNode::onPrepare(bool preserveAccurateTypes) {
    if (_valueType == ValueType::UNKNOWN || !_input) {
        throw std::runtime_error(
            std::format("No known value type for fast map lookup attribute '{}'", _attributeName));
    }
    _input->prepare(preserveAccurateTypes);
    const ResultNode* inputResult = _input->getResult();
    if (inputResult == nullptr || !inputResult->inherits(StringResultNodeVector::classId)) {
        throw std::runtime_error(
            std::format("Fast map lookup attribute '{}' is not an array of string", _attributeName));
    }
    setResultType(std::make_unique<StringResultNodeVector>());
}

void DecodeFastMapLookupNode::onExecute() const {
    _input->execute();
    const auto& encoded = static_cast<const StringResultNodeVector&>(*_input->getResult()).getVector();
    auto&       decoded = static_cast<StringResultNodeVector&>(updateResult()).getVector();
    decoded.resize(encoded.size());
    for (size_t i = 0; i < encoded.size(); ++i) {
        decoded[i].set(decode(encoded[i].get(), _valueType, _separator));
    }
}

void DecodeFastMapLookupNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                            vespalib::ObjectOperation&       operation) {
    // The input is not serialized, but must be visible for wiring attributes and setting the docid.
    FunctionNode::selectMembers(predicate, operation);
    if (_input) {
        _input->select(predicate, operation);
    }
}

Serializer& DecodeFastMapLookupNode::onSerialize(Serializer& os) const {
    FunctionNode::onSerialize(os);
    return os << _attributeName << static_cast<uint8_t>(_valueType) << _separator;
}

Deserializer& DecodeFastMapLookupNode::onDeserialize(Deserializer& is) {
    FunctionNode::onDeserialize(is);
    uint8_t valueType = 0;
    is >> _attributeName >> valueType >> _separator;
    _valueType = toValueType(valueType);
    createInput();
    return is;
}

void DecodeFastMapLookupNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    FunctionNode::visitMembers(visitor);
    visit(visitor, "attributeName", _attributeName);
    visit(visitor, "valueType", std::string(toString(_valueType)));
    visit(visitor, "separator", _separator);
}

} // namespace search::expression
