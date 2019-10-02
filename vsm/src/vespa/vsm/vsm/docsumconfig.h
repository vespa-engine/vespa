// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchsummary/docsummary/docsumconfig.h>

namespace vsm {

class DynamicDocsumConfig : public search::docsummary::DynamicDocsumConfig
{
public:
    using Parent = search::docsummary::DynamicDocsumConfig;
    using Parent::Parent;
private:
    std::unique_ptr<search::docsummary::IDocsumFieldWriter>
        createFieldWriter(const string & fieldName, const string & overrideName,
                          const string & cf, bool & rc, std::shared_ptr<search::StructFieldMapper> struct_field_mapper) override;
};

}

