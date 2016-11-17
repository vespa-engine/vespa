// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "partial_result.h"
#include "result_processor.h"
#include "sessionmanager.h"
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchcore/grouping/groupingmanager.h>
#include <vespa/searchcore/grouping/groupingsession.h>
#include <vespa/searchlib/attribute/attributecontext.h>
#include <vespa/searchlib/common/sortresults.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/vespalib/util/dual_merge_director.h>
#include <vespa/vespalib/util/noncopyable.hpp>

namespace search {
namespace engine {
    class SearchReply;
}
}

namespace proton {
namespace matching {

class ResultProcessor
{
public:
    /**
     * Sorter selection and owner of additional data needed for
     * multi-level sorting.
     **/
    struct Sort : vespalib::noncopyable {
        typedef std::unique_ptr<Sort> UP;
        FastS_IResultSorter *sorter;
        std::unique_ptr<search::common::ConverterFactory> _ucaFactory;
        FastS_SortSpec       sortSpec;
        Sort(const vespalib::Doom & doom, search::attribute::IAttributeContext &ac, const vespalib::string &ss);
        bool hasSortData() const {
            return (sorter == (const FastS_IResultSorter *) &sortSpec);
        }
    };

    /**
     * Adapter to use grouping contexts as merging sources.
     **/
    struct GroupingSource : vespalib::DualMergeDirector::Source {
        search::grouping::GroupingContext *ctx;
        GroupingSource(search::grouping::GroupingContext *g) : ctx(g) {}
        virtual void merge(Source &s) {
            GroupingSource &rhs = static_cast<GroupingSource&>(s);
            assert((ctx == 0) == (rhs.ctx == 0));
            if (ctx != 0) {
                search::grouping::GroupingManager man(*ctx);
                man.merge(*rhs.ctx);
            }
        }
    };

    /**
     * Context per thread used for result processing.
     **/
    struct Context {
        typedef std::unique_ptr<Context> UP;

        Sort::UP                              sort;
        PartialResult::LP                     result;
        search::grouping::GroupingContext::UP grouping;
        GroupingSource                        groupingSource;

        Context(Sort::UP s, PartialResult::LP r,
                search::grouping::GroupingContext::UP g)
            : sort(std::move(s)), result(r), grouping(std::move(g)),
              groupingSource(grouping.get()) {}
    };

    struct Result {
        typedef std::unique_ptr<Result> UP;
        Result(std::unique_ptr<search::engine::SearchReply> reply, size_t numFs4Hits);
        ~Result();
        std::unique_ptr<search::engine::SearchReply> _reply;
        size_t _numFs4Hits;
    };

private:
    search::attribute::IAttributeContext  &_attrContext;
    const search::IDocumentMetaStore      &_metaStore;
    SessionManager                        &_sessionMgr;
    search::grouping::GroupingContext     &_groupingContext;
    search::grouping::GroupingSession::UP  _groupingSession;
    const vespalib::string                &_sortSpec;
    size_t                                 _offset;
    size_t                                 _hits;
    PartialResult::LP                      _result;
    bool                                   _wasMerged;

public:
    ResultProcessor(search::attribute::IAttributeContext &attrContext,
                    const search::IDocumentMetaStore &metaStore,
                    SessionManager &sessionMgr,
                    search::grouping::GroupingContext &groupingContext,
                    const search::grouping::SessionId &sessionId,
                    const vespalib::string &sortSpec,
                    size_t offset, size_t hits);

    size_t countFS4Hits();
    void prepareThreadContextCreation(size_t num_threads);
    Context::UP createThreadContext(const vespalib::Doom & hardDoom, size_t thread_id);
    Result::UP makeReply();
};

} // namespace proton::matching
} // namespace proton

