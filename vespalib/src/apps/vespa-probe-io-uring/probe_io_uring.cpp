// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef VESPA_HAS_IO_URING

#include <sys/utsname.h>
#include <liburing.h>
#include <liburing/io_uring.h>

#include <string>
#include <vector>

std::vector<std::string> op_names = {
    "IORING_OP_NOP",
    "IORING_OP_READV",
    "IORING_OP_WRITEV",
    "IORING_OP_FSYNC",
    "IORING_OP_READ_FIXED",
    "IORING_OP_WRITE_FIXED",
    "IORING_OP_POLL_ADD",
    "IORING_OP_POLL_REMOVE",
    "IORING_OP_SYNC_FILE_RANGE",
    "IORING_OP_SENDMSG",
    "IORING_OP_RECVMSG",
    "IORING_OP_TIMEOUT",
    "IORING_OP_TIMEOUT_REMOVE",
    "IORING_OP_ACCEPT",
    "IORING_OP_ASYNC_CANCEL",
    "IORING_OP_LINK_TIMEOUT",
    "IORING_OP_CONNECT",
    "IORING_OP_FALLOCATE",
    "IORING_OP_OPENAT",
    "IORING_OP_CLOSE",
    "IORING_OP_FILES_UPDATE",
    "IORING_OP_STATX",
    "IORING_OP_READ",
    "IORING_OP_WRITE",
    "IORING_OP_FADVISE",
    "IORING_OP_MADVISE",
    "IORING_OP_SEND",
    "IORING_OP_RECV",
    "IORING_OP_OPENAT2",
    "IORING_OP_EPOLL_CTL",
    "IORING_OP_SPLICE",
    "IORING_OP_PROVIDE_BUFFERS",
    "IORING_OP_REMOVE_BUFFERS",
    "IORING_OP_TEE",
    "IORING_OP_SHUTDOWN",
    "IORING_OP_RENAMEAT",
    "IORING_OP_UNLINKAT",
    "IORING_OP_MKDIRAT",
    "IORING_OP_SYMLINKAT",
    "IORING_OP_LINKAT",
    "IORING_OP_MSG_RING",
    "IORING_OP_FSETXATTR",
    "IORING_OP_SETXATTR",
    "IORING_OP_FGETXATTR",
    "IORING_OP_GETXATTR",
    "IORING_OP_SOCKET",
    "IORING_OP_URING_CMD",
    "IORING_OP_SEND_ZC",
    "IORING_OP_SENDMSG_ZC"
};

int main() {
    fprintf(stderr, "Vespa was compiled with io_uring\n");
    utsname host_info;
    uname(&host_info);
    fprintf(stderr, "kernel version: %s\n", host_info.release);
    io_uring_probe *probe = io_uring_get_probe();
    if (probe == nullptr) {
        fprintf(stderr, "io_uring probe failed!\n");
        return 1;
    }
    fprintf(stderr, "operation support: {\n");
    for (size_t i = 0; i < op_names.size(); ++i) {
        fprintf(stderr, "  %s: %s\n", op_names[i].c_str(),
                io_uring_opcode_supported(probe, i) ? "yes" : "no");
    }
    fprintf(stderr, "}\n");
    free(probe);
    return 0;
}

#else // VESPA_HAS_IO_URING

int main() {
    fprintf(stderr, "Vespa was compiled without io_uring\n");
    return 1;
}

#endif // VESPA_HAS_IO_URING
