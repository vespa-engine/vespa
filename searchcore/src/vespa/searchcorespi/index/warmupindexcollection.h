// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchableindexcollection.h"
#include "warmupconfig.h"
#include <vespa/searchlib/attribute/attribute_blueprint_params.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <vespa/vespalib/util/retain_guard.h>

namespace searchcorespi {

class FieldTermMap;
class WarmupIndexCollection;

class IWarmupDone {
public:
    virtual ~IWarmupDone() { }
    virtual void warmupDone(std::shared_ptr<WarmupIndexCollection> current) = 0;
};
/**
 * Index collection that holds a reference to the active one and a new one that
 * is to be warmed up.
 */
class WarmupIndexCollection : public ISearchableIndexCollection,
                              public std::enable_shared_from_this<WarmupIndexCollection>
{
    using WarmupConfig = index::WarmupConfig;
public:
    using SP = std::shared_ptr<WarmupIndexCollection>;
    WarmupIndexCollection(const WarmupConfig & warmupConfig,
                          ISearchableIndexCollection::SP prev,
                          ISearchableIndexCollection::SP next,
                          IndexSearchable & warmup,
                          vespalib::Executor & executor,
                          const vespalib::Clock & clock,
                          IWarmupDone & warmupDone);
    ~WarmupIndexCollection() override;
    // Implements IIndexCollection
    const ISourceSelector &getSourceSelector() const override;
    size_t getSourceCount() const override;
    IndexSearchable &getSearchable(uint32_t i) const override;
    uint32_t getSourceId(uint32_t i) const override;

    // Implements IndexSearchable
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext, const FieldSpec &field, const Node &term) override;
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext, const FieldSpecList &fields, const Node &term) override;
    search::SearchableStats getSearchableStats() const override;
    search::SerialNum getSerialNum() const override;
    void accept(IndexSearchableVisitor &visitor) const override;

    // Implements IFieldLengthInspector
    search::index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override;

    // Implements ISearchableIndexCollection
    void append(uint32_t id, const IndexSearchable::SP &source) override;
    void replace(uint32_t id, const IndexSearchable::SP &source) override;
    IndexSearchable::SP getSearchableSP(uint32_t i) const override;
    void setSource(uint32_t docId) override;

    const ISearchableIndexCollection::SP & getNextIndexCollection() const { return _next; }
    vespalib::string toString() const override;
    bool doUnpack() const { return _warmupConfig.getUnpack(); }
    void drainPending();
    const vespalib::Clock & clock() const { return _clock; }
    vespalib::steady_time warmupEndTime() const { return _warmupEndTime; }
    vespalib::MonitoredRefCount & pendingTasks() { return _pendingTasks; }
private:
    using Task = vespalib::Executor::Task;

    void fireWarmup(Task::UP task);
    bool handledBefore(uint32_t fieldId, const Node &term);

    const WarmupConfig                 _warmupConfig;
    ISearchableIndexCollection::SP     _prev;
    ISearchableIndexCollection::SP     _next;
    IndexSearchable                  & _warmup;
    vespalib::Executor               & _executor;
    const vespalib::Clock            & _clock;
    IWarmupDone                      & _warmupDone;
    vespalib::steady_time              _warmupEndTime;
    std::mutex                         _lock;
    std::unique_ptr<FieldTermMap>      _handledTerms;
    vespalib::MonitoredRefCount        _pendingTasks;
};

}  // namespace searchcorespi
