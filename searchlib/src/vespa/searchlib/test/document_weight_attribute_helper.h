// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/i_docid_with_weight_posting_store.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/multinumericpostattribute.hpp>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/vespalib/testkit/test_kit.h>

namespace search::test {

class DocumentWeightAttributeHelper
{
private:
    AttributeVector::SP _attr;
    IntegerAttribute *_int_attr;
    const IDocidWithWeightPostingStore *_dww;

    AttributeVector::SP make_attr();

public:
    DocumentWeightAttributeHelper()
        : _attr(make_attr()),
          _int_attr(dynamic_cast<IntegerAttribute *>(_attr.get())),
          _dww(_attr->as_docid_with_weight_posting_store())
    {
        ASSERT_TRUE(_int_attr != nullptr);
        ASSERT_TRUE(_dww != nullptr);
    }
    ~DocumentWeightAttributeHelper();

    void add_docs(size_t limit) {
        AttributeVector::DocId docid;
        for (size_t i = 0; i < limit; ++i) {
            _attr->addDoc(docid);
        }
        _attr->commit();
        ASSERT_EQUAL((limit - 1), docid);
    }

    void set_doc(uint32_t docid, int64_t key, int32_t weight) {
        _int_attr->clearDoc(docid);
        _int_attr->append(docid, key, weight);
        _int_attr->commit();
    }

    const IDocidWithWeightPostingStore &dww() const { return *_dww; }
};

}
