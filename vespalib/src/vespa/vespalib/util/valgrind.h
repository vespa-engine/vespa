// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/types.h>

namespace vespalib {

/**
 * utilities to help valgrind perform better checking of your program
 **/
class Valgrind
{
public:
    /**
     * This method will write the buffer to '/dev/null' and thereby provoke the
     * valgrind check of parameters to system calls.
     * @param buf The buffer to write.
     * @param sz The size of the buffer
     * @return The number of bytes written.
     */
    static size_t testSystemCall(const void * buf, size_t sz);
    /**
     * This method will use the buffer given in a way that will trigger valgrind
     * check for uninitialized data.
     * @param buf The buffer to check.
     * @param sz The size of the buffer
     * @return Just a hash value of the buffer.
     */
    static size_t testUninitialized(const void * buf, size_t sz);
};

}

