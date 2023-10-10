// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/valgrind.h>
#include <cassert>
#include <fcntl.h>
#include <unistd.h>

namespace vespalib {

size_t Valgrind::testSystemCall(const void * buf, size_t sz)
{
    int fh = open("/dev/null", O_RDWR, 0644);
    assert (fh != -1);
    size_t written = write(fh, buf, sz);
    close(fh);
    assert(written == sz);
    return written;
}

size_t Valgrind::testUninitialized(const void * buf, size_t sz)
{
    size_t sum(0);
    const char * b(static_cast<const char *>(buf));
    for(size_t i(0); i < sz; i++) {
        sum += b[i];
    }
    return sum;
}


}
