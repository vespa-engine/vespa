// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeguard.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vector>

namespace search {

namespace attribute { class AttributeReadGuard; }

/**
 * This is an interface used to access all registered attribute vectors.
 **/
class IAttributeManager {
public:
    IAttributeManager(const IAttributeManager &) = delete;
    IAttributeManager & operator = (const IAttributeManager &) = delete;
    typedef std::shared_ptr<IAttributeManager> SP;
    typedef vespalib::string string;

    /**
     * Returns a view of the attribute vector with the given name.
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
     * Virtual destructor to allow safe subclassing.
     **/
    virtual ~IAttributeManager() {}
protected:
    IAttributeManager() = default;
};

} // namespace search

