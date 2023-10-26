// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "replace_variable.h"
#include "asciistream.h"
#include <ctype.h>
 
namespace vespalib {

vespalib::string
replace_variable(const vespalib::string &input,
                 const vespalib::string &variable,
                 const vespalib::string &replacement)
{
    vespalib::asciistream result;
    bool is_in_word = false;
    size_t last_word_start = 0;
    size_t last_word_size = 0;
    for (size_t i = 0; i < input.size(); ++i) {
        char c = input[i];
        if (isalnum(c) || (c == '_')) {
            if (! is_in_word) {
                last_word_start = i;
                last_word_size = 0;
                is_in_word = true;
            }
            ++last_word_size;
        } else {
            if (is_in_word) {
                vespalib::string last_word = input.substr(last_word_start, last_word_size);
                if (last_word == variable) {
                    result << replacement;
                } else {
                    result << last_word;
                }
                is_in_word = false;
            }
            result << c;
       }
   }
   if (is_in_word) {
       vespalib::string last_word = input.substr(last_word_start, last_word_size);
       if (last_word == variable) {
           result << replacement;
       } else {
           result << last_word;
       }
   }
   return result.str();
}

} // namespace
