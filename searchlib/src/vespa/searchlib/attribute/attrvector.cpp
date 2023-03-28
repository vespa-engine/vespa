// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attrvector.hpp"
#include "iattributesavetarget.h"
#include "load_utils.h"
#include <vespa/vespalib/data/databuffer.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attr_vector");

namespace search {

StringDirectAttribute::
StringDirectAttribute(const vespalib::string & baseFileName, const Config & c)
    : search::StringAttribute(baseFileName, c),
      _buffer(),
      _offsets(),
      _idx()
{
}

StringDirectAttribute::~StringDirectAttribute() = default;

std::unique_ptr<attribute::SearchContext>
StringDirectAttribute::getSearch(QueryTermSimpleUP, const attribute::SearchContextParams &) const {
    LOG_ABORT("StringDirectAttribute::getSearch is not implemented and should never be called.");
}

bool StringDirectAttribute::findEnum(const char * key, EnumHandle & e) const
{
    if (_offsets.size() < 1) {
        e = 0;
        return false;
    }
    int delta;
    const int eMax = getEnumMax();
    for (delta = 1; delta <= eMax; delta <<= 1) { }
    delta >>= 1;
    int pos = delta - 1;
    int cmpres(0);

    while (delta != 0) {
        delta >>= 1;
        if (pos >= eMax) {
            pos -= delta;
        } else {
            const char *name = &_buffer[_offsets[pos]];
            cmpres = strcmp(key, name);
            if (cmpres == 0) {
                e = pos;
                return true;
            }
            pos += (cmpres < 0) ? -delta : +delta;
        }
    }
    e = ((cmpres > 0) && (pos < eMax)) ? pos + 1 : pos;
    return false;
}


// XXX this is not really correct
std::vector<StringDirectAttribute::EnumHandle>
StringDirectAttribute::findFoldedEnums(const char *key) const
{
    std::vector<EnumHandle> result;
    EnumHandle handle;
    if (findEnum(key, handle)) {
        result.push_back(handle);
    }
    return result;
}

class stringComp {
public:
    stringComp(const char * buffer) : _buffer(buffer) { }
    bool operator()(uint32_t x, uint32_t y) const { return strcmp(_buffer+x, _buffer+y) < 0; }
private:
    const char * _buffer;
};

void addString(const char * v, StringAttribute::OffsetVector & offsets, std::vector<char> & buffer)
{
    offsets.push_back(buffer.size());
    for(const char *p(v); *p; p++) {
        buffer.push_back(*p);
    }
    buffer.push_back('\0');
}

void StringDirectAttribute::onCommit()
{
    LOG_ABORT("should not be reached");
}

bool StringDirectAttribute::addDoc(DocId & doc)
{
    (void) doc;
    return false;
}

template class NumericDirectAttribute<IntegerAttributeTemplate<int64_t>>;
template class NumericDirectAttribute<FloatingPointAttributeTemplate<double>>;

}  // namespace search
