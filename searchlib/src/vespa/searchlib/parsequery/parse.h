// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {

/**
 * Items on a simple query stack.
 *
 * An object of this class represents a single item
 * on the simple query stack. It has a type, which corresponds
 * to the different query stack execution operations. It also
 * provides an arity, and the string values indexName and term, to
 * accomodate the different needs of the operations.
 * It also includes a mechanism for making singly linked lists
 * with sub-lists. This is used during the parsing, and also
 * when constructing the simple query stack.
 */
class ParseItem
{
public:
    /** The type of the item is from this set of values.
        It is important that these defines match those in prelude/source/com/yahoo/prelude/query/Item.java */
    enum ItemType {
        ITEM_OR                    =   0,
        ITEM_AND                   =   1,
        ITEM_NOT                   =   2,
        ITEM_RANK                  =   3,
        ITEM_TERM                  =   4,
        ITEM_NUMTERM               =   5,
        ITEM_PHRASE                =   6,
        /* removed: ITEM_PAREN     =   7, */
        ITEM_PREFIXTERM            =   8,
        ITEM_SUBSTRINGTERM         =   9,
        ITEM_ANY                   =   10,
        ITEM_NEAR                  =   11,
        ITEM_ONEAR                 =   12,
        ITEM_SUFFIXTERM            =   13,
        ITEM_EQUIV                 =   14,
        ITEM_WEIGHTED_SET          =   15,
        ITEM_WEAK_AND              =   16,
        ITEM_EXACTSTRINGTERM       =   17,
        ITEM_SAME_ELEMENT          =   18,
        ITEM_PURE_WEIGHTED_STRING  =   19,
        ITEM_PURE_WEIGHTED_LONG    =   20,
        ITEM_DOT_PRODUCT           =   21,
        ITEM_WAND                  =   22,
        ITEM_PREDICATE_QUERY       =   23,
        ITEM_REGEXP                =   24,
        ITEM_WORD_ALTERNATIVES     =   25,
        ITEM_NEAREST_NEIGHBOR      =   26,
        ITEM_GEO_LOCATION_TERM         =   27,
        ITEM_MAX                   =   28,  // Indicates how long tables must be.
        ITEM_UNDEF                 =   31,
    };

    /** A tag identifying the origin of this query node.
     *  Note that descendants may origin from elsewhere.
     *  If changes necessary:
     *  NB! Append at end of list - corresponding type
     *  used in Juniper and updates of these two types must be synchronized.
     *  (juniper/src/query.h)
     */
    enum ItemCreator {
        CREA_ORIG = 0  // Original user query
    };

    enum ItemFeatures {
        IF_WEIGHT         = 0x20, // item has rank weight
        IF_UNIQUEID       = 0x40, // item has unique id
        IF_FLAGS          = 0x80, // item has extra flags
    };

    enum ItemFlags {
        IFLAG_NORANK         = 0x00000001, // this term should not be ranked (not exposed to rank framework)
        IFLAG_SPECIALTOKEN   = 0x00000002,
        IFLAG_NOPOSITIONDATA = 0x00000004, // we should not use position data when ranking this term
    };

    /** Extra information on each item (creator id) coded in bits 12-19 of _type */
    static inline ItemCreator GetCreator(uint8_t type) { return static_cast<ItemCreator>((type >> 3) & 0x01); }
    /** The old item type now uses only the lower 12 bits in a backward compatible way) */
    static inline ItemType GetType(uint8_t type) { return static_cast<ItemType>(type & 0x1F); }

    static inline bool GetFeature(uint8_t type, uint8_t feature)
    { return ((type & feature) != 0); }

    static inline bool GetFeature_Weight(uint8_t type)
    { return GetFeature(type, IF_WEIGHT); }

    static inline bool getFeature_UniqueId(uint8_t type)
    { return GetFeature(type, IF_UNIQUEID); }

    static inline bool getFeature_Flags(uint8_t type)
    { return GetFeature(type, IF_FLAGS); }
};

} // namespace search
