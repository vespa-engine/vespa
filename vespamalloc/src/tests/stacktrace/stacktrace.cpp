// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

void * run(void * arg)
{
    (void) arg;
    char * a = new char [100]; // a should not remain in stacktrace
    char * b = new char [1];   // but b should as it not deleted.
    (void) b;
    delete [] a;
    return NULL;
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
    int retval = pthread_create(&tid, NULL, run, NULL);
    if (retval != 0) {
        perror("pthread_create failed");
        abort();
    }
    retval = pthread_join(tid, NULL);
    if (retval != 0) {
        perror("pthread_join failed");
        abort();
    }
}
