// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/varholder.h>
#include <memory>
#include <optional>

namespace document { class DocumentTypeRepo; }
namespace search::index { class Schema; }

namespace proton {

class AttributeCollectionSpecFactory;
class DocumentDBConfig;
class DocumentSubDBReconfig;
class FastAccessFeedView;
class IAttributeWriter;
struct IReprocessingInitializer;
class ReconfigParams;

/**
 * Class used to reconfig the feed view used in a fast-access sub database
 * when the set of fast-access attributes change.
 */
class FastAccessDocSubDBConfigurer
{
public:
    using FeedViewVarHolder = vespalib::VarHolder<std::shared_ptr<FastAccessFeedView>>;

private:
    FeedViewVarHolder           &_feedView;
    vespalib::string             _subDbName;

    void reconfigureFeedView(FastAccessFeedView & curr,
                             std::shared_ptr<search::index::Schema> schema,
                             std::shared_ptr<const document::DocumentTypeRepo> repo,
                             std::shared_ptr<IAttributeWriter> attrWriter);

public:
    FastAccessDocSubDBConfigurer(FeedViewVarHolder &feedView,
                                 const vespalib::string &subDbName);
    ~FastAccessDocSubDBConfigurer();

    std::unique_ptr<DocumentSubDBReconfig>
    prepare_reconfig(const DocumentDBConfig& new_config_snapshot,
                     const AttributeCollectionSpecFactory& attr_spec_factory,
                     const ReconfigParams& reconfig_params,
                     uint32_t docid_limit,
                     std::optional<search::SerialNum> serial_num);

    std::unique_ptr<IReprocessingInitializer>
    reconfigure(const DocumentDBConfig &newConfig,
                const DocumentDBConfig &oldConfig,
                const DocumentSubDBReconfig& prepared_reconfig,
                search::SerialNum serial_num);
};

} // namespace proton

