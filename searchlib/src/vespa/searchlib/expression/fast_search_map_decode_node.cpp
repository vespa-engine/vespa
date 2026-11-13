// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_search_map_decode_node.h"

#include "attributenode.h"
#include "resultvector.h"

#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/objects/serializer.h>
#include <vespa/vespalib/text/excess_hex.h>

#include <format>

using vespalib::Deserializer;
using vespalib::Serializer;

namespace search::expression {

IMPLEMENT_EXPRESSIONNODE(FastSearchMapDecodeNode, FunctionNode);

namespace {

template <typename T> bool decode_excess_hex(std::string_view hex, std::string& out) {
    auto value = vespalib::excess_hex::decode<T>(hex);
    if (!value) {
        return false;
    }
    out = std::format("{}", *value);
    return true;
}

bool decode_value(std::string_view encoded, FastSearchMapDecodeNode::ValueType value_type, std::string& out) {
    using ValueType = FastSearchMapDecodeNode::ValueType;
    switch (value_type) {
    case ValueType::INT:
        return decode_excess_hex<int32_t>(encoded, out);
    case ValueType::LONG:
        return decode_excess_hex<int64_t>(encoded, out);
    case ValueType::FLOAT:
        return decode_excess_hex<float>(encoded, out);
    case ValueType::DOUBLE:
        return decode_excess_hex<double>(encoded, out);
    case ValueType::STRING:
        out = encoded;
        return true;
    case ValueType::UNKNOWN:
        break;
    }
    return false;
}

std::string_view to_string(FastSearchMapDecodeNode::ValueType value_type) {
    using ValueType = FastSearchMapDecodeNode::ValueType;
    switch (value_type) {
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
FastSearchMapDecodeNode::ValueType to_value_type(uint8_t value) {
    using ValueType = FastSearchMapDecodeNode::ValueType;
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

FastSearchMapDecodeNode::FastSearchMapDecodeNode() noexcept
    : FunctionNode(),
      _attribute_name(),
      _value_type(ValueType::UNKNOWN),
      _vector_mode(true),
      _separator(default_separator),
      _input() {
}

FastSearchMapDecodeNode::FastSearchMapDecodeNode(std::string_view attribute_name, ValueType value_type,
                                                 std::string_view separator)
    : FunctionNode(),
      _attribute_name(attribute_name),
      _value_type(value_type),
      _vector_mode(true),
      _separator(separator),
      _input() {
    createInput();
}

FastSearchMapDecodeNode::FastSearchMapDecodeNode(const FastSearchMapDecodeNode& rhs)
    : FunctionNode(rhs),
      _attribute_name(rhs._attribute_name),
      _value_type(rhs._value_type),
      _vector_mode(rhs._vector_mode),
      _separator(rhs._separator),
      _input(rhs._input ? std::make_unique<AttributeNode>(*rhs._input) : nullptr) {
}

FastSearchMapDecodeNode& FastSearchMapDecodeNode::operator=(const FastSearchMapDecodeNode& rhs) {
    if (this != &rhs) {
        FunctionNode::operator=(rhs);
        _attribute_name = rhs._attribute_name;
        _value_type = rhs._value_type;
        _vector_mode = rhs._vector_mode;
        _separator = rhs._separator;
        _input = rhs._input ? std::make_unique<AttributeNode>(*rhs._input) : nullptr;
    }
    return *this;
}

FastSearchMapDecodeNode::~FastSearchMapDecodeNode() = default;

void FastSearchMapDecodeNode::createInput() {
    _input = std::make_unique<AttributeNode>(_attribute_name);
}

std::string FastSearchMapDecodeNode::decode(std::string_view encoded, ValueType value_type,
                                            std::string_view separator) {
    auto pos = encoded.find(encoded_separator);
    if (pos == std::string_view::npos) {
        return std::string(encoded);
    }
    auto        key = encoded.substr(0, pos);
    auto        value = encoded.substr(pos + 1);
    std::string decoded;
    if (!decode_value(value, value_type, decoded)) {
        decoded = value;
    }
    return std::format("{}{}{}", key, separator, decoded);
}

void FastSearchMapDecodeNode::onPrepare(bool preserveAccurateTypes) {
    if (_value_type == ValueType::UNKNOWN || !_input) {
        throw std::runtime_error(
            std::format("No known value type for fast map lookup attribute '{}'", _attribute_name));
    }
    _input->prepare(preserveAccurateTypes);
    const ResultNode* inputResult = _input->getResult();
    if (inputResult != nullptr && inputResult->inherits(StringResultNodeVector::classId)) {
        setResultType(std::make_unique<StringResultNodeVector>());
        _vector_mode = true;
    } else if (inputResult != nullptr && inputResult->inherits(StringResultNode::classId)) {
        setResultType(std::make_unique<StringResultNode>());
        _vector_mode = false;
    } else {
        throw std::runtime_error(
            std::format("Fast map lookup attribute '{}' is not an array of string", _attribute_name));
    }
}

void FastSearchMapDecodeNode::onExecute() const {
    _input->execute();
    if (_vector_mode) {
        const auto& encoded = static_cast<const StringResultNodeVector&>(*_input->getResult()).getVector();
        auto&       decoded = static_cast<StringResultNodeVector&>(updateResult()).getVector();
        decoded.resize(encoded.size());
        for (size_t i = 0; i < encoded.size(); ++i) {
            decoded[i].set(decode(encoded[i].get(), _value_type, _separator));
        }
    } else {
        const auto& encoded = static_cast<const StringResultNode&>(*_input->getResult());
        auto&       decoded = static_cast<StringResultNode&>(updateResult());
        decoded.set(decode(encoded.get(), _value_type, _separator));
    }
}

void FastSearchMapDecodeNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                            vespalib::ObjectOperation&       operation) {
    // The input is not serialized, but must be visible for wiring attributes and setting the docid.
    FunctionNode::selectMembers(predicate, operation);
    if (_input) {
        _input->select(predicate, operation);
    }
}

Serializer& FastSearchMapDecodeNode::onSerialize(Serializer& os) const {
    FunctionNode::onSerialize(os);
    return os << _attribute_name << static_cast<uint8_t>(_value_type) << _separator;
}

Deserializer& FastSearchMapDecodeNode::onDeserialize(Deserializer& is) {
    FunctionNode::onDeserialize(is);
    uint8_t value_type = 0;
    is >> _attribute_name >> value_type >> _separator;
    _value_type = to_value_type(value_type);
    createInput();
    return is;
}

void FastSearchMapDecodeNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    FunctionNode::visitMembers(visitor);
    visit(visitor, "attributeName", _attribute_name);
    visit(visitor, "valueType", std::string(to_string(_value_type)));
    visit(visitor, "separator", _separator);
}

} // namespace search::expression
