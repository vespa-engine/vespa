// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/sortresults.h>
#include <vespa/vespalib/util/dual_merge_director.h>

namespace search {
    namespace engine {
        class SearchReply;
    }
    namespace grouping {
        class GroupingContext;
        class GroupingSession;
    }
    class IDocumentMetaStore;
}

namespace proton::matching {

class SessionManager;
class PartialResult;

class ResultProcessor
{
    using GroupingContext = search::grouping::GroupingContext;
    using GroupingSession = search::grouping::GroupingSession;
    using IAttributeContext = search::attribute::IAttributeContext;
    using PartialResultUP = std::unique_ptr<PartialResult>;
public:
    /**
     * Sorter selection and owner of additional data needed for
     * multi-level sorting.
     **/
    struct Sort {
        typedef std::unique_ptr<Sort> UP;
        FastS_IResultSorter *sorter;
        std::unique_ptr<search::common::ConverterFactory> _ucaFactory;
        FastS_SortSpec       sortSpec;
        Sort(const Sort &) = delete;
        Sort & operator = (const Sort &) = delete;
        Sort(uint32_t partitionId, const vespalib::Doom & doom, IAttributeContext &ac, const vespalib::string &ss);
        bool hasSortData() const {
            return (sorter == (const FastS_IResultSorter *) &sortSpec);
        }
    };

    /**
     * Adapter to use grouping contexts as merging sources.
     **/
    struct GroupingSource : vespalib::DualMergeDirector::Source {
        GroupingContext *ctx;
        GroupingSource(GroupingContext *g) : ctx(g) {}
        void merge(Source &s) override;
    };

    /**
     * Context per thread used for result processing.
     **/
    struct Context {
        using UP = std::unique_ptr<Context>;
        using GroupingContextUP = std::unique_ptr<GroupingContext>;

        Sort::UP          sort;
        PartialResultUP   result;
        GroupingContextUP grouping;
        GroupingSource    groupingSource;

        Context(Sort::UP s, PartialResultUP r, GroupingContextUP g);
        ~Context();
    };

    struct Result {
        using UP = std::unique_ptr<Result>;
        using SearchReply = search::engine::SearchReply;
        Result(std::unique_ptr<SearchReply> reply, size_t numFs4Hits);
        ~Result();
        std::unique_ptr<SearchReply> _reply;
        size_t _numFs4Hits;
    };

private:
    IAttributeContext                     &_attrContext;
    const search::IDocumentMetaStore      &_metaStore;
    SessionManager                        &_sessionMgr;
    GroupingContext                       &_groupingContext;
    std::unique_ptr<GroupingSession>       _groupingSession;
    const vespalib::string                &_sortSpec;
    size_t                                 _offset;
    size_t                                 _hits;
    bool                                   _drop_sort_data;
    bool                                   _wasMerged;

public:
    ResultProcessor(IAttributeContext &attrContext,
                    const search::IDocumentMetaStore & metaStore,
                    SessionManager & sessionMgr,
                    GroupingContext & groupingContext,
                    const vespalib::string & sessionId,
                    const vespalib::string & sortSpec,
                    size_t offset, size_t hits,
                    bool drop_sort_data);
    ~ResultProcessor();

    size_t countFS4Hits();
    void prepareThreadContextCreation(size_t num_threads);
    Context::UP createThreadContext(const vespalib::Doom & hardDoom, size_t thread_id, uint32_t distributionKey);
    std::unique_ptr<Result> makeReply(PartialResultUP full_result);
};

}
