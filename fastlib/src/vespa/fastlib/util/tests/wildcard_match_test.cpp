// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <iostream>
#include "../wildcard_match.h"

namespace
{
template<typename T>
bool Test(const T* word, const T* pattern, bool expect)
{
    if (fast::util::wildcard_match(word, pattern) != expect)
    {
        if (expect == true)
            std::cout << "ERROR: " << word << " didn't match " << pattern << std::endl;
        else
            std::cout << "ERROR: " << word << " matched " << pattern << std::endl;

        return false;
    }

    return true;
}
}

int main(int, char **)
{
    bool success =
        Test("a", "b", false) &&
        Test("b", "b", true) &&
        Test("abc", "def", false) &&
        Test("def", "def", true) &&
        Test("def", "d?f", true) &&
        Test("def", "d?d", false) &&
        Test("def", "??d", false) &&
        Test("def", "d??", true) &&
        Test("abcdef", "a*e", false) &&
        Test("abcdef", "a*f", true) &&
        Test("abcdef", "a?c*f", true) &&
        Test("abcdef", "a?b*f", false) &&
        Test("abcdef", "a*b*f", true) &&
        Test("abcdef", "abc*", true) &&
        Test("abcdef", "*def", true);

    if (success == true)
        std::cout << "wildcard_match_test: SUCCESS" << std::endl;

    return 0;
}
