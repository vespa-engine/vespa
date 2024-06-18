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

vespalib::string name_of(const auto &obj) {
    auto name = vespalib::getClassName(obj);
    auto end = name.find("<");
    auto ns = name.rfind("::", end);
    size_t begin = (ns > name.size()) ? 0 : ns + 2;
    return name.substr(begin, end - begin);
}

std::unique_ptr<SearchIterator> create(Profiler &profiler,
                                       const vespalib::string &path,
                                       std::unique_ptr<SearchIterator> search,
                                       auto ctor_token)
{
    vespalib::string prefix = fmt("%s%s/", path.c_str(), name_of(*search).c_str());
    return std::make_unique<ProfiledIterator>(profiler, std::move(search),
                                              profiler.resolve(prefix + "init"),
                                              profiler.resolve(prefix + "seek"),
                                              profiler.resolve(prefix + "unpack"),
                                              profiler.resolve(prefix + "termwise"),
                                              ctor_token);    
}

}

void
ProfiledIterator::initRange(uint32_t begin_id, uint32_t end_id)
{
    TaskGuard guard(_profiler, _init_tag);
    SearchIterator::initRange(begin_id, end_id);
    _search->initRange(begin_id, end_id);
    setDocId(_search->getDocId());
}

void
ProfiledIterator::doSeek(uint32_t docid)
{
    TaskGuard guard(_profiler, _seek_tag);
    _search->doSeek(docid);
    setDocId(_search->getDocId());
}

void
ProfiledIterator::doUnpack(uint32_t docid)
{
    TaskGuard guard(_profiler, _unpack_tag);
    _search->doUnpack(docid);
}

std::unique_ptr<BitVector>
ProfiledIterator::get_hits(uint32_t begin_id)
{
    TaskGuard guard(_profiler, _termwise_tag);
    return _search->get_hits(begin_id);
}

void
ProfiledIterator::or_hits_into(BitVector &result, uint32_t begin_id)
{
    TaskGuard guard(_profiler, _termwise_tag);
    _search->or_hits_into(result, begin_id);
}

void
ProfiledIterator::and_hits_into(BitVector &result, uint32_t begin_id)
{
    TaskGuard guard(_profiler, _termwise_tag);
    _search->and_hits_into(result, begin_id);
}

void
ProfiledIterator::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "search", _search);
}

std::unique_ptr<SearchIterator>
ProfiledIterator::profile(Profiler &profiler, std::unique_ptr<SearchIterator> node, const vespalib::string &path)
{
    node->transform_children([&](auto child, size_t i){
                                 return profile(profiler, std::move(child), fmt("%s%zu/", path.c_str(), i));
                             });
    return create(profiler, path, std::move(node), ctor_tag{});
}

}
