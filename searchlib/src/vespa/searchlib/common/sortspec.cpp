// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sortspec.h"
#include "converters.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/text/utf8.h>
#include <cstring>
#include <stdexcept>

namespace search::common {

using vespalib::ConstBufferRef;
using vespalib::make_string;
using sortspec::MissingPolicy;
using sortspec::SortOrder;

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
    std::string_view input((const char *)src.data(), src.size());
    vespalib::Utf8Reader r(input);
    vespalib::Utf8Writer w(_buffer);
    while (r.hasMore()) {
        ucs4_t c = r.getChar(0xFFFD);
        c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
        w.putChar(c);
    }
    return {_buffer.data(), _buffer.size()};
}

FieldSortSpec::FieldSortSpec(std::string_view field, bool ascending, std::shared_ptr<BlobConverter> converter) noexcept
    : FieldSortSpec(field, ascending ? SortOrder::ASCENDING : SortOrder::DESCENDING, std::move(converter))
{
}

FieldSortSpec::FieldSortSpec(std::string_view field, SortOrder sort_order, std::shared_ptr<BlobConverter> converter) noexcept
    : _field(field),
      _ascending(sort_order == SortOrder::ASCENDING),
      _sort_order(sort_order),
      _converter(std::move(converter)),
      _missing_policy(MissingPolicy::DEFAULT),
      _missing_value()
{
}

FieldSortSpec::~FieldSortSpec() = default;

SortSpec::SortSpec()
    : _spec(),
      _field_sort_specs()
{
}

SortSpec::SortSpec(const std::string & spec, const ConverterFactory & ucaFactory)
    : _spec(spec),
      _field_sort_specs()
{
    for (const char *pt(spec.c_str()), *mt(spec.c_str() + spec.size()); pt < mt;) {
        for (; pt < mt && *pt != '+' && *pt != '-'; pt++);
        if (pt != mt) {
            bool ascending = (*pt++ == '+');
            const char *vectorName = pt;
            for (;pt < mt && *pt != ' '; pt++);
            std::string funcSpec(vectorName, pt - vectorName);
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
                        std::string attr(attrName, p-attrName);
                        p++;
                        const char *localeName = p;
                        for(; (p < e) && (*p != ')') && (*p != ','); p++);
                        if (*p == ',') {
                            std::string locale(localeName, p-localeName);
                            p++;
                            const char *strengthName = p;
                            for(; (p < e) && (*p != ')'); p++);
                            if (*p == ')') {
                                std::string strength(strengthName, p - strengthName);
                                _field_sort_specs.emplace_back(attr, ascending, ucaFactory.create(locale, strength));
                            } else {
                                throw std::runtime_error(make_string("Missing ')' at %s attr=%s locale=%s strength=%s", p, attr.c_str(), localeName, strengthName));
                            }
                        } else if (*p == ')') {
                            std::string locale(localeName, p-localeName);
                            _field_sort_specs.emplace_back(attr, ascending, ucaFactory.create(locale, ""));
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
                        std::string attr(attrName, p-attrName);
                        _field_sort_specs.emplace_back(attr, ascending, std::make_shared<LowercaseConverter>());
                    } else {
                        throw std::runtime_error("Missing ')'");
                    }
                } else {
                    throw std::runtime_error("Unknown func " + std::string(func, p-func));
                }
            } else {
                _field_sort_specs.emplace_back(funcSpec, ascending, std::shared_ptr<search::common::BlobConverter>());
            }
        }
    }
}

SortSpec::~SortSpec() = default;

}
