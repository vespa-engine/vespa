// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/docsumconfig.h>

namespace vespa::config::search::vsm {
namespace internal { class InternalVsmfieldsType; }
typedef const internal::InternalVsmfieldsType VsmfieldsConfig;
}
namespace vsm {

class DynamicDocsumConfig : public search::docsummary::DynamicDocsumConfig
{
public:
    using Parent = search::docsummary::DynamicDocsumConfig;
    using VsmfieldsConfig = vespa::config::search::vsm::VsmfieldsConfig;
private:
    std::shared_ptr<VsmfieldsConfig> _vsm_fields_config;
public:
    DynamicDocsumConfig(search::docsummary::IDocsumEnvironment* env, search::docsummary::DynamicDocsumWriter* writer, std::shared_ptr<VsmfieldsConfig> vsm_fields_config);
private:
    std::unique_ptr<search::docsummary::IDocsumFieldWriter>
        createFieldWriter(const string & fieldName, const string & overrideName,
                          const string & cf, bool & rc, std::shared_ptr<search::MatchingElementsFields> matching_elems_fields) override;
};

}

