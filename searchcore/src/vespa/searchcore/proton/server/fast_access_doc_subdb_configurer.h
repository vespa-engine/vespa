// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fast_access_feed_view.h"
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_initializer.h>
#include <vespa/vespalib/util/varholder.h>

namespace proton {

class DocumentSubDBReconfig;
class ReconfigParams;

/**
 * Class used to reconfig the feed view used in a fast-access sub database
 * when the set of fast-access attributes change.
 */
class FastAccessDocSubDBConfigurer
{
public:
    using FeedViewVarHolder = vespalib::VarHolder<FastAccessFeedView::SP>;

private:
    FeedViewVarHolder           &_feedView;
    vespalib::string             _subDbName;

    void reconfigureFeedView(FastAccessFeedView & curr,
                             search::index::Schema::SP schema,
                             std::shared_ptr<const document::DocumentTypeRepo> repo,
                             IAttributeWriter::SP attrWriter);

public:
    FastAccessDocSubDBConfigurer(FeedViewVarHolder &feedView,
                                 const vespalib::string &subDbName);
    ~FastAccessDocSubDBConfigurer();

    std::unique_ptr<DocumentSubDBReconfig>
    prepare_reconfig(const DocumentDBConfig& new_config_snapshot,
                     const DocumentDBConfig& old_config_snapshot,
                     AttributeCollectionSpec&& attr_spec,
                     const ReconfigParams& reconfig_params,
                     std::optional<search::SerialNum> serial_num);

    IReprocessingInitializer::UP reconfigure(const DocumentDBConfig &newConfig,
                                             const DocumentDBConfig &oldConfig,
                                             const DocumentSubDBReconfig& prepared_reconfig,
                                             search::SerialNum serial_num);
};

} // namespace proton

