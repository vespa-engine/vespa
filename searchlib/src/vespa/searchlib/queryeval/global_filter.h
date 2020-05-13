// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

class GlobalFilter : public std::enable_shared_from_this<GlobalFilter>
{
private:
    struct ctor_tag {};
    std::unique_ptr<search::BitVector> bit_vector;

    GlobalFilter(const GlobalFilter &) = delete;
    GlobalFilter(GlobalFilter &&) = delete;
public:

    GlobalFilter(ctor_tag, std::unique_ptr<search::BitVector> bit_vector_in)
      : bit_vector(std::move(bit_vector_in))
    {}

    GlobalFilter(ctor_tag) : bit_vector() {}

    ~GlobalFilter() {}

    template<typename ... Params>
    static std::shared_ptr<GlobalFilter> create(Params&& ... params) {
        ctor_tag x;
        return std::make_shared<GlobalFilter>(x, std::forward<Params>(params)...);
    }

    const search::BitVector *filter() const { return bit_vector.get(); }

    bool has_filter() const { return (bool)bit_vector; }
};

} // namespace
