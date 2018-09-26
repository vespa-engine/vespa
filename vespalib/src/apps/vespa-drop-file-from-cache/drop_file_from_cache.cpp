// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cstdio>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>

int main(int argc, char **argv) {
    char errorBuf[200];
    if (argc != 2) {
        fprintf(stderr, "%s <filename>", argv[0]);
        return 1;
    }
    const char *fileName = argv[1];
    int fh = open(fileName, O_RDONLY);
    if (fh != -1) {
        int retval = 0;
        int err = posix_fadvise(fh, 0, 0, POSIX_FADV_DONTNEED);
        if (err != 0) {
            const char *errorString = strerror_r(errno, errorBuf, sizeof(errorBuf));
            fprintf(stderr, "posix_fadvise failed: %s", errorString);
            retval = 3;
        }
        close(fh);
        return retval;
    } else {
        const char *errorString = strerror_r(errno, errorBuf, sizeof(errorBuf));
        fprintf(stderr, "Failed opening file %s: %s", fileName, errorString);
        return 2;
    }
}
