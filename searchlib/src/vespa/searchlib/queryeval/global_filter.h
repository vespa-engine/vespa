// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/searchlib/common/bitvector.h>

namespace search::queryeval {

/**
 * Hold ownership of a global filter that can be taken into account by
 * adaptive query operators. The owned 'bitvector' should be a
 * white-list (documents that may possibly become hits have their bit
 * set, documents that are certain to be filtered away should have
 * theirs cleared).
 **/
class GlobalFilter : public std::enable_shared_from_this<GlobalFilter>
{
public:
    GlobalFilter();
    GlobalFilter(const GlobalFilter &) = delete;
    GlobalFilter(GlobalFilter &&) = delete;
    virtual bool is_active() const = 0;
    virtual uint32_t size() const = 0;
    virtual uint32_t count() const = 0;
    virtual bool check(uint32_t docid) const = 0;
    virtual ~GlobalFilter();

    static std::shared_ptr<GlobalFilter> create();
    static std::shared_ptr<GlobalFilter> create(std::unique_ptr<BitVector> vector);
};

} // namespace
