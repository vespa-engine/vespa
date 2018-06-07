// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_collection_spec.h"
#include "exclusive_attribute_read_accessor.h"
#include "i_attribute_factory.h"
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/serialnum.h>

namespace search {
class IDestructorCallback;
class ISequencedTaskExecutor;
}

namespace proton {

class IAttributeFunctor;
class ImportedAttributesRepo;

/**
 * Proton specific interface for an attribute manager that handles a set of attribute vectors.
 *
 * The attribute manager should handle initialization and loading of attribute vectors,
 * and then provide access to the attributes for feeding, searching and flushing.
 */
struct IAttributeManager : public search::IAttributeManager
{
    typedef std::shared_ptr<IAttributeManager> SP;

    using OnWriteDoneType = const std::shared_ptr<search::IDestructorCallback> &;

    virtual ~IAttributeManager() {}

    /**
     * Create a new attribute manager based on the content of the current one and
     * the given attribute collection spec.
     */
    virtual IAttributeManager::SP create(const AttributeCollectionSpec &spec) const = 0;

    /**
     * Return the list of flush targets for this attribute manager.
     */
    virtual std::vector<searchcorespi::IFlushTarget::SP> getFlushTargets() const = 0;

    /**
     * Returns the flushed serial num for the given attribute.
     * Return 0 if attribute is not found.
     */
    virtual search::SerialNum getFlushedSerialNum(const vespalib::string &name) const = 0;

    /**
     * Return the oldest flushed serial number among the underlying attribute vectors.
     */
    virtual search::SerialNum getOldestFlushedSerialNumber() const = 0;

    virtual search::SerialNum getNewestFlushedSerialNumber() const = 0;

    /**
     * Fills all underlying attribute vectors (including extra attributes) into the given list.
     */
    virtual void getAttributeListAll(std::vector<search::AttributeGuard> &list) const = 0;

    /**
     * Prune removed attributes from file system.
     */
    virtual void pruneRemovedFields(search::SerialNum serialNum) = 0;

    /**
     * Returns the attribute factory used by this manager.
     */
    virtual const IAttributeFactory::SP &getFactory() const = 0;

    virtual search::ISequencedTaskExecutor &getAttributeFieldWriter() const = 0;

    /*
     * Get pointer to named writable attribute.  If attribute isn't
     * found or is an extra attribute then nullptr is returned.
     *
     * The attribute writer doesn't need attribute guards to access
     * attributes.  Lifetime should be guaranteed by syncing threads
     * at config changes.
     */
    virtual search::AttributeVector *getWritableAttribute(const vespalib::string &name) const = 0;

    /*
     * Get pointers to all writable attributes.
     *
     * The attribute writer doesn't need attribute guards to access
     * attributes.  Lifetime should be guaranteed by syncing threads
     * at config changes.
     */
    virtual const std::vector<search::AttributeVector *> &getWritableAttributes() const = 0;

    virtual void asyncForEachAttribute(std::shared_ptr<IAttributeFunctor> func) const = 0;

    virtual ExclusiveAttributeReadAccessor::UP getExclusiveReadAccessor(const vespalib::string &name) const = 0;

    virtual void setImportedAttributes(std::unique_ptr<ImportedAttributesRepo> attributes) = 0;

    virtual const ImportedAttributesRepo *getImportedAttributes() const = 0;
};

} // namespace proton

