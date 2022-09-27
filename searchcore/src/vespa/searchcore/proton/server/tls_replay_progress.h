// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>
#include <atomic>
#include <memory>

namespace proton {

class TlsReplayProgress
{
private:
    const vespalib::string  _domainName;
    const search::SerialNum _first;
    const search::SerialNum _last;
    std::atomic<search::SerialNum> _current;

public:
    typedef std::unique_ptr<TlsReplayProgress> UP;

    TlsReplayProgress(const vespalib::string &domainName,
                      search::SerialNum first,
                      search::SerialNum last)
        : _domainName(domainName),
          _first(first),
          _last(last),
          _current(first)
    {
    }
    const vespalib::string &getDomainName() const noexcept { return _domainName; }
    search::SerialNum getFirst() const noexcept { return _first; }
    search::SerialNum getLast() const noexcept { return _last; }
    search::SerialNum getCurrent() const noexcept { return _current.load(std::memory_order_relaxed); }
    float getProgress() const noexcept {
        if (_first == _last) {
            return 1.0;
        } else {
            return ((float)(getCurrent() - _first)/float(_last - _first));
        }
    }
    void updateCurrent(search::SerialNum current) noexcept { _current.store(current, std::memory_order_relaxed); }
};

} // namespace proton

