// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::queryeval {

/**
 * Flags for sameElement search operator behavior.
 */
class SameElementFlags
{
    static bool _expose_descendants;
public:
    class ExposeDescendantsTweak {
        bool _old_expose_descendants;
    public:
        ExposeDescendantsTweak(bool expose_descendants_in);
        ~ExposeDescendantsTweak();
    };
    static bool expose_descendants() noexcept { return _expose_descendants; }
};

}
