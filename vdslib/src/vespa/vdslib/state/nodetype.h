// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::NodeType
 *
 * Sets what type of node we're talking about. This class exist so we don't need
 * to duplicate all functions for storage and distributor nodes in states, and
 * to avoid using a bool for type. This also makes it more easily expandable
 * with other node types.
 */
#pragma once

#include <vespa/vespalib/stllike/asciistream.h>
#include <stdint.h>
#include <iostream>

namespace storage {
namespace lib {

class NodeType  {
    typedef vespalib::asciistream asciistream;
    uint16_t _enumValue;
    vespalib::string _name;

    NodeType(const vespalib::stringref & name, uint16_t enumValue);

public:
    static const NodeType DISTRIBUTOR;
    static const NodeType STORAGE;

    /** Throws vespalib::IllegalArgumentException if invalid state given. */
    static const NodeType& get(const vespalib::stringref & serialized);
    const vespalib::string& serialize() const { return _name; }

    operator uint16_t() const { return _enumValue; }

    const vespalib::string & toString() const { return _name; }
    friend std::ostream & operator << (std::ostream & os, const NodeType & n) {
        return os << n.toString();
    }
    friend asciistream & operator << (asciistream & os, const NodeType & n) {
        return os << n.toString();
    }

    bool operator==(const NodeType& other) const { return (&other == this); }
    bool operator!=(const NodeType& other) const { return (&other != this); }

    bool operator<(const NodeType& other) const {
        return (&other == this ? false : *this == NodeType::DISTRIBUTOR);
    }
};

} // lib
} // storage

