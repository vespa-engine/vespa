// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/converters.h>
#include <vespa/searchcommon/common/iblobconverter.h>
#include <vespa/vespalib/stllike/string.h>
#include <unicode/coll.h>
#include <vector>
#include <cassert>

namespace search {

using common::BlobConverter;
using common::ConverterFactory;

namespace uca {

class UcaConverterFactory : public ConverterFactory {
public:
    BlobConverter::UP create(stringref local, stringref strength) const override;
};

class UcaConverter : public BlobConverter
{
public:
    using Collator = icu::Collator;
    UcaConverter(vespalib::stringref locale, vespalib::stringref strength);
    ~UcaConverter();
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
    int utf8ToUtf16(const ConstBufferRef & src) const;
    ConstBufferRef onConvert(const ConstBufferRef & src) const override;
    mutable Buffer               _buffer;
    mutable std::vector<UChar>   _u16Buffer;
    std::unique_ptr<Collator>      _collator;
};

}
}

