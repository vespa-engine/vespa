// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/thread_bundle.h>

namespace search::queryeval {

/**
 * Holds information about how query will be executed and how large part of corpus will pass through.
 * @author baldersheim
 */
class ExecuteInfo {
public:
    ExecuteInfo() noexcept;
    double hit_rate() const noexcept { return _hitRate; }
    const vespalib::Doom & doom() const noexcept { return _doom; }
    vespalib::ThreadBundle & thread_bundle() const noexcept { return _thread_bundle; }

    static const ExecuteInfo FULL;
    static ExecuteInfo create(const ExecuteInfo & org) noexcept {
        return create(org._hitRate, org);
    }
    static ExecuteInfo create(double hitRate, const ExecuteInfo & org) noexcept {
        return {hitRate, org._doom, org.thread_bundle()};
    }

    static ExecuteInfo create(double hitRate, const vespalib::Doom & doom,
                              vespalib::ThreadBundle & thread_bundle_in) noexcept
    {
         return {hitRate, doom, thread_bundle_in};
    }
    static ExecuteInfo createForTest() noexcept {
        return createForTest(1.0);
    }
    static ExecuteInfo createForTest(double hitRate) noexcept;
    static ExecuteInfo createForTest(double hitRate, const vespalib::Doom & doom) noexcept {
        return create(hitRate, doom, vespalib::ThreadBundle::trivial());
    }
private:
    ExecuteInfo(double hitRate_in, const vespalib::Doom & doom,
                vespalib::ThreadBundle & thread_bundle_in) noexcept
        : _doom(doom),
          _thread_bundle(thread_bundle_in),
          _hitRate(hitRate_in)
    { }
    const vespalib::Doom     _doom;
    vespalib::ThreadBundle & _thread_bundle;
    double                   _hitRate;
};

}
