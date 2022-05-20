// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace vespalib::xml {

class XmlOutputStream;

/**
 * @class document::XmlSerializable
 *
 * Base class for classes that can be converted into XML.
 */
class XmlSerializable
{
public:
    XmlSerializable() {}
    virtual ~XmlSerializable() = default;

    virtual void printXml(XmlOutputStream& out) const = 0;

    /** Utility function, using printXml() to create a string. */
    virtual std::string toXml(const std::string& indent = "") const;
};

}

namespace vespalib {
// The XmlSerializable and XmlOutputStream is often used in header files
// and is thus available in the vespalib namespace. To not pollute the
// vespalib namespace with all the other classes, use
// "using namespace vespalib::xml" within your printXml functions

using XmlSerializable = vespalib::xml::XmlSerializable;
using XmlOutputStream = vespalib::xml::XmlOutputStream;

} // vespalib

