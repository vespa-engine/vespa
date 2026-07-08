// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::queryeval {

/**
 * Flags for near and onear search operators behavior.
 */
class NearSearchFlags {
    static bool _filter_terms;

public:
    // Only used by unit tests to test behavior with and without filtering.
    class FilterTermsTweak {
        bool _old_filter_terms;

    public:
        FilterTermsTweak(bool filter_terms_in);
        ~FilterTermsTweak();
    };

    [[nodiscard]] static bool filter_terms() noexcept { return _filter_terms; }
};

} // namespace search::queryeval
