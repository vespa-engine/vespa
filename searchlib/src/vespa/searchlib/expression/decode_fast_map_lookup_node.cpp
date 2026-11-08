// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "decode_fast_map_lookup_node.h"

#include "attributenode.h"
#include "resultvector.h"

#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/objects/serializer.h>

#include <bit>
#include <charconv>
#include <format>

using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::expression {

IMPLEMENT_EXPRESSIONNODE(DecodeFastMapLookupNode, FunctionNode);

namespace {

/**
 * Parses exactly sizeof(T) * 2 hex digits, as written by the excess hex encodings in config-model.
 */
template <typename T> bool parseHex(std::string_view hex, T& bits) {
    if (hex.size() != sizeof(T) * 2) {
        return false;
    }
    auto [ptr, ec] = std::from_chars(hex.data(), hex.data() + hex.size(), bits, 16);
    return ec == std::errc() && ptr == hex.data() + hex.size();
}

// Inverse of Text.toExcessHex8 / toExcessHex16: the value biased by flipping the sign bit.
template <typename U, typename S> bool decodeExcessHex(std::string_view hex, std::string& out) {
    U bits = 0;
    if (!parseHex(hex, bits)) {
        return false;
    }
    constexpr U sign_bit = U(1) << (sizeof(U) * 8 - 1);
    out = std::format("{}", std::bit_cast<S>(U(bits ^ sign_bit)));
    return true;
}

// Inverse of Text.floatToExcessHex8 / doubleToExcessHex16: positive values have the sign bit set,
// while negative values have all bits inverted.
template <typename U, typename F> bool decodeFloatExcessHex(std::string_view hex, std::string& out) {
    U bits = 0;
    if (!parseHex(hex, bits)) {
        return false;
    }
    constexpr U sign_bit = U(1) << (sizeof(U) * 8 - 1);
    bits = (bits & sign_bit) ? (bits ^ sign_bit) : ~bits;
    out = std::format("{}", std::bit_cast<F>(bits));
    return true;
}

bool decodeValue(std::string_view encoded, DecodeFastMapLookupNode::ValueType valueType, std::string& out) {
    using ValueType = DecodeFastMapLookupNode::ValueType;
    switch (valueType) {
    case ValueType::INT:
        return decodeExcessHex<uint32_t, int32_t>(encoded, out);
    case ValueType::LONG:
        return decodeExcessHex<uint64_t, int64_t>(encoded, out);
    case ValueType::FLOAT:
        return decodeFloatExcessHex<uint32_t, float>(encoded, out);
    case ValueType::DOUBLE:
        return decodeFloatExcessHex<uint64_t, double>(encoded, out);
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

// Returns "fieldname$lookupname" for "fieldname.lookupname", or an empty string if the name is not on that form.
std::string toLookupAttributeName(const std::string& name) {
    auto pos = name.rfind('.');
    if (pos == std::string::npos || pos == 0 || pos + 1 == name.size()) {
        return {};
    }
    return name.substr(0, pos) + "$" + name.substr(pos + 1);
}

} // namespace

DecodeFastMapLookupNode::DecodeFastMapLookupNode() noexcept
    : FunctionNode(), _name(), _valueType(ValueType::UNKNOWN), _input() {
}

DecodeFastMapLookupNode::DecodeFastMapLookupNode(std::string_view name)
    : FunctionNode(), _name(name), _valueType(ValueType::UNKNOWN), _input() {
    createInput();
}

DecodeFastMapLookupNode::DecodeFastMapLookupNode(const DecodeFastMapLookupNode& rhs)
    : FunctionNode(rhs),
      _name(rhs._name),
      _valueType(rhs._valueType),
      _input(rhs._input ? std::make_unique<AttributeNode>(*rhs._input) : nullptr) {
}

DecodeFastMapLookupNode& DecodeFastMapLookupNode::operator=(const DecodeFastMapLookupNode& rhs) {
    if (this != &rhs) {
        FunctionNode::operator=(rhs);
        _name = rhs._name;
        _valueType = rhs._valueType;
        _input = rhs._input ? std::make_unique<AttributeNode>(*rhs._input) : nullptr;
    }
    return *this;
}

DecodeFastMapLookupNode::~DecodeFastMapLookupNode() = default;

void DecodeFastMapLookupNode::createInput() {
    // A malformed name gives no input, and fails when the node is configured or prepared.
    auto attributeName = toLookupAttributeName(_name);
    _input = attributeName.empty() ? nullptr : std::make_unique<AttributeNode>(attributeName);
}

void DecodeFastMapLookupNode::configure(const FastMapLookup& lookup) {
    std::string attributeName = lookupAttributeName();
    if (lookup.lookup_attribute() != attributeName) {
        throw std::runtime_error(std::format("Fast map lookup '{}' does not match decode node '{}'",
                                             lookup.lookup_attribute(), _name));
    }
    auto valueType = toValueType(lookup.value_type);
    if (valueType == ValueType::UNKNOWN) {
        throw std::runtime_error(
            std::format("Unsupported value type '{}' for fast map lookup '{}'", lookup.value_type, _name));
    }
    _valueType = valueType;
}

std::string DecodeFastMapLookupNode::lookupAttributeName() const {
    auto attributeName = toLookupAttributeName(_name);
    if (attributeName.empty()) {
        throw std::runtime_error(std::format("Fast map lookup '{}' is not on the form fieldname.lookupname", _name));
    }
    return attributeName;
}

std::string DecodeFastMapLookupNode::decode(std::string_view encoded, ValueType valueType) {
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
    result.reserve(key.size() + decoded_separator.size() + decoded.size());
    result.append(key).append(decoded_separator).append(decoded);
    return result;
}

DecodeFastMapLookupNode::ValueType DecodeFastMapLookupNode::toValueType(std::string_view typeName) {
    if (typeName == "int") return ValueType::INT;
    if (typeName == "long") return ValueType::LONG;
    if (typeName == "float") return ValueType::FLOAT;
    if (typeName == "double") return ValueType::DOUBLE;
    if (typeName == "string") return ValueType::STRING;
    return ValueType::UNKNOWN;
}

void DecodeFastMapLookupNode::onPrepare(bool preserveAccurateTypes) {
    if (_valueType == ValueType::UNKNOWN) {
        throw std::runtime_error(std::format("No fast map lookup configured for '{}'", _name));
    }
    if (!_input) {
        throw std::runtime_error(std::format("Fast map lookup '{}' is not on the form fieldname.lookupname", _name));
    }
    _input->prepare(preserveAccurateTypes);
    const ResultNode* inputResult = _input->getResult();
    if (inputResult == nullptr || !inputResult->inherits(StringResultNodeVector::classId)) {
        throw std::runtime_error(std::format("Attribute '{}' for fast map lookup '{}' is not an array of string",
                                             _input->getAttributeName(), _name));
    }
    setResultType(std::make_unique<StringResultNodeVector>());
}

void DecodeFastMapLookupNode::onExecute() const {
    _input->execute();
    const auto& encoded = static_cast<const StringResultNodeVector&>(*_input->getResult()).getVector();
    auto&       decoded = static_cast<StringResultNodeVector&>(updateResult()).getVector();
    decoded.resize(encoded.size());
    for (size_t i = 0; i < encoded.size(); ++i) {
        decoded[i].set(decode(encoded[i].get(), _valueType));
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
    return os << _name;
}

Deserializer& DecodeFastMapLookupNode::onDeserialize(Deserializer& is) {
    FunctionNode::onDeserialize(is);
    is >> _name;
    createInput();
    return is;
}

void DecodeFastMapLookupNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    FunctionNode::visitMembers(visitor);
    visit(visitor, "name", _name);
    visit(visitor, "valueType", std::string(toString(_valueType)));
}

} // namespace search::expression
