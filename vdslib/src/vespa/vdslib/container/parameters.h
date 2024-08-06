// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    using KeyT = std::string_view;
    class Value : public vespalib::string
    {
    public:
      Value() = default;
      explicit Value(std::string_view s) noexcept : vespalib::string(s) { }
      explicit Value(const vespalib::string & s) noexcept : vespalib::string(s) { }
      Value(const void *v, size_t sz) noexcept : vespalib::string(static_cast<const char *>(v), sz) { }
      size_t length() const noexcept { return size() - 1; }
    };
    using ValueRef = std::string_view;
    using ParametersMap = vespalib::hash_map<vespalib::string, Value>;
private:
    ParametersMap _parameters;

    void printXml(vespalib::xml::XmlOutputStream& xos) const override;

public:
    Parameters();
    explicit Parameters(document::ByteBuffer& buffer);
    ~Parameters() override;

    Parameters(const Parameters&);
    Parameters& operator=(const Parameters&);
    Parameters(Parameters&&) noexcept;
    Parameters& operator=(Parameters&&) noexcept;

    bool operator==(const Parameters &other) const;

    size_t getSerializedSize() const;

    bool hasValue(KeyT id)                 const { return (_parameters.find(id) != _parameters.end()); }
    unsigned int size()                    const { return _parameters.size(); }
    bool lookup(KeyT id, ValueRef & v) const;
    void set(KeyT id, const void * v, size_t sz) {
        _parameters[vespalib::string(id)] = Value(v, sz);
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    void serialize(vespalib::GrowableByteBuffer& buffer) const;
    void deserialize(document::ByteBuffer& buffer);

    // Disallow
    ParametersMap::const_iterator begin() const { return _parameters.begin(); }
    ParametersMap::const_iterator end() const { return _parameters.end(); }
    /// Convenience from earlier use.
    void set(KeyT id, std::string_view value) {
        _parameters[vespalib::string(id)] = Value(value.data(), value.size());
    }
    void set(KeyT id, int32_t value);
    void set(KeyT id, int64_t value);
    void set(KeyT id, uint64_t value);
    void set(KeyT id, double value);
    std::string_view get(KeyT id, std::string_view def = "") const;


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

