// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexcollection.h"
#include "indexsearchablevisitor.h"
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/queryeval/create_blueprint_visitor_helper.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.indexcollection");

using namespace search::queryeval;
using namespace search::query;
using search::attribute::IAttributeContext;

namespace searchcorespi {

IndexCollection::IndexCollection(const ISourceSelector::SP & selector)
    : _source_selector(selector),
      _sources()
{
}

IndexCollection::IndexCollection(const ISourceSelector::SP & selector,
                                 const ISearchableIndexCollection &sources)
    : _source_selector(selector),
      _sources()
{
    for (size_t i(0), m(sources.getSourceCount()); i < m; i++) {
        append(sources.getSourceId(i), sources.getSearchableSP(i));
    }
    setCurrentIndex(sources.getCurrentIndex());
}

IndexCollection::~IndexCollection() {}

void
IndexCollection::setSource(uint32_t docId)
{
    assert( valid() );
    _source_selector->setSource(docId, getCurrentIndex());
}

ISearchableIndexCollection::UP
IndexCollection::replaceAndRenumber(const ISourceSelector::SP & selector,
                                    const ISearchableIndexCollection &fsc,
                                    uint32_t id_diff,
                                    const IndexSearchable::SP &new_source)
{
    ISearchableIndexCollection::UP new_fsc(new IndexCollection(selector));
    new_fsc->append(0, new_source);
    for (size_t i = 0; i < fsc.getSourceCount(); ++i) {
        if (fsc.getSourceId(i) > id_diff) {
            new_fsc->append(fsc.getSourceId(i) - id_diff,
                            fsc.getSearchableSP(i));
        }
    }
    return new_fsc;
}

void
IndexCollection::append(uint32_t id, const IndexSearchable::SP &fs)
{
    _sources.push_back(SourceWithId(id, fs));
}

IndexSearchable::SP
IndexCollection::getSearchableSP(uint32_t i) const
{
    return _sources[i].source_wrapper;
}

void
IndexCollection::replace(uint32_t id, const IndexSearchable::SP &fs)
{
    for (size_t i = 0; i < _sources.size(); ++i) {
        if (_sources[i].id == id) {
            _sources[i].source_wrapper = fs;
            return;
        }
    }
    LOG(warning, "Tried to replace Searchable %d, but it wasn't there.", id);
    append(id, fs);
}

const ISourceSelector &
IndexCollection::getSourceSelector() const
{
    return *_source_selector;
}

size_t
IndexCollection::getSourceCount() const
{
    return _sources.size();
}

IndexSearchable &
IndexCollection::getSearchable(uint32_t i) const
{
    return *_sources[i].source_wrapper;
}

uint32_t
IndexCollection::getSourceId(uint32_t i) const
{
    return _sources[i].id;
}

search::SearchableStats
IndexCollection::getSearchableStats() const
{
    search::SearchableStats stats;
    for (size_t i = 0; i < _sources.size(); ++i) {
        stats.add(_sources[i].source_wrapper->getSearchableStats());
    }
    return stats;
}

search::SerialNum
IndexCollection::getSerialNum() const
{
    search::SerialNum serialNum = 0;
    for (auto &source : _sources) {
        serialNum = std::max(serialNum, source.source_wrapper->getSerialNum());
    }
    return serialNum;
}


void
IndexCollection::accept(IndexSearchableVisitor &visitor) const
{
    for (auto &source : _sources) {
        source.source_wrapper->accept(visitor);
    }
}

namespace {

struct Mixer {
    const ISourceSelector                &_selector;
    std::unique_ptr<SourceBlenderBlueprint> _blender;

    Mixer(const ISourceSelector &selector)
        : _selector(selector), _blender() {}

    void addIndex(Blueprint::UP index) {
        if (_blender.get() == NULL) {
            _blender.reset(new SourceBlenderBlueprint(_selector));
        }
        _blender->addChild(std::move(index));
    }

    Blueprint::UP mix() {
        if (_blender.get() == NULL) {
            return Blueprint::UP(new EmptyBlueprint());
        }
        return Blueprint::UP(_blender.release());
    }
};

class CreateBlueprintVisitor : public search::query::QueryVisitor {
private:
    const IIndexCollection  &_indexes;
    const FieldSpecList     &_fields;
    const IRequestContext   &_requestContext;
    Blueprint::UP            _result;

    template <typename NodeType>
    void visitTerm(NodeType &n) {
        Mixer mixer(_indexes.getSourceSelector());
        for (size_t i = 0; i < _indexes.getSourceCount(); ++i) {
            Blueprint::UP blueprint = _indexes.getSearchable(i).createBlueprint(_requestContext, _fields, n);
            blueprint->setSourceId(_indexes.getSourceId(i));
            mixer.addIndex(std::move(blueprint));
        }
        _result = mixer.mix();
    }

    void visit(And &)     override { }
    void visit(AndNot &)  override { }
    void visit(Or &)      override { }
    void visit(WeakAnd &) override { }
    void visit(Equiv &)   override { }
    void visit(Rank &)    override { }
    void visit(Near &)    override { }
    void visit(ONear &)   override { }

    void visit(WeightedSetTerm &n) override { visitTerm(n); }
    void visit(DotProduct &n)      override { visitTerm(n); }
    void visit(WandTerm &n)        override { visitTerm(n); }
    void visit(Phrase &n)          override { visitTerm(n); }
    void visit(SameElement &n)     override { visitTerm(n); }
    void visit(NumberTerm &n)      override { visitTerm(n); }
    void visit(LocationTerm &n)    override { visitTerm(n); }
    void visit(PrefixTerm &n)      override { visitTerm(n); }
    void visit(RangeTerm &n)       override { visitTerm(n); }
    void visit(StringTerm &n)      override { visitTerm(n); }
    void visit(SubstringTerm &n)   override { visitTerm(n); }
    void visit(SuffixTerm &n)      override { visitTerm(n); }
    void visit(PredicateQuery &n)  override { visitTerm(n); }
    void visit(RegExpTerm &n)      override { visitTerm(n); }

public:
    CreateBlueprintVisitor(const IIndexCollection &indexes,
                           const FieldSpecList &fields,
                           const IRequestContext & requestContext)
        : _indexes(indexes),
          _fields(fields),
          _requestContext(requestContext),
          _result() {}

    Blueprint::UP getResult() { return std::move(_result); }
};

}

Blueprint::UP
IndexCollection::createBlueprint(const IRequestContext & requestContext,
                                 const FieldSpec &field,
                                 const Node &term)
{
    FieldSpecList fields;
    fields.add(field);
    return createBlueprint(requestContext, fields, term);
}

Blueprint::UP
IndexCollection::createBlueprint(const IRequestContext & requestContext,
                                 const FieldSpecList &fields,
                                 const Node &term)
{
    CreateBlueprintVisitor visitor(*this, fields, requestContext);
    const_cast<Node &>(term).accept(visitor);
    return visitor.getResult();
}

}  // namespace searchcorespi
