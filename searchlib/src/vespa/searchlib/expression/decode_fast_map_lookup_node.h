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
 *
 * The attribute name, value type and separator are serialized; the container resolves the value type from the schema.
 */
class DecodeFastMapLookupNode : public FunctionNode {
public:
    // Serialized as one byte; keep in sync with the Java DecodeFastMapLookupNode.ValueType.
    enum class ValueType : uint8_t { UNKNOWN = 0, INT = 1, LONG = 2, FLOAT = 3, DOUBLE = 4, STRING = 5 };

    static constexpr char             encoded_separator = 0x7F;
    static constexpr std::string_view default_separator = ":";

private:
    std::string                    _attributeName;
    ValueType                      _valueType;
    std::string                    _separator;
    std::unique_ptr<AttributeNode> _input; // reads the hidden attribute; created from the name, not serialized

    void createInput();
    void onPrepare(bool preserveAccurateTypes) override;
    void onExecute() const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;

public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_EXPRESSIONNODE(DecodeFastMapLookupNode);
    DecodeFastMapLookupNode() noexcept;
    DecodeFastMapLookupNode(std::string_view attributeName, ValueType valueType,
                            std::string_view separator = default_separator);
    DecodeFastMapLookupNode(const DecodeFastMapLookupNode&);
    DecodeFastMapLookupNode& operator=(const DecodeFastMapLookupNode&);
    ~DecodeFastMapLookupNode() override;

    [[nodiscard]] const std::string& getAttributeName() const noexcept { return _attributeName; }
    [[nodiscard]] ValueType getValueType() const noexcept { return _valueType; }
    [[nodiscard]] const std::string& getSeparator() const noexcept { return _separator; }
    DecodeFastMapLookupNode& setSeparator(std::string_view separator) {
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
    static std::string decode(std::string_view encoded, ValueType valueType,
                              std::string_view separator = default_separator);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

} // namespace search::expression
