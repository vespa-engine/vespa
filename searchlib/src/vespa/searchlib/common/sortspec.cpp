// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "sortspec.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/text/utf8.h>
#include <stdexcept>

namespace search::common {

using vespalib::ConstBufferRef;
using vespalib::make_string;

ConstBufferRef
PassThroughConverter::onConvert(const ConstBufferRef & src) const
{
    return src;
}

LowercaseConverter::LowercaseConverter() noexcept
    : _buffer()
{
}

ConstBufferRef
LowercaseConverter::onConvert(const ConstBufferRef & src) const
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
    return {_buffer.begin(), _buffer.size()};
}

SortInfo::SortInfo(vespalib::stringref field, bool ascending, BlobConverter::SP converter) noexcept
    : _field(field), _ascending(ascending), _converter(std::move(converter))
{ }
SortInfo::~SortInfo() = default;

SortSpec::SortSpec(const vespalib::string & spec, const ConverterFactory & ucaFactory) :
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
                                emplace_back(attr, ascending, ucaFactory.create(locale, strength));
                            } else {
                                throw std::runtime_error(make_string("Missing ')' at %s attr=%s locale=%s strength=%s", p, attr.c_str(), localeName, strengthName));
                            }
                        } else if (*p == ')') {
                            vespalib::string locale(localeName, p-localeName);
                            emplace_back(attr, ascending, ucaFactory.create(locale, ""));
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
                        emplace_back(attr, ascending, std::make_shared<LowercaseConverter>());
                    } else {
                        throw std::runtime_error("Missing ')'");
                    }
                } else {
                    throw std::runtime_error("Unknown func " + vespalib::string(func, p-func));
                }
            } else {
                emplace_back(funcSpec, ascending, std::shared_ptr<search::common::BlobConverter>());
            }
        }
    }
}

SortSpec::~SortSpec() = default;

}
