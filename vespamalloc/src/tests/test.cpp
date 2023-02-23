// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/thread.h>
#include <cstdlib>
#include <cstdio>

namespace vespamalloc {
void info();
}

void testbigblocks(size_t count, size_t sz)
{
    for (size_t i=0; i < count; i++) {
        char * a = new char[sz];
        delete [] a;
        a = new char [sz-1];
        delete [] a;
    }
}

void testdd()
{
    char * a = (char *)malloc(0x1003);
    free(a);
}

void thread_run();

int main(int, char *[])
{
    vespalib::ThreadPool threadPool;
    printf("Main stack(%p)\n", &threadPool);

    for (int i = 0; i < 4; ++i) {
        threadPool.start([](){thread_run();});
    }
    threadPool.join();
    return 0;
}

void thread_run()
{
    char * a = new char [100];
    delete [] a;
    char * b;

    testbigblocks(1, 0x800003);
    testbigblocks(64000, 0x200003);
    for (size_t i=0; i<100;i++) a = new char[400];
    testdd();
    b = new char[200];
    (void)b;
}
