// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vespa/vespalib/net/socket.h>
#include "buffer.h"
#include "frame.h"

namespace vespalib {
namespace ws {

class Connection
{
private:
    Socket::UP _socket;
    Buffer     _input;
    Buffer     _output;

    bool fill_input(size_t min_bytes);

public:
    typedef std::unique_ptr<Connection> UP;
    explicit Connection(Socket::UP socket);

    int read_byte() {
        if (!_input.has_next()) {
            if (!fill_input(1)) {
                return -1;
            }
        }
        return (_input.next() & 0xff);
    }

    bool read_line(vespalib::string &dst);

    bool read_frame(Frame &frame);

    void write_frame(const Frame &frame);

    void printf(const char *fmt, ...)
#ifdef __GNUC__
        // Add printf format checks with gcc
        __attribute__ ((format (printf,2,3)))
#endif
        ;

    void write(const char *data, size_t len);

    bool flush();
};

} // namespace vespalib::ws
} // namespace vespalib
