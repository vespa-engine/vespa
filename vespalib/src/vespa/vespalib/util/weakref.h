// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2005 Overture Services Norway AS

#pragma once

#include <algorithm>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/atomic.h>

namespace vespalib {

/**
 * @brief A WeakRef is a pointer to an object that may disappear
 *
 * The object pointer is owned by a WeakRef::Owner. The owner can
 * decide to remove the pointer at any time, but not while it is being
 * used. To signal that a WeakRef is being used, you need to create a
 * WeakRef::Usage. This will ensure that the pointer is not removed
 * until you are done using it.
 **/
template <typename T>
class WeakRef
{
public:
    class Usage;
    friend class WeakRef::Usage;
private:
    struct Core {
        Monitor   monitor;
        uint32_t  refcnt;
        uint32_t  usecnt;
        bool      dead;
        T        *pt;

        Core(T *p) : monitor("WeakRef::Core", true),
                     refcnt(1), usecnt(0), dead(false), pt(p) {}
        ~Core() {
            assert(refcnt == 0);
            assert(usecnt == 0);
            assert(dead);
            assert(pt == 0);
        }

        Core *getRef() {
            Atomic::postInc(&refcnt);
            return this;
        }

        void dropRef() {
            if (Atomic::postDec(&refcnt) != 1) {
                return;
            }
            delete this;
        }

        Core *getUse() {
            MonitorGuard mon(monitor);
            if (dead) {
                return 0;
            }
            ++usecnt;
            return getRef();
        }

        void dropUse() {
            {
                MonitorGuard mon(monitor);
                --usecnt;
                if (dead && usecnt == 0) {
                    mon.signal();
                }
            }
            dropRef();
        }

        void kill() {
            {
                MonitorGuard mon(monitor);
                dead = true;
                while (usecnt != 0) {
                    mon.wait();
                }
                pt = 0;
            }
            dropRef();
        }

    private:
        Core(const Core &);
        Core &operator=(const Core &);
    };

public:
    /**
     * @brief A WeakRef::Owner owns the object pointer used by WeakRef
     * instances.
     **/
    class Owner {
        friend class WeakRef;

    private:
        Core *_core;
        Owner(const Owner &);
        Owner &operator=(const Owner &);

        Core *getRef() const { return (_core != 0) ? _core->getRef() : 0; }

    public:
        /**
         * @brief Create an owner with the given pointer
         *
         * @param pt object pointer
         **/
        Owner(T *pt) : _core(new Core(pt)) {}

        /**
         * @brief Remove the object pointer
         *
         * This method will block until all current usage of the
         * object pointer is complete (All WeakRef::Usage instances
         * created from WeakRef instances based on this WeakRef::Owner
         * has been destructed). Any further usage of the object
         * pointer will be denied.
         **/
        void clear() {
            if (_core != 0) {
                _core->kill();
                _core = 0;
            }
        }

        /**
         * @brief Remove the object pointer if it is not yet removed
         *
         * Note that if you use an embedded owner to point to the
         * embedding object, you should invoke the clear method at an
         * earlier stage in the destruction process of the embedding
         * object to avoid a weak reference to a half-destructed
         * object.
         **/
        ~Owner() { clear(); }
    };

    /**
     * @brief A WeakRef::Usage signals that a WeakRef is in use
     **/
    class Usage {
    private:
        Core *_core;
        Usage(const Usage &);
        Usage &operator=(const Usage &);

    public:
        /**
         * @brief Start using the given WeakRef
         *
         * @param rhs the WeakRef you want to use
         **/
        Usage(const WeakRef &rhs) : _core(rhs.getUse()) {}

        /**
         * @brief Stop using the underlying WeakRef
         *
         * This will signal that the WeakRef given in the constructor
         * is no longer in use.
         **/
        ~Usage() {
            if (_core != 0) {
                _core->dropUse();
            }
        }

        /**
         * @brief Check if the object pointer is valid
         *
         * This method will return false if we try to use a WeakRef
         * after the WeakRef::Owner has cleared the object pointer. If
         * this method returns true, the object pointer will not
         * become invalid while this object is alive.
         *
         * @return true if the object pointer is still valid
         **/
        bool valid() const { return (_core != 0); }

        /**
         * @brief Access the weakly referenced object
         *
         * This is the preferred way to access the weakly referenced
         * object as it makes the usage object act as a smart pointer.
         *
         * @return object pointer
         **/
        T *operator->() const { return _core->pt; }

        /**
         * @brief Obtain the weakly referenced object
         *
         * @return object pointer
         **/
        T *get() const { return _core->pt; }
    };

private:
    Core *_core;

    Core *getRef() const { return (_core != 0) ? _core->getRef() : 0; }
    Core *getUse() const { return (_core != 0) ? _core->getUse() : 0; }

public:
    /**
     * @brief Create a WeakRef not pointing to anything
     **/
    WeakRef() : _core(0) {}

    /**
     * @brief Copy constructor
     *
     * This will result in a WeakRef pointing to the same object as
     * <i>rhs</i>
     *
     * @param rhs copy this
     **/
    WeakRef(const WeakRef &rhs) : _core(rhs.getRef()) {}

    /**
     * @brief Create a WeakRef based on the given WeakRef::Owner
     *
     * This WeakRef will point to the object dictated by rhs
     *
     * @param rhs pointer owner
     **/
    WeakRef(const WeakRef::Owner &rhs) : _core(rhs.getRef()) {}

    /**
     * @brief Assignment operator
     *
     * This is implemented as a copy-swap-delete operation.
     *
     * @return reference to this
     * @param rhs copy this
     **/
    WeakRef &operator=(const WeakRef &rhs) {
        WeakRef tmp(rhs);
        std::swap(tmp._core, _core);
        return *this;
    }

    /**
     * @brief Perform internal cleanup.
     **/
    ~WeakRef() {
        if (_core != 0) {
            _core->dropRef();
        }
    }
};

} // namespace vespalib

