// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nodetype.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace lib {

// WARNING: Because static initialization happen in random order, the State
// class use these enum values directly during static initialization since
// these objects may yet not exist. Update in State if you change this.
const NodeType NodeType::STORAGE("storage", 0);
const NodeType NodeType::DISTRIBUTOR("distributor", 1);

const NodeType&
NodeType::get(const vespalib::stringref & serialized)
{
    if (serialized == STORAGE._name) {
        return STORAGE;
    }
    if (serialized == DISTRIBUTOR._name) {
        return DISTRIBUTOR;
    }
    throw vespalib::IllegalArgumentException(
            "Unknown node type " + serialized + " given.", VESPA_STRLOC);
}

NodeType::NodeType(const vespalib::stringref & name, uint16_t enumValue)
    : _enumValue(enumValue), _name(name)
{
}

std::ostream & operator << (std::ostream & os, const NodeType & n) {
    return os << n.toString();
}

vespalib::asciistream & operator << (vespalib::asciistream & os, const NodeType & n) {
    return os << n.toString();
}

} // lib
} // storage
