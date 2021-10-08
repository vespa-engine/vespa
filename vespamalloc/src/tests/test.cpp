// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/thread.h>
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

class Thread : public FastOS_Runnable
{
private:
    void Run(FastOS_ThreadInterface * ti, void * arg) override;
};

int main(int, char *[])
{
    FastOS_ThreadPool threadPool(512*1024);
    printf("Main stack(%p)\n", &threadPool);
    Thread context;

    FastOS_ThreadInterface * th[4];
    for (size_t i=0; i<sizeof(th)/sizeof(th[0]); i++) {
        th[i] = threadPool.NewThread(&context);
    }
    for (size_t i=0; i<sizeof(th)/sizeof(th[0]); i++) {
        th[i]->Join();
        delete th[i];
    }

    return 0;
}

void Thread::Run(FastOS_ThreadInterface *, void *)
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
