// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type_utils.h"
#include <vespa/config-summary.h>
#include <vespa/searchlib/util/stringenum.h>

namespace search::docsummary {

class ResultClass;

/**
 * This class represents the overall result configuration. A result
 * configuration may contain multiple result classes, where each
 * result class represents a specific docsum blob format. The first n
 * (32) bits in the docsum blob defines the id of a result
 * class. The rest of the data contained in the docsum blob is then
 * defined by the sequence of config entries held by the result class
 * with the given id. Unpacking of docsum blobs is performed by first
 * extracting the result class id and then using the appropriate
 * result class to unpack the rest of the docsum fields. The
 * extraction of the class id is done by the Unpack method in this
 * class, while the unpacking of the docsum fields is done by a
 * GeneralResult object backed by a ResultClass object.
 **/
class ResultConfig
{
private:
    ResultConfig(const ResultConfig &);
    ResultConfig& operator=(const ResultConfig &);

    typedef vespalib::hash_map<vespalib::string, uint32_t> NameMap;
    typedef vespalib::hash_map<uint32_t, std::unique_ptr<ResultClass>> IdMap;
    uint32_t                    _defaultSummaryId;
    bool                        _useV8geoPositions;
    search::util::StringEnum    _fieldEnum;
    IdMap                       _classLookup;
    NameMap                     _nameLookup; // name -> class id

    void Clean();
    void Init();

public:
    bool useV8geoPositions() const { return _useV8geoPositions; }
    class iterator {
    public:
        iterator(IdMap::iterator it) : _it(it) { }
        iterator operator ++(int) { iterator tmp(_it); ++_it; return tmp; }
        iterator & operator ++() { ++_it; return *this; }
        bool operator == (const iterator & b) const { return _it == b._it; }
        bool operator != (const iterator & b) const { return _it != b._it; }
        ResultClass & operator *() { return *_it->second; }
        ResultClass * operator ->() { return _it->second.get(); }
    private:
        IdMap::iterator _it;
    };

    class const_iterator {
    public:
        const_iterator(IdMap::const_iterator it) : _it(it) { }
        const_iterator operator ++(int) { const_iterator tmp(_it); ++_it; return tmp; }
        const_iterator & operator ++() { ++_it; return *this; }
        bool operator == (const const_iterator & b) const { return _it == b._it; }
        bool operator != (const const_iterator & b) const { return _it != b._it; }
        const ResultClass & operator *() const { return *_it->second; }
        const ResultClass * operator ->() const { return _it->second.get(); }
    private:
        IdMap::const_iterator _it;
    };

    iterator begin() { return iterator(_classLookup.begin()); }
    iterator   end() { return iterator(_classLookup.end()); }
    const_iterator begin() const { return const_iterator(_classLookup.begin()); }
    const_iterator   end() const { return const_iterator(_classLookup.end()); }

    /**
     * Constructor. Create an initially empty result configuration.
     * NOTE: This method simply calls the Init method.
     **/
    ResultConfig();

    /**
     * Destructor. Delete all internal structures. NOTE: This method
     * simply calls the Clean method.
     **/
    ~ResultConfig();


    /**
     * @return value denoting an undefined class id.
     **/
    static uint32_t NoClassID() { return static_cast<uint32_t>(-1); }


    static bool IsVariableSize(ResType t) { return ResTypeUtils::IsVariableSize(t); }
    static bool IsBinaryCompatible(ResType a, ResType b) { return ResTypeUtils::IsBinaryCompatible(a, b); }
    static bool IsRuntimeCompatible(ResType a, ResType b) { return ResTypeUtils::IsRuntimeCompatible(a, b); }

    // whether last config seen wanted useV8geoPositions = true
    static bool wantedV8geoPositions();

    /**
     * @return the name of the given result field type.
     * @param resType enum value of a result field type.
     **/
    static const char *GetResTypeName(ResType type) { return ResTypeUtils::GetResTypeName(type); }

    /**
     * Discard the current configuration and start over. After this
     * method returns, the state of this object will be equal to the
     * state right after it was created. This method may call both Clean
     * and Init.
     **/
    void Reset();


    /**
     * Add a new result class to this result configuration. This will
     * create a new result class object and insert it into the lookup
     * structure. This method will fail if another class with the same
     * ID has already been added or if the given ID is illegal.
     *
     * @return newly created result class object or NULL.
     * @param name name of result class to add.
     * @param classID id of result class to add.
     **/
    ResultClass *AddResultClass(const char *name, uint32_t classID);

    /*
     * Set default result class id.
     */
    void set_default_result_class_id(uint32_t id);

    /**
     * Obtain result class from the result class id. This method is used
     * when unpacking docsum blobs.
     *
     * @return result class with the given id or NULL if not found.
     * @param classID the id of the result class to look up.
     **/
    const ResultClass *LookupResultClass(uint32_t classID) const;


    /**
     * Obtain result class id from the result class name.
     *
     * @return result class id or configured default if empty or "default".
     * @param name the name of the result class, NoClassId(-1) meaning undefined
     **/
    uint32_t LookupResultClassId(const vespalib::string &name) const;


    /**
     * Obtain the number of result classes held by this result
     * configuration.
     *
     * @return number of result classes.
     **/
    uint32_t GetNumResultClasses() const { return _classLookup.size(); }


    /**
     * Obtain the string enumeration object that holds the mapping from
     * field name to field name enumerated value.
     *
     * @return field name enumeration.
     **/
    const search::util::StringEnum & GetFieldNameEnum() const { return _fieldEnum; }


    /**
     * This method calls the CreateEnumMap on all result classes held by
     * this object. This is needed in order to look up fields by field
     * name enumerated value.
     **/
    void CreateEnumMaps();

    /**
     * Read config that has been fetched from configserver.
     *
     * @return true(success)/false(fail)
     * @param configId reference on server
     **/
    bool ReadConfig(const vespa::config::search::SummaryConfig &cfg, const char *configId);

    /**
     * Inspect a docsum blob and return the class id of the docsum
     * contained within it. This method is useful if you want to know
     * what it is before deciding whether to unpack it.
     *
     * @return docsum blob class id.
     * @param buf docsum blob.
     * @param buflen length of docsum blob.
     **/
    uint32_t GetClassID(const char *buf, uint32_t buflen);
};

}
