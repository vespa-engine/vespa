// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "selector.h"

namespace vespalib {

namespace {

//-----------------------------------------------------------------------------

struct SingleFdHandler {
    int my_fd;
    bool got_wakeup;
    bool got_read;
    bool got_write;
    SingleFdHandler(int my_fd_in)
        : my_fd(my_fd_in), got_wakeup(false), got_read(false), got_write(false) {}
    void handle_wakeup() {
        got_wakeup = true;
    }
    void handle_event(int &ctx, bool read, bool write) {
        if ((ctx == my_fd) && read) {
            got_read = true;
        }
        if ((ctx == my_fd) && write) {
            got_write = true;
        }
    }
};

} // namespace vespalib::<unnamed>

//-----------------------------------------------------------------------------

SingleFdSelector::SingleFdSelector(int fd)
    : _fd(fd),
      _selector()
{
    _selector.add(_fd, _fd, false, false);
}

SingleFdSelector::~SingleFdSelector()
{
    _selector.remove(_fd);
}

bool
SingleFdSelector::wait_readable()
{
    _selector.update(_fd, _fd, true, false);
    for (;;) {
        _selector.poll(-1);
        SingleFdHandler handler(_fd);
        _selector.dispatch(handler);
        if (handler.got_read) {
            return true;
        }
        if (handler.got_wakeup) {
            return false;
        }
    }
}

bool
SingleFdSelector::wait_writable()
{
    _selector.update(_fd, _fd, false, true);
    for (;;) {
        _selector.poll(-1);
        SingleFdHandler handler(_fd);
        _selector.dispatch(handler);
        if (handler.got_write) {
            return true;
        }
        if (handler.got_wakeup) {
            return false;
        }
    }
}

void
SingleFdSelector::wakeup()
{
    _selector.wakeup();
}

//-----------------------------------------------------------------------------

} // namespace vespalib
