// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "floatbase.hpp"
#include "attributevector.hpp"
#include <vespa/document/fieldvalue/fieldvalue.h>

namespace search {

FloatingPointAttribute::FloatingPointAttribute(const vespalib::string & name, const Config & c) :
    NumericAttribute(name, c),
    _changes()
{
}

FloatingPointAttribute::~FloatingPointAttribute() = default;

uint32_t FloatingPointAttribute::clearDoc(DocId doc)
{
    uint32_t removed(0);
    if (hasMultiValue() && (doc < getNumDocs())) {
        removed = getValueCount(doc);
    }
    AttributeVector::clearDoc(_changes, doc);

    return removed;
}

uint32_t FloatingPointAttribute::get(DocId doc, WeightedString * s, uint32_t sz) const
{
    WeightedFloat * v = new WeightedFloat[sz];
    unsigned num(static_cast<const AttributeVector *>(this)->get(doc, v, sz));
    for(unsigned i(0); i < num; i++) {
        char tmp[32];
        snprintf(tmp, sizeof(tmp), "%g", v[i].getValue());
        s[i] = WeightedString(tmp, v[i].getWeight());
    }
    delete [] v;
    return num;
}

uint32_t FloatingPointAttribute::get(DocId doc, WeightedConstChar * v, uint32_t sz) const
{
    (void) doc;
    (void) v;
    (void) sz;
    return 0;
}

uint32_t FloatingPointAttribute::get(DocId doc, vespalib::string * s, uint32_t sz) const
{
    double * v = new double[sz];
    unsigned num(static_cast<const AttributeVector *>(this)->get(doc, v, sz));
    for(unsigned i(0); i < num; i++) {
        char tmp[32];
        snprintf(tmp, sizeof(tmp), "%g", v[i]);
        s[i] = tmp;
    }
    delete [] v;
    return num;
}

uint32_t FloatingPointAttribute::get(DocId doc, const char ** v, uint32_t sz) const
{
    (void) doc;
    (void) v;
    (void) sz;
    return 0;
}

bool FloatingPointAttribute::applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust)
{
    double v = fv.getAsDouble();
    return AttributeVector::adjustWeight(_changes, doc, NumericChangeData<double>(v), wAdjust);
}

bool FloatingPointAttribute::applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust)
{
    double v = fv.getAsDouble();
    return AttributeVector::adjustWeight(_changes, doc, NumericChangeData<double>(v), wAdjust);
}

bool FloatingPointAttribute::apply(DocId doc, const ArithmeticValueUpdate & op)
{
    bool retval(doc < getNumDocs());
    if (retval) {
        retval = AttributeVector::applyArithmetic(_changes, doc, NumericChangeData<double>(0), op);
    }
    return retval;
}

const char *
FloatingPointAttribute::getString(DocId doc, char * s, size_t sz) const {
    double v = getFloat(doc);
    snprintf(s, sz, "%g", v);
    return s;
}

vespalib::MemoryUsage
FloatingPointAttribute::getChangeVectorMemoryUsage() const
{
    return _changes.getMemoryUsage();
}

template class FloatingPointAttributeTemplate<float>;
template class FloatingPointAttributeTemplate<double>;

template bool AttributeVector::clearDoc(FloatingPointAttribute::ChangeVector& changes, DocId doc);
template bool AttributeVector::update(FloatingPointAttribute::ChangeVector& changes, DocId doc, const NumericChangeData<double>& v);
template bool AttributeVector::append(FloatingPointAttribute::ChangeVector& changes, DocId doc, const NumericChangeData<double>& v, int32_t w, bool doCount);
template bool AttributeVector::remove(FloatingPointAttribute::ChangeVector& changes, DocId doc, const NumericChangeData<double>& v, int32_t w);

}
