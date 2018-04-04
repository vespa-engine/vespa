// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchable_doc_subdb_configurer.h"
#include "fast_access_feed_view.h"
#include "i_attribute_writer_factory.h"
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_initializer.h>

namespace proton {

/**
 * Class used to reconfig the feed view used in a fast-access sub database
 * when the set of fast-access attributes change.
 */
class FastAccessDocSubDBConfigurer
{
public:
    typedef vespalib::VarHolder<FastAccessFeedView::SP> FeedViewVarHolder;

private:
    FeedViewVarHolder           &_feedView;
    IAttributeWriterFactory::UP _factory;
    vespalib::string             _subDbName;

    void reconfigureFeedView(const FastAccessFeedView::SP &curr,
                             const search::index::Schema::SP &schema,
                             const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                             const IAttributeWriter::SP &attrWriter);

public:
    FastAccessDocSubDBConfigurer(FeedViewVarHolder &feedView,
                                 IAttributeWriterFactory::UP factory,
                                 const vespalib::string &subDbName);
    ~FastAccessDocSubDBConfigurer();

    IReprocessingInitializer::UP reconfigure(const DocumentDBConfig &newConfig,
                                             const DocumentDBConfig &oldConfig,
                                             const AttributeCollectionSpec &attrSpec);
};

} // namespace proton

