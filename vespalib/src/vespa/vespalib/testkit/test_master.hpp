// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once
#include "test_master.h"

#include <sstream>

namespace vespalib {

#if defined(__clang__) && defined(__apple_build_version__)
// cf. https://cplusplus.github.io/LWG/issue2221
template<class charT, class traits>
std::basic_ostream<charT, traits>& operator<<(std::basic_ostream<charT, traits>& os, std::nullptr_t)
{
  return os << (void*) nullptr;
}
#endif

template<class A, class B, class OP>
bool
TestMaster::compare(const char *file, uint32_t line,
                    const char *aName, const char *bName,
                    const char *opText,
                    const A &a, const B &b, const OP &op, bool fatal)
{
    if (op(a,b)) [[likely]]{
        ++threadState().passCnt;
        return true;
    }
    report_compare(file, line, aName, bName, opText, fatal,
                   [&](std::ostream & os) { os << a;},
                   [&](std::ostream & os) { os << b;});
    return false;
}

} // namespace vespalib
