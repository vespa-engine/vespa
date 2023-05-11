// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nexus.h"

namespace vespalib::test {

size_t &
Nexus::my_thread_id() {
    thread_local size_t thread_id = invalid_thread_id;
    return thread_id;
}

Nexus::~Nexus() = default;

}
