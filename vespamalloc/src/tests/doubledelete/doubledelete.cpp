// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>

int main(int argc, char *argv[])
{
    (void) argc;
    (void) argv;
    char * a = new char [100];
    delete a;
    delete a;
}
