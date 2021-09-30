// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "integerbase.hpp"
#include "attributevector.hpp"
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <charconv>

namespace search {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(IntegerAttribute, NumericAttribute);

IntegerAttribute::IntegerAttribute(const vespalib::string & name, const Config & c) :
    NumericAttribute(name, c),
    _changes()
{
}

IntegerAttribute::~IntegerAttribute() = default;

uint32_t IntegerAttribute::clearDoc(DocId doc)
{
    uint32_t removed(0);
    if (hasMultiValue() && (doc < getNumDocs())) {
        removed = getValueCount(doc);
    }
    AttributeVector::clearDoc(_changes, doc);

    return removed;
}

namespace {

// TODO Move to vespalib::to_string and template on value type
vespalib::string
to_string(int64_t v) {
    char tmp[32];
    auto res = std::to_chars(tmp, tmp + sizeof(tmp) - 1, v, 10);
    return vespalib::string(tmp, res.ptr - tmp);
}

}
uint32_t IntegerAttribute::get(DocId doc, WeightedString * s, uint32_t sz) const
{
    WeightedInt * v = new WeightedInt[sz];
    unsigned num(static_cast<const AttributeVector *>(this)->get(doc, v, sz));
    for(unsigned i(0); i < num; i++) {
        s[i] = WeightedString(to_string(v[i].getValue()), v[i].getWeight());
    }
    delete [] v;
    return num;
}

uint32_t IntegerAttribute::get(DocId doc, WeightedConstChar * v, uint32_t sz) const
{
    (void) doc;
    (void) v;
    (void) sz;
    return 0;
}
const char *
IntegerAttribute::getString(DocId doc, char * s, size_t sz) const {
    if (sz > 1) {
        largeint_t v = getInt(doc);
        auto res = std::to_chars(s, s + sz - 1, v, 10);
        if (res.ec == std::errc()) {
            res.ptr[0] = 0;
        } else {
            s[0] = 0;
        }
    }
    return s;
}
uint32_t IntegerAttribute::get(DocId doc, vespalib::string * s, uint32_t sz) const
{
    largeint_t * v = new largeint_t[sz];
    unsigned num(static_cast<const AttributeVector *>(this)->get(doc, v, sz));
    for(unsigned i(0); i < num; i++) {
        s[i] = to_string(v[i]);
    }
    delete [] v;
    return num;
}

uint32_t IntegerAttribute::get(DocId doc, const char ** v, uint32_t sz) const
{
    (void) doc;
    (void) v;
    (void) sz;
    return 0;
}

bool IntegerAttribute::applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust)
{
    largeint_t v = fv.getAsLong();
    return AttributeVector::adjustWeight(_changes, doc, NumericChangeData<largeint_t>(v), wAdjust);
}

bool IntegerAttribute::applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust)
{
    largeint_t v = fv.getAsLong();
    return AttributeVector::adjustWeight(_changes, doc, NumericChangeData<largeint_t>(v), wAdjust);
}

bool IntegerAttribute::apply(DocId doc, const ArithmeticValueUpdate & op)
{
    bool retval(doc < getNumDocs());
    if (retval) {
        retval = AttributeVector::applyArithmetic(_changes, doc, NumericChangeData<largeint_t>(0), op);
    }
    return retval;
}

vespalib::MemoryUsage
IntegerAttribute::getChangeVectorMemoryUsage() const
{
    return _changes.getMemoryUsage();
}

template class IntegerAttributeTemplate<int8_t>;
template class IntegerAttributeTemplate<int16_t>;
template class IntegerAttributeTemplate<int32_t>;
template class IntegerAttributeTemplate<int64_t>;

template bool AttributeVector::clearDoc(IntegerAttribute::ChangeVector& changes, DocId doc);
template bool AttributeVector::update(IntegerAttribute::ChangeVector& changes, DocId doc, const NumericChangeData<largeint_t>& v);
template bool AttributeVector::append(IntegerAttribute::ChangeVector& changes, DocId doc, const NumericChangeData<largeint_t>& v, int32_t w, bool doCount);
template bool AttributeVector::remove(IntegerAttribute::ChangeVector& changes, DocId doc, const NumericChangeData<largeint_t>& v, int32_t w);

}
