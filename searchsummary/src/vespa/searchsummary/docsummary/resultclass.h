// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_config_entry.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/searchlib/util/stringenum.h>

namespace search::docsummary {

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
        DynamicInfo() noexcept
            : _overrideCnt(0),
              _generateCnt(0)
        {
        }
        void update_override_counts(bool generated) noexcept {
            ++_overrideCnt;
            if (generated) {
                ++_generateCnt;
            }
        }
    };

private:
    ResultClass(const ResultClass &);
    ResultClass& operator=(const ResultClass &);
    typedef vespalib::hash_map<vespalib::string, int> NameIdMap;
    typedef std::vector<ResConfigEntry> Configs;

    vespalib::string           _name;        // name of this class
    Configs                    _entries;     // config entries for this result class
    NameIdMap                  _nameMap;     // fieldname -> entry index
    DynamicInfo                _dynInfo;     // fields overridden and generated
    // Whether or not summary features should be omitted when filling this summary class.
    // As default, summary features are always included.
    bool                       _omit_summary_features;
    size_t                     _num_field_writer_states;

public:
    typedef std::unique_ptr<ResultClass> UP;

    /**
     * Constructor. Assign name and id to this result class. Also gain
     * ref. to shared string enum object and insert into linked list.
     *
     * @param name the name of this result class.
     **/
    ResultClass(const char *name);

    /**
     * Destructor. Delete internal structures.
     **/
    ~ResultClass();

    /**
     * Obtain reference to dynamic field data for this result class.
     *
     * @return reference to dynamic field data.
     **/
    DynamicInfo& getDynamicInfo() noexcept { return _dynInfo; }
    const DynamicInfo& getDynamicInfo() const noexcept { return _dynInfo; }

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
     * @param docsum_field_writer field writer for writing field
     **/
    bool AddConfigEntry(const char *name, ResType type, std::unique_ptr<DocsumFieldWriter> docsum_field_writer);
    bool AddConfigEntry(const char *name, ResType type);

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
     * Obtain config entry by field index.
     *
     * @return config entry or NULL if not found.
     **/
    const ResConfigEntry *GetEntry(uint32_t offset) const
    {
        return (offset < _entries.size()) ? &_entries[offset] : nullptr;
    }

    void set_omit_summary_features(bool value) {
        _omit_summary_features = value;
    }

    bool omit_summary_features() const {
        return _omit_summary_features;
    }

    size_t get_num_field_writer_states() const noexcept { return _num_field_writer_states; }
};

}
