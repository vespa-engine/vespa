// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/vespalib/stllike/string.h>
#include <cstdint>

namespace document {

/**
 * \class document::IdString
 * \ingroup base
 *
 * \brief Superclass for all document identifier schemes.
 */
class IdString : public vespalib::Cloneable {
public:
    typedef std::unique_ptr<IdString> UP;
    typedef vespalib::CloneablePtr<IdString> CP;
    typedef uint64_t LocationType;
    enum Type { DOC=0, USERDOC, GROUPDOC, ORDERDOC, ID, NULLID };
    static const vespalib::string & getTypeName(Type t);

    /** @throws document::IdParseException If parsing of id scheme failed. */
    static IdString::UP createIdString(vespalib::stringref id) { return createIdString(id.data(), id.size()); }
    static IdString::UP createIdString(const char *id, size_t sz);

    ~IdString() {}
    IdString* clone() const override = 0;

    virtual Type        getType() const = 0;
    vespalib::stringref getNamespace() const { return getComponent(0); }
    virtual vespalib::stringref getNamespaceSpecific() const = 0;
    virtual LocationType getLocation() const = 0;
    virtual std::pair<int16_t, int64_t> getGidBitsOverride() const { return std::pair<int16_t, int64_t>(0, 0); }
    virtual bool hasDocType() const { return false; }
    virtual vespalib::stringref getDocType() const { return ""; }
    virtual bool hasNumber() const { return false; }
    virtual uint64_t getNumber() const { return 0; }
    virtual bool hasGroup() const { return false; }
    virtual vespalib::stringref getGroup() const { return ""; }

    bool operator==(const IdString& other) const
        { return toString() == other.toString(); }

    const vespalib::string & toString() const;

protected:
    IdString(uint32_t maxComponents, uint32_t namespaceOffset, vespalib::stringref rawId);
    virtual vespalib::string getSchemeName() const;
    size_t offset(size_t index) const { return _offsets[index]; }
    size_t size(size_t index) const { return _offsets[index+1] - _offsets[index] - 1; }
    vespalib::stringref getComponent(size_t index) const { return vespalib::stringref(_rawId.c_str() + offset(index), size(index)); }
    const vespalib::string & getRawId() const { return _rawId; }
    virtual void validate() const;
    size_t getNumComponents() const { return _offsets.numComponents(); }

private:
    class Offsets {
    public:
        Offsets(uint32_t maxComponents, uint32_t first, vespalib::stringref id);
        uint16_t first() const { return _offsets[0]; }
        uint16_t operator [] (size_t i) const { return _offsets[i]; }
        size_t numComponents() const { return _numComponents; }
    private:
        uint16_t _offsets[5];
        uint32_t _numComponents;
    };
    Offsets _offsets;
    vespalib::string _rawId;
};

class NullIdString final : public IdString
{
public:
    NullIdString() : IdString(2, 5, "null::") { }
private:
    IdString* clone() const override { return new NullIdString(); }
    LocationType getLocation() const override { return 0; }
    Type getType() const override { return NULLID; }
    vespalib::stringref getNamespaceSpecific() const override { return getComponent(1); }
};

/**
 * \class document::IdIdString
 * \ingroup base
 *
 * \brief New scheme for documents with no forced distribution.
 *
 * By using this scheme, documents will be evenly distributed within VDS,
 * as the location of a doc identifier is a hash of the entire URI.
 * This scheme also contains the DocumentType.
 */
class IdIdString final : public IdString {
    LocationType _location;
    uint16_t     _groupOffset;
    bool         _has_number;

public:
    IdIdString(vespalib::stringref ns);

    bool hasDocType() const override { return true; }
    vespalib::stringref getDocType() const override { return getComponent(1); }
    IdIdString* clone() const override { return new IdIdString(*this); }
    LocationType getLocation() const override { return _location; }
    bool hasNumber() const override { return _has_number; }
    uint64_t getNumber() const override { return _location; }
    bool hasGroup() const override { return _groupOffset != 0; }
    vespalib::stringref getGroup() const override {
        return vespalib::stringref(getRawId().c_str() + _groupOffset, offset(3) - _groupOffset - 1);
    }
private:
    virtual void validate() const override;
    Type getType() const override { return ID; }
    vespalib::stringref getNamespaceSpecific() const override { return getComponent(3); }
};

/**
 * \class document::DocIdString
 * \ingroup base
 *
 * \brief Scheme for documents with no forced distribution.
 *
 * By using this scheme, documents will be evenly distributed within VDS,
 * as the location of a doc identifier is a hash of the entire URI.
 */
class DocIdString final : public IdString {
public:
    DocIdString(vespalib::stringref ns, vespalib::stringref id);
    DocIdString(vespalib::stringref rawId);
private:
    DocIdString* clone() const override { return new DocIdString(*this); }
    Type getType() const override { return DOC; }
    LocationType getLocation() const override;
    vespalib::stringref getNamespaceSpecific() const override { return getComponent(1); }
};

/**
 * \class document::UserDocIdString
 * \ingroup base
 *
 * \brief Scheme for distributing documents based on a 64 bit number.
 *
 * The location of a userdoc identifier is the 64 bit id given. The
 * name "userdoc" is purely syntactical; Vespa does not care what the source
 * of the number is.
 */
class UserDocIdString final : public IdString {
public:
    UserDocIdString(vespalib::stringref rawId);

    virtual int64_t getUserId() const { return _userId; }
    bool hasNumber() const override { return true; }
    uint64_t getNumber() const override { return _userId; }
    LocationType getLocation() const override { return _userId; }

private:
    UserDocIdString* clone() const override { return new UserDocIdString(*this); }
    Type getType() const override { return USERDOC; }
    vespalib::stringref getNamespaceSpecific() const override { return getComponent(2); }

    int64_t _userId;
};

/**
 * \class document::OrderDocIdString
 * \ingroup base
 * \brief Scheme for distributing documents based on a group and a parametrized ordering.
 */
class OrderDocIdString final : public IdString {
public:
    OrderDocIdString(vespalib::stringref rawId);

    int64_t  getUserId() const { return _location; }
    uint16_t getWidthBits() const { return _widthBits; }
    uint16_t getDivisionBits() const { return _divisionBits; }
    uint64_t getOrdering() const { return _ordering; }
    std::pair<int16_t, int64_t> getGidBitsOverride() const override;
    vespalib::string getSchemeName() const override;
    bool hasNumber() const override { return true; }
    uint64_t getNumber() const override { return _location; }
    bool hasGroup() const override { return true; }
    vespalib::stringref getGroup() const override { return getComponent(1); }

private:
    LocationType getLocation() const override { return _location; }
    OrderDocIdString* clone() const override { return new OrderDocIdString(*this); }
    Type getType() const override { return ORDERDOC; }
    vespalib::stringref getNamespaceSpecific() const override { return getComponent(3); }

    LocationType _location;
    uint16_t _widthBits;
    uint16_t _divisionBits;
    uint64_t _ordering;
};

/**
 * \class document::GroupDocIdString
 * \ingroup base
 *
 * \brief Scheme for distributing documents based on a group string.
 *
 * The location of a groupdoc identifier is a hash of the group string.
 */
class GroupDocIdString : public IdString {
public:
    GroupDocIdString(vespalib::stringref rawId);
    bool hasGroup() const override { return true; }
    vespalib::stringref getGroup() const override { return getComponent(1); }
    LocationType getLocation() const override;

    /**
     * Extract the location for the group-specific part of a document ID.
     * i.e. `name` here must match the `group` in ID "id::foo:g=group:".
     */
    static LocationType locationFromGroupName(vespalib::stringref name);

private:
    vespalib::stringref getNamespaceSpecific() const override { return getComponent(2); }
    GroupDocIdString* clone() const override { return new GroupDocIdString(*this); }
    Type getType() const override { return GROUPDOC; }
};

} // document

