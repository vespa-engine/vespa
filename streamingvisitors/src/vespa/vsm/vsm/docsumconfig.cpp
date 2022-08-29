// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumconfig.h"
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/vsm/config/config-vsmfields.h>
#include "docsum_field_writer_factory.h"

using vespa::config::search::vsm::VsmfieldsConfig;

namespace vsm {

DynamicDocsumConfig::DynamicDocsumConfig(search::docsummary::IDocsumEnvironment* env, search::docsummary::DynamicDocsumWriter* writer, std::shared_ptr<VsmfieldsConfig> vsm_fields_config)
    : Parent(env, writer),
      _vsm_fields_config(std::move(vsm_fields_config))
{
}

std::unique_ptr<search::docsummary::IDocsumFieldWriterFactory>
DynamicDocsumConfig::make_docsum_field_writer_factory()
{
    return std::make_unique<DocsumFieldWriterFactory>(getResultConfig().useV8geoPositions(), getEnvironment(), *_vsm_fields_config);
}

}
