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
    ExecuteInfo() noexcept : ExecuteInfo(false, 1.0, nullptr, vespalib::ThreadBundle::trivial(), true, true) { }
    bool is_strict() const noexcept { return _strict; }
    bool create_postinglist_when_non_strict() const noexcept { return _create_postinglist_when_non_strict; }
    bool use_estimate_for_fetch_postings() const noexcept { return _use_estimate_for_fetch_postings; }
    double hit_rate() const noexcept { return _hitRate; }
    bool soft_doom() const noexcept { return _doom && _doom->soft_doom(); }
    vespalib::ThreadBundle & thread_bundle() const noexcept { return _thread_bundle; }

    static const ExecuteInfo TRUE;
    static const ExecuteInfo FALSE;
    static ExecuteInfo create(bool strict, const ExecuteInfo & org) noexcept {
        return create(strict, org._hitRate, org);
    }
    static ExecuteInfo create(bool strict, double hitRate, const ExecuteInfo & org) noexcept {
        return {strict, hitRate, org._doom, org.thread_bundle(), org.create_postinglist_when_non_strict(), org.use_estimate_for_fetch_postings()};
    }

    static ExecuteInfo create(bool strict, double hitRate, const vespalib::Doom * doom, vespalib::ThreadBundle & thread_bundle_in,
                              bool postinglist_when_non_strict, bool use_estimate_for_fetch_postings) noexcept
    {
         return {strict, hitRate, doom, thread_bundle_in, postinglist_when_non_strict, use_estimate_for_fetch_postings};
    }
    static ExecuteInfo createForTest(bool strict) noexcept {
        return createForTest(strict, 1.0);
    }
    static ExecuteInfo createForTest(bool strict, double hitRate) noexcept {
        return createForTest(strict, hitRate, nullptr);
    }
    static ExecuteInfo createForTest(bool strict, double hitRate, const vespalib::Doom * doom) noexcept {
        return create(strict, hitRate, doom, vespalib::ThreadBundle::trivial(), true, true);
    }
private:
    ExecuteInfo(bool strict, double hitRate_in, const vespalib::Doom * doom, vespalib::ThreadBundle & thread_bundle_in,
                bool postinglist_when_non_strict, bool use_estimate_for_fetch_postings) noexcept
        : _doom(doom),
          _thread_bundle(thread_bundle_in),
          _hitRate(hitRate_in),
          _strict(strict),
          _create_postinglist_when_non_strict(postinglist_when_non_strict),
          _use_estimate_for_fetch_postings(use_estimate_for_fetch_postings)
    { }
    const vespalib::Doom   * _doom;
    vespalib::ThreadBundle & _thread_bundle;
    double                   _hitRate;
    bool                     _strict;
    bool                     _create_postinglist_when_non_strict;
    bool                     _use_estimate_for_fetch_postings;
};

}
