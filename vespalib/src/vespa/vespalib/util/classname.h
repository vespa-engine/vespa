// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <typeinfo>

namespace vespalib {

std::string demangle(const char * native);

template <typename T>
std::string getClassName(const T & obj) {
    return demangle(typeid(obj).name());
}

template <typename T>
std::string getClassName() {
    return demangle(typeid(T).name());
}

}
