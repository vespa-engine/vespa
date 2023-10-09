// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cstdio>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <system_error>

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "%s <filename>\n", argv[0]);
        return 1;
    }
    const char *fileName = argv[1];
    int fh = open(fileName, O_RDONLY);
    if (fh == -1) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "Failed opening file %s: %s\n", fileName, ec.message().c_str());
        return 2;
    }

    int retval = 0;
#ifdef __linux__
    int err = posix_fadvise(fh, 0, 0, POSIX_FADV_DONTNEED);
    if (err != 0) {
        std::error_code ec(errno, std::system_category());
        fprintf(stderr, "posix_fadvise failed: %s\n", ec.message().c_str());
        retval = 3;
    }
#endif
    close(fh);
    return retval;
}
