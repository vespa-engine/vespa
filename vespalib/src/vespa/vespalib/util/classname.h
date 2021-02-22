// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <typeinfo>

namespace vespalib {

string demangle(const char * native);

template <typename T>
string getClassName(const T & obj) {
    return demangle(typeid(obj).name());
}

template <typename T>
string getClassName() {
    return demangle(typeid(T).name());
}

}
