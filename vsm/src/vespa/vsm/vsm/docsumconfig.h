// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

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
                          const string & cf, bool & rc) override;
};

}

