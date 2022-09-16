// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <optional>

namespace search::docsummary {

/*
 * Class filtering which fields to render in a struct field.
 */
class SlimeFillerFilter {
    vespalib::hash_map<vespalib::string, std::unique_ptr<SlimeFillerFilter>> _filter;
    std::optional<const SlimeFillerFilter*> get_filter(vespalib::stringref field_name) const;
public:
    SlimeFillerFilter();
    ~SlimeFillerFilter();
    /*
     * If field is blocked by the filter then the return value is not set,
     * otherwise it is set to the filter for the next level.
     */
    static std::optional<const SlimeFillerFilter*> get_filter(const SlimeFillerFilter* filter, vespalib::stringref field_name);
    bool empty() const;
    SlimeFillerFilter& add(vespalib::stringref field_path);
};

}
