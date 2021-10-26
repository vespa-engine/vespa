// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iterator>
#include <cassert>

namespace vespalib {

template<typename T>
struct IntrusiveListNode
{
    IntrusiveListNode * prev;
    IntrusiveListNode * next;

    IntrusiveListNode() : prev(this), next(this) {
        static_assert(std::is_base_of<IntrusiveListNode, T>::value);
    }

    bool in_list() const { return prev != this; }
    bool is_free() const { return prev == this; }

    void remove_from_list() {
        auto old_prev = prev;
        auto old_next = next;
        old_prev->next = old_next;
        old_next->prev = old_prev;
        prev = this;
        next = this;
    }
};


template<typename T, bool is_const>
class intrusive_list_node_iterator {
    using NodeBase = IntrusiveListNode<T>;
    using CT = std::conditional<is_const, const T, T>::type;
    using Node = std::conditional<is_const, const NodeBase, NodeBase>::type;
    static CT* as_t(Node *p) { return static_cast<CT*>(p); }
public:
    using iterator_category = std::bidirectional_iterator_tag;
    using value_type = T;
    using difference_type = std::ptrdiff_t;
    using pointer = CT*;
    using reference = CT&;

    CT& operator* () const { return *as_t(_current); }
    CT* operator-> () const { return as_t(_current); }

    intrusive_list_node_iterator& operator++ () { _current = _current->next; return *this; }
    intrusive_list_node_iterator& operator-- () { _current = _current->prev; return *this; }
    intrusive_list_node_iterator operator++ (int) { intrusive_list_node_iterator old = *this; operator++(); return old; }
    intrusive_list_node_iterator operator-- (int) { intrusive_list_node_iterator old = *this; operator--(); return old; }

    bool operator== (const intrusive_list_node_iterator& other) const {
        return _current == other._current;
    }
    bool operator!= (const intrusive_list_node_iterator& other) const {
        return _current != other._current;
    }
    intrusive_list_node_iterator() : _current(nullptr) {}

    explicit intrusive_list_node_iterator(Node* current) : _current(current) {}

    // conversion iterator -> const_iterator
    template<bool maybe = is_const, typename = std::enable_if<maybe>::type>
    intrusive_list_node_iterator(const intrusive_list_node_iterator<T, false> &other)
        : _current(&*other)
    {}

private:
    Node * _current;
};


template<typename T,
         typename NodeBase = IntrusiveListNode<T>,
         typename = std::is_base_of<NodeBase, T>::type>
class IntrusiveList
{
public:
    using Node = NodeBase;

    IntrusiveList() : _terminator() {}
    
    void push_back(T &node) {
        Node &n = node;
        assert(n.is_free());
        auto old_last = _terminator.prev;
        n.prev = old_last;
        n.next = &_terminator;
        old_last->next = &n;
        _terminator.prev = &n;
    }

    using iterator = intrusive_list_node_iterator<T, false>;
    using const_iterator = intrusive_list_node_iterator<T, true>;

    iterator begin() { return iterator(_terminator.next); }
    iterator end() { return iterator(&_terminator); }

    const_iterator cbegin() const { return const_iterator(_terminator.next); }
    const_iterator cend() const { return const_iterator(&_terminator); }

    bool empty() const { return (_terminator.next == &_terminator); }

private:
    Node _terminator;
};


} // namespace


