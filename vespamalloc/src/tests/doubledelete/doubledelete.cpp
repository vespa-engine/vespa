// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

void *savedptr;

void delete_ptr_real(char *ptr)
{
    delete ptr;
}

void (*delete_ptr)(char *ptr) = delete_ptr_real;

int main(int argc, char *argv[])
{
    (void) argc;
    (void) argv;
    char * a = new char;
    savedptr = a;
    delete_ptr(a);
    delete_ptr(a);
}
