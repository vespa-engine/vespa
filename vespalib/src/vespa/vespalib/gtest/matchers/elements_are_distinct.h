// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include <ranges>

/**
 * Checks that all elements of a forward iterable range are distinct, i.e. the following must hold:
 *   - for any single element `foo`, foo == foo is true
 *   - for any two separate elements `foo` and `bar`, foo == bar is false
 */
MATCHER(ElementsAreDistinct, "") {
    const auto& range = arg;
    static_assert(std::ranges::forward_range<decltype(range)>);
    const auto end = std::ranges::cend(range);
    // Explicitly count element positions instead of comparing iterators to avoid depending
    // on iterators being comparable with each other.
    size_t i = 0;
    for (auto lhs = std::ranges::cbegin(range); lhs != end; ++lhs, ++i) {
        size_t j = 0;
        for (auto rhs = std::ranges::cbegin(range); rhs != end; ++rhs, ++j) {
            if (i != j) {
                if (*lhs == *rhs) {
                    *result_listener << "Expected elements to be distinct, but element at position "
                                     << i << " (" << *lhs << ") is equal to element at position "
                                     << j << " (" << *rhs << ")";
                    return false;
                }
            } else if (!(*lhs == *rhs)) {
                *result_listener << "Element at position " << i << " (" << *lhs << ") does not equal itself";
                return false;
            }
        }
    }
    return true;
}
