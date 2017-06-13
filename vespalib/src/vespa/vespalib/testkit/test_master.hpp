// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <sstream>

namespace vespalib {

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
        vespalib::LockGuard guard(_lock);
        checkFailed(guard, file, line, str.c_str());
        printDiff(guard, str, file, line, lhs.str(), rhs.str());
        handleFailure(guard, fatal);
    }
    return false;
}

} // namespace vespalib
