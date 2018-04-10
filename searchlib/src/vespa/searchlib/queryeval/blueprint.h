// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_spec.h"
#include "unpackinfo.h"
#include <vespa/searchlib/fef/handle.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

namespace vespalib { class ObjectVisitor; };

namespace search::queryeval {

class SearchIterator;

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
    typedef std::unique_ptr<Blueprint> UP;
    typedef std::unique_ptr<SearchIterator> SearchIteratorUP;

    struct HitEstimate {
        uint32_t estHits;
        bool     empty;

        HitEstimate() : estHits(0), empty(true) {}
        HitEstimate(uint32_t estHits_, bool empty_)
            : estHits(estHits_), empty(empty_) {}

        bool operator < (const HitEstimate &other) const {
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
        HitEstimate       _estimate;
        uint32_t          _tree_size;
        bool              _allow_termwise_eval;

    public:
        State(const FieldSpecBaseList &fields_in);
        ~State();
        void swap(State & rhs) {
            _fields.swap(rhs._fields);
            std::swap(_estimate, rhs._estimate);
            std::swap(_tree_size, rhs._tree_size);
            std::swap(_allow_termwise_eval, rhs._allow_termwise_eval);
        }

        bool isTermLike() const { return !_fields.empty(); }
        const FieldSpecBaseList &fields() const { return _fields; }

        size_t numFields() const { return _fields.size(); }
        const FieldSpecBase &field(size_t idx) const { return _fields[idx]; }
        const FieldSpecBase *lookupField(uint32_t fieldId) const {
            for (size_t i = 0; i < _fields.size(); ++i) {
                if (_fields[i].getFieldId() == fieldId) {
                    return &_fields[i];
                }
            }
            return nullptr;
        }

        void estimate(HitEstimate est) { _estimate = est; }
        HitEstimate estimate() const { return _estimate; }
        double hit_ratio(uint32_t docid_limit) const {
            uint32_t total_hits = _estimate.estHits;
            uint32_t total_docs = std::max(total_hits, docid_limit);
            return double(total_hits) / double(total_docs);
        }
        void tree_size(uint32_t value) { _tree_size = value; }
        uint32_t tree_size() const { return _tree_size; }
        void allow_termwise_eval(bool value) { _allow_termwise_eval = value; }
        bool allow_termwise_eval() const { return _allow_termwise_eval; }
    };

    // utility that just takes maximum estimate
    static HitEstimate max(const std::vector<HitEstimate> &data);

    // utility that just takes minium estimate
    static HitEstimate min(const std::vector<HitEstimate> &data);

    // utility to get the greater estimate to sort first
    struct GreaterEstimate {
        bool operator () (Blueprint * const &a, Blueprint * const &b) const {
            return (b->getState().estimate() < a->getState().estimate());
        }
    };

    // utility to get the lesser estimate to sort first
    struct LessEstimate {
        bool operator () (Blueprint * const &a, const Blueprint * const &b) const {
            return (a->getState().estimate() < b->getState().estimate());
        }
    };

private:
    Blueprint *_parent;
    uint32_t   _sourceId;
    uint32_t   _docid_limit;
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

public:
    class IPredicate {
    public:
        virtual ~IPredicate() {}
        virtual bool check(const Blueprint & bp) const = 0;
    };

    Blueprint();
    Blueprint(const Blueprint &) = delete;
    Blueprint &operator=(const Blueprint &) = delete;
    virtual ~Blueprint();

    void setParent(Blueprint *parent) { _parent = parent; }
    Blueprint *getParent() const { return _parent; }
    bool has_parent() const { return (_parent != nullptr); }

    Blueprint &setSourceId(uint32_t sourceId) { _sourceId = sourceId; return *this; }
    uint32_t getSourceId() const { return _sourceId; }

    virtual void setDocIdLimit(uint32_t limit) { _docid_limit = limit; }
    uint32_t get_docid_limit() const { return _docid_limit; }

    static Blueprint::UP optimize(Blueprint::UP bp);
    virtual void optimize(Blueprint* &self) = 0;
    virtual void optimize_self();
    virtual Blueprint::UP get_replacement();
    virtual bool should_optimize_children() const { return true; }

    virtual bool supports_termwise_children() const { return false; }

    virtual const State &getState() const = 0;
    const Blueprint &root() const;

    double hit_ratio() const { return getState().hit_ratio(_docid_limit); }        

    virtual void fetchPostings(bool strict) = 0;
    virtual void freeze() = 0;
    bool frozen() const { return _frozen; }

    virtual SearchIteratorUP createSearch(fef::MatchData &md, bool strict) const = 0;

    // for debug dumping
    vespalib::string asString() const;
    virtual vespalib::string getClassName() const;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual bool isEquiv() const { return false; }
    virtual bool isWhiteList() const { return false; }
    virtual bool isIntermediate() const { return false; }
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
    void notifyChange() override final {
        assert(!frozen());
        Blueprint::notifyChange();
        _stale = true;
    }
    virtual State calculateState() const = 0;

public:
    StateCache() : _stale(true), _state(FieldSpecBaseList()) {}
    const State &getState() const override final {
        if (_stale) {
            assert(!frozen());
            updateState();
        }
        return _state;
    }
};

} // namespace blueprint

//-----------------------------------------------------------------------------

class IntermediateBlueprint : public blueprint::StateCache
{
public:
    typedef std::vector<Blueprint*> Children;
private:
    Children _children;
    HitEstimate calculateEstimate() const;
    uint32_t calculate_tree_size() const;
    bool infer_allow_termwise_eval() const;

    size_t count_termwise_nodes(const UnpackInfo &unpack) const;

protected:
    // returns an empty collection if children have empty or
    // conflicting collections of field specs.
    FieldSpecBaseList mixChildrenFields() const;

    State calculateState() const override final;

    virtual bool isPositive(size_t index) const { (void) index; return true; }

    bool should_do_termwise_eval(const UnpackInfo &unpack, double match_limit) const;

public:
    typedef std::vector<size_t> IndexList;
    IntermediateBlueprint();
    virtual ~IntermediateBlueprint();

    void setDocIdLimit(uint32_t limit) override final;

    void optimize(Blueprint* &self) override final;

    IndexList find(const IPredicate & check) const;
    size_t childCnt() const { return _children.size(); }
    const Blueprint &getChild(size_t n) const;
    Blueprint &getChild(size_t n);
    IntermediateBlueprint & insertChild(size_t n, Blueprint::UP child);
    IntermediateBlueprint &addChild(Blueprint::UP child);
    Blueprint::UP removeChild(size_t n);
    SearchIteratorUP createSearch(fef::MatchData &md, bool strict) const override;

    virtual HitEstimate combine(const std::vector<HitEstimate> &data) const = 0;
    virtual FieldSpecBaseList exposeFields() const = 0;
    virtual void sort(std::vector<Blueprint*> &children) const = 0;
    virtual bool inheritStrict(size_t i) const = 0;
    virtual SearchIteratorUP
    createIntermediateSearch(const std::vector<SearchIterator *> &subSearches,
                             bool strict, fef::MatchData &md) const = 0;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(bool strict) override;
    void freeze() override final;

    UnpackInfo calculateUnpackInfo(const fef::MatchData & md) const;
    bool isIntermediate() const override { return true; }
};


class LeafBlueprint : public Blueprint
{
private:
    State _state;

protected:
    void optimize(Blueprint* &self) override final;
    void setEstimate(HitEstimate est);
    void set_allow_termwise_eval(bool value);
    void set_tree_size(uint32_t value);

    LeafBlueprint(const FieldSpecBaseList &fields, bool allow_termwise_eval);
public:
    ~LeafBlueprint();
    const State &getState() const override final { return _state; }
    void setDocIdLimit(uint32_t limit) override final { Blueprint::setDocIdLimit(limit); }
    void fetchPostings(bool strict) override;
    void freeze() override final;
    SearchIteratorUP createSearch(fef::MatchData &md, bool strict) const override;

    virtual SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda,
                                                bool strict) const = 0;
};

// for leaf nodes representing a single term
struct SimpleLeafBlueprint : LeafBlueprint {
    SimpleLeafBlueprint(const FieldSpecBase &field) : LeafBlueprint(FieldSpecBaseList().add(field), true) {}
    SimpleLeafBlueprint(const FieldSpecBaseList &fields) : LeafBlueprint(fields, true) {}
};

// for leaf nodes representing more complex structures like wand/phrase
struct ComplexLeafBlueprint : LeafBlueprint {
    ComplexLeafBlueprint(const FieldSpecBase &field) : LeafBlueprint(FieldSpecBaseList().add(field), false) {}    
    ComplexLeafBlueprint(const FieldSpecBaseList &fields) : LeafBlueprint(fields, false) {}
};

//-----------------------------------------------------------------------------

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::Blueprint &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::Blueprint *obj);

