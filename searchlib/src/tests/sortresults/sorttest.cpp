// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/sortresults.h>
#include <cassert>

using search::RankedHit;

unsigned int
myrandom()
{
    unsigned int r;
    r = random() & 0xffff;
    r <<= 16;
    r += random() & 0xffff;
    return r;
}


bool
test_sort(unsigned int caseNum, unsigned int n, unsigned int ntop)
{
    bool ok = true;
    double minmax;
    unsigned int i;
    RankedHit *array;

    if (ntop == 0) {
        printf("CASE %03d: [%d/%d] PASS\n", caseNum, ntop, n);
        return true;
    }
    if (ntop > n)
        ntop = n;

    array = new RankedHit[n];
    assert(array != NULL);

    for (i = 0; i < n; i++) {
        array[i]._docId = i;
        array[i]._rankValue = myrandom();
    }
    FastS_SortResults(array, n, ntop);

    minmax = array[ntop - 1].getRank();
    for(i = 0; i < n; i++) {
        if (i < ntop && i > 0
            && array[i].getRank() > array[i - 1].getRank()) {
            printf("ERROR: rank(%d) > rank(%d)\n",
                   i, i - 1);
            ok = false;
            break;
        }
        if (i >= ntop &&
            array[i].getRank() > minmax) {
            printf("ERROR: rank(%d) > rank(%d)\n",
                   i, ntop - 1);
            ok = false;
            break;
        }
    }
    delete [] array;
    printf("CASE %03d: [%d/%d] %s\n", caseNum, ntop, n,
           (ok)? "PASS" : "FAIL");
    return ok;
}


int
main(int argc, char **argv)
{
    (void) argc;
    (void) argv;

    bool ok = true;
    unsigned int caseNum = 0;
    unsigned int i;

    ok &= test_sort(++caseNum, 1, 1);
    for (i = 0; i < 5; i++) {
        ok &= test_sort(++caseNum, 2, 2);
    }
    for (i = 0; i < 5; i++) {
        ok &= test_sort(++caseNum, 50, 50);
    }
    for (i = 0; i < 5; i++) {
        ok &= test_sort(++caseNum,  50000,      1);
        ok &= test_sort(++caseNum,  50000,    500);
        ok &= test_sort(++caseNum,  50000,   1000);
        ok &= test_sort(++caseNum,  50000,   2000);
        ok &= test_sort(++caseNum,  50000,   5000);
        ok &= test_sort(++caseNum,  50000,  10000);
        ok &= test_sort(++caseNum,  50000,  50000);
    }
    printf("CONCLUSION: TEST %s\n", (ok)? "PASSED" : "FAILED");
    return (ok)? 0 : 1;
}
