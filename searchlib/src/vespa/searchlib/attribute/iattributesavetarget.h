// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/databuffer.h>
#include <stdint.h>
#include "iattributefilewriter.h"

namespace search {

/**
 * Interface used for saving an attribute vector.
 **/
class IAttributeSaveTarget {
public:
    /**
     * Config class used by actual saveTarget implementations.
     **/
    class Config {
    private:
        vespalib::string _fileName;
        vespalib::string _basicType;
        vespalib::string _collectionType;
        vespalib::string _tensorType;
        bool        _hasMultiValue;
        bool        _hasWeightedSetType;
        bool        _enumerated;
        uint32_t    _numDocs;
        uint32_t    _fixedWidth;
        uint64_t    _uniqueValueCount;
        uint64_t    _totalValueCount;
        uint64_t    _createSerialNum;
        uint32_t    _version;
    public:
        Config()
            : _fileName(""),
              _basicType(""),
              _collectionType(""),
              _hasMultiValue(false),
              _hasWeightedSetType(false),
              _enumerated(false),
              _numDocs(0),
              _fixedWidth(0),
              _uniqueValueCount(0),
              _totalValueCount(0),
              _createSerialNum(0u),
              _version(0)
        {
        }

        Config(const vespalib::string &fileName,
               const vespalib::string &basicType,
               const vespalib::string &collectionType,
               const vespalib::string &tensorType,
               bool multiValue, bool weightedSetType,
               bool enumerated,
               uint32_t numDocs,
               uint32_t fixedWidth,
               uint64_t uniqueValueCount,
               uint64_t totalValueCount,
               uint64_t createSerialNum,
               uint32_t version
        )
            : _fileName(fileName),
              _basicType(basicType),
              _collectionType(collectionType),
              _tensorType(tensorType),
              _hasMultiValue(multiValue),
              _hasWeightedSetType(weightedSetType),
              _enumerated(enumerated),
              _numDocs(numDocs),
              _fixedWidth(fixedWidth),
              _uniqueValueCount(uniqueValueCount),
              _totalValueCount(totalValueCount),
              _createSerialNum(createSerialNum),
              _version(version)
        {
        }
        ~Config();

        const vespalib::string & getFileName() const { return _fileName; }

        const vespalib::string &
        getBasicType() const
        {
            return _basicType;
        }

        const vespalib::string &
        getCollectionType() const
        {
            return _collectionType;
        }

        const vespalib::string &getTensorType() const {
            return _tensorType;
        }

        bool hasMultiValue() const { return _hasMultiValue; }
        bool hasWeightedSetType() const { return _hasWeightedSetType; }
        uint32_t getNumDocs() const { return _numDocs; }
        size_t getFixedWidth() const { return _fixedWidth; }

        uint64_t
        getUniqueValueCount(void) const
        {
            return _uniqueValueCount;
        }

        uint64_t
        getTotalValueCount(void) const
        {
            return _totalValueCount;
        }

        bool
        getEnumerated(void) const
        {
            return _enumerated;
        }

        uint64_t
        getCreateSerialNum(void) const
        {
            return _createSerialNum;
        }

        uint32_t getVersion() const  { return _version; }
    };
    using Buffer = IAttributeFileWriter::Buffer;
protected:
    Config _cfg;
public:
    IAttributeSaveTarget() : _cfg() {}
    void setConfig(const Config & cfg) { _cfg = cfg; }

    bool
    getEnumerated(void) const
    {
        return _cfg.getEnumerated();
    }

    /**
     * Setups this saveTarget before any data is written. Returns true
     * on success.
     **/
    virtual bool setup() = 0;
    /**
     * Closes this saveTarget when all data is written.
     **/
    virtual void close() = 0;

    virtual IAttributeFileWriter &datWriter() = 0;
    virtual IAttributeFileWriter &idxWriter() = 0;
    virtual IAttributeFileWriter &weightWriter() = 0;
    virtual IAttributeFileWriter &udatWriter() = 0;

    virtual ~IAttributeSaveTarget();
};

} // namespace search

