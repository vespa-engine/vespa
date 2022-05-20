// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "xmlserializable.h"
#include "xmlstream.h"
#include <sstream>

namespace vespalib::xml {

std::string
XmlSerializable::toXml(const std::string& indent) const
{
    std::ostringstream ost;
    XmlOutputStream xos(ost, indent);
    printXml(xos);
    return ost.str();
}

}
