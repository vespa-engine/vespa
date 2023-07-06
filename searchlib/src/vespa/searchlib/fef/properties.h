// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vector>

namespace search::fef {

class Properties;

//-----------------------------------------------------------------------------

/**
 * This object represents the result of a lookup in a @ref Properties
 * object. This class is also used for property visitation. It
 * contains all values associated with the key used for lookup. The
 * values are accessible in the order in which they were originally
 * added. This object is only valid until the @ref Properties object
 * it was obtained from is changed or deleted.
 **/
class Property
{
public:
    using Value = vespalib::string;
    using Values = std::vector<Value>;
private:
    friend class Properties;

    static const Value     _emptyValue;
    static const Values    _emptyValues;
    const Values          *_values;

    /**
     * Create a new property using the given value vector.
     *
     * @param values the values for this property
     **/
    Property(const Values &values) noexcept : _values(&values) { }

public:
    /**
     * Create a property that represents the result of a lookup that
     * did not find anything. This method may be used to allocate an
     * object on the stack in the application, and will also be used
     * by the @ref Properties class when a lookup gives no results.
     **/
    Property() noexcept : _values(&_emptyValues) { }

    /**
     * Check if we found what we were looking for or not.
     *
     * @return true if the key we looked up had at least one value
     **/
    bool found() const noexcept {
        return !(*_values).empty();
    }

    /**
     * Get the first value assigned to the looked up key. This method
     * will return an empty string if no values were found.
     *
     * @return first value for the looked up key, or ""
     **/
    const Value &get() const noexcept {
        if ((*_values).empty()) {
            return _emptyValue;
        }
        return (*_values)[0];
    }

    /**
     * Get the first value assigned to the looked up key. This method
     * will return the specified fallback string if no values were
     * found.
     *
     * @return first value for the looked up key, or fallBack
     * @param fallBack value to return if no values were found
     **/
    const Value & get(const Value &fallBack) const noexcept {
        if ((*_values).empty()) {
            return fallBack;
        }
        return (*_values)[0];
    }

    /**
     * The number of values found for the looked up key.
     *
     * @return number of values for this property
     **/
    uint32_t size() const noexcept { return (*_values).size(); }

    /**
     * Obtain a specific value for the looked up key.
     *
     * @return the requested value, or "" if idx was out of bounds
     * @param idx the index of the value we want to access
     **/
    const Value &getAt(uint32_t idx) const noexcept;
};

//-----------------------------------------------------------------------------

/**
 * This interface is implemented by objects that want to visit all
 * properties contained in a Properties object.
 **/
class IPropertiesVisitor
{
public:
    /**
     * Visit a single key and all its values. Keys are visited in
     * sorting order according to the less operator of the string
     * class. The values are wrapped in a Property object that is
     * equivalent to the object that would be returned if the key had
     * been used as parameter to the lookup method in the Properties
     * object.
     *
     * @param key the key
     * @param values the values
     **/
    virtual void visitProperty(const Property::Value &key,
                               const Property &values) = 0;

    /**
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~IPropertiesVisitor() = default;
};

//-----------------------------------------------------------------------------

/**
 * A simple wrapper for a set of key/value pairs. Each key may be
 * added multiple times, resulting in multiple values for a single
 * key. When data is imported from one object to another, the set of
 * values for common keys are totally replaced.
 **/
class Properties
{
private:
    using Key = vespalib::string;
    using Value =  Property::Values;
    using Map = vespalib::hash_map<Key, Value, vespalib::hash<Key>,
                                   std::equal_to<>, vespalib::hashtable_base::and_modulator>;

    uint32_t _numValues;
    Map      _data;

    /**
     * Calculate a hash code from raw data.
     *
     * @return hash code
     * @param buf data pointer
     * @param len data length
     **/
    static uint32_t rawHash(const void *buf, uint32_t len) noexcept;

public:
    using UP = std::unique_ptr<Properties>;

    /**
     * Create an empty properties object.
     **/
    Properties() noexcept;
    Properties(Properties &&) noexcept = default;
    Properties & operator=(Properties &&) noexcept = default;
    Properties(const Properties &);
    Properties & operator=(const Properties &);

    /**
     * The destructor asserts that key/value counts look sane before
     * deleting the internal data.
     **/
    ~Properties();

    /**
     * Add a value to a key. If the key is an empty string, the value
     * will be ignored.
     *
     * @return this object, for chaining
     * @param key the key
     * @param value the value
     **/
    Properties &add(vespalib::stringref key, vespalib::stringref value);

    /**
     * Obtain the number of values for a given key.
     *
     * @return number of values for the given key
     * @param key the key
     **/
    uint32_t count(vespalib::stringref key) const noexcept;

    /**
     * Remove all values for the given key.
     *
     * @return this object, for chaining
     * @param key the key
     **/
    Properties &remove(vespalib::stringref key);

    /**
     * Import all key/value pairs from src into this object. All
     * values stored in this object for keys present in src will be
     * removed during this operation.
     *
     * @return this object, for chaining
     * @param src where to import from
     **/
    Properties &import(const Properties &src);

    /**
     * Remove all key/value pairs from this object, making it
     * equivalent with a freshly created object. It is relatively
     * cheap to clear an already empty object.
     *
     * @return this object, for chaining
     **/
    Properties &clear();

    /**
     * Obtain the total number of keys stored in this object.
     *
     * @return number of keys
     **/
    uint32_t numKeys() const noexcept { return _data.size(); }

    /**
     * Obtain the total number of values stored in this object.
     *
     * @return number of values
     **/
    uint32_t numValues() const noexcept { return _numValues; }

    /**
     * Check if rhs contains the same key/value pairs as this
     * object. If a key has multiple values, they need to be in the
     * same order to match.
     *
     * @return true if we are equal to rhs
     **/
    bool operator==(const Properties &rhs) const noexcept;

    /**
     * Calculate a hash code for this object
     *
     * @return hash code for this object
     **/
    uint32_t hashCode() const noexcept;

    /**
     * Visit all key/value pairs
     *
     * @param visitor the object being notified of all key/value pairs
     **/
    void visitProperties(IPropertiesVisitor &visitor) const;

    /**
     * Visit all key/value pairs inside a namespace. The namespace
     * itself will be stripped from the keys that are visited.
     *
     * @param ns the namespace to visit
     * @param visitor the object being notified of key/value pairs inside the namespace
     **/
    void visitNamespace(vespalib::stringref ns,
                        IPropertiesVisitor &visitor) const;

    /**
     * Look up a key in this object. An empty key will result in an
     * empty property.
     *
     * @return object encapsulating lookup result
     * @param key the key to look up
     **/
    Property lookup(vespalib::stringref key) const noexcept;

    /**
     * Look up a key inside a namespace using the proposed namespace
     * syntax. When using namespaces, the actual key is generated by
     * concatenating all namespaces and the key, inserting a '.'
     * between elements. An empty key and/or namespace will result in
     * an empty property.
     *
     * @return object encapsulating lookup result
     * @param namespace1 the namespace
     * @param key the key to look up
     **/
    Property lookup(vespalib::stringref namespace1,
                    vespalib::stringref key) const noexcept;

    /**
     * Look up a key inside a namespace using the proposed namespace
     * syntax. When using namespaces, the actual key is generated by
     * concatenating all namespaces and the key, inserting a '.'
     * between elements. An empty key and/or namespace will result in
     * an empty property.
     *
     * @return object encapsulating lookup result
     * @param namespace the first namespace
     * @param namespace the second namespace
     * @param key the key to look up
     **/
    Property lookup(vespalib::stringref namespace1,
                    vespalib::stringref namespace2,
                    vespalib::stringref key) const noexcept;

    /**
     * Look up a key inside a namespace using the proposed namespace
     * syntax. When using namespaces, the actual key is generated by
     * concatenating all namespaces and the key, inserting a '.'
     * between elements. An empty key and/or namespace will result in
     * an empty property.
     *
     * @return object encapsulating lookup result
     * @param namespace the first namespace
     * @param namespace the second namespace
     * @param namespace the third namespace
     * @param key the key to look up
     **/
    Property lookup(vespalib::stringref namespace1,
                    vespalib::stringref namespace2,
                    vespalib::stringref namespace3,
                    vespalib::stringref key) const noexcept;

    void swap(Properties & rhs) noexcept ;
};

inline void
swap(Properties & a, Properties & b) noexcept
{
    a.swap(b);
}


}
