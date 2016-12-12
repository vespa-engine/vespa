// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file idstring.h
 *
 * Contains the various URI schemes accepted in document identifiers.
 */

#pragma once

#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/vespalib/stllike/string.h>

namespace document {

/**
 * \class document::IdParseException
 * \ingroup base
 *
 * \brief Exception used to indicate failure to parse a %document identifier
 * URI.
 */
VESPA_DEFINE_EXCEPTION(IdParseException, vespalib::Exception);

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
    static IdString::UP createIdString(const vespalib::stringref & id) { return createIdString(id.c_str(), id.size()); }
    static IdString::UP createIdString(const char *id, size_t sz);

    virtual ~IdString() {}
    IdString* clone() const = 0;

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
    IdString(uint32_t maxComponents, uint32_t namespaceOffset, const vespalib::stringref & rawId);
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
        Offsets(uint32_t maxComponents, uint32_t first, const vespalib::stringref & id);
        uint16_t first() const { return _offsets[0]; }
        uint16_t operator [] (size_t i) const { return _offsets[i]; }
        size_t numComponents() const { return _numComponents; }
    private:
        uint16_t _offsets[5];
        uint32_t _numComponents;
    };
    Offsets _offsets;
    uint32_t _nssComponentId;
    vespalib::string _rawId;
};

class NullIdString : public IdString
{
public:
    NullIdString() : IdString(2, 5, "null::") { }
private:
    IdString* clone() const { return new NullIdString(); }
    virtual LocationType getLocation() const { return 0; }
    virtual Type getType() const { return NULLID; }
    virtual vespalib::stringref getNamespaceSpecific() const { return getComponent(1); }
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
class IdIdString : public IdString {
    LocationType _location;
    uint16_t     _groupOffset;
    bool         _has_number;

public:
    IdIdString(const vespalib::stringref &ns);

    virtual bool hasDocType() const { return true; }
    virtual vespalib::stringref getDocType() const { return getComponent(1); }
    virtual IdIdString* clone() const { return new IdIdString(*this); }
    virtual LocationType getLocation() const { return _location; }
    virtual bool hasNumber() const { return _has_number; }
    virtual uint64_t getNumber() const { return _location; }
    virtual bool hasGroup() const { return _groupOffset != 0; }

    virtual vespalib::stringref getGroup() const { return vespalib::stringref(getRawId().c_str() + _groupOffset, offset(3) - _groupOffset - 1); }
private:
    virtual void validate() const;
    virtual Type getType() const { return ID; }
    virtual vespalib::stringref getNamespaceSpecific() const { return getComponent(3); }
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
class DocIdString : public IdString {
public:
    DocIdString(const vespalib::stringref & ns, const vespalib::stringref & id);
    DocIdString(const vespalib::stringref & rawId);
private:
    virtual DocIdString* clone() const { return new DocIdString(*this); }
    virtual Type getType() const { return DOC; }
    virtual LocationType getLocation() const;
    virtual vespalib::stringref getNamespaceSpecific() const { return getComponent(1); }
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
class UserDocIdString : public IdString {
public:
    UserDocIdString(const vespalib::stringref & rawId);

    virtual int64_t getUserId() const { return _userId; }
    virtual bool hasNumber() const { return true; }
    uint64_t getNumber() const { return _userId; }
    virtual LocationType getLocation() const { return _userId; }

private:
    virtual UserDocIdString* clone()   const { return new UserDocIdString(*this); }
    virtual Type getType() const { return USERDOC; }
    virtual vespalib::stringref getNamespaceSpecific() const { return getComponent(2); }

    int64_t _userId;
};

/**
 * \class document::OrderDocIdString
 * \ingroup base
 * \brief Scheme for distributing documents based on a group and a parametrized ordering.
 */
class OrderDocIdString : public IdString {
public:
    OrderDocIdString(const vespalib::stringref& rawId);

    int64_t  getUserId() const { return _location; }
    uint16_t getWidthBits() const { return _widthBits; }
    uint16_t getDivisionBits() const { return _divisionBits; }
    uint64_t getOrdering() const { return _ordering; }
    std::pair<int16_t, int64_t> getGidBitsOverride() const;
    vespalib::string getSchemeName() const;
    virtual bool hasNumber() const { return true; }
    uint64_t getNumber() const { return _location; }
    virtual bool hasGroup() const { return true; }
    virtual vespalib::stringref getGroup() const { return getComponent(1); }

private:
    virtual LocationType getLocation() const { return _location; }
    virtual OrderDocIdString* clone() const { return new OrderDocIdString(*this); }
    virtual Type getType() const { return ORDERDOC; }
    virtual vespalib::stringref getNamespaceSpecific() const { return getComponent(3); }

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
    GroupDocIdString(const vespalib::stringref & rawId);
    virtual bool hasGroup() const { return true; }
    virtual vespalib::stringref getGroup() const { return getComponent(1); }
    virtual LocationType getLocation() const;

    /**
     * Extract the location for the group-specific part of a document ID.
     * i.e. `name` here must match the `group` in ID "id::foo:g=group:".
     */
    static LocationType locationFromGroupName(vespalib::stringref name);

private:
    virtual vespalib::stringref getNamespaceSpecific() const { return getComponent(2); }
    virtual GroupDocIdString* clone() const { return new GroupDocIdString(*this); }
    virtual Type getType() const { return GROUPDOC; }
};

} // document

