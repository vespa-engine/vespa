// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_health_producer.h"
#include <vespa/defaults.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
struct DiskPing {
    std::string path;

    DiskPing()
      : path(vespa::Defaults::underVespaHome("var/run/diskping."))
    {
        int pid = getpid();
        while (pid > 0) {
            char c = '0' + (pid % 10);
            path.append(1, c);
            pid /= 10;
        }
    }
    bool failed() {
        const char *fn = path.c_str();
        ::unlink(fn);
        int fd = ::creat(fn, S_IRWXU);
        if (fd < 0) {
            return true;
        }
        int wr = ::write(fd, "foo\n", 4);
        int cr = ::close(fd);
        ::unlink(fn);
        return (wr != 4 || cr != 0);
    }
};

bool diskFailed() {
    static DiskPing disk;
    return disk.failed();
}

}

namespace vespalib {

SimpleHealthProducer::SimpleHealthProducer()
    : _lock(),
      _health(true, "")
{
    setOk();
}

SimpleHealthProducer::~SimpleHealthProducer() = default;

void
SimpleHealthProducer::setOk()
{
    std::lock_guard guard(_lock);
    _health = Health(true, "All OK");
}

void
SimpleHealthProducer::setFailed(const vespalib::string &msg)
{
    std::lock_guard guard(_lock);
    _health = Health(false, msg);
}

HealthProducer::Health
SimpleHealthProducer::getHealth() const
{
    std::lock_guard guard(_lock);
    if (_health.ok && diskFailed()) {
        return Health(false, "disk ping failed");
    }
    return _health;
}

} // namespace vespalib
