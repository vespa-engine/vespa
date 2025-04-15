// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "profiled_iterator.h"
#include "sourceblendersearch.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "wand/weak_and_search.h"

#include <typeindex>

using vespalib::make_string_short::fmt;

namespace search::queryeval {

namespace {

using Profiler = vespalib::ExecutionProfiler;

struct TaskGuard {
    Profiler &profiler;
    TaskGuard(Profiler &profiler_in, Profiler::TaskId task) noexcept
      : profiler(profiler_in) { profiler.start(task); }
    ~TaskGuard() { profiler.complete(); }
};

std::string name_of(const SearchIterator &search) {
    return search.getClassName();
}

std::unique_ptr<SearchIterator> create(Profiler &profiler,
                                       std::unique_ptr<SearchIterator> search,
                                       auto ctor_token)
{
    std::string prefix = fmt("%s%s", search->make_id_ref_str().c_str(), name_of(*search).c_str());
    return std::make_unique<ProfiledIterator>(profiler, std::move(search),
                                              profiler.resolve(prefix + "::initRange"),
                                              profiler.resolve(prefix + "::doSeek"),
                                              profiler.resolve(prefix + "::doUnpack"),
                                              profiler.resolve(prefix + "::get_hits"),
                                              profiler.resolve(prefix + "::or_hits_into"),
                                              profiler.resolve(prefix + "::and_hits_into"),
                                              ctor_token);
}

}

void
ProfiledIterator::initRange(uint32_t begin_id, uint32_t end_id)
{
    TaskGuard guard(_profiler, _initRange_tag);
    SearchIterator::initRange(begin_id, end_id);
    _search->initRange(begin_id, end_id);
    setDocId(_search->getDocId());
}

void
ProfiledIterator::doSeek(uint32_t docid)
{
    TaskGuard guard(_profiler, _doSeek_tag);
    _search->doSeek(docid);
    setDocId(_search->getDocId());
}

void
ProfiledIterator::doUnpack(uint32_t docid)
{
    TaskGuard guard(_profiler, _doUnpack_tag);
    _search->doUnpack(docid);
}

std::unique_ptr<BitVector>
ProfiledIterator::get_hits(uint32_t begin_id)
{
    TaskGuard guard(_profiler, _get_hits_tag);
    return _search->get_hits(begin_id);
}

void
ProfiledIterator::or_hits_into(BitVector &result, uint32_t begin_id)
{
    TaskGuard guard(_profiler, _or_hits_into_tag);
    _search->or_hits_into(result, begin_id);
}

void
ProfiledIterator::and_hits_into(BitVector &result, uint32_t begin_id)
{
    TaskGuard guard(_profiler, _and_hits_into_tag);
    _search->and_hits_into(result, begin_id);
}

void
ProfiledIterator::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "search", _search);
}

std::unique_ptr<SearchIterator>
ProfiledIterator::profile(Profiler &profiler, std::unique_ptr<SearchIterator> node)
{
    node->transform_children([&](auto child){
                                 return profile(profiler, std::move(child));
                             });
    return create(profiler, std::move(node), ctor_tag{});
}

}
