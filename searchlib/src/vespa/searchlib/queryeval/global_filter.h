// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>

namespace vespalib { struct ThreadBundle; }
namespace search { class BitVector; }

namespace search::engine { class Trace; }

namespace search::queryeval {

class Blueprint;

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
    using Trace = search::engine::Trace;
    GlobalFilter() noexcept;
    GlobalFilter(const GlobalFilter &) = delete;
    GlobalFilter(GlobalFilter &&) = delete;
    virtual bool is_active() const = 0;
    virtual uint32_t size() const = 0;
    virtual uint32_t count() const = 0;
    virtual bool check(uint32_t docid) const = 0;
    virtual ~GlobalFilter();

    const GlobalFilter *ptr_if_active() const {
        return is_active() ? this : nullptr;
    }

    static std::shared_ptr<GlobalFilter> create();
    static std::shared_ptr<GlobalFilter> create(std::vector<uint32_t> docids, uint32_t size);
    static std::shared_ptr<GlobalFilter> create(std::unique_ptr<BitVector> vector);
    static std::shared_ptr<GlobalFilter> create(std::vector<std::unique_ptr<BitVector>> vectors);
    static std::shared_ptr<GlobalFilter> create(Blueprint &blueprint, uint32_t docid_limit, vespalib::ThreadBundle &thread_bundle, Trace *trace);
    static std::shared_ptr<GlobalFilter> create(Blueprint &blueprint, uint32_t docid_limit, vespalib::ThreadBundle &thread_bundle) {
        return create(blueprint, docid_limit, thread_bundle, nullptr);
    }
};

} // namespace
