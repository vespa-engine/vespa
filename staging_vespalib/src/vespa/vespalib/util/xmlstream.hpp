// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "xmlstream.h"
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

namespace vespalib::xml {

template<typename T>
XmlAttribute::XmlAttribute(const std::string& name, T value, uint32_t flags)
    : _name(name),
      _value(),
      _next()
{
    std::ostringstream ost;
    if (flags & HEX) ost << std::hex << "0x";
    ost << value;
    _value = ost.str();
    if (!isLegalName(name)) {
        throw IllegalArgumentException("Name '" + name + "' contains "
                "illegal XML characters and cannot be used as attribute name");
    }
}

}
