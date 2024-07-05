// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcommon/attribute/i_document_meta_store_context.h>

namespace search::attribute { struct AttributeBlueprintParams; }
namespace search::attribute { class IAttributeVector; }
namespace vespalib::eval { struct Value; }
namespace vespalib {
    class Doom;
    struct ThreadBundle;
}

namespace search::queryeval {

/**
 * Provides a context that follows the life of a query.
 */
class IRequestContext
{
public:
    using MetaStoreReadGuardSP = std::shared_ptr<IDocumentMetaStoreContext::IReadGuard>;
    virtual ~IRequestContext() = default;

    /**
     * Provides the time of soft doom for the query. Now it is time to start cleaning up and return what you have.
     * @return time of soft doom.
     */
    virtual const vespalib::Doom & getDoom() const = 0;
    /**
     * Provide an optional thread bundle that can be used for multithreading parts of the query.
     */
    virtual vespalib::ThreadBundle & thread_bundle() const = 0;

    /**
     * Provide access to attributevectors
     * @return AttributeVector or nullptr if it does not exist.
     */
    virtual const attribute::IAttributeVector *getAttribute(const vespalib::string &name) const = 0;
    virtual const attribute::IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const = 0;

    /**
     * Returns the tensor of the given name that was passed with the query.
     * Returns nullptr if the tensor is not found or if it is not a tensor.
     */
    virtual const vespalib::eval::Value* get_query_tensor(const vespalib::string& tensor_name) const = 0;

    virtual const search::attribute::AttributeBlueprintParams& get_attribute_blueprint_params() const = 0;

    virtual const MetaStoreReadGuardSP * getMetaStoreReadGuard() const = 0;
};

}
