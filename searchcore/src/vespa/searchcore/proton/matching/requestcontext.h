// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/tensor/tensor.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>

namespace search::fef { class Properties; }

namespace proton {

class RequestContext : public search::queryeval::IRequestContext,
                       public search::attribute::IAttributeExecutor
{
public:
    using IAttributeContext = search::attribute::IAttributeContext;
    using IAttributeFunctor = search::attribute::IAttributeFunctor;
    using Doom = vespalib::Doom;
    RequestContext(const Doom & softDoom, IAttributeContext & attributeContext,
                   const search::fef::Properties& rank_properties);

    const Doom & getSoftDoom() const override { return _softDoom; }
    const search::attribute::IAttributeVector *getAttribute(const vespalib::string &name) const override;

    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;

    const search::attribute::IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override;

    vespalib::tensor::Tensor::UP get_query_tensor(const vespalib::string& tensor_name) const override;


private:
    const Doom          _softDoom;
    IAttributeContext & _attributeContext;
    const search::fef::Properties& _rank_properties;
};

}
