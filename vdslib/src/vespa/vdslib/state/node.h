// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::Node
 *
 * Refers to a single node in a VDS cluster.
 */

#pragma once

#include "nodetype.h"
#include <vespa/vespalib/util/printable.h>

namespace storage {
namespace lib {

class Node : public vespalib::AsciiPrintable {
    const NodeType* _type;
    uint16_t _index;

public:
    Node() : _type(&NodeType::STORAGE), _index(0) {}
    Node(const NodeType& type, uint16_t index);

    const NodeType& getType() const { return *_type; }
    uint16_t getIndex() const { return _index; }

    void print(vespalib::asciistream&, const PrintProperties&) const override;

    bool operator==(const Node& other) const
        { return (other._index == _index && *other._type == *_type); }
    bool operator!=(const Node& other) const
        { return (other._index != _index || *other._type != *_type); }

    bool operator<(const Node& n) const {
        if (*_type != *n._type) return (*_type < *n._type);
        return (_index < n._index);
    }
};

} // lib
} // storage
