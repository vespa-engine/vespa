// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::Parameters
 * @ingroup messageapi
 *
 * @brief A serializable way of setting parameters.
 *
 * Utility class for passing sets of name-value parameter pairs around.
 *
 * @author Fledsbo, Håkon Humberset
 * @date 2004-03-24
 * @version $Id$
 */

#pragma once

#include <vespa/vespalib/util/xmlserializable.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace vespalib { class GrowableByteBuffer; }
namespace document { class ByteBuffer; }

namespace vdslib {

class Parameters : public vespalib::xml::XmlSerializable {
public:
    typedef vespalib::stringref KeyT;
    class Value : public vespalib::string
    {
    public:
      Value() { }
      Value(vespalib::stringref s) : vespalib::string(s) { }
      Value(const vespalib::string & s) : vespalib::string(s) { }
      Value(const void *v, size_t sz) : vespalib::string(v, sz) { }
      size_t length() const  { return size() - 1; }
    };
    typedef vespalib::stringref ValueRef;
    typedef vespalib::hash_map<vespalib::string, Value> ParametersMap;
private:
    ParametersMap _parameters;

    void printXml(vespalib::xml::XmlOutputStream& xos) const override;

public:
    Parameters();
    Parameters(document::ByteBuffer& buffer);
    ~Parameters();

    bool operator==(const Parameters &other) const;

    size_t getSerializedSize() const;

    bool hasValue(KeyT id)                 const { return (_parameters.find(id) != _parameters.end()); }
    unsigned int size()                    const { return _parameters.size(); }
    bool lookup(KeyT id, ValueRef & v) const;
    void set(KeyT id, const void * v, size_t sz) { _parameters[id] = Value(v, sz); }

    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    void serialize(vespalib::GrowableByteBuffer& buffer) const;
    void deserialize(document::ByteBuffer& buffer);

    // Disallow
    ParametersMap::const_iterator begin() const { return _parameters.begin(); }
    ParametersMap::const_iterator end() const { return _parameters.end(); }
    /// Convenience from earlier use.
    void set(KeyT id, vespalib::stringref value) { _parameters[id] = Value(value.data(), value.size()); }
    vespalib::stringref get(KeyT id, vespalib::stringref def = "") const;
    /**
     * Set the value identified by the id given. This requires the type to be
     * convertible by stringstreams.
     *
     * @param id The value to get.
     * @param t The value to save. Will be converted to a string.
     */
    template<typename T>
    void set(KeyT id, T t);

    /**
     * Get the value identified by the id given, as the same type as the default
     * value given. This requires the type to be convertible by stringstreams.
     *
     * @param id The value to get.
     * @param def The value to return if the value does not exist.
     * @return The value represented as the same type as the default given, or
     *         the default itself if value did not exist.
     */
    template<typename T>
    T get(KeyT id, T def) const;

    vespalib::string toString() const;
};

} // vdslib

