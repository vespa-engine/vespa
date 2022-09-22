// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <optional>

namespace search::docsummary {

/**
 * Class filtering which fields to render in a struct field.
 */
class SlimeFillerFilter {
public:
    /**
     * Iterator used to step through the sub fields of a struct field
     * to find out which parts to render.
     */
    class Iterator {
    private:
        friend class SlimeFillerFilter;
        bool _should_render;
        const SlimeFillerFilter* _next;
        explicit Iterator(bool should_render_in) noexcept;
        explicit Iterator(const SlimeFillerFilter* next) noexcept;
    public:
        Iterator check_field(vespalib::stringref field_name) const;
        bool should_render() const noexcept { return _should_render; }
    };

private:
    vespalib::hash_map<vespalib::string, std::unique_ptr<SlimeFillerFilter>> _filter;
    Iterator check_field(vespalib::stringref field_name) const;

public:
    SlimeFillerFilter();
    ~SlimeFillerFilter();

    Iterator begin() const;

    bool empty() const;

    /**
     * Add a field path (e.g 'my_field.my_subfield') that should be rendered.
     */
    SlimeFillerFilter& add(vespalib::stringref field_path);

    /**
     * Called by DocsumFilter::prepareFieldSpec() with each input field name as field_path. First component
     * is assumed to be the same as the output field name.
     */
    static void add_remaining(std::unique_ptr<SlimeFillerFilter>& filter, vespalib::stringref field_path);

    /**
     * Returns a pass-through filter iterator that renders all parts of a struct field.
     */
    static Iterator all();
};

}
