// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::Node
 *
 * Refers to a single node in a VDS cluster.
 */

#pragma once

#include "nodetype.h"

namespace storage::lib {

class Node {
    const NodeType* _type;
    uint16_t        _index;

public:
    Node() noexcept : _type(&NodeType::STORAGE), _index(0) { }
    Node(const NodeType& type, uint16_t index) noexcept
        : _type(&type), _index(index) { }

    const NodeType& getType() const noexcept { return *_type; }
    uint16_t getIndex() const noexcept { return _index; }
    uint32_t hash() const noexcept { return (_index << 1) | *_type; }

    bool operator==(const Node& other) const noexcept {
        return (other._index == _index && *other._type == *_type);
    }
    bool operator!=(const Node& other) const noexcept {
        return (other._index != _index || *other._type != *_type);
    }

    bool operator<(const Node& n) const noexcept {
        if (*_type != *n._type) return (*_type < *n._type);
        return (_index < n._index);
    }
};

std::ostream & operator << (std::ostream & os, const Node & n);
vespalib::asciistream & operator << (vespalib::asciistream & os, const Node & n);

}
