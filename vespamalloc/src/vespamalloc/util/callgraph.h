// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdio.h>
#include <vespamalloc/util/stream.h>
#include <memory>

namespace vespamalloc {

template<typename T, typename AddSub>
class CallGraphNode
{
public:
    CallGraphNode() : _callers(nullptr), _next(nullptr), _content(), _count(0) { }
    const CallGraphNode *next()    const { return _next; }
    const CallGraphNode *callers() const { return _callers; }
    const T & content()            const { return _content; }
    CallGraphNode *next()                { return _next; }
    CallGraphNode *callers()             { return _callers; }
    T & content()                        { return _content; }
    size_t count()                 const { return _count; }
    void content(const T & v)            { _content = v; }
    template <typename Store>
    bool addStack(const T * stack, size_t nelem, Store & store);
    template<typename Object>
    void traverseDepth(size_t depth, size_t width, Object func);
    template<typename Object>
    void traverseWidth(size_t depth, size_t width, Object & func);
    friend asciistream & operator << (asciistream & os, const CallGraphNode & v) {
        return os << v._content << '(' << v._count << ')';
    }
private:
    CallGraphNode * _callers;
    CallGraphNode * _next;
    T               _content;
    AddSub          _count;
};

template<typename T, typename AddSub>
template <typename Store>
bool CallGraphNode<T, AddSub>::addStack(const T * stack, size_t nelem, Store & store) {
    bool retval(false);
    if (nelem == 0) {
        retval = true;
    } else if (_content == stack[0]) {
        _count++;
        if (nelem > 1) {
            if (_callers == nullptr) {
                _callers = store.alloc();
                if (_callers != nullptr) {
                    _callers->content(stack[1]);
                }
            }
            if (_callers) {
                retval = _callers->addStack(stack+1, nelem-1, store);
            }
        } else {
            retval = true;
        }
    } else {
        if (_next == nullptr) {
            _next = store.alloc();
            if (_next != nullptr) {
                _next->content(stack[0]);
            }
        }
        if (_next) {
            retval = _next->addStack(stack, nelem, store);
        }
    }
    return retval;
}

template<typename T, typename AddSub>
template<typename Object>
void CallGraphNode<T, AddSub>::traverseDepth(size_t depth, size_t width, Object func) {
    Object newFunc(func);
    newFunc.handle(*this);
    if (_callers) {
        _callers->traverseDepth(depth+1, width, newFunc);
    }
    if (_next) {
        _next->traverseDepth(depth, width+1, func);
    }
}

template<typename T, typename AddSub>
template<typename Object>
void CallGraphNode<T, AddSub>::traverseWidth(size_t depth, size_t width, Object & func) {
    Object newFunc(func);
    newFunc.handle(*this);
    if (_next) {
        _next->traverseWidth(depth, width+1, newFunc);
    }
    if (_callers) {
        _callers->traverseWidth(depth+1, width, func);
    }
}

template<typename T, size_t MaxElem, typename AddSub>
class ArrayStore
{
public:
    ArrayStore() : _used(0) { }
    T * alloc() { return (_used < MaxElem) ? &_array[_used++] : nullptr; }
    AddSub size() const { return _used; }
private:
    AddSub _used;
    T      _array[MaxElem];
};

template <typename Content, size_t MaxElems, typename AddSub>
class CallGraph
{
public:
    using Node = CallGraphNode<Content, AddSub>;

    CallGraph() :
        _root(nullptr),
        _nodeStore(std::make_unique<NodeStore>())
    { }
    CallGraph(Content root) :
        _root(nullptr),
        _nodeStore(std::make_unique<NodeStore>())
    {
        checkOrSetRoot(root);
    }
    bool addStack(const Content * stack, size_t nelem) {
        checkOrSetRoot(stack[0]);
        return _root->addStack(stack, nelem, *_nodeStore);
    }
    template<typename Object>
    void traverseDepth(Object func) {
        if (_root) { _root->traverseDepth(0, 0, func); }
    }
    template<typename Object>
    void traverseWidth(Object func) {
        if (_root) {_root->traverseWidth(0, 0, func); }
    }
    size_t size() const { return _nodeStore->size(); }
    bool empty()  const { return size()==0; }
private:
    CallGraph(const CallGraph &);
    CallGraph & operator = (const CallGraph &);
    bool checkOrSetRoot(const Content & root) {
        if (_root == nullptr) {
            _root = _nodeStore->alloc();
            _root->content(root);
        }
        return (_root != nullptr);
    }
    typedef ArrayStore<Node, MaxElems, AddSub> NodeStore;
    Node                       * _root;
    std::unique_ptr<NodeStore>   _nodeStore;
};

}

