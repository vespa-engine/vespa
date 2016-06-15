// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/iblobconverter.h>
#include <unicode/coll.h>
#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace common {

class PassThroughConverter : public BlobConverter
{
private:
    virtual vespalib::ConstBufferRef onConvert(const vespalib::ConstBufferRef & src) const;
};

class LowercaseConverter : public BlobConverter
{
public:
    LowercaseConverter();
private:
    virtual vespalib::ConstBufferRef onConvert(const vespalib::ConstBufferRef & src) const;
    mutable vespalib::string _buffer;
};

class UcaConverter : public BlobConverter
{
public:
    typedef icu::Collator Collator;
    UcaConverter(const vespalib::string & locale, const vespalib::string & strength);
    const Collator & getCollator() const { return *_collator; }
private:
    struct Buffer {
        vespalib::string _data;
        uint8_t *ptr() { return (uint8_t *)_data.begin(); }
        int32_t siz() { return _data.size(); }
        Buffer() : _data() {
            reserve(_data.capacity()-8); // do not cause extra malloc() by default
        }
        void reserve(size_t size) {
            _data.reserve(size+8);
            _data.resize(size);
            _data[size+1] = '\0';
            _data[size+2] = '\0';
            _data[size+3] = 'd';
            _data[size+4] = 'e';
            _data[size+5] = 'a';
            _data[size+6] = 'd';
            _data[size+7] = '\0';
        }
        void check() {
            assert(_data[siz()+3] == 'd');
            assert(_data[siz()+4] == 'e');
            assert(_data[siz()+5] == 'a');
            assert(_data[siz()+6] == 'd');
        }
    };
    int utf8ToUtf16(const vespalib::ConstBufferRef & src) const;
    virtual vespalib::ConstBufferRef onConvert(const vespalib::ConstBufferRef & src) const;
    mutable Buffer               _buffer;
    mutable std::vector<UChar>   _u16Buffer;
    std::unique_ptr<Collator>      _collator;
};

}
}

