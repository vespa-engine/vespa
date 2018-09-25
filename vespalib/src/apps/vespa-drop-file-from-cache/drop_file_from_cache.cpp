// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "%s <filename>", argv[0]);
        return 1;
    }
    const char *fileName = argv[1];
    int fh = open(fileName, O_RDONLY);
    if (fh != -1) {
        int err = posix_fadvise(fh, 0, 0, POSIX_FADV_DONTNEED);
        if (err != 0) {
            perror("posix_fadvise failed");
        }
        close(fh);
    } else {
        perror("Failed opening file");
        return 1;
    }
    return 0;
}
