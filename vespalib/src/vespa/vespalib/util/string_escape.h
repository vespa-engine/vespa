// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace vespalib {

/**
 * Returns input string but where the following characters are escaped:
 *   - all control chars < char value 32
 *   - <, >, &, " and '
 */
[[nodiscard]] vespalib::string xml_attribute_escaped(vespalib::stringref s);

/**
 * Returns input string but where the following characters are escaped:
 *   - all control chars < char value 32, _except_ linebreak
 *   - <, > and &
 */
[[nodiscard]] vespalib::string xml_content_escaped(vespalib::stringref s);
void write_xml_content_escaped(vespalib::asciistream& out, vespalib::stringref s);
void write_xml_content_escaped(std::ostream& out, vespalib::stringref s);

}
