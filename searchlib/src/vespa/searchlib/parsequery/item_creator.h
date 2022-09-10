// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::parseitem {

/** A tag identifying the origin of a query node.
 *  Note that descendants may originate from elsewhere.
 *  Used in search::ParseItem and in Juniper.
 *  If changes necessary:
 *  NB! Append at end of list - corresponding type used in search
 *  container and updates of these two types must be synchronized.
 *  (container-search/src/main/java/com/yahoo/prelude/query/Item.java)
 */
enum class ItemCreator {
    CREA_ORIG = 0, // Original user query
    CREA_FILTER    // Automatically applied filter (no specific type)
};

}
