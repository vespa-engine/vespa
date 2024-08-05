// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
[[nodiscard]] vespalib::string xml_attribute_escaped(std::string_view s);

/**
 * Returns input string but where the following characters are escaped:
 *   - all control chars < char value 32, _except_ linebreak
 *   - <, > and &
 */
[[nodiscard]] vespalib::string xml_content_escaped(std::string_view s);
void write_xml_content_escaped(vespalib::asciistream& out, std::string_view s);
void write_xml_content_escaped(std::ostream& out, std::string_view s);

}
