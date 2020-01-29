// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeguard.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vector>

namespace search {

namespace attribute {
class AttributeReadGuard;
class ReadableAttributeVector;
}

/**
 * This is an interface used to access all registered attribute vectors.
 **/
class IAttributeManager : public attribute::IAttributeExecutor {
public:
    IAttributeManager(const IAttributeManager &) = delete;
    IAttributeManager & operator = (const IAttributeManager &) = delete;
    using SP = std::shared_ptr<IAttributeManager>;
    using string = vespalib::string;

    /**
     * Returns a view of the attribute vector with the given name.
     *
     * NOTE: this method is deprecated! Prefer using readable_attribute_vector(name) instead,
     * as that enforces appropriate guards to be taken before accessing the underlying vector.
     *
     * TODO remove this when all usages are gone.
     *
     * @param name name of the attribute vector.
     * @return view of the attribute vector or empty view if the attribute vector does not exists.
     **/
    virtual AttributeGuard::UP getAttribute(const string & name) const = 0;

    /**
     * Returns a read view of the attribute vector with the given name.
     *
     * @param name name of the attribute vector.
     * @param stableEnumGuard flag to block enumeration changes during use of the attribute vector via the view.
     * @return read view of the attribute vector if the attribute vector exists
     **/
    virtual std::unique_ptr<attribute::AttributeReadGuard> getAttributeReadGuard(const string &name, bool stableEnumGuard) const = 0;

    /**
     * Fill the given list with all attribute vectors registered in this manager.
     *
     * @param list the list to fill in attribute vectors.
     **/
    virtual void getAttributeList(std::vector<AttributeGuard> & list) const = 0;

    /**
     * Creates a per thread attribute context used to provide read access to attributes.
     *
     * @return the attribute context
     **/
    virtual attribute::IAttributeContext::UP createContext() const = 0;

    /**
     * Looks up and returns a readable attribute vector shared_ptr with the provided name.
     * This transparently supports imported attribute vectors.
     *
     * @param name name of the attribute vector.
     * @return The attribute vector, or an empty shared_ptr if no vector was found with the given name.
     */
    virtual std::shared_ptr<attribute::ReadableAttributeVector> readable_attribute_vector(const string& name) const = 0;

    ~IAttributeManager() override = default;
protected:
    IAttributeManager() = default;
};

} // namespace search

