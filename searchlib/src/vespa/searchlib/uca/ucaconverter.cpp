// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ucaconverter.h"
#include <unicode/ustring.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/text/utf8.h>
#include <mutex>
#include <vespa/log/log.h>
LOG_SETUP(".search.common.sortspec");

namespace search {
namespace uca {

using vespalib::ConstBufferRef;
using vespalib::make_string;

namespace {
std::mutex _GlobalDirtyICUThreadSafeLock;
}

BlobConverter::UP
UcaConverterFactory::create(stringref local, stringref strength) const {
    return std::make_unique<UcaConverter>(local, strength);
}

UcaConverter::UcaConverter(vespalib::stringref locale, vespalib::stringref strength) :
    _buffer(),
    _u16Buffer(128),
    _collator()
{
    UErrorCode status = U_ZERO_ERROR;
    Collator *coll(NULL);
    {
        std::lock_guard<std::mutex> guard(_GlobalDirtyICUThreadSafeLock);
        coll = Collator::createInstance(icu::Locale(locale.c_str()), status);
    }
    if(U_SUCCESS(status)) {
        _collator.reset(coll);
        if (strength.empty()) {
            _collator->setStrength(Collator::PRIMARY);
        } else if (strength == "PRIMARY") {
            _collator->setStrength(Collator::PRIMARY);
        } else if (strength == "SECONDARY") {
            _collator->setStrength(Collator::SECONDARY);
        } else if (strength == "TERTIARY") {
            _collator->setStrength(Collator::TERTIARY);
        } else if (strength == "QUATERNARY") {
            _collator->setStrength(Collator::QUATERNARY);
        } else if (strength == "IDENTICAL") {
            _collator->setStrength(Collator::IDENTICAL);
        } else {
            throw std::runtime_error("Illegal uca collation strength : " + strength);
        }
    } else {
        delete coll;
        throw std::runtime_error("Failed Collator::createInstance(Locale(locale.c_str()), status) with locale : " + locale);
    }
}

UcaConverter::~UcaConverter() {}

int UcaConverter::utf8ToUtf16(const ConstBufferRef & src) const
{
    UErrorCode status = U_ZERO_ERROR;
    int32_t u16Wanted(0);
    u_strFromUTF8(&_u16Buffer[0], _u16Buffer.size(), &u16Wanted, static_cast<const char *>(src.data()), -1, &status);
    if (U_SUCCESS(status)) {
    } else if (status == U_INVALID_CHAR_FOUND) {
        LOG(warning, "ICU was not able to convert the %ld alleged utf8 characters'%s' to utf16", src.size(), src.c_str());
    } else if (status == U_BUFFER_OVERFLOW_ERROR) {
        //Ignore as this is handled on the outside.
    } else {
        LOG(warning, "ICU made a undefined complaint(%d) about the %ld alleged utf8 characters'%s' to utf16", status, src.size(), src.c_str());
    }
    return u16Wanted;
}

ConstBufferRef UcaConverter::onConvert(const ConstBufferRef & src) const
{
    int32_t u16Wanted(utf8ToUtf16(src));
    if (u16Wanted > (int)_u16Buffer.size()) {
        _u16Buffer.resize(u16Wanted);
        u16Wanted = utf8ToUtf16(src);
    }
    int wanted = _collator->getSortKey(&_u16Buffer[0], u16Wanted, _buffer.ptr(), _buffer.siz());
    _buffer.check();
    if (wanted > _buffer.siz()) {
        _buffer.reserve(wanted);
        wanted = _collator->getSortKey(&_u16Buffer[0], u16Wanted, _buffer.ptr(), _buffer.siz());
        _buffer.check();
    }
    return ConstBufferRef(_buffer.ptr(), wanted);
}

}
}
