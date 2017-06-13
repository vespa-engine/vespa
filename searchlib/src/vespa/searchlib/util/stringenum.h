// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2001-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vector>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search {
namespace util {

/**
 * An object of this class represents an enumeration of a set of
 * strings. This is useful for mapping a set of strings into a
 * continuous range of integers.
 **/
class StringEnum
{
private:
    StringEnum(const StringEnum &);
    StringEnum& operator=(const StringEnum &);
    typedef vespalib::hash_map<vespalib::string, int> Map;

    uint32_t                          _numEntries;
    Map                               _mapping;
    mutable std::vector<const char *> _reverseMap;

    /**
     * Create a reverse mapping that enables the user to map integers
     * into strings. This method is called by the Lookup(int) method.
     **/
    void CreateReverseMapping() const;

public:

    /**
     * Create an empty string enumeration.
     **/
    StringEnum()
        : _numEntries(0),
          _mapping(),
          _reverseMap()
    {
    }

    /**
     * Destructor.
     **/
    ~StringEnum();


    /**
     * Discard all entries held by this object.
     **/
    void Clear()
    {
        _reverseMap.clear();
        _mapping.clear();
        _numEntries = 0;
    }


    /**
     * Add a string to this enumeration. Equal strings will get the same
     * enumerated value. Different string will get different enumerated
     * values. The set of values returned from multiple invocations of
     * this method will always be a contiuous range beginning at 0.
     *
     * @return the enumerated value for the given string.
     * @param str string you want to add.
     **/
    int Add(const char *str)
    {
        Map::const_iterator found(_mapping.find(str));
        if (found != _mapping.end()) {
            return found->second;
        } else {
            int value = _numEntries++;
            _mapping[str] = value;
            return value;
        }
    }


    /**
     * Obtain the enumerated value for the given string.
     *
     * @return enumerated value or -1 if not present.
     * @param str the string to look up.
     **/
    int Lookup(const char *str) const
    {
        Map::const_iterator found(_mapping.find(str));
        return (found != _mapping.end()) ? found->second : -1;
    }


    /**
     * Obtain the string for the given enumerated value.
     *
     * @return string or NULL if out of range.
     * @param value the enumerated value to look up.
     **/
    const char *Lookup(uint32_t value) const
    {
        if (value >= _numEntries)
            return NULL;

        if (_numEntries > _reverseMap.size())
            CreateReverseMapping();

        return _reverseMap[value];
    }


    /**
     * Obtain the number of entries currently present in this
     * enumeration.
     *
     * @return current number of entries.
     **/
    uint32_t GetNumEntries() const { return _numEntries; }


    /**
     * Save the enumeration currently held by this object to file.
     *
     * @return success(true)/fail(false).
     * @param filename name of save file.
     **/
    bool Save(const char *filename);


    /**
     * Load an enumeration from file. The loaded enumeration will
     * replace the one currently held by this object.
     *
     * @return success(true)/fail(false).
     * @param filename name of file to load.
     **/
    bool Load(const char *filename);
};

}
}

