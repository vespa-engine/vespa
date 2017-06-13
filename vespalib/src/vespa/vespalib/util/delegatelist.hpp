// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2009 Yahoo

#pragma once

#include "eventbarrier.hpp"
#include "sync.h"

namespace vespalib {

/**
 * Data structure for robust event multi-casting in a multi-threaded
 * environment. The state tracked by this class can be modeled as a
 * set of bald object pointers; the delegates. All interaction with
 * the delegates is done through a snapshot of the delegate list. The
 * list may be modified at any time. Modifications will not be visible
 * to already existing snapshots. A separate method may be used to
 * wait for the destruction of all currently active snapshots. This
 * synchronization will ensure visibility of any previous
 * modifications to the delegate list. State snapshotting is
 * implemented with reference counted immutable lists. Snapshot
 * waiting is implemented using event barriers.
 **/
template <typename T>
class DelegateList
{
private:
    /**
     * Simple class used to synchronize with the completion of an
     * event barrier by coupling barrier completion to a gate.
     **/
    struct Sync
    {
        Gate gate;
        Sync() : gate() {}
        void completeBarrier() { gate.countDown(); }
    };

    /**
     * Inner structure used when keeping track of a set of delegates.
     **/
    struct Node {
        uint32_t refcnt;   // the number of incoming pointers to this node.
        T       *delegate; // the delegate tracked by this node.
        Node    *next;     // the next node in this list.
    };

    Lock               _lock;        // lock protecting access to this object
    Node              *_head;        // head of current list of delegates
    EventBarrier<Sync> _barrier;     // object used to resolve event barriers
    Node              *_freeNodes;   // a list of recyclable internal nodes
    int                _activeNodes; // number of internal nodes currently in use
    ArrayQueue<T*>     _stack;       // explicit stack for cheap 'recursion'

    /**
     * Allocate a new node and initialize it with the given data
     * members. Nodes are recycled internally by this object to reduce
     * overhead.
     *
     * @return the new node
     * @param delegate the delegate for the new node
     * @param next the next pointer for the new node
     **/
    Node *allocNode(T *delegate, Node *next) {
        Node *node = _freeNodes;
        if (node != 0) {
            _freeNodes = node->next;
        } else {
            node = new Node();
        }
        node->refcnt = 1;
        node->delegate = delegate;
        node->next = next;
        ++_activeNodes;
        return node;
    }

    /**
     * Copy a list of nodes. This will increase the reference count of
     * the list.
     *
     * @return the copy of the list
     * @param list the list to copy
     **/
    Node *copyList(Node *list) {
        if (list != 0) {
            ++list->refcnt;
        }
        return list;
    }

    /**
     * Free a list of nodes. This will decrease the reference count of
     * the list. Any nodes no longer in use will be put back on the
     * internal free list.
     *
     * @return 0
     * @param list the list to free
     **/
    Node *freeList(Node *list) {
        while (list != 0 && --list->refcnt == 0) {
            Node *node = list;
            list = node->next;
            node->next = _freeNodes;
            _freeNodes = node;
            --_activeNodes;
        }
        return 0;
    }

    DelegateList(const DelegateList &);
    DelegateList &operator=(const DelegateList &);

public:
    /**
     * A snapshot of a delegate list. The only way to access the
     * delegates kept by a delegate list is to create a snapshot of
     * it. The snapshot lets the user traverse the list of delegates,
     * accessing each of them in turn. The existence of a snapshot is
     * used by the delegate list to identify that someone is observing
     * the delegate list in a specific state. Snapshots should be
     * created on the stack in a scope as close as possible to the
     * code actually accessing the delegates. The delegate list itself
     * may not be destructed until all snapshots of that list have
     * been destructed.
     **/
    class Snapshot
    {
    private:
        DelegateList &_list;  // the parent object
        Node         *_head;  // head of the snapshotted list
        Node         *_node;  // current position within snapshot
        uint32_t      _token; // token used for barrier resolving

        Snapshot(const Snapshot &);
        Snapshot &operator=(const Snapshot &);
    public:
        /**
         * Create a snapshot of the given delegate list. The snapshots
         * current position will be set to the first delegate part of
         * the snapshot.
         *
         * @param list the delegate list we are snapshotting
         **/
        explicit Snapshot(DelegateList &list) : _list(list) {
            LockGuard guard(list._lock);
            _head = list.copyList(list._head);
            _node = _head;
            _token = list._barrier.startEvent();
        }

        /**
         * Destructing a snapshot will tell the delegate list that we
         * are no longer accessing the list in the state observed by
         * the snapshot.
         **/
        ~Snapshot() {
            LockGuard guard(_list._lock);
            _list.freeList(_head);
            _list._barrier.completeEvent(_token);
        }

        /**
         * Check whether the current delegate is valid. A snapshot
         * becomes invalid after the user has stepped through all
         * delegates part of the snapshot using the 'next' method.
         *
         * @return true if the current delegate is valid
         **/
        bool valid() const {
            return (_node != 0);
        }

        /**
         * Step to the next delegate. This method may only be called
         * if the 'valid' method returns true.
         **/
        void next() {
            _node = _node->next;
        }

        /**
         * Get the current delegate. This method may only be called if
         * the 'valid' method returns true.
         *
         * @return current delegate
         **/
        T *get() const {
            return _node->delegate;
        }
    };

    /**
     * Create an initially empty delegate list.
     **/
    DelegateList()
        : _lock(),
          _head(0),
          _barrier(),
          _freeNodes(0),
          _activeNodes(0),
          _stack()
    {
    }

    /**
     * The destructor will clean up internal memory usage. The
     * delegate list itself does not need to be empty when it is
     * deleted. However, there may be no active snapshots of it and
     * no-one may be waiting for snapshot destruction.
     **/
    ~DelegateList() {
        freeList(_head);
        assert(_barrier.countBarriers() == 0);
        assert(_barrier.countEvents() == 0);
        assert(_activeNodes == 0);
        while (_freeNodes != 0) {
            Node *node = _freeNodes;
            _freeNodes = node->next;
            delete node;
        }
    }

    /**
     * Add a delegate to this list. Adding a delegate that is already
     * in the list will have no effect.
     *
     * @return this object, for chaining
     * @param delegate the delegate to add
     **/
    DelegateList &add(T *delegate) {
        LockGuard guard(_lock);
        Node *node = _head;
        while (node != 0 && node->delegate != delegate) {
            node = node->next;
        }
        if (node == 0) {
            _head = allocNode(delegate, _head);
        }
        return *this;
    }

    /**
     * Remove a delegate from this list.
     *
     * @return this object, for chaining
     * @param delegate the delegate to remove
     **/
    DelegateList &remove(T *delegate) {
        LockGuard guard(_lock);
        _stack.clear();
        Node *node = _head;
        while (node != 0 && node->delegate != delegate) {
            _stack.push(node->delegate);
            node = node->next;
        }
        if (node != 0) { // delegate found in list
            node = copyList(node->next);
            while (!_stack.empty()) {
                try {
                    node = allocNode(_stack.back(), node);
                } catch (...) {
                    freeList(node);
                    throw;
                }
                _stack.popBack();
            }
            freeList(_head);
            _head = node;
        }
        return *this;
    }

    /**
     * Remove all delegates currently in this list.
     *
     * @return this object, for chaining
     **/
    DelegateList &clear() {
        LockGuard guard(_lock);
        _head = freeList(_head);
        return *this;
    }

    /**
     * Wait for the destruction of all currently active snapshots of
     * this list. This method will block until all relevant snapshots
     * are destructed. The creation of new snapshots will not
     * interfere with the completion of this method. This method is
     * used to enforce visibility between threads; after this method
     * returns, any modifications performed on the list before this
     * method was invoked will be visible to all threads.
     *
     * @return this object, for chaining
     **/
    DelegateList &waitSnapshots() {
        Sync sync;
        {
            LockGuard guard(_lock);
            if (!_barrier.startBarrier(sync)) {
                return *this;
            }
        }
        sync.gate.await();
        return *this;
    }
};

} // namespace vespalib

