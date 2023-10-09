// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inspector.h"
#include "object_traverser.h"
#include "array_traverser.h"
#include <cassert>

namespace vespalib::slime {

class Equal {
public:
    Equal(const Inspector & rhs) : _rhs(rhs), _equal(true) { }
    bool isEqual() const { return _equal; }
protected:
    const Inspector & _rhs;
    bool _equal;
};
    
class EqualObject : public ObjectTraverser, public Equal {
public:
    EqualObject(const Inspector & rhs) : Equal(rhs) { }
private:
    void field(const Memory &symbol, const Inspector &inspector) override {
        if ( _equal ) {
            _equal = (inspector == _rhs[symbol]);
        }
    }
};

class EqualArray : public ArrayTraverser, public Equal {
public:
    EqualArray(const Inspector & rhs) : Equal(rhs) { }
private:
    void entry(size_t idx, const Inspector &inspector) override {
        if ( _equal ) {
            _equal = (inspector == _rhs[idx]);
        }
    }
};

bool operator == (const Inspector & a, const Inspector & b)
{
    bool equal(a.type().getId() == b.type().getId());
    if (equal) {
        switch (a.type().getId()) {
        case NIX::ID:
            equal = a.valid() == b.valid();
            break;
        case BOOL::ID:
            equal = a.asBool() == b.asBool();
            break;
        case LONG::ID:
            equal = a.asLong() == b.asLong();
            break;
        case DOUBLE::ID:
            equal = a.asDouble() == b.asDouble();
            break;
        case STRING::ID:
            equal = a.asString() == b.asString();
            break;
        case DATA::ID:
            equal = a.asData() == b.asData();
            break;
        case ARRAY::ID:
            {
                EqualArray traverser(b);
                a.traverse(traverser);
                equal = traverser.isEqual() && (a.entries() == b.entries());
            }
            break;
        case OBJECT::ID:
            {
                EqualObject traverser(b);
                a.traverse(traverser);
                equal = traverser.isEqual() && (a.fields() == b.fields());
            }
            break;
        default:
            assert(false);
            break;
        }
    }
    return equal;
}

std::ostream & operator << (std::ostream & os, const Inspector & inspector)
{
    os << inspector.toString();
    return os;
}

} // namespace vespalib::slime
