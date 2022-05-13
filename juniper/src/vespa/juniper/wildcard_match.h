// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace fast
{
namespace util
{
template<typename T>
bool wildcard_match(const T* word, const T* pattern, T multiple = '*',
                    T single = '?')
{
    while (*word != 0)
        if (*pattern == 0)
            return false;
        else if (*pattern == multiple)
        {
            // advance past occurrences of multiple
            while (*pattern == multiple)
                ++pattern;

            // if pattern ended with multiple, we're done
            if (*pattern == 0)
                return true;

            while (*word != 0)
            {
                // does this position in the word match
                if (*pattern == single || *pattern == *word)
                {
                    // test the rest of the word
                    if (wildcard_match(word, pattern, multiple, single) == true)
                    {
                        // it matched
                        return true;
                    }
                }

                // try next character
                ++word;
            }
        }
        else if (*pattern != single && *pattern != *word)
            return false;
        else
        {
            ++word;
            ++pattern;
        }

    // should be at end of pattern too if the word matched
    return *pattern == 0;
}
}
}
