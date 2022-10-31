// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/multisearch.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/objects/visit.hpp>

namespace search::queryeval {

//-----------------------------------------------------------------------------

class MySearch : public MultiSearch
{
public:
    typedef search::fef::TermFieldMatchDataArray TFMDA;
    typedef search::fef::MatchData               MatchData;

private:
    vespalib::string _tag;
    bool             _isLeaf;
    bool             _isStrict;
    TFMDA            _match;
    MatchData       *_md;

    std::vector<uint32_t> _handles;

protected:
    void doSeek(uint32_t) override {}
    void doUnpack(uint32_t) override {}

public:
    MySearch(const std::string &tag, bool leaf, bool strict)
        : _tag(tag), _isLeaf(leaf), _isStrict(strict),
          _match(), _md(0) {}

    MySearch(const std::string &tag, const TFMDA &tfmda, bool strict)
        : _tag(tag), _isLeaf(true), _isStrict(strict),
          _match(tfmda), _md(0) {}

    MySearch(const std::string &tag, Children children,
             MatchData *md, bool strict)
      : MultiSearch(std::move(children)),
        _tag(tag), _isLeaf(false), _isStrict(strict),
        _match(), _md(md) {}

    MySearch &add(SearchIterator *search) {
        _children.emplace_back(search);
        return *this;
    }

    MySearch &addHandle(uint32_t handle) {
        _handles.push_back(handle);
        return *this;
    }

    bool verifyAndInferImpl(MatchData &md) {
        bool ok = true;
        if (!_isLeaf) {
            ok &= (_md == &md);
        }
        for (size_t i = 0; i < _children.size(); ++i) {
            MySearch *child = dynamic_cast<MySearch *>(_children[i].get());
            ok &= (child != 0);
            if (child != 0) {
                ok &= child->verifyAndInferImpl(md);
            }
        }
        for (size_t i = 0; i < _match.size(); ++i) {
            search::fef::TermFieldMatchData *tfmd = _match[i];
            _handles.push_back(search::fef::IllegalHandle);
            for (search::fef::TermFieldHandle j = 0; j < md.getNumTermFields(); ++j) {
                if (md.resolveTermField(j) == tfmd) {
                    _handles.back() = j;
                    break;
                }
            }
            ok &= (_handles.back() != search::fef::IllegalHandle);
        }
        return ok;
    }

    static bool verifyAndInfer(SearchIterator *search, MatchData &md) {
        MySearch *self = dynamic_cast<MySearch *>(search);
        if (self == 0) {
            return false;
        } else {
            return self->verifyAndInferImpl(md);
        }
    }

    void visitMembers(vespalib::ObjectVisitor &visitor) const override {
        visit(visitor, "_tag",      _tag);
        visit(visitor, "_isLeaf",   _isLeaf);
        visit(visitor, "_isStrict", _isStrict);
        MultiSearch::visitMembers(visitor);
        visit(visitor, "_handles",  _handles);
    }

    ~MySearch() override {}
};

//-----------------------------------------------------------------------------

class MyLeaf : public SimpleLeafBlueprint
{
    typedef search::fef::TermFieldMatchDataArray TFMDA;
    bool _got_global_filter;

public:
    SearchIterator::UP
    createLeafSearch(const TFMDA &tfmda, bool strict) const override
    {
        return SearchIterator::UP(new MySearch("leaf", tfmda, strict));
    }

    MyLeaf(const FieldSpecBaseList &fields)
        : SimpleLeafBlueprint(fields), _got_global_filter(false)
    {}

    MyLeaf &estimate(uint32_t hits, bool empty = false) {
        setEstimate(HitEstimate(hits, empty));
        return *this;
    }

    MyLeaf &cost_tier(uint32_t value) {
        set_cost_tier(value);
        return *this;
    }
    void set_global_filter(const GlobalFilter &, double) override {
        _got_global_filter = true;
    }
    bool got_global_filter() const { return _got_global_filter; }
    // make public
    using LeafBlueprint::set_want_global_filter;

    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override {
        return create_default_filter(strict, constraint);
    }
};

//-----------------------------------------------------------------------------

class MyLeafSpec
{
private:
    FieldSpecBaseList      _fields;
    Blueprint::HitEstimate _estimate;
    uint32_t               _cost_tier;
    bool                   _want_global_filter;

public:
    explicit MyLeafSpec(uint32_t estHits, bool empty = false)
        : _fields(), _estimate(estHits, empty), _cost_tier(0), _want_global_filter(false) {}

    MyLeafSpec &addField(uint32_t fieldId, uint32_t handle) {
        _fields.add(FieldSpecBase(fieldId, handle));
        return *this;
    }
    MyLeafSpec &cost_tier(uint32_t value) {
        assert(value > 0);
        _cost_tier = value;
        return *this;
    }
    MyLeafSpec &want_global_filter() {
        _want_global_filter = true;
        return *this;
    }
    MyLeaf *create() const {
        return create<MyLeaf>();
    }
    template<typename Leaf>
    Leaf *create() const {
        Leaf *leaf = new Leaf(_fields);
        leaf->estimate(_estimate.estHits, _estimate.empty);
        if (_cost_tier > 0) {
            leaf->cost_tier(_cost_tier);
        }
        leaf->set_want_global_filter(_want_global_filter);
        return leaf;
    }
};

//-----------------------------------------------------------------------------

}
