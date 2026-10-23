// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/asciistream.h>

#include <iosfwd>
#include <string>

namespace vespalib {

/**
 * Returns input string but where the following characters are escaped:
 *   - all control chars < char value 32
 *   - <, >, &, " and '
 */
[[nodiscard]] std::string xml_attribute_escaped(std::string_view s);

/**
 * Returns input string but where the following characters are escaped:
 *   - all control chars < char value 32, _except_ linebreak
 *   - <, > and &
 */
[[nodiscard]] std::string xml_content_escaped(std::string_view s);
void write_xml_content_escaped(vespalib::asciistream& out, std::string_view s);
void write_xml_content_escaped(std::ostream& out, std::string_view s);

/*
 * "C-style" generic escaping of strings.
 *
 * Characters that are considered printable under US-ASCII are emitted as-is, aside
 * from double quotes and backslashes, which are escaped as \" and \\, respectively.
 *
 * Special characters that are "named" in C are emitted as their escaped, named variant.
 * Example: 0x00 (nil) is \0, 0x09 (horizontal tab) is \t, 0x0D (carriage return) is \r etc.
 *
 * All other characters are emitted in escaped hex form \x##
 */
[[nodiscard]] std::string escape(std::string_view s);

} // namespace vespalib
