// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/vespalib/stllike/hash_map.h>

namespace search::util {

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
    StringEnum();

    /**
     * Destructor.
     **/
    ~StringEnum();

    /**
     * Discard all entries held by this object.
     **/
    void Clear();

    /**
     * Add a string to this enumeration. Equal strings will get the same
     * enumerated value. Different string will get different enumerated
     * values. The set of values returned from multiple invocations of
     * this method will always be a contiuous range beginning at 0.
     *
     * @return the enumerated value for the given string.
     * @param str string you want to add.
     **/
    int Add(const char *str);

    /**
     * Obtain the enumerated value for the given string.
     *
     * @return enumerated value or -1 if not present.
     * @param str the string to look up.
     **/
    int Lookup(const char *str) const;

    /**
     * Obtain the string for the given enumerated value.
     *
     * @return string or NULL if out of range.
     * @param value the enumerated value to look up.
     **/
    const char *Lookup(uint32_t value) const;

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
