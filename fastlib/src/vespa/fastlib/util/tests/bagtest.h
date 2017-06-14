// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastlib/util/bag.h>
#include <vespa/fastlib/testsuite/test.h>
#include <vespa/fastos/app.h>
#include <iostream>

/**

 */
class Tester
{
private:
    bool _isTouched;

    int _i;

protected:
    Tester() : _isTouched(false), _i() { }

public:
    Tester(int i) : _isTouched(false), _i(i) { }

    ~Tester()  { }

    bool IsTouched() const { return _isTouched;  }

    void Touch() {
        if(_isTouched) { _isTouched = false; }
        else           { _isTouched = true;  }
    }

    int GetI() { return _i; }


};

std::ostream& operator<<(std::ostream& s, const Tester* t)
{
    return s << static_cast<const void*>(t) << ": " << (t->IsTouched()?"X":"-");
}


//Print method, not used when thing works
template <typename T> void PrintBag(Fast_Bag<T>* bag) {
    for (Fast_BagIterator<T> bagIterator(bag);
         !bagIterator.End();
         bagIterator.Next()) {
        std::cout << bagIterator.GetCurrent() << " ";
    }
    std::cout << std::endl;
}
//Print method, not used when thing works
template <typename T> void PrintArray(T* array, int n) {
    for(int i = 0; i < n; i++) {
        std::cout << array[i] << " ";
    }
    std::cout << std::endl;
}


class BagTester : public Test
{
private:
    BagTester(const BagTester &other);
    BagTester& operator=(const BagTester &other);

    Tester**           _array;
    Fast_Bag<Tester*>* _bag;

    int _elements;


    void RunTest(void (BagTester::*testFunc)()) {
        StartUp();
        (this->*testFunc)();
        TearDown();
    }


protected:

    /** Touches all elemenets in the bag */
    void TouchBag(Fast_Bag<Tester*>* bag) {
        for(Fast_BagIterator<Tester*> bagIterator(bag);
            !bagIterator.End();
            bagIterator.Next()) {
            bagIterator.GetCurrent()->Touch();
        }
    }


    /** Generate a test bag with num tester and init cap of max */
    void InitArray(int num, int maxCapacity = 0) {
        if (maxCapacity == 0) maxCapacity = num;
        _bag = new Fast_Bag<Tester*>(maxCapacity);
        for(int i = 0; i < num; i++) {
            Tester* tester = new Tester(i);
            _array[i] = tester;
            _bag->Insert(tester);
        }
        _elements = num;
    }

public:

    BagTester(void)
        : _array(NULL),
          _bag(NULL),
          _elements(0)
    {
    }


    /** Checks if the iterator new'ed with a bagpointer works */
    void IterPtrInitTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);

        TouchBag(_bag);

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }

    /** Checks if the iterator new'ed with a bagref works */
    void IterRefInitTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);

        for(Fast_BagIterator<Tester*> bagIterator(*_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            bagIterator.GetCurrent()->Touch();
        }

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }

    void IterPPOperTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);

        for(Fast_BagIterator<Tester*> bagIterator(*_bag);
            !bagIterator.End();
            ++bagIterator,
                bagIterator++) {
            bagIterator.GetCurrent()->Touch();
        }

        for(int i = 0 ; i < num; i = i + 2) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }


    /** Checks if the iterator new'ed with a bagref works */
    void IterPtrStartTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);

        Fast_BagIterator<Tester*> bagIterator;

        for(bagIterator.Start(*_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            bagIterator.GetCurrent()->Touch();
        }

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }



    /** Checks if the iterator new'ed with a bagref works */
    void IterRefStartTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);

        Fast_BagIterator<Tester*> bagIterator;

        for(bagIterator.Start(_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            bagIterator.GetCurrent()->Touch();
        }

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }




    void IterStartOverTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);

        int n = 0;
        Fast_BagIterator<Tester*> bagIterator;
        for(bagIterator.Start(_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            if(n>4) break;
            n++;
        }

        for(bagIterator.Start(_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            bagIterator.GetCurrent()->Touch();
        }

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }




    /** Checks if the delete in iterator works */
    void DeleteEnumTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);

        Tester* del = NULL;

        for(Fast_BagIterator<Tester*> bagIterator(_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            if(bagIterator.GetCurrent()->GetI() == 5) {
                del = bagIterator.GetCurrent();
                bagIterator.RemoveCurrent();
            }
        }

        // <inv: del points to a tester not in the bag anymore>

        TouchBag(_bag);

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                if(_array[i] != del) {
                    ok = false;
                }
            }
        }

        delete _bag;

        _test(ok);
    }

    /** Checks is the RemoveElement method in bag.*/
    void RemoveTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num, num+num);

        Tester* del = NULL;

        for(Fast_BagIterator<Tester*> bagIterator(_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            if(bagIterator.GetCurrent()->GetI() == 5) {
                del = bagIterator.GetCurrent();
            }
        }

        _bag->RemoveElement(del);

        // <inv: del points to a tester not in the bag anymore>

        TouchBag(_bag);

        for(int i = 0 ; i < 10; i++) {
            if(!_array[i]->IsTouched()) {
                if(_array[i] != del) {
                    ok = false;
                }
            }
        }

        delete _bag;

        _test(ok);
    }


    /**Test the HasElement method in bag.*/
    void HasElementTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num, num+num);

        Tester* current= NULL;

        for(Fast_BagIterator<Tester*> bagIterator(_bag);
            !bagIterator.End();
            bagIterator.Next()) {
            current=bagIterator.GetCurrent();
            if(!_bag->HasElement(current)) {
                ok=false;
            }
        }
        Tester* t=new Tester(4);
        if(_bag->HasElement(t) ) {
            ok=false;
        }

        delete t;
        delete _bag;

        _test(ok);
    }

    void GrowTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num, 2);

        // <inv: now the bag has grown many times>
        TouchBag(_bag);

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }

    void CopyConstTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);
        Fast_Bag<Tester*> bag(*_bag);
        TouchBag(&bag);

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }

    void AssignTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);

        Fast_Bag<Tester*> bag;
        bag = *_bag;

        TouchBag(&bag);

        for(int i = 0 ; i < num; i++) {
            if(!_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }


    void EqualTest(void) {
        bool ok = true;
        int num = 10;

        InitArray(num);
        Fast_Bag<Tester*> equalBag;
        equalBag = *_bag;

        if(!(equalBag == *_bag)) { ok = false; }

        Tester *elem1 = new Tester(4);
        equalBag.Insert(elem1);

        if(equalBag == *_bag) { ok = false; }

        equalBag.RemoveElement(elem1);
        delete elem1;
        delete _bag;

        _test(ok);
    }


    void RemoveAllElementsTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);
        _bag->RemoveAllElements();

        TouchBag(_bag);

        for(int i = 0 ; i < num; i++) {
            if(_array[i]->IsTouched()) {
                ok = false;
            }
        }

        delete _bag;

        _test(ok);
    }

    void GetBlocksizeTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);
        if(_bag->GetBlocksize() != num) {
            ok = false;
        }

        delete _bag;

        _test(ok);
    }

    void SetBlocksizeTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);
        _bag->SetBlocksize(19);
        if(_bag->GetBlocksize() != 19) {
            ok = false;
        }

        delete _bag;

        _test(ok);
    }

    void NumberOfElementsTest(void) {
        bool ok = true;
        int num = 10;
        InitArray(num);
        if(_bag->NumberOfElements() != num) {
            ok= false;
        }
        _bag->RemoveAllElements();
        if(_bag->NumberOfElements() != 0) {
            ok = false;
        }

        delete _bag;

        _test(ok);
    }

    void StartUp(void) {
        _array = new Tester*[1024];
    }

    void TearDown(void) {
        for(int i = 0; i < _elements; i++) {
            delete _array[i];
        }
        delete[] _array;
    }

    void Run() override {
        RunTest(&BagTester::IterPtrInitTest);
        RunTest(&BagTester::IterRefInitTest);
        RunTest(&BagTester::IterPtrStartTest);
        RunTest(&BagTester::IterRefStartTest);
        RunTest(&BagTester::IterStartOverTest);
        RunTest(&BagTester::IterPPOperTest);
        RunTest(&BagTester::GrowTest);
        RunTest(&BagTester::AssignTest);
        RunTest(&BagTester::CopyConstTest);
        RunTest(&BagTester::EqualTest);
        RunTest(&BagTester::DeleteEnumTest);
        RunTest(&BagTester::RemoveTest);
        RunTest(&BagTester::HasElementTest);
        RunTest(&BagTester::RemoveAllElementsTest);
        RunTest(&BagTester::GetBlocksizeTest);
        RunTest(&BagTester::SetBlocksizeTest);
        RunTest(&BagTester::NumberOfElementsTest);
    }

};

class BagTest : public FastOS_Application
{
public:
    int Main() override;
};
