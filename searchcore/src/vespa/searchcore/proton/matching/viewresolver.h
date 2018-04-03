// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace search::index { class Schema; }

namespace proton::matching {

/**
 * A small utility class used to resolve views into fields when
 * setting up the query tree. A view contains a set of fields. An
 * undefined view is considered empty. A view will resolve to the set
 * of fields it contains. If the view is empty, it will resolve to a
 * field with the same name as the view itself.
 **/
class ViewResolver
{
private:
    typedef std::map<vespalib::string, std::vector<vespalib::string> > Map;
    Map _map;

public:
    /**
     * Add a field to the given view. This function is public to
     * facilitate testing. Duplicate detection is not performed here,
     * so adding the same field to a view multiple times is not a good
     * idea.
     *
     * @return this object, for chaining
     * @param view the name of the view
     * @param field the name of the field
     **/
    ViewResolver &add(const vespalib::stringref &view,
                      const vespalib::stringref &field);

    /**
     * Resolve a view to obtain the set of fields it
     * contains. Undefined views are considered empty and will resolve
     * to a field with the same name as the view itself.
     *
     * @return true if the view was non-empty
     * @param view the name of the view
     * @param fields vector that will be filled out with the fields
     *               that are part of the requested view.
     **/
    bool resolve(const vespalib::stringref &view,
                 std::vector<vespalib::string> &fields) const;

    /**
     * Create a view resolver based on the field collections defined
     * in the given schema. View definitions should be completely
     * separate from how fields are combined into collections in the
     * index, but this is a good start, as view and index correlate
     * 1-to-1 in the current model.
     *
     * @return view resolver
     * @param schema index schema
     **/
    static ViewResolver createFromSchema(const search::index::Schema &schema);
};

}
