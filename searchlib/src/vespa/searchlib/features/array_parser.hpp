// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_parser.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <vector>
#include <algorithm>

using vespalib::Issue;

namespace search::features {

template <typename OutputType, typename T>
void
ArrayParser::parse(const vespalib::string &input, OutputType &output)
{
    using SparseVector = std::vector<ValueAndIndex<T>>;
    SparseVector sparse;
    parsePartial(input, sparse);
    std::sort(sparse.begin(), sparse.end());
    if ( ! sparse.empty() ) {
        output.resize(sparse.back().getIndex()+1);
        for (const typename SparseVector::value_type &elem : sparse) {
            output[elem.getIndex()] = elem.getValue();
        }
    }
}

template <typename OutputType>
void
ArrayParser::parsePartial(const vespalib::string &input, OutputType &output)
{
    size_t len = input.size();
    if (len >= 2) {
        vespalib::stringref s(input.c_str()+1, len - 2);
        using ValueAndIndexType = typename OutputType::value_type;
        typename ValueAndIndexType::ValueType value;
        if ((input[0] == '{' && input[len - 1] == '}') ||
            (input[0] == '(' && input[len - 1] == ')') ) {
            size_t key;
            char colon;
            while ( ! s.empty() ) {
                vespalib::string::size_type commaPos(s.find(','));
                vespalib::stringref item(s.substr(0, commaPos));
                vespalib::asciistream is(item);
                try {
                    is >> key >> colon >> value;
                    if ((colon == ':') && is.eof()) {
                        output.emplace_back(value, key);
                    } else {
                        Issue::report("Could not parse item '%s' in query vector '%s', skipping. "
                                      "Expected ':' between dimension and component.",
                                      vespalib::string(item).c_str(), input.c_str());
                        return;
                    }
                } catch (vespalib::IllegalArgumentException & e) {
                    Issue::report("Could not parse item '%s' in query vector '%s', skipping. "
                                  "Incorrect type of operands", vespalib::string(item).c_str(), input.c_str());
                    return;
                }
                if (commaPos != vespalib::string::npos) {
                    s = s.substr(commaPos+1);
                } else {
                    s = vespalib::stringref();
                }
            }
        } else if (len >= 2 && input[0] == '[' && input[len - 1] == ']') {
            vespalib::asciistream is(s);
            uint32_t index(0);
            while (!is.eof()) {
                try {
                    is >> value;
                    output.emplace_back(value, index++);
                } catch (vespalib::IllegalArgumentException & e) {
                    Issue::report("Could not parse item[%ld] = '%s' in query vector '%s', skipping. "
                                  "Incorrect type of operands", output.size(), is.c_str(), vespalib::string(s).c_str());
                    return;
                }
            }
        }
    } else {
        Issue::report("Could not parse query vector '%s'. Expected surrounding '(' and ')' or '{' and '}'.", input.c_str());
    }
}

template void
ArrayParser::parse(const vespalib::string &input, std::vector<int> &);

}
