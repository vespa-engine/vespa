// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "juniper_dfw_query_item.h"
#include <vespa/searchlib/fef/properties.h>

namespace juniper { class IQueryVisitor; }

namespace search::docsummary {

/*
 * Visitor used by juniper query adapter to visit properties describing
 * terms to highlight.
 */
class JuniperDFWTermVisitor : public search::fef::IPropertiesVisitor
{
public:
    juniper::IQueryVisitor *_visitor;
    JuniperDFWQueryItem _item;

    explicit JuniperDFWTermVisitor(juniper::IQueryVisitor *visitor)
        : _visitor(visitor),
          _item()
    {}
    void visitProperty(const search::fef::Property::Value &key, const search::fef::Property &values) override;
};

}
