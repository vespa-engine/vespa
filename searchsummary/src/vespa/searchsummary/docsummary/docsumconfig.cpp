// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumconfig.h"
#include "docsum_field_writer_factory.h"
#include "docsumwriter.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search::docsummary {

using vespalib::IllegalArgumentException;
using vespalib::make_string;

const ResultConfig &
DynamicDocsumConfig::getResultConfig() const {
    return *_writer->GetResultConfig();
}

std::unique_ptr<IDocsumFieldWriterFactory>
DynamicDocsumConfig::make_docsum_field_writer_factory()
{
    return std::make_unique<DocsumFieldWriterFactory>(getResultConfig().useV8geoPositions(), getEnvironment());
}

void
DynamicDocsumConfig::configure(const vespa::config::search::SummarymapConfig &cfg)
{
    std::vector<string> strCfg;
    auto docsum_field_writer_factory = make_docsum_field_writer_factory();
    for (const auto & o : cfg.override) {
        bool rc(false);
        auto fieldWriter = docsum_field_writer_factory->create_docsum_field_writer(o.field, o.command, o.arguments, rc);
        if (rc && fieldWriter) {
            rc = _writer->Override(o.field.c_str(), std::move(fieldWriter)); // OBJECT HAND-OVER
        }
        if (!rc) {
            throw IllegalArgumentException(o.command + " override operation failed during initialization");
        }
    }
}

}
