// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "viewresolver.h"
#include <vespa/searchcommon/common/schema.h>

namespace proton::matching {

ViewResolver &
ViewResolver::add(std::string_view view,
                  std::string_view field)
{
    _map[view].push_back(field);
    return *this;
}

bool
ViewResolver::resolve(std::string_view view,
                      std::vector<vespalib::string> &fields) const
{
    auto pos = _map.find(view);
    if (pos == _map.end()) {
        fields.push_back(view);
        return false;
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
        const vespalib::string &view = f.getName();
        const std::vector<vespalib::string> &fields = f.getFields();
        for (uint32_t j = 0; j < fields.size(); ++j) {
            resolver.add(view, fields[j]);
        }
    }
    return resolver;
}

}
