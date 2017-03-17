// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>

using namespace search;
using namespace search::index;
using namespace vespalib;
using namespace config;

namespace searchcorespi {
class IndexManager : public searchcorespi::IIndexManager
{
public:

    typedef search::SerialNum SerialNum;
    typedef search::index::Schema Schema;
    typedef document::Document Document;
    using OnWriteDoneType =
        const std::shared_ptr<search::IDestructorCallback> &;
    virtual void putDocument(uint32_t, const Document &, SerialNum) override { }
    virtual void removeDocument(uint32_t, SerialNum) override { }
    virtual void commit(SerialNum, OnWriteDoneType) override { }
    virtual void heartBeat(SerialNum ) {}
    virtual SerialNum getCurrentSerialNum() const { return 0; }
    virtual SerialNum getFlushedSerialNum() const { return 0; }
    virtual IndexSearchable::SP getSearchable() const {
        IndexSearchable::SP s;
        return s;
    }
    virtual SearchableStats getSearchableStats() const {
        SearchableStats s;
        return s;
    }
    virtual searchcorespi::IFlushTarget::List getFlushTargets() {
        searchcorespi::IFlushTarget::List l;
        return l;
    }
    virtual void setSchema(const Schema &) override { }
    virtual void wipeHistory(SerialNum) override { }
};

class IndexManagerFactory : public searchcorespi::IIndexManagerFactory
{
public:
    virtual IIndexManager::UP createIndexManager(const IndexManagerConfig &managerCfg,
                                                 const index::IndexMaintainerConfig &maintainerConfig,
                                                 const index::IndexMaintainerContext &maintainerContext);

    virtual ConfigKeySet getConfigKeys(const string &configId,
                                       const Schema &schema,
                                       const ConfigInstance &rootConfig);
};

IIndexManager::UP
IndexManagerFactory::createIndexManager(const IndexManagerConfig &,
                                        const index::IndexMaintainerConfig &,
                                        const index::IndexMaintainerContext &)
{
    return IIndexManager::UP(new IndexManager());
}

ConfigKeySet
IndexManagerFactory::getConfigKeys(const string &,
                                   const Schema &,
                                   const ConfigInstance &)
{
    ConfigKeySet keys;
    return keys;
}

}

searchcorespi::IIndexManagerFactory *
createIndexManagerFactory()
{
    return new searchcorespi::IndexManagerFactory();
}

