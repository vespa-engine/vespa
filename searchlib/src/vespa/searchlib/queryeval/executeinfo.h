// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/doom.h>

namespace search::queryeval {

/**
 * Holds information about how query will be executed and how large part of corpus will pass through.
 * @author baldersheim
 */
class ExecuteInfo {
public:
    ExecuteInfo() noexcept : ExecuteInfo(false, 1.0F, nullptr) { }
    bool isStrict() const noexcept { return _strict; }
    float hitRate() const noexcept { return _hitRate; }
    bool soft_doom() const noexcept { return _doom && _doom->soft_doom(); }
    const vespalib::Doom * getDoom() const { return _doom; }
    static const ExecuteInfo TRUE;
    static const ExecuteInfo FALSE;
    static ExecuteInfo create(bool strict, const ExecuteInfo & org) noexcept {
        return {strict, org._hitRate, org.getDoom()};
    }
    static ExecuteInfo create(bool strict, float hitRate, const vespalib::Doom * doom) noexcept {
        return {strict, hitRate, doom};
    }
    static ExecuteInfo createForTest(bool strict) noexcept {
        return create(strict, 1.0F);
    }
    static ExecuteInfo create(bool strict, float hitRate) noexcept {
        return create(strict, hitRate, nullptr);
    }
private:
    ExecuteInfo(bool strict, float hitRate_in, const vespalib::Doom * doom) noexcept
        : _doom(doom),
          _hitRate(hitRate_in),
          _strict(strict)
    { }
    const vespalib::Doom * _doom;
    float                  _hitRate;
    bool                   _strict;
};

}
