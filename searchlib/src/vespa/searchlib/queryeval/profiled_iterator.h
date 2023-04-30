// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include <vespa/vespalib/util/execution_profiler.h>

namespace search::queryeval {

/**
 * Wraps a search iterator to profile its operations. Each iterator
 * has 4 distinct operations that will be profiled separately:
 *
 * 'init' -> initRange
 * 'seek' -> doSeek
 * 'unpack' -> doUnpack
 * 'termwise' -> get_hits, or_hits_into, and_hits_into
 *
 * The full name of each profiled task will be the path down the
 * iterator tree combined with the class name and the operation name.
 **/
class ProfiledIterator : public SearchIterator
{
private:
    using Profiler = vespalib::ExecutionProfiler;
    Profiler &_profiler;
    std::unique_ptr<SearchIterator> _search;
    Profiler::TaskId _init_tag;
    Profiler::TaskId _seek_tag;
    Profiler::TaskId _unpack_tag;
    Profiler::TaskId _termwise_tag;
    struct ctor_tag{};
public:
    ProfiledIterator(Profiler &profiler,
                     std::unique_ptr<SearchIterator> search,
                     Profiler::TaskId init_tag,
                     Profiler::TaskId seek_tag,
                     Profiler::TaskId unpack_tag,
                     Profiler::TaskId termwise_tag,
                     ctor_tag) noexcept
      : _profiler(profiler), _search(std::move(search)),
        _init_tag(init_tag), _seek_tag(seek_tag),
        _unpack_tag(unpack_tag), _termwise_tag(termwise_tag) {}
    void initRange(uint32_t begin_id, uint32_t end_id) override;
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    UP andWith(UP filter, uint32_t estimate) override { return _search->andWith(std::move(filter), estimate); }
    Trinary is_strict() const override { return _search->is_strict(); }
    Trinary matches_any() const override { return _search->matches_any(); }
    const PostingInfo *getPostingInfo() const override { return _search->getPostingInfo(); }
    static std::unique_ptr<SearchIterator> profile(Profiler &profiler,
                                                   std::unique_ptr<SearchIterator> root,
                                                   const vespalib::string &root_path = "/");
};

} // namespace
