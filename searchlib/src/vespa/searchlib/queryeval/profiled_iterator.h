// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/vespalib/util/execution_profiler.h>

namespace search::queryeval {

/**
 * Wraps a search iterator to profile its operations. Each iterator
 * has 6 distinct operations that will be profiled separately.
 *
 * The full name of each profiled task will be the id of the iterator (enumeration from blueprint tree)
 * followed by the type of the iterator.
 * The suffix will be the actual function name associated with the operation.
 *
 * [id]type::initRange
 * [id]type::doSeek
 * [id]type::doUnpack
 * [id]type::get_hits
 * [id]type::or_hits_into
 * [id]type::and_hits_into
 **/
class ProfiledIterator : public SearchIterator
{
private:
    using Profiler = vespalib::ExecutionProfiler;
    Profiler &_profiler;
    std::unique_ptr<SearchIterator> _search;
    Profiler::TaskId _initRange_tag;
    Profiler::TaskId _doSeek_tag;
    Profiler::TaskId _doUnpack_tag;
    Profiler::TaskId _get_hits_tag;
    Profiler::TaskId _or_hits_into_tag;
    Profiler::TaskId _and_hits_into_tag;
    struct ctor_tag{};
public:
    ProfiledIterator(Profiler &profiler,
                     std::unique_ptr<SearchIterator> search,
                     Profiler::TaskId initRange_tag,
                     Profiler::TaskId doSeek_tag,
                     Profiler::TaskId doUnpack_tag,
                     Profiler::TaskId get_hits_tag,
                     Profiler::TaskId or_hits_into_tag,
                     Profiler::TaskId and_hits_into_tag,
                     ctor_tag) noexcept
      : _profiler(profiler), _search(std::move(search)),
        _initRange_tag(initRange_tag), _doSeek_tag(doSeek_tag),
        _doUnpack_tag(doUnpack_tag), _get_hits_tag(get_hits_tag),
        _or_hits_into_tag(or_hits_into_tag), _and_hits_into_tag(and_hits_into_tag) {}
    ~ProfiledIterator() override;
    void initRange(uint32_t begin_id, uint32_t end_id) override;
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    void get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    UP andWith(UP filter, uint32_t estimate) override { return _search->andWith(std::move(filter), estimate); }
    Trinary is_strict() const override { return _search->is_strict(); }
    Trinary matches_any() const override { return _search->matches_any(); }
    const PostingInfo *getPostingInfo() const override { return _search->getPostingInfo(); }
    static std::unique_ptr<SearchIterator> profile(Profiler &profiler, std::unique_ptr<SearchIterator> node);
};

} // namespace
