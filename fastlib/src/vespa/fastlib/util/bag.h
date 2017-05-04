// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//**************************************************************************
/**
 * @author B�rd Kvalheim
 * @version $Id$
 * @file
 */
/*
 * @date Creation date: 2000-03-22
 **************************************************************************/

#pragma once

template<class Type>
class Fast_BagIterator;

/**
 * A Fast_Bag is a collection which can contain duplicates. The
 * only way to access the elements in a bag is via the
 * Fast_BagIterator. The Fast_BagIterator is also capable of
 * removing the current element.
 *
 * The Fast_BagIterator is used to iterate over the elements in
 * the bag. It can be initiated by a pointer or a reference to a
 * bag. The recomended use is to init the Fast_BagIterator of the
 * stack and not on the heap - to avoid new and potential memory leaks.
 *
 * The Fast_BagIterator can be new'ed with the empty constructor
 * or initialized with a pointer or a reference to a bag. It can be
 * rewind with the Start() method accepting a bag pointer or reference.
 *
 * The internal array are resized with the private Grow() method.
 * Choose the block size carefully to avoid overhead, since the Grow
 * method is costly: If an array grows or are copied the elements in
 * the array are copied with the = operator. In other words, the copy
 * constructor of the objects are used.
 *
 * The amount the bag size are double each time the bag grows. The next
 * grow-amount can be set with the void SetBlocksize(int bs)
 * method.
 *
 *  Example of use:
 *  <pre>
 *  // Init. 10 is start size. It grows as needed.
 *  Fast_Bag<int> bag(10);
 *
 *  // Insert 4 elements
 *  bag.Insert(1);
 *  bag.Insert(2);
 *  bag.Insert(3);
 *  bag.Insert(4);
 *
 *  // Iterate through and delete element equal to  2.
 *  for(Fast_BagIterator<int> iter(bag);
 *  	   !iter.End();
 *  	   iter.Next()) {
 *    if(iter.GetCurrent() == 2) {
 *      iter.RemoveCurrent();
 *    }
 *  }
 * </pre>
 */

template <class Type>
class Fast_Bag
{
    friend class Fast_BagIterator<Type>;

protected:
    /**
     * The current capacity of the bag
     */
    int _capacity;

    /**
     * The actual array
     */
    Type*  _array;

    /**
     * The block size used when the bag is growing
     */
    int _blocksize;

    /**
     * The number of elements in the bag
     */
    int _numElements;

private:
    /**
     * Increases the bag size with _blocksize
     */
    void Grow();

public:

    typedef Type value_type;

    /**
     * Default constructor. Sets the capacity to 0 and the
     * _blocksize to 1.  Very inefficient. Should only be used for
     * testing
     */
    Fast_Bag();

    /**
     * Copy constructor. Uses the element's = operator to make the
     * copy. Hence, a deep copy are only done when actual objects are
     * stored in the array and they implement a proper copy constructor.
     * @param source the orginal array to copy from.
     */
    Fast_Bag(const Fast_Bag<Type>& source);

    /**
     * Constructor setting the _capacity . The _blocksize is
     * set to capacity.
     * @param capacity the initial capacity of the bag
     */
    Fast_Bag(const int capacity);

    /**
     * Constructor setting the _capacity and the _blocksize
     * @param capacity the initial _capacity of the bag
     * @param blocksize the initial _blocksize of the bag */
    Fast_Bag(const int capacity, const int blocksize);

    ~Fast_Bag();

    /**
     * Assignment operator.
     * @param other the orginal array to assign from
     * @return reference to the newly assigned bag
     */
    Fast_Bag& operator=(const Fast_Bag<Type>& other);

    /**
     * Comparison operator
     * @param other the right side of ==
     * @return true/false
     */
    bool operator==(const Fast_Bag<Type>& other) const;


    /**
     * The _blocksize is the size the bag grows with when it grows
     * @return the _blocksize
     */
    inline int  GetBlocksize() const { return _blocksize; }

    /**
     * Set a new _blocksize
     * @param blocksize the new _blocksize
     */
    void SetBlocksize(const int blocksize);

    /**
     * @return the number of elements in the bag
     */
    inline int  NumberOfElements() const { return _numElements; }

    /**
     * Adds a new element to the bag
     * @param element the element to add
     */
    void Insert(const Type element);

    /**
     * Removes all the elements in the bag
     */
    void RemoveAllElements();

    /**
     * Removes a element for the bag.
     * @param element the element to be removed.
     */
    void RemoveElement(const Type element);

    /**
     * Returns true iff the element is in the bag
     * @param element the element to look up.
     */
    bool HasElement(const Type element);
};

//**************************************************************************
/**
 * A Fast_BagIterator. The Fast_BagIterator can delete the
 * current element from the Fast_Bag. Ensures that all elements
 * are visited before the end is reached. The Fast_BagIterator can
 * reused with calls to Start(Fast_Bag<Type>*) or
 * Start(Fast_Bag<Type>&)
 *
 *  Example of use:
 *  <pre>
 *  // Init. 10 is start size. It grows as needed.
 *  Fast_Bag<int> bag(10);
 *
 *  // Insert 4 elements
 *  bag.Insert(1);
 *  bag.Insert(2);
 *  bag.Insert(3);
 *  bag.Insert(4);
 *
 *  // Iterate through and delete element equal to  2.
 *  for(Fast_BagIterator<int> iter(bag);
 *  	   !iter.End();
 *  	   iter.Next()) {
 *    if(iter.GetCurrent() == 2) {
 *      iter.RemoveCurrent();
 *    }
 *  }
 * </pre>
 */

template <class Type>
class Fast_BagIterator
{
    friend class Fast_Bag<Type>;

private:

    /**
     * Pointer to the Fast_Bag.
     */
    Fast_Bag<Type>* _bag;

    /**
     * Pointer to the array used to represent the Fast_Bag.
     */
    Type* _array;

    /**
     * The point in the Fast_Bag the iterator has come to.
     */
    int _index;

    /**
     * Flag used to indicate if the iterator has reached the "end". The
     * value of GetCurrent() will still be the "last" element in
     * the Fast_Bag.
     */
    bool _end;


public:
    /**
     * Private copy-constructor the avoid usage.
     */
    Fast_BagIterator(const Fast_BagIterator& source) :
        _bag(source._bag),
        _array(source._array),
        _index(source._index),
	_end(source._end) {}

    Fast_BagIterator &operator=(const Fast_BagIterator& source)
    {
        _bag = source._bag;
        _array = source._array;
        _index = source._index;
        _end = source._end;
        return *this;
    }
    /**
     * Default constructor, the iterator if not initialized with a
     * Fast_Bag
     */
    Fast_BagIterator(void) :
	_bag(NULL),
	_array(NULL),
	_index(0),
	_end(true)
    {

    }

    /**
     * Contructor that inits the Fast_bag with a pointer to a Fast_Bag
     * @param bag pointer to a Fast_Bag of the same Type as the iterator
     */
    inline Fast_BagIterator(Fast_Bag<Type>* bag) :
        _bag(bag),
        _array(bag->_array),
        _index(0),
        _end(bag->NumberOfElements() == 0)
    {
    }


    /**
     * Contructor that inits the Fast_BagIterator with a reference to a Fast_Bag
     * @param bag reference to a Fast_Bag of the same Type as the iterator
     */
    inline Fast_BagIterator(Fast_Bag<Type>& bag) :
        _bag(&bag),
        _array(bag._array),
        _index(0),
        _end(bag.NumberOfElements() == 0)
    {
    }

    /**
     * Destructor - nothing to do
     (commented out because having it generated warning)
     ~Fast_BagIterator(void){};
    */

    /**
     * Reinits (or intis) the Fast_BagIterator with another
     * Fast_Bag Alows the user to reuse a Fast_BagIterator
     * @param bag the Fast_Bag to init the Fast_BagIterator with
     */
    inline void Start(Fast_Bag<Type>* bag) {
	_bag = bag;
	_array = bag->_array;
	_index = 0;
	if(_bag->NumberOfElements() == 0) {
            _end = true;
	} else {
            _end = false;
	}
    }

    /**
     * Reinits (or intis) the Fast_BagIterator with another
     * Fast_Bag. Alows the user to reuse a Fast_BagIterator.
     * @param bag the Fast_Bag to init the Fast_BagIterator with
     */
    inline void Start(Fast_Bag<Type>& bag) {
	_bag = &bag;
	_array = bag._array;
	_index = 0;
	if(_bag->NumberOfElements() == 0) {
            _end = true;
	} else {
            _end = false;
	}
    }



    /**
     * @return the current element
     */
    inline Type GetCurrent() {
	return _array[_index];
    }

    /**
     * Moves the Fast_BagIterator to the next element. If the
     * Fast_BagIterator already are at the end the _end flag is set to
     * true.
     */
    inline void Next() {

	// If we are at the end and should return.
	if(_end) {
            return;
	}
	_index = _index + 1;

	if(_index == _bag->NumberOfElements()) {
            // Now the return value from GetCurrent are undefined.
            _end = true;
	}
    }

    /**
     * Deletes the current element in the Fast_Bag and moves the
     * Fast_BagIterator to the previous
     */
    inline void RemoveCurrent() {
        _bag->_numElements = _bag->_numElements - 1;

        _array[_index] = _array[_bag->NumberOfElements()];

	_index = _index - 1;
    }

    /**
     * @return true if the Fast_BagIterator is at the end
     */
    inline bool End() const { return _end; }

};


template <typename Type>
Fast_BagIterator<Type>& operator++(Fast_BagIterator<Type>& bi) {
    bi.Next();
    return bi;
}

template <typename Type>
Fast_BagIterator<Type> operator++(Fast_BagIterator<Type>& bi, int) {
    Fast_BagIterator<Type> old = bi;
    bi.Next();
    return old;
}



template <class Type>
Fast_Bag<Type>::Fast_Bag() :
    _capacity(1),
    _array(new Type[_capacity]),
    _blocksize(1),
    _numElements(0)
{

}


template <class Type>
Fast_Bag<Type>::Fast_Bag(const Fast_Bag<Type>& source) :
    _capacity(source._capacity),
    _array(NULL),
    _blocksize(source._blocksize),
    _numElements(source._numElements)
{
    // Self assignment
    if(this == &source) return;

    _array = new Type[_capacity];

    for(int i = 0; i < _capacity; i++) {
	_array[i] = source._array[i];
    }
}


template <class Type>
Fast_Bag<Type>::Fast_Bag(const int capacity) :
    _capacity(capacity),
    _array(new Type[capacity]),
    _blocksize(capacity),
    _numElements(0)
{

}


template <class Type>
Fast_Bag<Type>::Fast_Bag(const int capacity, const int blocksize) :
    _capacity(capacity),
    _array(new Type[capacity]),
    _blocksize(blocksize),
    _numElements(0)
{

}


template <class Type>
Fast_Bag<Type>::~Fast_Bag()
{
    delete[] _array;
}


template<class Type>
inline Fast_Bag<Type>& Fast_Bag<Type>::operator=(const Fast_Bag<Type>& other)
{
    // Self assignment
    if(this == &other) return *this;

    if(_array != NULL) {
	delete[] _array;
    }

    _numElements = other._numElements;
    _capacity = other._capacity;
    _blocksize = other._blocksize;
    _array = new Type[_capacity];

    for(int i = 0; i < _numElements; i++) {
	_array[i] = other._array[i];
    }

    return *this;
}


template<class Type>
inline bool Fast_Bag<Type>::operator==(const Fast_Bag<Type>& other) const
{
    if(_numElements == other._numElements &&
       _capacity     == other._capacity   &&
       _blocksize    == other._blocksize)
    {
	for(int i = 0; i < _numElements; i++) {
            if (!(_array[i] == other._array[i])) return false;
	}
	return true;
    } else {
	return false;
    }
}


template <class Type>
void Fast_Bag<Type>::Grow()
{
    int newCapacity =  _capacity + _blocksize;

    // Grow exponentially
    _blocksize = newCapacity;

    Type* newArray = new Type[newCapacity];

    for(int i = 0; i < _capacity; i++) {
	newArray[i] = _array[i];
    }

    _capacity = newCapacity;

    delete[] _array;
    _array = newArray;
}


template <class Type>
inline void Fast_Bag<Type>::SetBlocksize(const int blocksize)
{
    if(blocksize > 0 ) {
	_blocksize = blocksize;
    }
}


template <class Type>
inline void Fast_Bag<Type>::Insert(const Type element)
{
    if(_numElements == _capacity) Grow();
    _array[_numElements++] = element;
}


template <class Type>
void Fast_Bag<Type>::RemoveAllElements()
{
    delete[] _array;
    _numElements = 0;
    _array = new Type[_capacity];
}


template <class Type>
void Fast_Bag<Type>::RemoveElement(const Type element)
{
    for(Fast_BagIterator<Type> it(this);
        !it.End();
        it.Next()) {
        if(it.GetCurrent() == element) {
            it.RemoveCurrent();
        }
    }
}

template <class Type>
bool Fast_Bag<Type>::HasElement(const Type element)
{
    bool retVal=false;
    for(Fast_BagIterator<Type> it(this);
        !it.End();
        it.Next()) {
        if(it.GetCurrent() == element) {
            retVal=true;
            break;
        }
    }
    return retVal;
}
