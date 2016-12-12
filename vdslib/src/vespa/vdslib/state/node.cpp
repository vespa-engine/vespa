// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace lib {

Node::Node(const NodeType& type, uint16_t index)
    : _type(&type),
      _index(index)
{
}

void
Node::print(vespalib::asciistream& as, const PrintProperties&) const
{
    as << *_type << '.' << _index;
}

} // lib
} // storage
