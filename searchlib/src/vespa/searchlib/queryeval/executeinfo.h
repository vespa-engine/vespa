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
    ExecuteInfo() noexcept : ExecuteInfo(false, 1.0F, nullptr, true, true) { }
    bool isStrict() const noexcept { return _strict; }
    bool create_postinglist_when_non_strict() const noexcept { return _create_postinglist_when_non_strict; }
    bool use_estimate_for_fetch_postings() const noexcept { return _use_estimate_for_fetch_postings; }
    float hitRate() const noexcept { return _hitRate; }
    bool soft_doom() const noexcept { return _doom && _doom->soft_doom(); }
    const vespalib::Doom * getDoom() const { return _doom; }
    static const ExecuteInfo TRUE;
    static const ExecuteInfo FALSE;
    static ExecuteInfo create(bool strict, const ExecuteInfo & org) noexcept {
        return create(strict, org._hitRate, org);
    }
    static ExecuteInfo create(bool strict, float hitRate, const ExecuteInfo & org) noexcept {
        return {strict, hitRate, org.getDoom(), org.create_postinglist_when_non_strict(), org.use_estimate_for_fetch_postings()};
    }

    static ExecuteInfo create(bool strict, float hitRate, const vespalib::Doom * doom, bool postinglist_when_non_strict,
                              bool use_estimate_for_fetch_postings) noexcept
    {
         return {strict, hitRate, doom, postinglist_when_non_strict, use_estimate_for_fetch_postings};
    }
    static ExecuteInfo createForTest(bool strict) noexcept {
        return createForTest(strict, 1.0F);
    }
    static ExecuteInfo createForTest(bool strict, float hitRate) noexcept {
        return createForTest(strict, hitRate, nullptr);
    }
    static ExecuteInfo createForTest(bool strict, float hitRate, const vespalib::Doom * doom) noexcept {
        return create(strict, hitRate, doom, true, true);
    }
private:
    ExecuteInfo(bool strict, float hitRate_in, const vespalib::Doom * doom, bool postinglist_when_non_strict,
                bool use_estimate_for_fetch_postings) noexcept
        : _doom(doom),
          _hitRate(hitRate_in),
          _strict(strict),
          _create_postinglist_when_non_strict(postinglist_when_non_strict),
          _use_estimate_for_fetch_postings(use_estimate_for_fetch_postings)
    { }
    const vespalib::Doom * _doom;
    float                  _hitRate;
    bool                   _strict;
    bool                   _create_postinglist_when_non_strict;
    bool                   _use_estimate_for_fetch_postings;
};

}
