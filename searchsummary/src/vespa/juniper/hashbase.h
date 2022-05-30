// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdio.h>
#include <cassert>

// Simple default order that everybody has - pointer order:
template <typename T>
struct PtrComparator
{
    inline bool operator()(T m1, T m2)
    {
        return m1 < m2;
    }
};

template <typename Key, typename T, int _tableSize, typename Comparator = PtrComparator<T> >
class Fast_HashTable;

template<typename Key, typename T>
class Fast_HashTableElement
{
private:
    Fast_HashTableElement(const Fast_HashTableElement&);
    Fast_HashTableElement& operator=(const Fast_HashTableElement&);

protected:

    Key _key;
    Fast_HashTableElement *_next;
    T _item;

public:

    Fast_HashTableElement(Key key,
                          Fast_HashTableElement<Key, T> *next,
                          T item)
        : _key(key), _next(next), _item(item) {}
    ~Fast_HashTableElement(){}

    inline Fast_HashTableElement<Key, T> *GetNext(void) { return _next; }
    inline void SetNext(Fast_HashTableElement<Key, T> *next) { _next = next; }
    inline Key GetKey(void) { return _key; }
    inline T GetItem(void) { return _item; }
};



template <typename Key, typename T, int _tableSize>
class Fast_HashTableIterator
{
    friend class Fast_HashTable<Key, T, _tableSize>;

private:

    const Fast_HashTable<Key, T, _tableSize> *_hashTable;
    int _index;
    Fast_HashTableElement<Key, T> *_runner; //current element in list

protected:

    Fast_HashTableIterator(const Fast_HashTable<Key, T, _tableSize>& hashTable) : _hashTable(&hashTable), _index(-1)
    {
        _runner = SearchNext();
    };


    Fast_HashTableElement<Key, T> *SearchNext(void)
    {
        Fast_HashTableElement<Key, T> *retVal = NULL;

        for (++_index; _index<_hashTable->_tableSize; _index++)
        {
            retVal = _hashTable->_lookupTable[_index];

            if (retVal != NULL)
                break;
        }

        return retVal;
    }


public:

    inline T GetCurrent() { return _runner->GetItem(); };
    inline Key GetCurrentKey() { return _runner->GetKey(); }

    inline void Next()
    {
        if (_runner != NULL)
        {
            _runner = _runner->GetNext();

            if (_runner == NULL)
            {
                _runner = SearchNext();
            }
        }
    };

    inline bool End() const { return _runner == NULL; };
    // becomes true when ++ on the last element

    inline void Rewind(void)
    {
        _runner = NULL;
        _index = -1;

        _runner = SearchNext();
    };
};


template <typename Key, typename T, int _tableSize = 0x10,  typename Comparator>
class Fast_HashTable
{
private:
    Fast_HashTable(Fast_HashTable &);
    Fast_HashTable &operator=(Fast_HashTable &);

public:
    typedef Fast_HashTableElement<Key, T> element;
    typedef Fast_HashTableIterator<Key, T, _tableSize> iterator;
    typedef Key keytype;

    friend class Fast_HashTableIterator<Key, T, _tableSize>;

protected:

    int _numElements;
    element **_lookupTable;
    Comparator _compare;

    inline int HashFunction(Key key)
    {
        if constexpr ((_tableSize & (_tableSize - 1)) == 0) {
            return (key & (_tableSize - 1));
        } else {
            return (key % _tableSize);
        }
    }

public:
    Fast_HashTable() : _numElements(0), _lookupTable(NULL), _compare()
    {
        typedef element dummyDef;
        _lookupTable = new dummyDef* [_tableSize];
        memset(_lookupTable, 0, _tableSize * sizeof(element *));
    }


    Fast_HashTableIterator<Key, T, _tableSize> *NewIterator(void)
    {
        return new iterator(*this);
    }

    inline int ElementCount(void) { return _numElements; }

    inline void Clear(void)
    {
        if (_numElements == 0) return;
        for (int i=0; i<_tableSize; i++)
        {
            element *curr, *prev=NULL;

            for (curr=_lookupTable[i]; curr != NULL; curr=curr->GetNext())
            {
                if (prev != NULL)
                {
                    delete prev;
                    _numElements--;
                    if (_numElements == 0) break;
                }
                prev = curr;
                _lookupTable[i] = NULL;
            }

            if (prev != NULL) delete prev;
        }
    }


    Key Insert(Key key, T item)
    {
        int pos = HashFunction(key);

        if (_lookupTable[pos] == NULL || !_compare(item, _lookupTable[pos]->GetItem()))
        {
            _lookupTable[pos] = new element(key, _lookupTable[pos], item);
        }
        else
        {
            element* pel = _lookupTable[pos];
            element* el = pel->GetNext();
            while (el && _compare(item, el->GetItem()))
            {
                pel = el;
                el = el->GetNext();
            }
            pel->SetNext(new element(key, el, item));
        }

        _numElements++;

        return _lookupTable[pos]->GetKey();
    }


    T Find(Key key)
    {
        T retVal;
        retVal = NULL;

        int pos = HashFunction(key);

        for (element *curr=_lookupTable[pos]; curr != NULL; curr=curr->GetNext())
        {
            if (curr->GetKey() == key)
            {
                retVal = curr->GetItem();
                break;
            }
        }

        return retVal;
    }


    element* FindRef(Key key)
    {
        int pos = HashFunction(key);

        for (element *curr=_lookupTable[pos]; curr != NULL; curr=curr->GetNext())
            if (curr->GetKey() == key) return curr;
        return NULL;
    }


    T Remove(Key key)
    {
        T retVal = NULL;

        int pos = HashFunction(key);

        element *curr=_lookupTable[pos];
        element *prev = NULL;

        for (; curr != NULL; curr=curr->GetNext())
        {
            if (curr->GetKey() == key)
            {
                retVal = curr->GetItem();
                break;
            }

            prev = curr;
        }

        if (curr != NULL)
        {
            if (prev != NULL)
            {
                prev->SetNext(curr->GetNext());
            }
            else
            {
                _lookupTable[pos] = curr->GetNext();
            }

            _numElements--;

            delete curr;
        }

        return retVal;
    }



    void RemoveItem(T item)
    {
        for (int i=0; i<_tableSize; i++)
        {
            element *curr = _lookupTable[i];
            element *prev = NULL;

            while(curr != NULL)
            {
                if (item == curr->GetItem())
                {
                    // Found item to delete
                    element *toBeDeleted = curr;

                    curr = curr->GetNext();

                    if (prev != NULL)
                    {
                        prev->SetNext(curr);
                    }
                    else
                    {
                        _lookupTable[i] = curr;
                    }

                    _numElements--;

                    delete toBeDeleted;
                }
                else
                {
                    prev = curr;
                    curr = curr->GetNext();
                }
            }
        }
    }



    void Print(void)
    {
        for (int i=0; i<_tableSize; i++)
        {
            if (_lookupTable[i] != NULL)
            {
                printf("[%i]", i);

                for (element *curr=_lookupTable[i]; curr != NULL; curr=curr->GetNext())
                {
                    printf(" -> %u", curr->GetKey());
                }

                printf("\n");
            }
        }
    }



    virtual ~Fast_HashTable(void)
    {
        Clear();
        delete [] _lookupTable;
    }

};

