// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/defaults.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <variable>\n", argv[0]);
        fprintf(stderr, "  the variable must be 'home' or 'portbase' currently\n");
        return 1;
    }
    if (strcmp(argv[1], "home") == 0) {
        printf("%s\n", vespa::Defaults::vespaHome());
        return 0;
    } else if (strcmp(argv[1], "portbase") == 0) {
        printf("%d\n", vespa::Defaults::vespaPortBase());
        return 0;
    } else {
        fprintf(stderr, "Unknown variable '%s'\n", argv[1]);
        return 1;
    }
}
