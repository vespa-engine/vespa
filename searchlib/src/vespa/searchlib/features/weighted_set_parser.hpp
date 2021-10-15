// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "weighted_set_parser.h"
#include <vespa/vespalib/util/issue.h>

using vespalib::Issue;

namespace search::features {

template <typename OutputType>
void
WeightedSetParser::parse(const vespalib::string &input, OutputType &output)
{
    size_t len = input.size();
    // Note that we still handle '(' and ')' for backward compatibility.
    if (len >= 2 && ((input[0] == '{' && input[len - 1] == '}') ||
                     (input[0] == '(' && input[len - 1] == ')')) ) {
        vespalib::stringref s(input.c_str()+1, len - 2);
        while ( ! s.empty() ) {
            vespalib::string::size_type commaPos(s.find(','));
            vespalib::stringref item(s.substr(0, commaPos));
            vespalib::string::size_type colonPos(item.find(':'));
            if (colonPos != vespalib::string::npos) {
                vespalib::string tmpKey(item.substr(0, colonPos));
                vespalib::string::size_type start(tmpKey.find_first_not_of(' '));
                vespalib::stringref key(tmpKey.data() + start, colonPos - start);
                vespalib::stringref value(item.substr(colonPos+1));
                output.insert(key, value);
            } else {
                Issue::report("weighted set parser: Could not parse item '%s' in input string '%s', skipping. "
                              "Expected ':' between key and weight.", vespalib::string(item).c_str(), input.c_str());
            }
            if (commaPos != vespalib::string::npos) {
                s = s.substr(commaPos+1);
            } else {
                s = vespalib::stringref();
            }
        }
    } else {
        Issue::report("weighted set parser: Could not parse input string '%s'. "
                      "Expected surrounding '(' and ')' or '{' and '}'.", input.c_str());
    }
}

}
