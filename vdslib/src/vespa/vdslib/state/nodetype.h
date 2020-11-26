// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::NodeType
 *
 * Sets what type of node we're talking about. This class exist so we don't need
 * to duplicate all functions for storage and distributor nodes in states, and
 * to avoid using a bool for type. This also makes it more easily expandable
 * with other node types.
 */
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class asciistream;
}

namespace storage::lib {

class NodeType  {
public:
    NodeType(const NodeType &) = delete;
    NodeType & operator = (const NodeType &) = delete;
    NodeType(NodeType &&) = delete;
    NodeType & operator =(NodeType &&) = delete;
    enum class Type : uint8_t {STORAGE = 0, DISTRIBUTOR = 1, UNKNOWN = 2};
    static const NodeType DISTRIBUTOR;
    static const NodeType STORAGE;

    /** Throws vespalib::IllegalArgumentException if invalid state given. */
    static const NodeType& get(vespalib::stringref serialized);
    static const NodeType& get(Type type) noexcept;
    const vespalib::string& serialize() const noexcept { return _name; }

    Type getType() const noexcept { return _type; }
    operator uint16_t() const noexcept { return static_cast<uint16_t>(_type); }

    const vespalib::string & toString() const noexcept { return _name; }
    bool operator==(const NodeType& other) const noexcept { return (&other == this); }
    bool operator!=(const NodeType& other) const noexcept { return (&other != this); }

    bool operator<(const NodeType& other) const noexcept {
        return (&other == this ? false : *this == NodeType::DISTRIBUTOR);
    }
private:
    Type             _type;
    vespalib::string _name;

    NodeType(vespalib::stringref name, Type type) noexcept;
};

std::ostream & operator << (std::ostream & os, const NodeType & n);
vespalib::asciistream & operator << (vespalib::asciistream & os, const NodeType & n);

}

