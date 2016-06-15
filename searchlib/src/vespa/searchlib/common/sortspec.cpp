// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/sync.h>
#include <unicode/ustring.h>
#include <stdexcept>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/log/log.h>
LOG_SETUP(".search.common.sortspec");

namespace search {
namespace common {

using vespalib::ConstBufferRef;
using vespalib::make_string;

ConstBufferRef PassThroughConverter::onConvert(const ConstBufferRef & src) const
{
    return src;
}

LowercaseConverter::LowercaseConverter() :
    _buffer()
{
}

ConstBufferRef LowercaseConverter::onConvert(const ConstBufferRef & src) const
{
    _buffer.clear();
    vespalib::stringref input((const char *)src.data(), src.size());
    vespalib::Utf8Reader r(input);
    vespalib::Utf8Writer w(_buffer);
    while (r.hasMore()) {
        ucs4_t c = r.getChar(0xFFFD);
        c = Fast_NormalizeWordFolder::ToFold(c);
        w.putChar(c);
    }
    return ConstBufferRef(_buffer.begin(), _buffer.size());
}

namespace {
    vespalib::Lock _GlobalDirtyICUThreadSafeLock;
}

UcaConverter::UcaConverter(const vespalib::string & locale, const vespalib::string & strength) :
    _buffer(),
    _u16Buffer(128),
    _collator()
{
    UErrorCode status = U_ZERO_ERROR;
    Collator *coll(NULL);
    {
        vespalib::LockGuard guard(_GlobalDirtyICUThreadSafeLock);
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

SortSpec::SortSpec(const vespalib::string & spec) :
    _spec(spec)
{
    for (const char *pt(spec.c_str()), *mt(spec.c_str() + spec.size()); pt < mt;) {
        for (; pt < mt && *pt != '+' && *pt != '-'; pt++);
        if (pt != mt) {
            bool ascending = (*pt++ == '+');
            const char *vectorName = pt;
            for (;pt < mt && *pt != ' '; pt++);
            vespalib::string funcSpec(vectorName, pt - vectorName);
            const char * func = funcSpec.c_str();
            const char *p = func;
            const char *e = func+funcSpec.size();
            for(; (p < e) && (*p != '('); p++);
            if (*p == '(') {
                if (strncmp(func, "uca", std::min(3l, p-func)) == 0) {
                    p++;
                    const char * attrName = p;
                    for(; (p < e) && (*p != ','); p++);
                    if (*p == ',') {
                        vespalib::string attr(attrName, p-attrName);
                        p++;
                        const char *localeName = p;
                        for(; (p < e) && (*p != ')') && (*p != ','); p++);
                        if (*p == ',') {
                            vespalib::string locale(localeName, p-localeName);
                            p++;
                            const char *strengthName = p;
                            for(; (p < e) && (*p != ')'); p++);
                            if (*p == ')') {
                                vespalib::string strength(strengthName, p - strengthName);
                                push_back(SortInfo(attr, ascending, BlobConverter::SP(new UcaConverter(locale, strength))));
                            } else {
                                throw std::runtime_error(make_string("Missing ')' at %s attr=%s locale=%s strength=%s", p, attr.c_str(), localeName, strengthName));
                            }
                        } else if (*p == ')') {
                            vespalib::string locale(localeName, p-localeName);
                            push_back(SortInfo(attr, ascending, BlobConverter::SP(new UcaConverter(locale, ""))));
                        } else {
                            throw std::runtime_error(make_string("Missing ')' or ',' at %s attr=%s locale=%s", p, attr.c_str(), localeName));
                        }
                    } else {
                        throw std::runtime_error(make_string("Missing ',' at %s", p));
                    }
                } else if (strncmp(func, "lowercase", std::min(9l, p-func)) == 0) {
                    p++;
                    const char * attrName = p;
                    for(; (p < e) && (*p != ')'); p++);
                    if (*p == ')') {
                        vespalib::string attr(attrName, p-attrName);
                        push_back(SortInfo(attr, ascending, BlobConverter::SP(new LowercaseConverter())));
                    } else {
                        throw std::runtime_error("Missing ')'");
                    }
                } else {
                    throw std::runtime_error("Unknown func " + vespalib::string(func, p-func));
                }
            } else {
                push_back(SortInfo(funcSpec, ascending, BlobConverter::SP(NULL)));
            }
        }
    }
}

}
}
