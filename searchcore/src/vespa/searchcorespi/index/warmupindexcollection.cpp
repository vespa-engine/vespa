// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "warmupindexcollection.h"
#include "idiskindex.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/eval/eval/value.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".searchcorespi.index.warmupindexcollection");

namespace searchcorespi {

using index::IDiskIndex;
using search::fef::MatchDataLayout;
using search::fef::MatchData;
using search::index::FieldLengthInfo;
using search::query::StringBase;
using search::query::Node;
using search::queryeval::Blueprint;
using search::queryeval::ISourceSelector;
using search::queryeval::SearchIterator;
using search::queryeval::IRequestContext;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecList;
using TermMap = vespalib::hash_set<vespalib::string>;

namespace {
class WarmupRequestContext : public IRequestContext {
    using IAttributeVector = search::attribute::IAttributeVector;
    using AttributeBlueprintParams = search::attribute::AttributeBlueprintParams;
public:
    explicit WarmupRequestContext();
    ~WarmupRequestContext() override;
    const vespalib::Doom & getDoom() const override { return vespalib::Doom::never(); }
    vespalib::ThreadBundle & thread_bundle() const override { return vespalib::ThreadBundle::trivial(); }
    const IAttributeVector *getAttribute(const vespalib::string &) const override { return nullptr; }
    const IAttributeVector *getAttributeStableEnum(const vespalib::string &) const override { return nullptr; }
    const vespalib::eval::Value* get_query_tensor(const vespalib::string&) const override;
    const AttributeBlueprintParams& get_attribute_blueprint_params() const override { return _params; }
    const MetaStoreReadGuardSP * getMetaStoreReadGuard() const override { return nullptr; }
private:
    const AttributeBlueprintParams _params;
};
class WarmupTask : public vespalib::Executor::Task {
public:
    WarmupTask(std::unique_ptr<MatchData> md, std::shared_ptr<WarmupIndexCollection> warmup);
    ~WarmupTask() override;
    WarmupTask &createBlueprint(const FieldSpec &field, const Node &term) {
        _bluePrint = _warmup->createBlueprint(_requestContext, field, term);
        return *this;
    }
    WarmupTask &createBlueprint(const FieldSpecList &fields, const Node &term) {
        _bluePrint = _warmup->createBlueprint(_requestContext, fields, term);
        return *this;
    }
private:
    void run() override;
    std::shared_ptr<WarmupIndexCollection>         _warmup;
    vespalib::RetainGuard                          _retainGuard;
    std::unique_ptr<MatchData>                     _matchData;
    std::unique_ptr<search::queryeval::Blueprint>  _bluePrint;
    WarmupRequestContext                           _requestContext;
};
}

class FieldTermMap : public vespalib::hash_map<uint32_t, TermMap>
{

};

WarmupIndexCollection::WarmupIndexCollection(const WarmupConfig & warmupConfig,
                                             ISearchableIndexCollection::SP prev,
                                             ISearchableIndexCollection::SP next,
                                             IndexSearchable & warmup,
                                             vespalib::Executor & executor,
                                             IWarmupDone & warmupDone) :
    _warmupConfig(warmupConfig),
    _prev(std::move(prev)),
    _next(std::move(next)),
    _warmup(warmup),
    _executor(executor),
    _warmupDone(warmupDone),
    _warmupEndTime(vespalib::steady_clock::now() + warmupConfig.getDuration()),
    _handledTerms(std::make_unique<FieldTermMap>()),
    _pendingTasks()
{
    if (_next->valid()) {
        setCurrentIndex(_next->getCurrentIndex());
    } else {
        LOG(warning, "Next index is not valid, Dangerous !! : %s", _next->toString().c_str());
    }
    LOG(debug, "For %g seconds I will warm up '%s' %s unpack.", vespalib::to_s(warmupConfig.getDuration()), typeid(_warmup).name(), warmupConfig.getUnpack() ? "with" : "without");
    LOG(debug, "%s", toString().c_str());
}

void
WarmupIndexCollection::setSource(uint32_t docId)
{
    assert(_prev->valid());
    assert(_next->valid());
    _prev->setSource(docId);
    _next->setSource(docId);
}

vespalib::string
WarmupIndexCollection::toString() const
{
    vespalib::asciistream os;
    os << "warmup : ";
    if (dynamic_cast<const IDiskIndex *>(&_warmup) != nullptr) {
        os << static_cast<const IDiskIndex &>(_warmup).getIndexDir();
    } else {
        os << typeid(_warmup).name();
    }
    os << "\n";
    os << "next   : " << _next->toString() << "\n";
    os << "prev   : " << _prev->toString() << "\n";
    return os.str();
}

WarmupIndexCollection::~WarmupIndexCollection()
{
    if (_warmupEndTime != vespalib::steady_time()) {
        LOG(info, "Warmup aborted due to new state change or application shutdown");
    }
    assert(_pendingTasks.has_zero_ref_count());
}

const ISourceSelector &
WarmupIndexCollection::getSourceSelector() const
{
    return _next->getSourceSelector();
}

size_t
WarmupIndexCollection::getSourceCount() const
{
    return _next->getSourceCount();
}

IndexSearchable &
WarmupIndexCollection::getSearchable(uint32_t i) const
{
    return _next->getSearchable(i);
}

uint32_t
WarmupIndexCollection::getSourceId(uint32_t i) const
{
    return _next->getSourceId(i);
}

void
WarmupIndexCollection::fireWarmup(Task::UP task)
{
    vespalib::steady_time now(vespalib::steady_clock::now());
    if (now < _warmupEndTime) {
        auto bounced = _executor.execute(std::move(task));
        if (bounced) {
            //TODO Reduce to debug
            LOG(warning, "Warmup prohibited due to overload.");
        }
    } else {
        std::unique_lock<std::mutex> guard(_lock);
        if (_warmupEndTime != vespalib::steady_time()) {
            _warmupEndTime = vespalib::steady_time();
            guard.unlock();
            LOG(info, "Done warming up. Posting WarmupDoneTask");
            _warmupDone.warmupDone(shared_from_this());
        }
    }
}

bool
WarmupIndexCollection::handledBefore(uint32_t fieldId, const Node &term)
{
    const auto * sb(dynamic_cast<const StringBase *>(&term));
    if (sb != nullptr) {
        const vespalib::string & s = sb->getTerm();
        std::lock_guard<std::mutex> guard(_lock);
        TermMap::insert_result found = (*_handledTerms)[fieldId].insert(s);
        return ! found.second;
    }
    return true;
}
Blueprint::UP
WarmupIndexCollection::createBlueprint(const IRequestContext & requestContext,
                                       const FieldSpec &field,
                                       const Node &term)
{
    FieldSpecList fsl;
    fsl.add(field);
    return createBlueprint(requestContext, fsl,term);
}

Blueprint::UP
WarmupIndexCollection::createBlueprint(const IRequestContext & requestContext,
                                       const FieldSpecList &fields,
                                       const Node &term)
{
    if ( _warmupEndTime == vespalib::steady_time()) {
        // warmup done
        return _next->createBlueprint(requestContext, fields, term);
    }
    MatchDataLayout mdl;
    FieldSpecList fsl;
    bool needWarmUp(false);
    for(size_t i(0); i < fields.size(); i++) {
        const FieldSpec & f(fields[i]);
        FieldSpec fs(f.getName(), f.getFieldId(), mdl.allocTermField(f.getFieldId()), f.isFilter());
        fsl.add(fs);
        needWarmUp = needWarmUp || ! handledBefore(fs.getFieldId(), term);
    }
    if (needWarmUp) {
        auto task = std::make_unique<WarmupTask>(mdl.createMatchData(), shared_from_this());
        task->createBlueprint(fsl, term);
        fireWarmup(std::move(task));
    }
    return _prev->createBlueprint(requestContext, fields, term);
}

search::SearchableStats
WarmupIndexCollection::getSearchableStats() const
{
    return _prev->getSearchableStats();
}


search::SerialNum
WarmupIndexCollection::getSerialNum() const
{
    return std::max(_prev->getSerialNum(), _next->getSerialNum());
}


void
WarmupIndexCollection::accept(IndexSearchableVisitor &visitor) const
{
    _prev->accept(visitor);
    _next->accept(visitor);
}

FieldLengthInfo
WarmupIndexCollection::get_field_length_info(const vespalib::string& field_name) const
{
    return _next->get_field_length_info(field_name);
}

void
WarmupIndexCollection::append(uint32_t id, const IndexSearchable::SP &source)
{
    _next->append(id, source);
}

void
WarmupIndexCollection::replace(uint32_t id, const IndexSearchable::SP &source)
{
    _next->replace(id, source);
}

IndexSearchable::SP
WarmupIndexCollection::getSearchableSP(uint32_t i) const
{
    return _next->getSearchableSP(i);
}

void
WarmupIndexCollection::drainPending() {
    _pendingTasks.waitForZeroRefCount();
}

WarmupRequestContext::WarmupRequestContext() = default;
WarmupRequestContext::~WarmupRequestContext() = default;

const vespalib::eval::Value*
WarmupRequestContext::get_query_tensor(const vespalib::string&) const {
    return {};
}
WarmupTask::WarmupTask(std::unique_ptr<MatchData> md, std::shared_ptr<WarmupIndexCollection> warmup)
    : _warmup(std::move(warmup)),
      _retainGuard(_warmup->pendingTasks()),
      _matchData(std::move(md)),
      _bluePrint(),
      _requestContext()
{
}

WarmupTask::~WarmupTask() = default;

void
WarmupTask::run()
{
    if (_warmup->warmupEndTime() != vespalib::steady_time()) {
        LOG(debug, "Warming up %s", _bluePrint->asString().c_str());
        uint32_t dummy_docid_limit = 1337;
        _bluePrint->basic_plan(true, dummy_docid_limit);
        _bluePrint->fetchPostings(search::queryeval::ExecuteInfo::FULL);
        SearchIterator::UP it(_bluePrint->createSearch(*_matchData));
        it->initFullRange();
        for (uint32_t docId = it->seekFirst(1); !it->isAtEnd(); docId = it->seekNext(docId+1)) {
            if (_warmup->doUnpack()) {
                it->unpack(docId);
            }
        }
    } else {
        LOG(debug, "Warmup has finished, ignoring task.");
    }
}

}
