// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::StringUtil
 * \ingroup util
 *
 * \brief Utility class for string related functionality.
 */

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace document {

class StringUtil {
public:
    /**
     * Escapes a string, turning backslash or unprintable characters into
     * \\\\ \\n \\t \\f \\r or \\x##.
     *
     * The delimiter can be set to also escape an otherwise printable character that you don't
     * want the string to contain. (Useful to escape content to use in a context where you want
     * to use a given delimiter)
     */
    static vespalib::string escape(const vespalib::string & source, char delimiter = '\0') {
        vespalib::string escaped;
        return escape(source, escaped, delimiter);
    }
    /**
    */
    static const vespalib::string & escape(const vespalib::string & source, vespalib::string & dst,
                                      char delimiter = '\0');

    /**
     * Unescape a string, replacing \\\\ \\n \\t \\f \\r or \\x## with their
     * ascii value counterparts.
     */
    static vespalib::string unescape(vespalib::stringref source);

    /**
     * Print whatever source points to in a readable format.
     *
     * @param output Stream to print to
     * @param source Pointer to what should be printed
     * @param size The number of bytes to print.
     * @param columnwidth Max number of bytes to print per line.
     * @param inlinePrintables If true, print printable characters in the list
     *                         instead of ASCII values. If false, print ASCII
     *                         values for all bytes, and add printables output
     *                         only to the right column.
     * @param indent Whitespace to put after each newline in output
     */
    static void printAsHex(std::ostream& output,
                           const void* source, unsigned int size,
                           unsigned int columnwidth = 16,
                           bool inlinePrintables = false,
                           const std::string& indent = "");
};

} // document

