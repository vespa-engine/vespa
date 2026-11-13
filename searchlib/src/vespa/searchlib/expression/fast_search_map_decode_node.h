// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "functionnode.h"

namespace search::expression {

class AttributeNode;

/**
 * Decodes the entries of a map, or array of struct, with fast map search.
 *
 * The node reads the hidden array of string attribute "fieldname$lookupname" through an attribute node it creates
 * itself, where each element is the key, a separator (0x7F) and the value encoded according to the value type.
 * The result is an array of strings, one per entry, on the form key + separator + decoded value,
 * where the separator is ":" unless set otherwise.
 * When the input is a single string (as when grouping on the same array, or an associated one, has already
 * expanded it into separate elements), the result is the single decoded string.
 *
 * The attribute name, value type and separator are serialized; the container resolves the value type from the schema.
 */
class FastSearchMapDecodeNode : public FunctionNode {
public:
    // Serialized as one byte; keep in sync with the Java FastSearchMapDecodeNode.ValueType.
    enum class ValueType : uint8_t { UNKNOWN = 0, INT = 1, LONG = 2, FLOAT = 3, DOUBLE = 4, STRING = 5 };

    static constexpr char             encoded_separator = 0x7F;
    static constexpr std::string_view default_separator = ":";

private:
    std::string                    _attribute_name;
    ValueType                      _value_type;
    bool                           _vector_mode;
    std::string                    _separator;
    std::unique_ptr<AttributeNode> _input; // reads the hidden attribute; created from the name, not serialized

    void createInput();
    void onPrepare(bool preserveAccurateTypes) override;
    void onExecute() const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;

public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_EXPRESSIONNODE(FastSearchMapDecodeNode);
    FastSearchMapDecodeNode() noexcept;
    FastSearchMapDecodeNode(std::string_view attribute_name, ValueType value_type,
                            std::string_view separator = default_separator);
    FastSearchMapDecodeNode(const FastSearchMapDecodeNode&);
    FastSearchMapDecodeNode& operator=(const FastSearchMapDecodeNode&);
    ~FastSearchMapDecodeNode() override;

    [[nodiscard]] const std::string& attribute_name() const noexcept { return _attribute_name; }
    [[nodiscard]] ValueType value_type() const noexcept { return _value_type; }
    [[nodiscard]] const std::string& separator() const noexcept { return _separator; }
    FastSearchMapDecodeNode& separator(std::string_view separator) {
        _separator = separator;
        return *this;
    }

    /**
     * Returns the decoded form of one element of the hidden attribute, with separator between key and value.
     *
     * The key is kept as fed. Values are formatted with std::format("{}"): int and long in decimal, float and double
     * in the shortest form that round-trips (for example "1", "-1.5", "1e+30", "inf", "-inf" and "nan"), and strings
     * as is. An element without the encoded separator, or with a value that is not valid excess hex for the value
     * type, is returned as stored, without decoding.
     */
    static std::string decode(std::string_view encoded, ValueType value_type,
                              std::string_view separator = default_separator);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

} // namespace search::expression
