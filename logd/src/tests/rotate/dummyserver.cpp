// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <time.h>
#include <fcntl.h>
#include <vespa/vespalib/net/socket_address.h>
#include <cstdlib>

void error(const char *msg)
{
    perror(msg);
    std::_Exit(1);
}

int main(int /*argc*/, char ** /*argv*/)
{
    auto handle = vespalib::SocketAddress::select_local(0).listen();
    if (!handle) {
        error("ERROR: could not listen to server port");
    }
    int portno = vespalib::SocketAddress::address_of(handle.get()).port();
    printf("Got port %d", portno);
    int fd = open("logserver.port", O_CREAT | O_WRONLY,
                  S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
    char out[6];
    snprintf(out, sizeof(out), "%d\n", portno);
    ssize_t writeRes = write(fd, out, sizeof(out));
    close(fd);
    if (writeRes != sizeof(out)) {
        error("ERROR: could not write port number");
    }
    sockaddr_storage cli_addr;
    socklen_t clilen = sizeof(cli_addr);
    int newsockfd = accept(handle.get(),
                           (struct sockaddr *) &cli_addr,
                           &clilen);
    if (newsockfd < 0)
        error("ERROR on accept");
    char buffer[1024];
    while (true) {
        ssize_t n = read(newsockfd, buffer, sizeof(buffer));
        if (n < 0) error("ERROR reading from socket");
        struct timespec t;
        t.tv_sec  = 0;
        t.tv_nsec = 200000000;
        nanosleep(&t, 0);
    }
    return 0;
}
