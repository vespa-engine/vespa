// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @ingroup storageutil
 *
 * @brief Functors ards storage, not dependent on external messaging.
 *
 * @author H�kon Humberset
 * @date 2005-05-13
 * @version $Id$
 */

#pragma once

namespace storage {

class Functor {
public:

    /**
     * For instance, using this functor you can say:
     *
     * string mystring("this is a test");
     * for_each(mystring.begin(), mystring.end(),
     *          Functor.Replace<char>(' ', '_'));
     *
     * or
     *
     * vector<string> myvector;
     * for_each(myvector.begin(), myvector.end(),
     *          Functor.Replace<string>("this", "that"));
     */
    template<class T>
    class Replace {
    private:
        const T& _what;
        const T& _with;

    public:
        Replace(const T& what, const T& with)
            : _what(what),
              _with(with) {}

        void operator()(T& element) const
            { if (element == _what) element = _with; }
    };

    /**
     * To easily delete containers of pointers.
     *
     * for_each(myvec.begin(), myvec.end(), Functor::DeletePointer());
     */
    class DeletePointer {
    public:
        template<class T> void operator()(T *ptr) const { delete ptr; }
    };

};

}

