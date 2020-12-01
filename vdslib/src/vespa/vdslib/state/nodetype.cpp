// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nodetype.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

namespace storage::lib {

// WARNING: Because static initialization happen in random order, the State
// class use these enum values directly during static initialization since
// these objects may yet not exist. Update in State if you change this.
const NodeType NodeType::STORAGE("storage", NodeType::Type::STORAGE);
const NodeType NodeType::DISTRIBUTOR("distributor", NodeType::Type::DISTRIBUTOR);

const NodeType&
NodeType::get(vespalib::stringref serialized)
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

const NodeType&
NodeType::get(Type type) noexcept
{
    switch (type) {
        case Type::STORAGE:
            return STORAGE;
        case Type::DISTRIBUTOR:
            return DISTRIBUTOR;
        case Type::UNKNOWN:
            assert(type != Type::UNKNOWN);
    }
    abort();
}

NodeType::NodeType(vespalib::stringref name, Type type) noexcept
    : _type(type), _name(name)
{
}

std::ostream & operator << (std::ostream & os, const NodeType & n) {
    return os << n.toString();
}

vespalib::asciistream & operator << (vespalib::asciistream & os, const NodeType & n) {
    return os << n.toString();
}

}
