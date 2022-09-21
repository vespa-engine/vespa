// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>

namespace vespa::config::search::internal {
    class InternalSummaryType;
}
namespace search::docsummary {

class IDocsumFieldWriterFactory;
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
    using NameMap = vespalib::hash_map<vespalib::string, uint32_t>;
    using IdMap = vespalib::hash_map<uint32_t, std::unique_ptr<ResultClass>>;
    uint32_t                    _defaultSummaryId;
    IdMap                       _classLookup;
    NameMap                     _nameLookup; // name -> class id

    void Clean();

public:
    using SummaryConfig = const vespa::config::search::internal::InternalSummaryType;
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

    iterator begin() { return { _classLookup.begin() }; }
    iterator   end() { return { _classLookup.end() }; }
    const_iterator begin() const { return { _classLookup.begin() }; }
    const_iterator   end() const { return { _classLookup.end() }; }

    /**
     * Constructor. Create an initially empty result configuration.
     * NOTE: This method simply calls the Init method.
     **/
    ResultConfig();
    ResultConfig(const ResultConfig &) = delete;
    ResultConfig& operator=(const ResultConfig &) = delete;

    /**
     * Destructor. Delete all internal structures. NOTE: This method
     * simply calls the Clean method.
     **/
    ~ResultConfig();


    /**
     * @return value denoting an undefined class id.
     **/
    static uint32_t noClassID() { return static_cast<uint32_t>(-1); }

    // whether last config seen wanted useV8geoPositions = true
    static bool wantedV8geoPositions();

    // This function should only be called by unit tests.
    static void set_wanted_v8_geo_positions(bool value);

    /**
     * Discard the current configuration and start over. After this
     * method returns, the state of this object will be equal to the
     * state right after it was created. This method may call both Clean
     * and Init.
     **/
    void reset();


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
    ResultClass *addResultClass(const char *name, uint32_t classID);

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
    const ResultClass *lookupResultClass(uint32_t classID) const;


    /**
     * Obtain result class id from the result class name.
     *
     * @return result class id or configured default if empty or "default".
     * @param name the name of the result class, NoClassId(-1) meaning undefined
     **/
    uint32_t lookupResultClassId(const vespalib::string &name) const;

    /**
     * Read config that has been fetched from configserver.
     *
     * @return true(success)/false(fail)
     * @param configId reference on server
     **/
    bool readConfig(const SummaryConfig &cfg, const char *configId, IDocsumFieldWriterFactory& docsum_field_writer_factory);
};

}
