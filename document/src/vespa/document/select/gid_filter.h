// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/globalid.h>

namespace document::select {

class Node;

/**
 * This class allows for very quickly and cheaply filtering away metadata
 * entries that may not possibly match a document selection with a location
 * predicate, based on nothing but the GIDs in the metadata. This avoids
 * having to fetch the document IDs or whole documents themselves from
 * potentially slow storage in order to evaluate the selection in full.
 */
class GidFilter {
public:
    // GCC <= 5.3 has a bug where copying a boost::optional at certain -O
    // levels causes a erroneous compiler warning.
    // Using our own poor-man's optional for now.
    // TODO: replace with std::otional once on a C++17 stdlib.
    struct OptionalLocation {
        uint32_t _location;
        bool _valid;

        OptionalLocation() : _location(0), _valid(false) {}

        explicit OptionalLocation(uint32_t location)
            : _location(location),
              _valid(true)
        {
        }
    };
private:
    OptionalLocation _required_gid_location;

    /**
     * Lifetime of AST Node pointed to does not have to extend beyond the call
     * to this constructor.
     */
    explicit GidFilter(const Node& ast_root);
public:
    /**
     * No-op filter; everything matches always.
     */
    GidFilter()
        : _required_gid_location()
    {
    }

    /**
     * A GidFilter instance may be safely and cheaply copied. No dependencies
     * exist on the life time of the AST from which it was created.
     */
    GidFilter(const GidFilter&) = default;
    GidFilter& operator=(const GidFilter&) = default;

    /**
     * Create a filter with a location inferred from the provided selection.
     * If the selection does not contain a location predicate, the GidFilter
     * will effectively act as a no-op which assumes every document may match.
     *
     * It is safe to use the resulting GidFilter even if the lifetime of the
     * Node pointed to by ast_root does not extend beyond this call; the
     * GidFilter does not store any implicit or explicit references to it.
     */
    static GidFilter for_selection_root_node(const Node& ast_root) {
        return GidFilter(ast_root);
    }

    /**
     * Returns false iff there exists no way that a document whose ID has the
     * given GID can possibly match the selection. This currently only applies
     * if the document selection contains a location-based predicate (i.e.
     * id.user or id.group).
     *
     * As the name implies this is a probabilistic match; it's possible for
     * this function to return true even if the document selection matched
     * against the full document/documentid would return false.
     */
    bool gid_might_match_selection(const GlobalId& gid) const {
        const uint32_t gid_location = gid.getLocationSpecificBits();
        return (!_required_gid_location._valid
                || (gid_location == _required_gid_location._location));
    }
};

}
