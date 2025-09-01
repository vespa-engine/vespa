// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "prioritizedbucket.h"
#include <vespa/storage/bucketdb/bucketdatabase.h>

namespace storage::distributor {

class BucketPriorityDatabase
{
protected:
    class ConstIteratorImpl
    {
    public:
        virtual ~ConstIteratorImpl() = default;
        virtual void increment() noexcept = 0;
        virtual bool equal(const ConstIteratorImpl& other) const noexcept = 0;
        virtual PrioritizedBucket dereference() const noexcept = 0;
    };

    using ConstIteratorImplPtr = std::unique_ptr<ConstIteratorImpl>;
public:
    // Note: does not fulfill LegacyIterator requirements.
    class ConstIterator final
    {
        ConstIteratorImplPtr _impl;
    public:
        explicit ConstIterator(ConstIteratorImplPtr impl) noexcept
            : _impl(std::move(impl))
        {}
        const PrioritizedBucket operator* () const noexcept { return dereference(); }
        void operator++() noexcept { increment(); }
        bool operator== (const ConstIterator& other) const noexcept {
            return equal(other);
        }
        ConstIterator(const ConstIterator &) = delete;
        ConstIterator(ConstIterator &&) noexcept = default;
        ~ConstIterator() = default;
    private:
        void increment() noexcept {
            _impl->increment();
        }

        [[nodiscard]] bool equal(const ConstIterator& other) const noexcept {
            return _impl->equal(*other._impl);
        }

        PrioritizedBucket dereference() const noexcept {
            return _impl->dereference();
        }
    };

    using const_iterator = ConstIterator;

    virtual ~BucketPriorityDatabase() = default;

    virtual const_iterator begin() const = 0;
    virtual const_iterator end() const = 0;
    virtual void setPriority(const PrioritizedBucket&) = 0;
};

}
