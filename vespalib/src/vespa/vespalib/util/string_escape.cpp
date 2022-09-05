// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_escape.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vector>
#include <ostream>

namespace vespalib {

namespace {

std::vector<bool> precompute_escaped_xml_chars() {
    std::vector<bool> vec(256, false);
    for (uint32_t i=0; i<32; ++i) {
        vec[i] = true;
    }
    vec['\n'] = false;
    vec['<'] = true;
    vec['>'] = true;
    vec['&'] = true;
    return vec;
}

std::vector<bool> escaped_xml_chars = precompute_escaped_xml_chars();

template <typename StreamT>
void do_write_xml_content_escaped(StreamT& out, vespalib::stringref str) {
    for (const char s : str) {
        if (escaped_xml_chars[static_cast<uint8_t>(s)]) {
            if      (s == '<') out << "&lt;";
            else if (s == '>') out << "&gt;";
            else if (s == '&') out << "&amp;";
            else {
                out << "&#" << static_cast<int>(s) << ";";
            }
        } else {
            out << s;
        }
    }
}

}

vespalib::string xml_attribute_escaped(vespalib::stringref str) {
    vespalib::asciistream ost;
    for (const char s : str) {
        if (s == '"' || s == '\'' || s == '\n'
            || escaped_xml_chars[static_cast<uint8_t>(s)])
        {
            if      (s == '<')  ost << "&lt;";
            else if (s == '>')  ost << "&gt;";
            else if (s == '&')  ost << "&amp;";
            else if (s == '"')  ost << "&quot;";
            else if (s == '\'') ost << "&#39;";
            else {
                ost << "&#" << static_cast<int>(s) << ";";
            }
        } else {
            ost << s;
        }
    }
    return ost.str();
}

vespalib::string xml_content_escaped(vespalib::stringref str) {
    vespalib::asciistream out;
    do_write_xml_content_escaped(out, str);
    return out.str();
}

void write_xml_content_escaped(vespalib::asciistream& out, vespalib::stringref str) {
    do_write_xml_content_escaped(out, str);
}

void write_xml_content_escaped(std::ostream& out, vespalib::stringref str) {
    do_write_xml_content_escaped(out, str);
}

}
