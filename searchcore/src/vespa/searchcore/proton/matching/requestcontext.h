// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/attribute/attribute_blueprint_params.h>
#include <vespa/vespalib/util/doom.h>

namespace search::fef {
class IObjectStore;
class IQueryEnvironment;
}

namespace proton {

class RequestContext : public search::queryeval::IRequestContext,
                       public search::attribute::IAttributeExecutor
{
public:
    using IAttributeContext = search::attribute::IAttributeContext;
    using IAttributeFunctor = search::attribute::IAttributeFunctor;
    using Doom = vespalib::Doom;
    RequestContext(const Doom& softDoom,
                   IAttributeContext& attributeContext,
                   const search::fef::IQueryEnvironment& query_env,
                   search::fef::IObjectStore& shared_store,
                   const search::attribute::AttributeBlueprintParams& attribute_blueprint_params,
                   const MetaStoreReadGuardSP * metaStoreReadGuard);

    const Doom & getDoom() const override { return _doom; }
    const search::attribute::IAttributeVector *getAttribute(const vespalib::string &name) const override;

    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;

    const search::attribute::IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override;

    const vespalib::eval::Value* get_query_tensor(const vespalib::string& tensor_name) const override;

    const search::attribute::AttributeBlueprintParams& get_attribute_blueprint_params() const override {
        return _attribute_blueprint_params;
    }
    const MetaStoreReadGuardSP * getMetaStoreReadGuard() const override {
        return _metaStoreReadGuard;
    }

private:
    const Doom                                    _doom;
    IAttributeContext                           & _attributeContext;
    const search::fef::IQueryEnvironment        & _query_env;
    search::fef::IObjectStore                   & _shared_store;
    search::attribute::AttributeBlueprintParams   _attribute_blueprint_params;
    const MetaStoreReadGuardSP                  * _metaStoreReadGuard;
};

}
