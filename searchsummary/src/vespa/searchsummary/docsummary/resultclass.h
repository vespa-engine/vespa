// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type.h"
#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/searchlib/util/stringenum.h>

namespace search::docsummary {

/**
 * This struct describes a single docsum field (name and type).
 **/
struct ResConfigEntry {
    ResType          _type;
    vespalib::string _bindname;
    int              _enumValue;
};


/**
 * This class represents a specific docsum format (docsum class). It
 * contains an array of ResConfigEntry instances (config
 * entries). It also contains methods for mapping both
 * field name and field name enum value into field index.
 **/
class ResultClass
{
public:
    struct DynamicInfo
    {
        uint32_t _overrideCnt; // # fields overridden
        uint32_t _generateCnt; // # fields generated
    };

private:
    ResultClass(const ResultClass &);
    ResultClass& operator=(const ResultClass &);
    typedef vespalib::hash_map<vespalib::string, int> NameIdMap;
    typedef std::vector<ResConfigEntry> Configs;

    vespalib::string           _name;        // name of this class
    uint32_t                   _classID;     // ID of this class
    Configs                    _entries;     // config entries for this result class
    NameIdMap                  _nameMap;     // fieldname -> entry index
    util::StringEnum          &_fieldEnum;   // fieldname -> f.n. enum value [SHARED]
    std::vector<int>           _enumMap;     // fieldname enum value -> entry index
    DynamicInfo               *_dynInfo;     // fields overridden and generated
    // Whether or not summary features should be omitted when filling this summary class.
    // As default, summary features are always included.
    bool                       _omit_summary_features;

public:
    typedef std::unique_ptr<ResultClass> UP;

    /**
     * Constructor. Assign name and id to this result class. Also gain
     * ref. to shared string enum object and insert into linked list.
     *
     * @param name the name of this result class.
     * @param id the numeric id of this result class.
     * @param fieldEnum shared object used to enumerate field names.
     **/
    ResultClass(const char *name, uint32_t id, util::StringEnum & fieldEnum);

    /**
     * Destructor. Delete internal structures.
     **/
    ~ResultClass();


    /**
     * Attach dynamic field data to this result class.
     *
     * @param data pointer to dynamic field data.
     **/
    void setDynamicInfo(DynamicInfo *data) { _dynInfo = data; }


    /**
     * Obtain pointer to dynamic field data attached to this result class.
     *
     * @return pointer to dynamic field data.
     **/
    DynamicInfo *getDynamicInfo() const { return _dynInfo; }


    /**
     * Obtain the name of this result class.
     *
     * @return name of this result class.
     **/
    const char *GetClassName() const { return _name.c_str(); }


    /**
     * Obtain the numeric id of this result class.
     *
     * @return numeric id of this result class.
     **/
    uint32_t GetClassID() const { return _classID; }


    /**
     * Obtain the number of config entries (size of the
     * ResConfigEntry array) held by this result class.
     *
     * @return number of config entries held by this object.
     **/
    uint32_t GetNumEntries() const { return _entries.size(); }


    /**
     * Add a config entry to this result class. Each config entry
     * contains the name and type of a field present in the docsum blobs
     * conforming to this result class. This method will fail if the
     * field name given already has been used to name a field in this
     * result class.
     *
     * @return true(success)/false(fail)
     * @param name the name of the field to add.
     * @param type the type of the field to add.
     **/
    bool AddConfigEntry(const char *name, ResType type);


    /**
     * This method may be called to create an internal mapping from
     * field name enumerated value to field index. When building up a
     * result configuration possibly containing several result classes,
     * all field names are enumerated (across all result classes),
     * assigning a single unique integer value to each field name. This
     * is done with the StringEnum object given to the
     * constructor. This way, fastserver components that want to
     * reference a unique field name may use the enumerated value
     * instead of the string itself. NOTE: This method must be called in
     * order to use the GetIndexFromEnumValue method. NOTE2: This method
     * is called by the ResultConfig::CreateEnumMaps method; no
     * need to call it directly.
     **/
    void CreateEnumMap();


    /**
     * Obtain the field index from the field name. The field index may
     * be used to look up a config entry in this object, or to look up a
     * result entry in a GeneralResult object. NOTE: When using
     * the return value from this method to look up a result entry in a
     * GeneralResult object, make sure that the
     * GeneralResult object has this object as it's result
     * class. NOTE2: This method is called by the
     * GeneralResult::GetEntry(string) method; no need to call it
     * directly.
     *
     * @return field index or -1 if not found
     **/
    int GetIndexFromName(const char* name) const;


    /**
     * Obtain the field index from the field name enumerated value. The
     * field index may be used to look up a config entry in this object,
     * or to look up a result entry in a GeneralResult
     * object. NOTE: When using the return value from this method to
     * look up a result entry in a GeneralResult object, make sure
     * that the GeneralResult object has this object as it's
     * result class. NOTE2: This method is called by the
     * GeneralResult::GetEntryFromEnumValue method; no need to
     * call it directly. NOTE3: You need to call the CreateEnumMap
     * method before calling this one.
     *
     * @return field index or -1 if not found
     **/
    int GetIndexFromEnumValue(uint32_t value) const
    {
        return (value < _enumMap.size()) ? _enumMap[value] : -1;
    }


    /**
     * Obtain config entry by field index.
     *
     * @return config entry or NULL if not found.
     **/
    const ResConfigEntry *GetEntry(uint32_t offset) const
    {
        return (offset < _entries.size()) ? &_entries[offset] : NULL;
    }

    void set_omit_summary_features(bool value) {
        _omit_summary_features = value;
    }

    bool omit_summary_features() const {
        return _omit_summary_features;
    }
};

}
