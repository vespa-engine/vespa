// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "viewresolver.h"
#include <vespa/searchcommon/common/schema.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.viewresolver");

namespace proton::matching {

ViewResolver &
ViewResolver::add(const std::string & view, std::string_view field)
{
    _map[view].emplace_back(field);
    LOG(debug, "ViewResolver[%p] add '%s' -> '%s'", this, view.data(), field.data());
    return *this;
}

bool
ViewResolver::resolve(std::string_view view, std::vector<std::string> &fields) const
{
    LOG(debug, "ViewResolver[%p] resolve %s", this, view.data());
    auto pos = _map.find(view);
    if (pos == _map.end()) {
        // special case for fixed resolver: always return same list (from parent node)
        pos = _map.find("");
        if (pos == _map.end()) {
            LOG(debug, "no view->fields found for %s\n", view.data());
            fields.emplace_back(view);
            return false;
        }
        LOG(debug, "using fixed list (size %zd) for %s", pos->second.size(), view.data());
    }
    fields = pos->second;
    return true;
}

ViewResolver
ViewResolver::createFromSchema(const search::index::Schema &schema)
{
    ViewResolver resolver;
    for (uint32_t i = 0; i < schema.getNumFieldSets(); ++i) {
        const search::index::Schema::FieldSet &f = schema.getFieldSet(i);
        const std::string &view = f.getName();
        const std::vector<std::string> &fields = f.getFields();
        for (const auto & field : fields) {
            resolver.add(view, field);
        }
    }
    return resolver;
}

}
