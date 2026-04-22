// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ann_doom.h"
#include "fake_doom.h"

namespace vespalib {

AnnDoom::AnnDoom(const std::atomic<steady_time> & now, steady_time doom, bool is_timeout) noexcept
    : _now(now), _doom(doom), _is_ann_timeout(is_timeout) {
}

const AnnDoom& AnnDoom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_ann_doom();
}

}
