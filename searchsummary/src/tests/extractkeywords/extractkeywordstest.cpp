// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extractkeywordstest.h"
#include <vespa/searchsummary/docsummary/keywordextractor.h>
#include <vespa/searchlib/parsequery/simplequerystack.h>
#include <vespa/fastos/time.h>

#define NUMTESTS 5

int
ExtractKeywordsTest::Main()
{
    int doTest[NUMTESTS];
    int low, high, accnum, num;
    int indicator;
    bool verify = false;
    int multiplier = 1;
    bool failed = false;

    if (_argc == 1)
        Usage(_argv[0]);

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

    // init keyword extractor
    _extractor = new search::docsummary::KeywordExtractor(NULL);
    _extractor->AddLegalIndexSpec("*");

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

    delete _extractor;
    _extractor  = NULL;

    return failed ? 1 : 0;
}

bool
ExtractKeywordsTest::ShowResult(int testNo,
                                const char *actual, const char *correct)
{
    const char *act_word = actual;
    const char *cor_word = correct;
    printf("%03d: ", testNo);

    while (*act_word != '\0') {
        if (strcmp(act_word, cor_word) != 0) {
            printf("fail. Keywords differ for act: %s, corr: %s\n",
                   act_word, cor_word);
            return false;
        } else {
            act_word += strlen(act_word) + 1;
            cor_word += strlen(cor_word) + 1;
        }
    }
    if (*cor_word != '\0') {
        printf("fail. actual list shorter than correct at %s\n", cor_word);
        return false;
    }
    printf("ok\n");
    return true;
}

/**
 *
 * @param testno The test to run.
 * @param verify Verify the result of the test.
 */
bool
ExtractKeywordsTest::RunTest(int testno, bool verify)
{
    search::SimpleQueryStack stack;
    search::RawBuf buf(32768);
    const char *correct = NULL;
    const char *keywords = NULL;

    switch (testno) {
    case 0:
    {
        // Simple term query
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foobar"));

        stack.AppendBuffer(&buf);
        keywords = _extractor->ExtractKeywords(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        correct = "foobar\0\0";

        if (verify) ShowResult(testno, keywords, correct);
        free(const_cast<char *>(keywords));
        break;
    }

    case 1:
    {
        // multi term query
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foobar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "bar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_OR, 3));

        stack.AppendBuffer(&buf);
        keywords = _extractor->ExtractKeywords(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        correct = "bar\0foo\0foobar\0\0";

        if (verify) ShowResult(testno, keywords, correct);
        free(const_cast<char *>(keywords));
        break;
    }

    case 2:
    {
        // phrase term query
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foobar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "bar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_PHRASE, 3, "index"));

        stack.AppendBuffer(&buf);
        keywords = _extractor->ExtractKeywords(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        correct = "bar foo foobar\0\0";

        if (verify) ShowResult(testno, keywords, correct);
        free(const_cast<char *>(keywords));
        break;
    }

    case 3:
    {
        // multiple phrase and term query
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "xyzzy"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "xyz"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_PHRASE, 2, "index"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foobar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "bar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_PHRASE, 3, "index"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "baz"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "zog"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_AND, 3));

        stack.AppendBuffer(&buf);
        keywords = _extractor->ExtractKeywords(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        correct = "zog\0baz\0bar foo foobar\0xyz xyzzy\0\0";

        if (verify) ShowResult(testno, keywords, correct);
        free(const_cast<char *>(keywords));
        break;
    }

    case 4:
    {
        // phrase term query with wrong argument items
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foobar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "foo"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_AND, 2));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_TERM, "bar"));
        stack.Push(new search::ParseItem(search::ParseItem::ITEM_PHRASE, 2, "index"));

        stack.AppendBuffer(&buf);
        keywords = _extractor->ExtractKeywords(vespalib::stringref(buf.GetDrainPos(), buf.GetUsedLen()));
        correct = "\0";

        if (verify) ShowResult(testno, keywords, correct);
        free(const_cast<char *>(keywords));
        break;
    }

    default:
    {
        printf("%03d: no such test\n", testno);
        return false;
    }
    }

    bool result = true;
    /*
      if (verify) {
      result = ShowResult(testno, pq->GetStack(), correct);
      delete correct;
      } else {
      result = true;
      }
      delete pq;
    */
    return result;
}

void
ExtractKeywordsTest::Usage(char *progname)
{
    printf("%s {testnospec}+\n\
    Where testnospec is:\n\
      num:     single test\n\
      num-num: inclusive range (open range permitted)\n",progname);
    printf("There are tests from %d to %d\n\n", 0, NUMTESTS-1);
    exit(-1);
}

int
main(int argc, char** argv)
{
    ExtractKeywordsTest tester;
    return tester.Entry(argc, argv);
}

