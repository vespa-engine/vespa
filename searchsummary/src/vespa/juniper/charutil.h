// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace juniper
{

template <typename T>
int strncmp(const T* s1, const T* s2, size_t n)
{
    size_t i = 0;
    for (; i < n; i++)
        if (s1[i] != s2[i]) break;
    if (i == n) return 0;
    return (int)s1[i] - (int)s2[i];
}

} // end namespace juniper

