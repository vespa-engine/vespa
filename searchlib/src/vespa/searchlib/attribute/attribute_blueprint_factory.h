// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>

namespace search {

class AttributeBlueprintFactory : public queryeval::Searchable
{
public:
    // implements Searchable
    queryeval::Blueprint::UP
    createBlueprint(const queryeval::IRequestContext & requestContext,
                    const queryeval::FieldSpec &field,
                    const query::Node &term) override;
};

}  // namespace search
