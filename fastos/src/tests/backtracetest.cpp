// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/backtrace.h>
#include <cassert>
#include <string.h>

#include "tests.h"

#if (defined(__x86_64__) || defined(__aarch64__)) && defined(__linux__)
class Tracker
{
private:
    int   _found;
    int   _level;
    void dotrace() {
        _found = FastOS_backtrace(_codepoints, _level);
    }
    void deepFill();

protected:
    virtual void deepFill20();
    virtual void deepFill19();
    virtual void deepFill18();
    virtual void deepFill17();
    virtual void deepFill16();
    virtual void deepFill15();
    virtual void deepFill14();
    virtual void deepFill13();
    virtual void deepFill12();
    virtual void deepFill11();
    virtual void deepFill10();
    virtual void deepFill9();
    virtual void deepFill8();
    virtual void deepFill7();
    virtual void deepFill6();
    virtual void deepFill5();
    virtual void deepFill4();
    virtual void deepFill3();
    virtual void deepFill2();
    virtual void deepFill1();

    virtual void deepFill0();

public:
    void *_codepoints[25];
    Tracker() : _found(0), _level(25) {}
    virtual ~Tracker() { }
    void doTest(int levels) {
        for (int j=0; j<25; j++) {
            _codepoints[j] = (void *)0;
        }
        _level = levels;
        deepFill();
        printf("found levels: %d\n", _found);
        for (int i=0; i<levels; i++) {
            printf("level %2d -> %p\n", i, _codepoints[i]);
            if (_codepoints[i] == 0) break;
        }
    }
    int found() { return _found; }
};

class Tracker2: public Tracker
{
protected:
    void deepFill20() override;
    void deepFill18() override;
    void deepFill16() override;
    void deepFill14() override;
    void deepFill12() override;
    void deepFill10() override;
    void deepFill8() override;
    void deepFill6() override;
    void deepFill4() override;
    void deepFill2() override;
};


class BackTraceTest : public BaseTest
{

public:
    void TestBackTrace ()
    {
        bool rc = true;

        TestHeader("backtrace test");

        Tracker2 t;

        t.doTest(25);
        Progress(rc, "minimal functionality");
        t.doTest(25);
        rc = (t._codepoints[10] != 0);
        Progress(rc, "many levels");
        rc = (t.found() > 10);
        Progress(rc, "many levels retval");
        t.doTest(8);
        rc = (t.found() == 8);
        Progress(rc, "few levels retval");
        rc = (t._codepoints[8] == 0);
        Progress(rc, "few levels");

        PrintSeparator();
    }

    int Main () override
    {
        TestBackTrace();
        return allWasOk() ? 0 : 1;
    }
};


int main (int argc, char **argv)
{
    BackTraceTest app;

    setvbuf(stdout, nullptr, _IOLBF, 8192);
    return app.Entry(argc, argv);
}


void Tracker2::deepFill20() { printf("a"); deepFill19(); printf("a"); }
void Tracker2::deepFill18() { printf("c"); deepFill17(); printf("c"); }
void Tracker2::deepFill16() { printf("e"); deepFill15(); printf("e"); }
void Tracker2::deepFill14() { printf("g"); deepFill13(); printf("g"); }
void Tracker2::deepFill12() { printf("i"); deepFill11(); printf("i"); }
void Tracker2::deepFill10() { printf("k"); deepFill9();  printf("k"); }
void Tracker2::deepFill8()  { printf("m"); deepFill7();  printf("m"); }
void Tracker2::deepFill6()  { printf("o"); deepFill5();  printf("o"); }
void Tracker2::deepFill4()  { printf("q"); deepFill3();  printf("q"); }
void Tracker2::deepFill2()  { printf("s"); deepFill1();  printf("s"); }


void Tracker::deepFill()   { deepFill20(); printf("\n"); }

void Tracker::deepFill20() { printf("a"); deepFill19(); }
void Tracker::deepFill19() { printf("b"); deepFill18(); }
void Tracker::deepFill18() { printf("c"); deepFill17(); }
void Tracker::deepFill17() { printf("d"); deepFill16(); }
void Tracker::deepFill16() { printf("e"); deepFill15(); }
void Tracker::deepFill15() { printf("f"); deepFill14(); }
void Tracker::deepFill14() { printf("g"); deepFill13(); }
void Tracker::deepFill13() { printf("h"); deepFill12(); }
void Tracker::deepFill12() { printf("i"); deepFill11(); }
void Tracker::deepFill11() { printf("j"); deepFill10(); }
void Tracker::deepFill10() { printf("k"); deepFill9(); }
void Tracker::deepFill9()  { printf("l"); deepFill8(); }
void Tracker::deepFill8()  { printf("m"); deepFill7(); }
void Tracker::deepFill7()  { printf("n"); deepFill6(); }
void Tracker::deepFill6()  { printf("o"); deepFill5(); }
void Tracker::deepFill5()  { printf("p"); deepFill4(); }
void Tracker::deepFill4()  { printf("q"); deepFill3(); }
void Tracker::deepFill3()  { printf("r"); deepFill2(); }
void Tracker::deepFill2()  { printf("s"); deepFill1(); }
void Tracker::deepFill1()  { printf("t"); deepFill0(); }

void Tracker::deepFill0()  { dotrace(); }

#else
int
main(int argc, char **argv)
{
   (void)argc;
   (void)argv;
   printf("No backtrace support, skipping tests...\n");
   return 0;
}
#endif
