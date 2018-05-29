// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpiteratortest.h"
#include <vespa/searchlib/parsequery/simplequerystack.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/fastos/time.h>

#define NUMTESTS 5

int
StackDumpIteratorTest::Main()
{
    int doTest[NUMTESTS];
    int low, high, accnum, num;
    int indicator;
    bool verify = false;
    int multiplier = 1;
    bool failed = false;

    if (_argc == 1) {
        Usage(_argv[0]);
        return 1;
    }

    // default initialize to not run any tests.
    for (int n = 0; n < NUMTESTS; n++)
        doTest[n] = 0;

    // parse the command line arguments
    for (int i = 1; i < _argc; i++) {
        low = 0;
        high = NUMTESTS - 1;
        char *p = _argv[i];

        // Check if a multiplier is specified
        if (*p == '*') {
            p++;
            accnum = 0;
            while (*p != '\0') {
                num = *p - '0';
                accnum = accnum * 10 + num;
                p++;
            }
            multiplier = accnum;
            continue;
        }

        // Default is to run the tests specified, unless the first char is '/'
        indicator = 1;
        if (*p == '/') {
            p++;
            indicator = 0;
        }

        // Find the first number
        accnum = 0;
        while (*p != '-' && *p != '\0') {
            num = *p - '0';
            accnum = accnum * 10 + num;
            p++;
        }
        if (accnum >= NUMTESTS)
            continue;
        low = accnum;
        // Check for range operator
        if (*p == '-') {
            p++;
            // Find the second number
            accnum = 0;
            while (*p != '\0') {
                num = *p - '0';
                accnum = accnum * 10 + num;
                p++;
            }
            if (accnum > 0)
                high = accnum < NUMTESTS ? accnum : NUMTESTS-1;
        } else
            high = low;

        // Indicate the runrequest for the desired range.
        for (int j = low; j <= high; j++)
            doTest[j] = indicator;
    }

    // Remove unused tests.
    // doTest[1] = 0;

    // Remember time
    if (multiplier > 1) {
        printf("Running all tests %d times.\n", multiplier);
        verify = false;
    } else {
        verify = true;
    }

    int testCnt = 0;

    FastOS_Time timer;
    timer.SetNow();

    // Actually run the tests that we wanted.
    for (int j = 0; j < multiplier; j++)
        for (int k = 0; k < NUMTESTS; k++)
            if (doTest[k] == 1) {
                if (!RunTest(k, verify))
                    failed = true;
                testCnt++;
            }

    // Print time taken
    double timeTaken = timer.MilliSecsToNow();

    printf("Time taken : %f ms\n", timeTaken);
    printf("Number of tests run: %d\n", testCnt);
    double avgTestPrMSec = static_cast<double>(testCnt) / timeTaken;
    printf("Tests pr Sec: %f\n", avgTestPrMSec * 1000.0);

    return failed ? 1 : 0;
}

#define ITERATOR_NOERROR                0x0
#define ITERATOR_ERROR_WRONG_NUM        0x1
#define ITERATOR_ERROR_WRONG_TYPE       0x2
#define ITERATOR_ERROR_WRONG_ARITY      0x4
#define ITERATOR_ERROR_WRONG_INDEX     0x10
#define ITERATOR_ERROR_WRONG_TERM      0x20
#define ITERATOR_ERROR_WRONG_GETINDEX  0x40
#define ITERATOR_ERROR_WRONG_GETTERM   0x80
#define ITERATOR_ERROR_WRONG_SIZE     0x100

bool
StackDumpIteratorTest::ShowResult(int testNo,
                                  search::SimpleQueryStackDumpIterator &actual,
                                  search::SimpleQueryStack &correct,
                                  unsigned int expected)
{
    unsigned int results = 0;

    int num = 0;

    search::ParseItem *item;

    printf("%03d: ", testNo);

    while (actual.next()) {
        vespalib::stringref idx = actual.getIndexName();
        vespalib::stringref term = actual.getTerm();

#if 0
        printf("StackItem #%d: %d %d '%.*s:%.*s'\n",
               actual.getNum(),
               actual.getType(),
               actual.getArity(),
               idx.size(), idx.c_str(),
               term.size(), term.c_str());
#endif

        item = correct.Pop();

        if (num++ != actual.getNum()) {
            results |= ITERATOR_ERROR_WRONG_NUM;
            delete item;
            break;
        }
        if (item->Type() != actual.getType()) {
            results |= ITERATOR_ERROR_WRONG_TYPE;
            delete item;
            break;
        }
        if (item->_arity != actual.getArity()) {
            results |= ITERATOR_ERROR_WRONG_ARITY;
            delete item;
            break;
        }
        if (strncmp(item->_indexName.c_str(), idx.c_str(), idx.size()) != 0) {
            results |= ITERATOR_ERROR_WRONG_INDEX;
            delete item;
            break;
        }
        if (strncmp(item->_term.c_str(), term.c_str(), term.size()) != 0) {
            results |= ITERATOR_ERROR_WRONG_TERM;
            delete item;
            break;
        }

        delete item;
    }
    if (correct.GetSize() != 0) results |= ITERATOR_ERROR_WRONG_SIZE;

    if (results == expected)
        printf("ok\n");
    else
        printf("fail. exp: %x, result: %x\n", expected, results);

    return results == expected;
}

/**
 *
 * @param testno The test to run.
 * @param verify Verify the result of the test.
 */
bool
StackDumpIteratorTest::RunTest(int testno, bool verify)
{
    search::SimpleQueryStack stack;
    search::RawBuf buf(32768);

    switch (testno) {
    case 0:
    {
        // Simple term query
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foobar"));

        stack.AppendBuffer(&buf);
        search::SimpleQueryStackDumpIterator si(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));

        if (verify)
            return ShowResult(testno, si, stack, ITERATOR_NOERROR);
        break;
    }

    case 1:
    {
        // multi term query
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo", "foobar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo", "xyzzy"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "bar", "baz"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_OR, 2));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_AND, 2));

        stack.AppendBuffer(&buf);
        search::SimpleQueryStackDumpIterator si(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));

        if (verify)
            return ShowResult(testno, si, stack, ITERATOR_NOERROR);
        break;
    }

    case 2:
    {
        // all stack items
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo", "foobar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_NUMTERM, "foo", "[0;22]"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_PREFIXTERM, "bar", "baz"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_PHRASE, 3, "bar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_SAME_ELEMENT, 3, "bar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_OR, 2));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_AND, 3));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_RANK, 5));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_NOT, 3));

        stack.AppendBuffer(&buf);
        search::SimpleQueryStackDumpIterator si(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));

        if (verify)
            return ShowResult(testno, si, stack, ITERATOR_NOERROR);
        break;
    }

    case 3:
    {
        // malicous type in buffer
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo", "foobar"));
        stack.AppendBuffer(&buf);
        *buf.GetWritableDrainPos(0) = 0x1e;
        search::SimpleQueryStackDumpIterator si(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        if (verify)
            return ShowResult(testno, si, stack, ITERATOR_ERROR_WRONG_SIZE);
        break;
    }

    case 4:
    {
        // malicous length in buffer
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo", "foobar"));
        stack.AppendBuffer(&buf);
        *buf.GetWritableDrainPos(1) = 0xba;
        search::SimpleQueryStackDumpIterator si(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        if (verify)
            return ShowResult(testno, si, stack, ITERATOR_ERROR_WRONG_SIZE);
        break;
    }


    default:
    {
        printf("%03d: no such test\n", testno);
    }
    }

    return true;
}

void
StackDumpIteratorTest::Usage(char *progname)
{
    printf("%s {testnospec}+\n\
    Where testnospec is:\n\
      num:     single test\n\
      num-num: inclusive range (open range permitted)\n",progname);
    printf("There are tests from %d to %d\n\n", 0, NUMTESTS-1);
}

int
main(int argc, char** argv)
{
    StackDumpIteratorTest tester;
    return tester.Entry(argc, argv);
}

