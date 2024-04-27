// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "item_creator.h"
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/query/query_normalization.h>
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
        It is important that these defines match those in container-search/src/main/java/com/yahoo/prelude/query/Item.java */
    enum ItemType : uint8_t {
        ITEM_OR                    =   0,
        ITEM_AND                   =   1,
        ITEM_NOT                   =   2,
        ITEM_RANK                  =   3,
        ITEM_TERM                  =   4,
        ITEM_NUMTERM               =   5,
        ITEM_PHRASE                =   6,
        ITEM_MULTI_TERM            =   7,
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
        ITEM_GEO_LOCATION_TERM     =   27,
        ITEM_TRUE                  =   28,
        ITEM_FALSE                 =   29,
        ITEM_FUZZY                 =   30,
        ITEM_STRING_IN             =   31,
        ITEM_NUMERIC_IN            =   32,
        ITEM_UNDEF                 =   33,
    };

    /*
     * Mask for item type. 5 bits item type, 3 bits item features.
     */
    static constexpr uint8_t item_type_mask = 31;
    /*
     * Value encoded as item type in original serialization to indicate
     * that an additional byte is needed for item type.
     */
    static constexpr uint8_t item_type_extension_mark = 31;

    /** A tag identifying the origin of this query node.
     */
    using ItemCreator = parseitem::ItemCreator;

    enum ItemFeatures {
        IF_WEIGHT         = 0x20, // item has rank weight
        IF_UNIQUEID       = 0x40, // item has unique id
        IF_FLAGS          = 0x80, // item has extra flags
    };

    enum ItemFlags {
        IFLAG_NORANK         = 0x00000001, // this term should not be ranked (not exposed to rank framework)
        IFLAG_SPECIALTOKEN   = 0x00000002,
        IFLAG_NOPOSITIONDATA = 0x00000004, // we should not use position data when ranking this term
        IFLAG_FILTER         = 0x00000008, // see GetCreator(flags) below
        IFLAG_PREFIX_MATCH   = 0x00000010
    };

    /** Extra information on each item (creator id) coded in bit 3 of flags */
    static inline ItemCreator GetCreator(uint8_t flags) {
        return static_cast<ItemCreator>((flags >> 3) & 0x01);
    }

    static inline bool GetFeature(uint8_t type, uint8_t feature) {
        return ((type & feature) != 0);
    }

    static inline bool GetFeature_Weight(uint8_t type) {
        return GetFeature(type, IF_WEIGHT);
    }

    static inline bool getFeature_UniqueId(uint8_t type) {
        return GetFeature(type, IF_UNIQUEID);
    }
    static inline bool getFeature_Flags(uint8_t type) {
        return GetFeature(type, IF_FLAGS);
    }
    static TermType toTermType(ItemType itemType) noexcept {
        switch (itemType) {
            case ParseItem::ITEM_REGEXP: return TermType::REGEXP;
            case ParseItem::ITEM_PREFIXTERM: return TermType::PREFIXTERM;
            case ParseItem::ITEM_SUBSTRINGTERM: return TermType::SUBSTRINGTERM;
            case ParseItem::ITEM_EXACTSTRINGTERM: return TermType::EXACTSTRINGTERM;
            case ParseItem::ITEM_SUFFIXTERM: return TermType::SUFFIXTERM;
            case ParseItem::ITEM_FUZZY: return TermType::FUZZYTERM;
            case ParseItem::ITEM_GEO_LOCATION_TERM: return TermType::GEO_LOCATION;
            case ParseItem::ITEM_NEAREST_NEIGHBOR: return TermType::NEAREST_NEIGHBOR;
            default: return TermType::WORD;
        }
    }
};

} // namespace search
