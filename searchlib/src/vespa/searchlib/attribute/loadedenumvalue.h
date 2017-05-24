// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/array.h>
#include <vespa/searchlib/attribute/enumstorebase.h>

namespace search
{

namespace attribute
{

/**
 * Temporary representation of enumerated attribute loaded from enumerated
 * save file.
 */

class LoadedEnumAttribute
{
private:
    uint32_t _enum;
    uint32_t _docId;
    int32_t  _weight;

public:
    class EnumRadix
    {
    public:
        uint64_t
        operator()(const LoadedEnumAttribute &v)
        {
            return (static_cast<uint64_t>(v._enum) << 32) | v._docId;
        } 
    };

    class EnumCompare : public std::binary_function<LoadedEnumAttribute,
                                                    LoadedEnumAttribute,
                                                    bool>
    {
    public:
        bool
        operator()(const LoadedEnumAttribute &x,
                   const LoadedEnumAttribute &y) const
        {
            if (x.getEnum() != y.getEnum())
                return x.getEnum() < y.getEnum();
            return x.getDocId() < y.getDocId();
        }
    };

    LoadedEnumAttribute()
        : _enum(0),
          _docId(0),
          _weight(1)
    {
    }

    LoadedEnumAttribute(uint32_t e,
                        uint32_t docId,
                        int32_t weight)
        : _enum(e),
          _docId(docId),
          _weight(weight)
    {
    }
        
    uint32_t getEnum() const  { return _enum; }
    uint32_t getDocId() const { return _docId; }
    int32_t getWeight() const { return _weight; }
};
    
typedef vespalib::Array<LoadedEnumAttribute> LoadedEnumAttributeVector;


/**
 * Helper class used to populate temporary vector representing loaded
 * enumerated attribute with posting lists loaded from enumerated save
 * file.
 */

class SaveLoadedEnum
{
private:
    LoadedEnumAttributeVector &_loaded;
        
public:
    SaveLoadedEnum(LoadedEnumAttributeVector &loaded)
        : _loaded(loaded)
    {
    }
        
    void
    save(uint32_t e, uint32_t docId, int32_t weight)
    {
        _loaded.push_back(LoadedEnumAttribute(e, docId, weight));
    }
};
    
/**
 * Helper class used when loading non-enumerated attribute from 
 * enumerated save file.
 */

class NoSaveLoadedEnum
{
public:
    static void
    save(uint32_t e, uint32_t docId, int32_t weight)
    {
        (void) e;
        (void) docId;
        (void) weight;
    }
};

/**
 * Helper class used to populate temporary vector representing loaded
 * enumerated attribute without posting lists loaded from enumerated
 * save file.
 */

class SaveEnumHist
{
    vespalib::ArrayRef<uint32_t> _hist;

public:
    SaveEnumHist(EnumStoreBase::EnumVector &enumHist)
        : _hist(enumHist)
    {
    }

    void
    save(uint32_t e, uint32_t docId, int32_t weight)
    {
        (void) docId;
        (void) weight;
        assert(e < _hist.size());
        ++_hist[e];
    }
};

void
sortLoadedByEnum(LoadedEnumAttributeVector &loaded);

} // namespace attribute

} // namespace search

