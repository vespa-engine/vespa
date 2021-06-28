// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cstdlib>
#include <cstdio>
#include <pthread.h>
#include <dlfcn.h>
#include <cassert>

void * run(void * arg)
{
    (void) arg;
    char * a = new char [100]; // a should not remain in stacktrace
    char * b = new char [1];   // but b should as it not deleted.
    (void) b;
    delete [] a;
    return nullptr;
}

void verify_that_vespamalloc_datasegment_size_exists() {
    assert(dlsym(RTLD_NEXT, "vespamalloc_datasegment_size") != nullptr);
}

int main(int argc, char *argv[])
{
    (void) argc;
    (void) argv;
    char * a = new char [100]; // a should not remain in stacktrace
    char * b = new char [1];   // but b should as it not deleted.
    (void) b;
    delete [] a;
    pthread_t tid;
    int retval = pthread_create(&tid, nullptr, run, nullptr);
    if (retval != 0) {
        perror("pthread_create failed");
        abort();
    }
    retval = pthread_join(tid, nullptr);
    if (retval != 0) {
        perror("pthread_join failed");
        abort();
    }

    verify_that_vespamalloc_datasegment_size_exists();
}
