// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "functionnode.h"

#include <vespa/searchcommon/common/schema.h>

namespace search::expression {

class AttributeNode;

/**
 * Decodes the entries of a map, or array of struct, with fast map search.
 *
 * The node is named "fieldname.lookupname", and reads the hidden array of string attribute
 * "fieldname$lookupname" through an attribute node it creates itself, where each element is the key,
 * a separator (0x7F) and the encoded value.
 * The result is an array of strings, one per entry, on the form key + separator + decoded value,
 * where the separator is ":" unless set otherwise.
 *
 * The name and the separator are serialized. The value type needed for decoding must be given by configure() with the
 * fast map lookup of the field before the node is prepared.
 */
class DecodeFastMapLookupNode : public FunctionNode {
public:
    using FastMapLookup = search::index::Schema::FastMapLookup;
    enum class ValueType : uint8_t { UNKNOWN, INT, LONG, FLOAT, DOUBLE, STRING };

    static constexpr char encoded_separator = 0x7F;
    static constexpr std::string_view default_separator = ":";

private:
    std::string                    _name;
    std::string                    _separator;
    ValueType                      _valueType;
    std::unique_ptr<AttributeNode> _input; // reads the hidden attribute; created from the name, not serialized

    void createInput();
    void onPrepare(bool preserveAccurateTypes) override;
    void onExecute() const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;

public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_EXPRESSIONNODE(DecodeFastMapLookupNode);
    DecodeFastMapLookupNode() noexcept;
    explicit DecodeFastMapLookupNode(std::string_view name, std::string_view separator = default_separator);
    DecodeFastMapLookupNode(const DecodeFastMapLookupNode&);
    DecodeFastMapLookupNode& operator=(const DecodeFastMapLookupNode&);
    ~DecodeFastMapLookupNode() override;

    /**
     * Configure this node with the fast map lookup it decodes, which must have the same name as this node
     * (the caller finds it by lookupAttributeName()). Throws if the value type cannot be decoded.
     */
    void configure(const FastMapLookup& lookup);

    [[nodiscard]] const std::string& getName() const noexcept { return _name; }
    [[nodiscard]] const std::string& getSeparator() const noexcept { return _separator; }
    DecodeFastMapLookupNode& setSeparator(std::string_view separator) {
        _separator = separator;
        return *this;
    }
    [[nodiscard]] ValueType getValueType() const noexcept { return _valueType; }

    /** Returns the name of the hidden attribute, "fieldname$lookupname" for node name "fieldname.lookupname". */
    [[nodiscard]] std::string lookupAttributeName() const;

    /**
     * Returns the decoded form of one element of the hidden attribute, with the given separator between key and value.
     *
     * The key is kept as fed. Values are formatted with std::format("{}"): int and long in decimal, float and double
     * in the shortest form that round-trips (for example "1", "-1.5", "1e+30", "inf", "-inf" and "nan"), and strings
     * as is. An element without the encoded separator, or with a value that is not valid excess hex for the value
     * type, is returned as stored, without decoding.
     */
    static std::string decode(std::string_view encoded, ValueType valueType,
                              std::string_view separator = default_separator);

    static ValueType toValueType(std::string_view typeName);

    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
};

} // namespace search::expression
