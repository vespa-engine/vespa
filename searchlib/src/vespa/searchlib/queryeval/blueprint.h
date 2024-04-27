// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "flow.h"
#include "field_spec.h"
#include "unpackinfo.h"
#include "executeinfo.h"
#include "global_filter.h"
#include "multisearch.h"
#include <vespa/searchlib/common/bitvector.h>

namespace vespalib { class ObjectVisitor; }
namespace vespalib::slime {
    struct Cursor;
    struct Inserter;
}
namespace search { class MatchingElementsFields; }
namespace search::attribute { class ISearchContext; }
namespace search::fef {
    class TermFieldMatchDataArray;
    class MatchData;
}

namespace search::queryeval {

class SearchIterator;
class ExecuteInfo;
class MatchingElementsSearch;
class LeafBlueprint;
class IntermediateBlueprint;
class SourceBlenderBlueprint;
class WeakAndBlueprint;
class AndBlueprint;
class AndNotBlueprint;
class OrBlueprint;
class EmptyBlueprint;

/**
 * A Blueprint is an intermediate representation of a search. More
 * concretely, it is a tree of search iterator factories annotated
 * with meta-data about the fields to be searched, how match
 * information is to be exposed to the ranking framework and estimates
 * for the number of results that will be produced. Intermediate
 * operations are implemented by extending the blueprint::Intermediate
 * template class. Leaf operations are implemented by extending the
 * blueprint::Leaf template class.
 **/
class Blueprint
{
public:
    using UP = std::unique_ptr<Blueprint>;
    using Children = std::vector<Blueprint::UP>;
    using SearchIteratorUP = std::unique_ptr<SearchIterator>;

    enum class OptimizePass { FIRST, LAST };

    class Options {
    private:
        bool _sort_by_cost;
        bool _allow_force_strict;
        bool _keep_order;
    public:
        constexpr Options() noexcept
          : _sort_by_cost(false),
            _allow_force_strict(false),
            _keep_order(false) {}
        constexpr bool sort_by_cost() const noexcept { return _sort_by_cost; }
        constexpr Options &sort_by_cost(bool value) noexcept {
            _sort_by_cost = value;
            return *this;
        }
        constexpr bool allow_force_strict() const noexcept { return _allow_force_strict; }
        constexpr Options &allow_force_strict(bool value) noexcept {
            _allow_force_strict = value;
            return *this;
        }
        constexpr bool keep_order() const noexcept { return _keep_order; }
        constexpr Options &keep_order(bool value) noexcept {
            _keep_order = value;
            return *this;
        }
    };

private:
    static Options &thread_opts() noexcept {
        thread_local Options opts;
        return opts;
    }
    struct BindOpts {
        Options prev;
        BindOpts(Options opts) noexcept : prev(thread_opts()) {
            thread_opts() = opts;
        }
        ~BindOpts() noexcept {
            thread_opts() = prev;
        }
        BindOpts(BindOpts &&) = delete;
        BindOpts(const BindOpts &) = delete;
        BindOpts &operator=(BindOpts &&) = delete;
        BindOpts &operator=(const BindOpts &) = delete;
    };

public:
    // thread local Options are used during query planning (calculate_flow_stats/sort)
    //
    // The optimize_and_sort function will handle this for you by
    // binding the given options to the current thread before calling
    // optimize and sort. If you do low-level stuff directly, make
    // sure to keep the relevant options bound while doing so.
    static BindOpts bind_opts(Options opts) noexcept { return BindOpts(opts); }
    static bool opt_sort_by_cost() noexcept { return thread_opts().sort_by_cost(); }
    static bool opt_allow_force_strict() noexcept { return thread_opts().allow_force_strict(); }
    static bool opt_keep_order() noexcept { return thread_opts().keep_order(); }

    struct HitEstimate {
        uint32_t estHits;
        bool     empty;

        HitEstimate() noexcept : estHits(0), empty(true) {}
        HitEstimate(uint32_t estHits_, bool empty_) noexcept
            : estHits(estHits_), empty(empty_) {}

        bool operator < (const HitEstimate &other) const noexcept {
            if (empty == other.empty) {
                return (estHits < other.estHits);
            } else {
                return empty;
            }
        }
    };

    class State
    {
    private:
        FieldSpecBaseList _fields;
        uint32_t          _estimateHits;
        uint32_t          _tree_size : 20;
        bool              _estimateEmpty : 1;
        bool              _allow_termwise_eval : 1;
        bool              _want_global_filter : 1;
        uint8_t           _cost_tier;

    public:
        static constexpr uint8_t COST_TIER_NORMAL = 1;
        static constexpr uint8_t COST_TIER_EXPENSIVE = 2;
        static constexpr uint8_t COST_TIER_MAX = 255;

        State() noexcept;
        explicit State(FieldSpecBase field) noexcept;
        explicit State(FieldSpecBaseList fields_in) noexcept;
        State(const State &rhs) = delete;
        State(State &&rhs) noexcept = default;
        State &operator=(const State &rhs) = delete;
        State &operator=(State &&rhs) noexcept = default;
        ~State();

        bool isTermLike() const noexcept { return !_fields.empty(); }
        const FieldSpecBaseList &fields() const noexcept { return _fields; }

        size_t numFields() const noexcept { return _fields.size(); }
        const FieldSpecBase &field(size_t idx) const noexcept { return _fields[idx]; }
        const FieldSpecBase *lookupField(uint32_t fieldId) const noexcept {
            for (const FieldSpecBase & field : _fields) {
                if (field.getFieldId() == fieldId) {
                    return &field;
                }
            }
            return nullptr;
        }

        void estimate(HitEstimate est) noexcept {
            _estimateHits = est.estHits;
            _estimateEmpty = est.empty;
        }
        //TODO replace use of estimate by using empty/estHits directly and then have a real estimate here
        HitEstimate estimate() const noexcept { return {_estimateHits, _estimateEmpty}; }

        double hit_ratio(uint32_t docid_limit) const noexcept {
            return abs_to_rel_est(_estimateHits, docid_limit);
        }

        void tree_size(uint32_t value) noexcept {
            assert(value < 0x100000);
            _tree_size = value;
        }
        uint32_t tree_size() const noexcept { return _tree_size; }
        void allow_termwise_eval(bool value) noexcept { _allow_termwise_eval = value; }
        bool allow_termwise_eval() const noexcept { return _allow_termwise_eval; }
        void want_global_filter(bool value) noexcept { _want_global_filter = value; }
        bool want_global_filter() const noexcept { return _want_global_filter; }
        void cost_tier(uint8_t value) noexcept { _cost_tier = value; }
        uint8_t cost_tier() const noexcept { return _cost_tier; }
    };
    
    // converts from an absolute to a relative estimate
    static double abs_to_rel_est(uint32_t est, uint32_t docid_limit) noexcept {
        uint32_t total_docs = std::max(est, docid_limit);
        return (total_docs == 0) ? 0.0 : double(est) / double(total_docs);
    }

    // utility that just takes maximum estimate
    static HitEstimate max(const std::vector<HitEstimate> &data);

    // utility that just takes minium estimate
    static HitEstimate min(const std::vector<HitEstimate> &data);

    // utility that calculates saturated sum
    //
    // upper limit for estimate: docid_limit
    // lower limit for docid_limit: max child estimate
    static HitEstimate sat_sum(const std::vector<HitEstimate> &data, uint32_t docid_limit);

    // utility to get the greater estimate to sort first, higher tiers last
    struct TieredGreaterEstimate {
        bool operator () (const auto &a, const auto &b) const noexcept {
            const auto &lhs = a->getState();
            const auto &rhs = b->getState();
            if (lhs.cost_tier() != rhs.cost_tier()) {
                return (lhs.cost_tier() < rhs.cost_tier());
            }
            return (rhs.estimate() < lhs.estimate());
        }
    };

    // utility to get the lesser estimate to sort first, higher tiers last
    struct TieredLessEstimate {
        bool operator () (const auto &a, const auto &b) const noexcept {
            const auto &lhs = a->getState();
            const auto &rhs = b->getState();
            if (lhs.cost_tier() != rhs.cost_tier()) {
                return (lhs.cost_tier() < rhs.cost_tier());
            }
            return (lhs.estimate() < rhs.estimate());
        }
    };

private:
    Blueprint *_parent;
    FlowStats  _flow_stats;
    uint32_t   _sourceId;
    uint32_t   _docid_limit;
    bool       _strict;
    bool       _frozen;

protected:
    virtual void notifyChange() {
        if (_parent != nullptr) {
            _parent->notifyChange();
        }
    }
    void freeze_self() {
        getState();
        _frozen = true;
    }

    // Call this first inside sort implementations to handle 2 things:
    //
    // (1) force in_flow to be strict if allowed and better.
    // (2) tag blueprint with the strictness of the in_flow.
    void resolve_strict(InFlow &in_flow) noexcept;

public:
    class IPredicate {
    public:
        virtual ~IPredicate() = default;
        virtual bool check(const Blueprint & bp) const = 0;
    };

    // Signal if createFilterSearch should ensure the returned
    // iterator is an upper bound (yielding a hit on at least
    // all matching documents) or a lower bound (never yielding a
    // hit that isn't certain to be a match).
    enum class FilterConstraint { UPPER_BOUND, LOWER_BOUND };

    Blueprint() noexcept;
    Blueprint(const Blueprint &) = delete;
    Blueprint &operator=(const Blueprint &) = delete;
    virtual ~Blueprint();

    void setParent(Blueprint *parent) noexcept { _parent = parent; }
    Blueprint *getParent() const noexcept { return _parent; }
    bool has_parent() const { return (_parent != nullptr); }

    Blueprint &setSourceId(uint32_t sourceId) noexcept { _sourceId = sourceId; return *this; }
    uint32_t getSourceId() const noexcept { return _sourceId; }

    virtual void setDocIdLimit(uint32_t limit) noexcept { _docid_limit = limit; }
    uint32_t get_docid_limit() const noexcept { return _docid_limit; }

    bool strict() const noexcept { return _strict; }

    virtual void each_node_post_order(const std::function<void(Blueprint&)> &f);

    // The combination of 'optimize' (2 passes bottom-up) and 'sort'
    // (1 pass top-down) is considered 'planning'. Flow stats are
    // calculated during the last optimize pass (which itself requires
    // knowledge about the docid limit) and strict tagging is done
    // during sorting. Strict tagging is needed for fetchPostings
    // (which also needs the estimate part of the flow stats),
    // createSearch and createFilterSearch to work correctly. This
    // means we always need to perform some form of planning.
    //
    // This function will perform basic planning. The docid limit will
    // be tagged on all nodes, flow stats will be calculated for all
    // nodes, sorting will be performed based on optimal flow cost and
    // strict tagging will be conservative. The only structural change
    // allowed is child node reordering.
    void basic_plan(InFlow in_flow, uint32_t docid_limit);

    // Similar to basic_plan, but will not reorder children. Note that
    // this means that flow stats will be misleading as they assume
    // optimal ordering. Used for testing.
    void null_plan(InFlow in_flow, uint32_t docid_limit);

    // Estimate the actual cost of evaluating the (sub-)query
    // represented by this blueprint with the given in-flow. This
    // function should be called after query planning has been
    // performed. This function could be useful to predict very
    // expensive queries, but the initial use-case is to understand
    // query cost better in micro-benchmarks to improve low-level cost
    // tuning.
    virtual double estimate_actual_cost(InFlow in_flow) const noexcept;
    // Estimate the change in cost caused by having a strict iterator
    // with a non-strict in-flow. Note that this function might force
    // the in_flow to be strict in order to align it with the
    // strictness of this blueprint.
    double estimate_strict_cost_diff(InFlow &in_flow) const noexcept;

    static Blueprint::UP optimize(Blueprint::UP bp);
    virtual void sort(InFlow in_flow) = 0;
    static Blueprint::UP optimize_and_sort(Blueprint::UP bp, InFlow in_flow, const Options &opts) {
        auto opts_guard = bind_opts(opts);
        auto result = optimize(std::move(bp));
        result->sort(in_flow);
        return result;
    }
    static Blueprint::UP optimize_and_sort(Blueprint::UP bp, InFlow in_flow) {
        return optimize_and_sort(std::move(bp), in_flow, Options().sort_by_cost(true));
    }
    static Blueprint::UP optimize_and_sort(Blueprint::UP bp) {
        return optimize_and_sort(std::move(bp), true);
    }
    virtual void optimize(Blueprint* &self, OptimizePass pass) = 0;
    virtual void optimize_self(OptimizePass pass);
    virtual Blueprint::UP get_replacement();

    virtual bool supports_termwise_children() const { return false; }
    virtual bool always_needs_unpack() const { return false; }

    /**
     * Sets the global filter on the query blueprint tree.
     *
     * This function is implemented by leaf blueprints that want the global filter,
     * signaled by LeafBlueprint::set_want_global_filter().
     *
     * @param global_filter The global filter that is calculated once per query if wanted.
     * @param estimated_hit_ratio The estimated hit ratio of the query (in the range [0.0, 1.0]).
     */
    virtual void set_global_filter(const GlobalFilter &global_filter, double estimated_hit_ratio);

    virtual const State &getState() const = 0;
    const Blueprint &root() const;

    double hit_ratio() const { return getState().hit_ratio(_docid_limit); }

    // The flow statistics for a blueprint is calculated during the
    // LAST optimize pass (just prior to sorting). After being
    // calculated, each value is available through a simple accessor
    // function. Since the optimize process is performed bottom-up, a
    // blueprint can expect all children to already have these values
    // calculated when the calculate_flow_stats function is called.
    //
    // Note that values are not automatically available for blueprints
    // used inside complex leafs since they are not part of the tree
    // seen by optimize. When the calculate_flow_stats function is
    // called on a complex leaf, it can call the update_flow_stats
    // function directly (the function that is normally called by
    // optimize) on internal blueprints to make these values available
    // before using them to calculate its own flow stats.
    //
    //    'estimate': relative estimate in the range [0,1]
    //        'cost': cost of non-strict evaluation: multiply by non-strict in-flow
    // 'strict_cost': cost of strict evaluation: assuming strict in-flow of 1.0
    double estimate() const noexcept { return _flow_stats.estimate; }
    double cost() const noexcept { return _flow_stats.cost; }
    double strict_cost() const noexcept { return _flow_stats.strict_cost; }
    virtual FlowStats calculate_flow_stats(uint32_t docid_limit) const = 0;
    void update_flow_stats(uint32_t docid_limit) {
        _flow_stats = calculate_flow_stats(docid_limit);
    }
    static FlowStats default_flow_stats(uint32_t docid_limit, uint32_t abs_est, size_t child_cnt);
    static FlowStats default_flow_stats(size_t child_cnt);

    virtual void fetchPostings(const ExecuteInfo &execInfo) = 0;
    virtual void freeze() = 0;
    bool frozen() const { return _frozen; }

    virtual SearchIteratorUP createSearch(fef::MatchData &md) const = 0;
    virtual SearchIteratorUP createFilterSearch(FilterConstraint constraint) const = 0;
    static SearchIteratorUP create_and_filter(const Children &children, bool strict, FilterConstraint constraint);
    static SearchIteratorUP create_or_filter(const Children &children, bool strict, FilterConstraint constraint);
    static SearchIteratorUP create_atmost_and_filter(const Children &children, bool strict, FilterConstraint constraint);
    static SearchIteratorUP create_atmost_or_filter(const Children &children, bool strict, FilterConstraint constraint);
    static SearchIteratorUP create_andnot_filter(const Children &children, bool strict, FilterConstraint constraint);
    static SearchIteratorUP create_first_child_filter(const Children &children, FilterConstraint constraint);
    static SearchIteratorUP create_default_filter(FilterConstraint constraint);

    // for debug dumping
    vespalib::string asString() const;
    vespalib::slime::Cursor & asSlime(const vespalib::slime::Inserter & cursor) const;
    virtual vespalib::string getClassName() const;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual bool isEquiv() const noexcept { return false; }
    virtual bool isWhiteList() const noexcept { return false; }
    virtual IntermediateBlueprint * asIntermediate() noexcept { return nullptr; }
    const IntermediateBlueprint * asIntermediate() const noexcept { return const_cast<Blueprint *>(this)->asIntermediate(); }
    virtual const LeafBlueprint * asLeaf() const noexcept { return nullptr; }
    virtual AndBlueprint * asAnd() noexcept { return nullptr; }
    bool isAnd() const noexcept { return const_cast<Blueprint *>(this)->asAnd() != nullptr; }
    virtual AndNotBlueprint * asAndNot() noexcept { return nullptr; }
    bool isAndNot() const noexcept { return const_cast<Blueprint *>(this)->asAndNot() != nullptr; }
    virtual OrBlueprint * asOr() noexcept { return nullptr; }
    virtual SourceBlenderBlueprint * asSourceBlender() noexcept { return nullptr; }
    virtual WeakAndBlueprint * asWeakAnd() noexcept { return nullptr; }
    virtual bool isRank() const noexcept { return false; }
    virtual const attribute::ISearchContext *get_attribute_search_context() const noexcept { return nullptr; }

    // to avoid replacing an empty blueprint with another empty blueprint
    virtual EmptyBlueprint *as_empty() noexcept { return nullptr; }

    // For document summaries with matched-elements-only set.
    virtual std::unique_ptr<MatchingElementsSearch> create_matching_elements_search(const MatchingElementsFields &fields) const;
};

namespace blueprint {

//-----------------------------------------------------------------------------

class StateCache : public Blueprint
{
private:
    mutable bool  _stale;
    mutable State _state;
    void updateState() const;

protected:
    void notifyChange() final;
    virtual State calculateState() const = 0;

public:
    StateCache() : _stale(true), _state() {}
    const State &getState() const final {
        if (_stale) {
            updateState();
        }
        return _state;
    }
};

} // namespace blueprint

//-----------------------------------------------------------------------------

class IntermediateBlueprint : public blueprint::StateCache
{
private:
    Children _children;
    HitEstimate calculateEstimate() const;
    virtual uint8_t calculate_cost_tier() const;
    uint32_t calculate_tree_size() const;
    bool infer_allow_termwise_eval() const;
    bool infer_want_global_filter() const;

    size_t count_termwise_nodes(const UnpackInfo &unpack) const;
    virtual AnyFlow my_flow(InFlow in_flow) const = 0;

protected:
    // returns an empty collection if children have empty or
    // conflicting collections of field specs.
    FieldSpecBaseList mixChildrenFields() const;

    State calculateState() const final;

    virtual bool isPositive(size_t index) const { (void) index; return true; }

    bool should_do_termwise_eval(const UnpackInfo &unpack, double match_limit) const;

    const Children& get_children() const { return _children; }

public:
    using IndexList = std::vector<size_t>;
    IntermediateBlueprint() noexcept;
    ~IntermediateBlueprint() override;

    void setDocIdLimit(uint32_t limit) noexcept final;
    void each_node_post_order(const std::function<void(Blueprint&)> &f) override;

    // additional cost not attributed to the children flow (heap merge/unpack/etc)
    virtual double estimate_self_cost(InFlow in_flow) const noexcept;
    double estimate_actual_cost(InFlow in_flow) const noexcept override;
    void optimize(Blueprint* &self, OptimizePass pass) final;
    void sort(InFlow in_flow) override;
    void set_global_filter(const GlobalFilter &global_filter, double estimated_hit_ratio) override;

    IndexList find(const IPredicate & check) const;
    size_t childCnt() const { return _children.size(); }
    const Blueprint &getChild(size_t n) const { return *_children[n]; }
    Blueprint &getChild(size_t n) { return *_children[n]; }
    void reserve(size_t sz) { _children.reserve(sz); }
    IntermediateBlueprint & insertChild(size_t n, Blueprint::UP child);
    IntermediateBlueprint &addChild(Blueprint::UP child);
    Blueprint::UP removeChild(size_t n);
    Blueprint::UP removeLastChild() { return removeChild(childCnt() - 1); }
    SearchIteratorUP createSearch(fef::MatchData &md) const override;
    
    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const = 0;
    virtual FieldSpecBaseList exposeFields() const = 0;
    virtual void sort(Children &children, InFlow in_flow) const = 0;
    virtual SearchIteratorUP
    createIntermediateSearch(MultiSearch::Children subSearches, fef::MatchData &md) const = 0;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
    void freeze() final;

    UnpackInfo calculateUnpackInfo(const fef::MatchData & md) const;
    IntermediateBlueprint * asIntermediate() noexcept final { return this; }
};


class LeafBlueprint : public Blueprint
{
private:
    State _state;
protected:
    void optimize(Blueprint* &self, OptimizePass pass) final;
    void setEstimate(HitEstimate est) {
        _state.estimate(est);
        notifyChange();
    }
    void set_cost_tier(uint32_t value);
    void set_allow_termwise_eval(bool value) {
        _state.allow_termwise_eval(value);
        notifyChange();
    }
    void set_want_global_filter(bool value);
    void set_tree_size(uint32_t value);

    explicit LeafBlueprint(bool allow_termwise_eval) noexcept
        : _state()
    {
        _state.allow_termwise_eval(allow_termwise_eval);
    }

    LeafBlueprint(FieldSpecBase field, bool allow_termwise_eval) noexcept
        : _state(field)
    {
        _state.allow_termwise_eval(allow_termwise_eval);
    }
    LeafBlueprint(FieldSpecBaseList fields, bool allow_termwise_eval) noexcept
        : _state(std::move(fields))
    {
        _state.allow_termwise_eval(allow_termwise_eval);
    }

public:
    ~LeafBlueprint() override = default;
    const State &getState() const final { return _state; }
    void fetchPostings(const ExecuteInfo &execInfo) override;
    void freeze() final;
    SearchIteratorUP createSearch(fef::MatchData &md) const override;
    const LeafBlueprint * asLeaf() const noexcept final { return this; }

    virtual bool getRange(vespalib::string & from, vespalib::string & to) const;
    virtual SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const = 0;
};

// for leaf nodes representing a single term
struct SimpleLeafBlueprint : LeafBlueprint {
    explicit SimpleLeafBlueprint() noexcept : LeafBlueprint(true) {}
    explicit SimpleLeafBlueprint(FieldSpecBase field) noexcept : LeafBlueprint(field, true) {}
    explicit SimpleLeafBlueprint(FieldSpecBaseList fields) noexcept: LeafBlueprint(std::move(fields), true) {}
    void sort(InFlow in_flow) override;
};

// for leaf nodes representing more complex structures like wand/phrase
struct ComplexLeafBlueprint : LeafBlueprint {
    explicit ComplexLeafBlueprint(FieldSpecBase field) noexcept : LeafBlueprint(field, false) {}
    explicit ComplexLeafBlueprint(FieldSpecBaseList fields) noexcept : LeafBlueprint(std::move(fields), false) {}
};

//-----------------------------------------------------------------------------

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::Blueprint &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::Blueprint *obj);

