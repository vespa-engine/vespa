// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

/**
 * Hold ownership of a global filter that can be taken
 * into account by adaptive query operators.  The owned
 * bitvector should be a white-list (documents that may
 * possibly become hits have their bit set, documents
 * that are certain to be filtered away should have theirs
 * cleared).
 **/
class GlobalFilter : public std::enable_shared_from_this<GlobalFilter>
{
private:
    struct ctor_tag {};
    std::unique_ptr<search::BitVector> bit_vector;

public:
    GlobalFilter(const GlobalFilter &) = delete;
    GlobalFilter(GlobalFilter &&) = delete;

    GlobalFilter(ctor_tag, std::unique_ptr<search::BitVector> bit_vector_in) noexcept
      : bit_vector(std::move(bit_vector_in))
    {}

    GlobalFilter(ctor_tag) noexcept : bit_vector() {}

    ~GlobalFilter() {}

    template<typename ... Params>
    static std::shared_ptr<GlobalFilter> create(Params&& ... params) {
        return std::make_shared<GlobalFilter>(ctor_tag(), std::forward<Params>(params)...);
    }

    const search::BitVector *filter() const { return bit_vector.get(); }

    bool has_filter() const { return bool(bit_vector); }
};

} // namespace
