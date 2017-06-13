// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributevector.h"
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/fastlib/io/bufferedfile.h>

namespace search {

namespace common {
    class FileHeaderContext;
}

class AttributeFile
{
public:
    class Record {
    public:
        virtual ~Record() { }
        virtual size_t getValueCount() const = 0;
        virtual void setValue(const void * v, size_t len) = 0;
    protected:
        std::vector<int32_t> _weight;
    private:
        bool write(AttributeFile & dest) const { return onWrite(dest); }
        bool read(AttributeFile & src, size_t numValues) { return onRead(src, numValues); }
        virtual bool onWrite(AttributeFile & dest) const = 0;
        virtual bool onRead(AttributeFile & src, size_t numValues) = 0;

        friend class AttributeFile;
    };
    template <typename T>
    class FixedRecord : public Record
    {
    public:
        size_t getValueCount() const override { return _data.size(); }
    private:
        void setValue(const void * v, size_t len) override {
            assert(len == sizeof(T));
            (void) len;
            _data.resize(1);
            _weight.clear();
            _data[0] = * static_cast<const T *>(v);
        }

        bool onWrite(AttributeFile & dest) const override;
        bool onRead(AttributeFile & src, size_t numValues) override;

        std::vector<T>       _data;
    };

    class VariableRecord : public Record
    {
    public:
        size_t getValueCount() const override;
    private:
        void setValue(const void * v, size_t len) override;
        bool onWrite(AttributeFile & dest) const override;
        bool onRead(AttributeFile & src, size_t numValues) override;
        std::vector<char>    _data;
    };
protected:
    typedef attribute::Config Config;
public:
    AttributeFile(const vespalib::string & fileName, const Config & config);
    ~AttributeFile();

    std::unique_ptr<Record> getRecord();
    bool read(Record & record);
    bool write(const Record & toWrite);
    void enableDirectIO();
protected:
    void OpenReadOnly();
    void OpenWriteOnly(const search::common::FileHeaderContext &fileHeaderContext, uint32_t docIdLimit);
    void Close();
    bool seekIdxPos(size_t idxPos);
private:
    uint32_t                          _currIdx;
    std::unique_ptr<Fast_BufferedFile>  _datFile;
    std::unique_ptr<Fast_BufferedFile>  _idxFile;
    std::unique_ptr<Fast_BufferedFile>  _weightFile;
    vespalib::string                  _fileName;
    Config                            _config;
    uint32_t                          _datHeaderLen;
    uint32_t                          _idxHeaderLen;
    uint32_t                          _weightHeaderLen;
    uint64_t                          _datFileSize;
    uint64_t                          _idxFileSize;
};

class ReadAttributeFile : public AttributeFile
{
public:
    ReadAttributeFile(const vespalib::string &fileName, const Config &config);
};

class WriteAttributeFile : public AttributeFile
{
public:
    WriteAttributeFile(const vespalib::string &fileName,
                       const Config &config,
                       const search::common::FileHeaderContext &
                       fileHeaderContext,
                       uint32_t docIdLimit);
};

}

