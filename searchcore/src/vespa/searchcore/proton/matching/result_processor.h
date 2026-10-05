// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/grouping/grouping_pass_details.h>
#include <vespa/searchlib/common/sortresults.h>
#include <vespa/vespalib/util/dual_merge_director.h>

#include <atomic>

namespace search {
namespace engine {
class SearchReply;
}
namespace grouping {
class GroupingContext;
class GroupingSession;
} // namespace grouping
struct IDocumentMetaStore;
class BitVector;
} // namespace search

namespace proton::matching {

class SessionManager;
class PartialResult;

class ResultProcessor {
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
        using UP = std::unique_ptr<Sort>;
        FastS_IResultSorter*                              sorter;
        std::unique_ptr<search::common::ConverterFactory> _ucaFactory;
        FastS_SortSpec                                    sortSpec;
        Sort(const Sort&) = delete;
        Sort& operator=(const Sort&) = delete;
        /**
         * The sort value provider is only needed when the sort spec sorts on
         * rank features. If it cannot supply all the features named by the
         * sort spec, feature_binding_failed is set; the hits cannot be ordered
         * as requested and the query must fail.
         **/
        Sort(uint32_t partitionId, const vespalib::Doom& doom, IAttributeContext& ac, const std::string& ss,
             INumericSortValueProvider* sort_value_provider);
        ~Sort();
        bool hasSortData() const noexcept { return (sorter == (const FastS_IResultSorter*)&sortSpec); }
        bool feature_binding_failed() const noexcept { return _feature_binding_failed; }

    private:
        bool _feature_binding_failed;
    };

    /**
     * Adapter to use grouping contexts as merging sources.
     **/
    struct GroupingSource : vespalib::DualMergeDirector::Source {
        GroupingContext* ctx;
        // Only when set are merges timed and counted (used for query tracing).
        bool timed;
        // Time spent merging grouping results into this source and the number of merges, both
        // including what was already accumulated by the sources merged into it. After all
        // threads are merged, the source of thread 0 holds the totals for the query. Note that
        // the time is a sum over merges that may have run in parallel, not wall clock time.
        double   merge_time_s;
        uint32_t merge_count;
        explicit GroupingSource(GroupingContext* g) noexcept
            : ctx(g), timed(false), merge_time_s(0.0), merge_count(0) {}
        void merge(Source& s) override;
    };

    /**
     * Context per thread used for result processing.
     **/
    struct Context {
        using GroupingContextUP = std::unique_ptr<GroupingContext>;

        const search::BitVector& _validLids;
        Sort::UP                 sort;
        PartialResultUP          result;
        GroupingContextUP        grouping;
        GroupingSource           groupingSource;

        Context(const search::BitVector& validLids, Sort::UP s, PartialResultUP r, GroupingContextUP g);
        ~Context();
    };

    struct Result {
        using UP = std::unique_ptr<Result>;
        using SearchReply = search::engine::SearchReply;
        Result(std::unique_ptr<SearchReply> reply, size_t numFs4Hits);
        ~Result();
        std::unique_ptr<SearchReply> _reply;
        size_t                       _numFs4Hits;
    };

private:
    IAttributeContext&                  _attrContext;
    const search::IDocumentMetaStore&   _metaStore;
    SessionManager&                     _sessionMgr;
    GroupingContext&                    _groupingContext;
    std::unique_ptr<GroupingSession>    _groupingSession;
    const std::string&                  _sortSpec;
    size_t                              _offset;
    size_t                              _hits;
    bool                                _wasMerged;
    std::atomic<bool>                   _sort_feature_failed;
    bool                                _collect_grouping_details;
    bool                                _grouping_session_cached;
    search::grouping::GroupingPassTrace _grouping_trace;
    const GroupingSource*               _first_grouping_source;
    double                              _grouping_merge_ms;
    uint32_t                            _grouping_merge_count;
    double                              _grouping_prune_ms;
    double                              _grouping_continue_ms;

public:
    ResultProcessor(IAttributeContext& attrContext, const search::IDocumentMetaStore& metaStore,
                    SessionManager& sessionMgr, GroupingContext& groupingContext, const std::string& sessionId,
                    const std::string& sortSpec, size_t offset, size_t hits);
    ~ResultProcessor();

    void prepareThreadContextCreation(size_t num_threads);
    std::unique_ptr<Context> createThreadContext(const vespalib::Doom& hardDoom, size_t thread_id,
                                                 uint32_t                   distributionKey,
                                                 INumericSortValueProvider* sort_value_provider);
    std::vector<std::pair<uint32_t, uint32_t>> extract_docid_ordering(const PartialResult& result) const;
    std::unique_ptr<Result> makeReply(PartialResultUP full_result);

    /**
     * Records that a thread could not order its hits by the requested rank
     * features, either because binding failed or because the sort values of
     * some hit were missing while the sort blobs were encoded.
     **/
    void note_sort_feature_failure() noexcept { _sort_feature_failed.store(true, std::memory_order_relaxed); }

    /**
     * True if any thread's sort spec named a rank feature whose sort values
     * were not available. Only meaningful once all match threads are done.
     **/
    bool sort_feature_failed() const noexcept { return _sort_feature_failed.load(std::memory_order_relaxed); }

    /**
     * Collect details about each grouping when makeReply() produces the grouping result,
     * to be traced by the caller.
     **/
    void collect_grouping_details() noexcept { _collect_grouping_details = true; }
    const search::grouping::GroupingPassTrace& grouping_trace() const noexcept { return _grouping_trace; }

    /**
     * True if makeReply() stored the grouping session for later passes.
     **/
    bool grouping_session_cached() const noexcept { return _grouping_session_cached; }

    /**
     * Timing of the grouping work done when results from the match threads are combined, only
     * measured together with grouping details: total time spent in (and number of) merges of
     * grouping results across threads, time spent pruning the merged result (both only done when
     * more than one thread was used) and time spent producing the result for this pass in
     * makeReply(), including serialization.
     **/
    double grouping_merge_ms() const noexcept { return _grouping_merge_ms; }
    uint32_t grouping_merge_count() const noexcept { return _grouping_merge_count; }
    double grouping_prune_ms() const noexcept { return _grouping_prune_ms; }
    double grouping_continue_ms() const noexcept { return _grouping_continue_ms; }
    bool grouping_was_merged() const noexcept { return _wasMerged; }
};

} // namespace proton::matching
