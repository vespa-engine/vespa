// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

void *savedptr;

int main(int argc, char *argv[])
{
    (void) argc;
    (void) argv;
    char * a = new char;
    savedptr = a;
    delete a;
    delete a;
}
