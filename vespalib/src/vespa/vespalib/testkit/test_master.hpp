// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    if (op(a,b)) {
        ++threadState().passCnt;
        return true;
    }
    std::string str;
    str += aName;
    str += opText;
    str += bName;
    std::ostringstream lhs;
    std::ostringstream rhs;
    lhs << a;
    rhs << b;
    {
        lock_guard guard(_lock);
        checkFailed(guard, file, line, str.c_str());
        printDiff(guard, str, file, line, lhs.str(), rhs.str());
        handleFailure(guard, fatal);
    }
    return false;
}

} // namespace vespalib
