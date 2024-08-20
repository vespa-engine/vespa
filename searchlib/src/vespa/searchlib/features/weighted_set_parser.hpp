// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "weighted_set_parser.h"
#include <vespa/vespalib/util/issue.h>

using vespalib::Issue;

namespace search::features {

template <typename OutputType>
void
WeightedSetParser::parse(const std::string &input, OutputType &output)
{
    size_t len = input.size();
    // Note that we still handle '(' and ')' for backward compatibility.
    if (len >= 2 && ((input[0] == '{' && input[len - 1] == '}') ||
                     (input[0] == '(' && input[len - 1] == ')')) ) {
        std::string_view s(input.data()+1, len - 2);
        while ( ! s.empty() ) {
            std::string::size_type commaPos(s.find(','));
            std::string_view item(s.substr(0, commaPos));
            std::string::size_type colonPos(item.find(':'));
            if (colonPos != std::string::npos) {
                std::string tmpKey(item.substr(0, colonPos));
                std::string::size_type start(tmpKey.find_first_not_of(' '));
                if (start == std::string::npos) {
                    start = colonPos; // Spaces only => empty key
                }
                std::string key(tmpKey.data() + start, colonPos - start);
                std::string_view value(item.substr(colonPos+1));
                output.insert(key, value);
            } else {
                Issue::report("weighted set parser: Could not parse item '%s' in input string '%s', skipping. "
                              "Expected ':' between key and weight.", std::string(item).c_str(), input.c_str());
            }
            if (commaPos != std::string::npos) {
                s = s.substr(commaPos+1);
            } else {
                s = std::string_view();
            }
        }
    } else {
        Issue::report("weighted set parser: Could not parse input string '%s'. "
                      "Expected surrounding '(' and ')' or '{' and '}'.", input.c_str());
    }
}

}
