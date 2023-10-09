// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prod_features_test.h"
#include <vespa/searchlib/features/fieldmatchfeature.h>

#include <vespa/log/log.h>
LOG_SETUP(".prod_features_fieldmatch");

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;
using search::AttributeVector;
using CollectionType = FieldInfo::CollectionType;

Test::Test() {}
Test::~Test() {}

void
Test::testFieldMatch()
{
    testFieldMatchBluePrint();
    testFieldMatchExecutor();
}


void
Test::testFieldMatchBluePrint()
{
    FieldMatchBlueprint pt;
    StringList out;
    out.add("score").
        add("proximity").
        add("completeness").
        add("queryCompleteness").
        add("fieldCompleteness").
        add("orderness").
        add("relatedness").
        add("earliness").
        add("longestSequenceRatio").
        add("segmentProximity").
        add("unweightedProximity").
        add("absoluteProximity").
        add("occurrence").
        add("absoluteOccurrence").
        add("weightedOccurrence").
        add("weightedAbsoluteOccurrence").
        add("significantOccurrence").

        add("weight").
        add("significance").
        add("importance").

        add("segments").
        add("matches").
        add("outOfOrder").
        add("gaps").
        add("gapLength").
        add("longestSequence").
        add("head").
        add("tail").
        add("segmentDistance").
        add("degradedMatches");
    {
        EXPECT_TRUE(assertCreateInstance(pt, "fieldMatch"));

        StringList params, in;
        FT_SETUP_FAIL(pt, params);
        FT_SETUP_FAIL(pt, params.add("foo"));
        FT_SETUP_FAIL(pt, params.add("bar"));
        params.clear();

        {
            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "abar");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wbar");
            FT_SETUP_FAIL(pt, ie, params.add("foo"));
            FT_SETUP_FAIL(pt, ie, params.add("abar"));
            FT_SETUP_FAIL(pt, ie, params.add("wbar"));

            FT_SETUP_OK(pt, ie, params.clear().add("bar"), in, out);
        }

        { // test illegal proximity table
            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            Properties & p = ie.getProperties();
            p.add("fieldMatch(foo).proximityLimit", "1");

            // too few elements, should be 3 (1*2 + 1)
            p.add("fieldMatch(foo).proximityTable", "0.5");
            p.add("fieldMatch(foo).proximityTable", "1.0");
            FT_SETUP_FAIL(pt, ie, params);

            // too many elements, should be 3 (1*2 + 1)
            p.add("fieldMatch(foo).proximityTable", "1.0");
            p.add("fieldMatch(foo).proximityTable", "0.5");
            FT_SETUP_FAIL(pt, ie, params);
        }
    }
    { // test dumping with a regular index field
        FT_DUMP_EMPTY(_factory, "fieldMatch");

        FtIndexEnvironment ie;
        ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
        FT_DUMP_EMPTY(_factory, "fieldMatch", ie); // must be an index field

        ie.getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "abar");
        FT_DUMP_EMPTY(_factory, "fieldMatch", ie); // must be single value

        ie.getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wbar");
        FT_DUMP_EMPTY(_factory, "fieldMatch", ie); // must be single value

        StringList dump;
        ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        vespalib::string bn = "fieldMatch(bar)";
        dump.add(bn);
        for (uint32_t i = 1; i < out.size(); ++i) {
            dump.add(bn + "." + out[i]);
        }
        FT_DUMP(_factory, "fieldMatch", ie, dump);
    }

    { // test dumping with a filter index field
        FtIndexEnvironment ie;
        ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ie.getFields()[0].setFilter(true);

        StringList dump;
        vespalib::string bn = "fieldMatch(foo)";
        dump.add(bn);
        dump.add(bn + ".completeness");
        dump.add(bn + ".queryCompleteness");
        dump.add(bn + ".weight");
        dump.add(bn + ".matches");
        dump.add(bn + ".degradedMatches");
        FT_DUMP(_factory, "fieldMatch", ie, dump);
    }
}


void
Test::testFieldMatchExecutor()
{
    testFieldMatchExecutorOutOfOrder();
    testFieldMatchExecutorSegments();
    testFieldMatchExecutorGaps();
    testFieldMatchExecutorHead();
    testFieldMatchExecutorTail();
    testFieldMatchExecutorLongestSequence();
    testFieldMatchExecutorMatches();
    testFieldMatchExecutorCompleteness();
    testFieldMatchExecutorOrderness();
    testFieldMatchExecutorRelatedness();
    testFieldMatchExecutorLongestSequenceRatio();
    testFieldMatchExecutorEarliness();
    testFieldMatchExecutorWeight();
    testFieldMatchExecutorSignificance();
    testFieldMatchExecutorImportance();
    testFieldMatchExecutorOccurrence();
    testFieldMatchExecutorAbsoluteOccurrence();
    testFieldMatchExecutorWeightedOccurrence();
    testFieldMatchExecutorWeightedAbsoluteOccurrence();
    testFieldMatchExecutorSignificantOccurrence();
    testFieldMatchExecutorUnweightedProximity();
    testFieldMatchExecutorReverseProximity();
    testFieldMatchExecutorAbsoluteProximity();
    testFieldMatchExecutorMultiSegmentProximity();
    testFieldMatchExecutorSegmentDistance();
    testFieldMatchExecutorSegmentProximity();
    testFieldMatchExecutorSegmentStarts();
    testFieldMatchExecutorMoreThanASegmentLengthOfUnmatchedQuery();
    testFieldMatchExecutorQueryRepeats();
    testFieldMatchExecutorZeroCases();
    testFieldMatchExecutorExceedingIterationLimit();
    testFieldMatchExecutorRemaining();
}    


void
Test::testFieldMatchExecutorOutOfOrder() 
{
    assertFieldMatch("outOfOrder:0","a","a");
    assertFieldMatch("outOfOrder:0","a b c","a b c");
    assertFieldMatch("outOfOrder:1","a b c","a c b");
    assertFieldMatch("outOfOrder:2","a b c","c b a");
    assertFieldMatch("outOfOrder:2","a b c d e","c x a b x x x x x e x x d");
    assertFieldMatch("outOfOrder:2","a b c d e","c x a b x x x x x e x x d");
    assertFieldMatch("outOfOrder:2","a b c d e","c x a b x x x x x e x x d");
}


void
Test::testFieldMatchExecutorSegments() 
{
    assertFieldMatch("segments:1","a","a");
    assertFieldMatch("segments:1","a b c","a b c");
    assertFieldMatch("segments:1","a b c","a x x b c");
    assertFieldMatch("segments:2","a b c","a x x x x x x x x x x x x x x x x x x x b c");
    assertFieldMatch("segments:2","a b c","b c x x x x x x x x x x x x x x x x x x x a");
    assertFieldMatch("segments:2 gaps:1","a b c","x x x a x x x x x x x x x x x x x x x x x x x b x x c x x");
    assertFieldMatch("segments:2 gaps:0 outOfOrder:0","a b c","b c x x x x x x x x x x x x x x x x x x x a");
    assertFieldMatch("segments:2 gaps:1","a b c","x x x b x x c x x x x x x x x x x x x x x x x x x x a x x");
    assertFieldMatch("segments:2 gaps:1","a y y b c","x x x b x x c x x x x x x x x x x x x x x x x x x x a x x");
}


void
Test::testFieldMatchExecutorGaps() 
{
    assertFieldMatch("gaps:0","a","a");
    assertFieldMatch("gaps:0","x�a","a"); // TODO: which char ?
    assertFieldMatch("gaps:0 gapLength:0","a b c","a b c");
    assertFieldMatch("gaps:1 gapLength:1","a b","b a");
    assertFieldMatch("gaps:1 gapLength:1","a b c","a x b c");
    assertFieldMatch("gaps:1 gapLength:3","a b c","a x X Xb c");
    assertFieldMatch("gaps:2 gapLength:2 outOfOrder:1","a b c","a c b");
    assertFieldMatch("gaps:2 gapLength:2 outOfOrder:0","a b c","a x b x c");
    assertFieldMatch("gaps:2 gapLength:5 outOfOrder:1","a b c","a x c x b");
    assertFieldMatch("gaps:3 outOfOrder:2 segments:1","a b c d e","x d x x b c x x a e");
    assertFieldMatch("gaps:0","y a b c","a b c x");
}


void
Test::testFieldMatchExecutorHead()
{
    assertFieldMatch("head:0","a","a");
    //assertFieldMatch("head:0","y","a"); // no hit, executor will not run
    assertFieldMatch("head:1","a","x a");
    assertFieldMatch("head:2","a b c","x x a b c");
    assertFieldMatch("head:2","a b c","x x c x x a b");
    assertFieldMatch("head:2","a b c","x x c x x x x x x x x x x x x x x x a b");
}


void
Test::testFieldMatchExecutorTail()
{
    assertFieldMatch("tail:0","a","a");
    //assertFieldMatch("tail:0","y","a"); // no hit, executor will not run
    assertFieldMatch("tail:1","a","a x");
    assertFieldMatch("tail:2","a b c","a b c x x");
    assertFieldMatch("tail:2","a b c","x x x c x x x x a b x x");
    assertFieldMatch("tail:0","a b c","x x c x x x x x x x x x x x x x x x a b");
}

void
Test::testFieldMatchExecutorLongestSequence()
{
    assertFieldMatch("longestSequence:1","a","a");
    assertFieldMatch("longestSequence:1","a","a b c");
    assertFieldMatch("longestSequence:1","b","a b c");
    assertFieldMatch("longestSequence:3","a b c","x x a b c x x a b x");
    assertFieldMatch("longestSequence:3 segments:1","a b c","x x a b x x a b c x");
    assertFieldMatch("longestSequence:2","a b c d","x x c d x x a b x");
    assertFieldMatch("longestSequence:2","a b c d","x x a b x c d x x");
    assertFieldMatch("longestSequence:2","a b c d","x x a b x x x x x x x x x x x x x x x x x c d x x");
    assertFieldMatch("longestSequence:4 segments:1","a b c d","x x a b x x x x x x x x x x x x x x x x x c d x x a b c d");
}


void
Test::testFieldMatchExecutorMatches()
{
    assertFieldMatch("matches:1 queryCompleteness:1 fieldCompleteness:1","a","a");
    assertFieldMatch("matches:3 queryCompleteness:1 fieldCompleteness:1","a b c","a b c");
    assertFieldMatch("matches:3 queryCompleteness:1 fieldCompleteness:0.5","a b c","a b c a b d");
    assertFieldMatch("matches:3 queryCompleteness:0.5 fieldCompleteness:0.25","a y y b c y","a x x b c x a x a b x x");
}


void
Test::testFieldMatchExecutorCompleteness()
{
    assertFieldMatch("completeness:1 queryCompleteness:1 fieldCompleteness:1","a","a");
    assertFieldMatch("completeness:0 queryCompleteness:0 fieldCompleteness:0","a","x");
    assertFieldMatch("completeness:0 queryCompleteness:0 fieldCompleteness:0","y","a");
    assertFieldMatch("completeness:0.975 queryCompleteness:1 fieldCompleteness:0.5","a","a a");
    assertFieldMatch("completeness:0.525 queryCompleteness:0.5 fieldCompleteness:1","a a","a");
    assertFieldMatch("completeness:1 queryCompleteness:1 fieldCompleteness:1","a b c","a b c");
    assertFieldMatch("completeness:0.525 queryCompleteness:0.5 fieldCompleteness:1","a b c d","a b");
    assertFieldMatch("completeness:0.975 queryCompleteness:1 fieldCompleteness:0.5","a b","a b c d");
    assertFieldMatch("completeness:0.97 queryCompleteness:1 fieldCompleteness:0.4","a b","a b c d e");
}


void
Test::testFieldMatchExecutorOrderness()
{
    assertFieldMatch("orderness:1",  "a","a");
    // Note: we have no hits -> orderness: 0(1)
    assertFieldMatch("orderness:0",  "a","x");
    assertFieldMatch("orderness:0",  "a a a","a"); // Oh well...
    assertFieldMatch("orderness:1",  "a","a a a");
    assertFieldMatch("orderness:0",  "a b","b a");
    assertFieldMatch("orderness:0.5","a b c","b a c");
    assertFieldMatch("orderness:0.5","a b c d","c b d x x x x x x x x x x x x x x x x x x x x x a");
}


void
Test::testFieldMatchExecutorRelatedness()
{
    assertFieldMatch("relatedness:1",  "a","a");
    assertFieldMatch("relatedness:0",  "a","x");
    assertFieldMatch("relatedness:1",  "a b","a b");
    assertFieldMatch("relatedness:1",  "a b c","a b c");
    assertFieldMatch("relatedness:0.5","a b c","a b x x x x x x x x x x x x x x x x x x x x x x x c");
    assertFieldMatch("relatedness:0.5","a y b y y y c","a b x x x x x x x x x x x x x x x x x x x x x x x c");
}


void
Test::testFieldMatchExecutorLongestSequenceRatio()
{
    assertFieldMatch("longestSequenceRatio:1",  "a","a");
    assertFieldMatch("longestSequenceRatio:0",  "a","x");
    assertFieldMatch("longestSequenceRatio:1",  "a a","a");
    assertFieldMatch("longestSequenceRatio:1",  "a","a a");
    assertFieldMatch("longestSequenceRatio:1",  "a b","a b");
    assertFieldMatch("longestSequenceRatio:1",  "a y"," a x");
    assertFieldMatch("longestSequenceRatio:0.5","a b","a x b");
    assertFieldMatch("longestSequenceRatio:0.75","a b c d","x x a b x a x c d a b c x d x");
}


void
Test::testFieldMatchExecutorEarliness()
{
    assertFieldMatch("earliness:1",     "a","a");
    assertFieldMatch("earliness:0",     "a","x");
    assertFieldMatch("earliness:1",     "a","a a a");
    assertFieldMatch("earliness:1",     "a a a","a");
    assertFieldMatch("earliness:0.8",   "b","a b c");
    assertFieldMatch("earliness:0.8",   "b","a b");
    assertFieldMatch("earliness:0.9091","a b c","x b c x x x x x a x x x");
    assertFieldMatch("earliness:0.2",   "a b c","x b c a x x x x a x x x x x x x a b c x x");
}


void
Test::testFieldMatchExecutorWeight()
{
    assertFieldMatch("weight:1",     "a","a");
    assertFieldMatch("weight:0",     "y","a");
    assertFieldMatch("weight:0.3333","a a a","a");
    assertFieldMatch("weight:1",     "a","a a a");
    assertFieldMatch("weight:1",     "a b c","a b c");
    assertFieldMatch("weight:1",     "a b c","x x a b x a x c x x a b x c c x");
    
    assertFieldMatch("weight:0.3333","a b c","a");
    assertFieldMatch("weight:0.6667","a b c","a b");

    assertFieldMatch("weight:1",   "a b c!200","a b c");  // Best
    assertFieldMatch("weight:0.75","a b c!200","b c");    // Middle
    assertFieldMatch("weight:0.5", "a b c!200","a b");    // Worst

    assertFieldMatch("weight:1","a!300 b c!200","a b c"); // Best too

    assertFieldMatch("weight:1",  "a b c!50","a b c");    // Best
    assertFieldMatch("weight:0.6","a b c!50","b c");      // Worse
    assertFieldMatch("weight:0.4","a b c!50","b");        // Worse
    assertFieldMatch("weight:0.2","a b c!50","c");        // Worst
    assertFieldMatch("weight:0.8","a b c!50","a b");      // Middle

    assertFieldMatch("weight:1",  "a b c!0","a b c");     // Best
    assertFieldMatch("weight:0.5","a b c!0","b c");       // Worst
    assertFieldMatch("weight:1",  "a b c!0","a b");       // As good as best
    assertFieldMatch("weight:0",  "a b c!0","c");         // No contribution

    assertFieldMatch("weight:0","a!0 b!0","a b");
    assertFieldMatch("weight:0","a!0 b!0","");

    // The query also has other terms having a total weight of 300
    // so we add a weight parameter which is the sum of the weights of this query terms + 300
    assertFieldMatch("weight:0.25",  "a","a",400);
    assertFieldMatch("weight:0",     "y","a",400);
    assertFieldMatch("weight:0.1667","a a a","a",600);
    assertFieldMatch("weight:0.25",  "a","a a a",400);
    assertFieldMatch("weight:0.5",   "a b c","a b c",600);
    assertFieldMatch("weight:0.5",   "a b c","x x a b x a x c x x a b x c c x",600);

    assertFieldMatch("weight:0.1667","a b c","a",600);
    assertFieldMatch("weight:0.3333","a b c","a b",600);

    assertFieldMatch("weight:0.5714","a b c!200","a b c",700); // Best
    assertFieldMatch("weight:0.4286","a b c!200","b c",700);   // Middle
    assertFieldMatch("weight:0.2857","a b c!200","a b",700);   // Worst

    assertFieldMatch("weight:0.6667","a!300 b c!200","a b c",900); // Better than best

    assertFieldMatch("weight:0.4545","a b c!50","a b c",550);  // Best
    assertFieldMatch("weight:0.2727","a b c!50","b c",550);    // Worse
    assertFieldMatch("weight:0.1818","a b c!50","b",550);      // Worse
    assertFieldMatch("weight:0.0909","a b c!50","c",550);      // Worst
    assertFieldMatch("weight:0.3636","a b c!50","a b",550);    // Middle

    assertFieldMatch("weight:0.4","a b c!0","a b c",500);      // Best
    assertFieldMatch("weight:0.2","a b c!0","b c",500);        // Worst
    assertFieldMatch("weight:0.4","a b c!0","a b",500);        // As good as best
    assertFieldMatch("weight:0",  "a b c!0","c",500);          // No contribution

    assertFieldMatch("weight:0","a!0 b!0","a b",300);
    assertFieldMatch("weight:0","a!0 b!0","",300);
}


void
Test::testFieldMatchExecutorSignificance()
{
    assertFieldMatch("significance:1",     "a","a");
    assertFieldMatch("significance:0",     "a","x");
    assertFieldMatch("significance:0.3333","a a a","a");
    assertFieldMatch("significance:1",     "a","a a a");
    assertFieldMatch("significance:1",     "a b c","a b c");
    assertFieldMatch("significance:1",     "a b c","x x a b x a x c x x a b x c c x");

    assertFieldMatch("significance:0.3333","a b c","a");
    assertFieldMatch("significance:0.6667","a b c","a b");

    assertFieldMatch("significance:1",   "a b c%0.2","a b c");  // Best
    assertFieldMatch("significance:0.75","a b c%0.2","b c");    // Middle
    assertFieldMatch("significance:0.5", "a b c%0.2","a b");    // Worst

    assertFieldMatch("significance:1","a%0.3 b c%0.2","a b c"); // Best too

    assertFieldMatch("significance:1",  "a b c%0.05","a b c");  // Best
    assertFieldMatch("significance:0.6","a b c%0.05","b c");    // Worse
    assertFieldMatch("significance:0.4","a b c%0.05","b");      // Worse
    assertFieldMatch("significance:0.2","a b c%0.05","c");      // Worst
    assertFieldMatch("significance:0.8","a b c%0.05","a b");    // Middle

    assertFieldMatch("significance:1",  "a b c%0","a b c");     // Best
    assertFieldMatch("significance:0.5","a b c%0","b c");       // Worst
    assertFieldMatch("significance:1",  "a b c%0","a b");       // As good as best
    assertFieldMatch("significance:0",  "a b c%0","c");         // No contribution

    assertFieldMatch("significance:0","a%0 b%0","a b");
    assertFieldMatch("significance:0","a%0 b%0","");

    // The query also has other terms having a total significance of 0.3
    // so we add a significance parameter which is the sum of the significances of this query terms + 0.3
    assertFieldMatchTS("significance:0.25",  "a","a",0.4f);
    assertFieldMatchTS("significance:0",     "y","a",0.4f);
    assertFieldMatchTS("significance:0.1667","a a a","a",0.6f);
    assertFieldMatchTS("significance:0.25",  "a","a a a",0.4f);
    assertFieldMatchTS("significance:0.5",   "a b c","a b c",0.6f);
    assertFieldMatchTS("significance:0.5",   "a b c","x x a b x a x c x x a b x c c x",0.6f);

    assertFieldMatchTS("significance:0.1667","a b c","a",0.6f);
    assertFieldMatchTS("significance:0.3333","a b c","a b",0.6f);

    assertFieldMatchTS("significance:0.5714","a b c%0.2","a b c",0.7f); // Best
    assertFieldMatchTS("significance:0.4286","a b c%0.2","b c",0.7f);   // Middle
    assertFieldMatchTS("significance:0.2857","a b c%0.2","a b",0.7f);   // Worst

    assertFieldMatchTS("significance:0.6667","a%0.3 b c%0.2","a b c",0.9f); // Better than best

    assertFieldMatchTS("significance:0.4545","a b c%0.05","a b c",0.55f); // Best
    assertFieldMatchTS("significance:0.2727","a b c%0.05","b c",0.55f);   // Worse
    assertFieldMatchTS("significance:0.1818","a b c%0.05","b",0.55f);     // Worse
    assertFieldMatchTS("significance:0.0909","a b c%0.05","c",0.55f);     // Worst
    assertFieldMatchTS("significance:0.3636","a b c%0.05","a b",0.55f);   // Middle

    assertFieldMatchTS("significance:0.4","a b c%0","a b c",0.5f);  // Best
    assertFieldMatchTS("significance:0.2","a b c%0","b c",0.5f);    // Worst
    assertFieldMatchTS("significance:0.4","a b c%0","a b",0.5f);    // As good as best
    assertFieldMatchTS("significance:0",  "a b c%0","c",0.5f);      // No contribution

    assertFieldMatchTS("significance:0","a%0 b%0","a b",0.3f);
    assertFieldMatchTS("significance:0","a%0 b%0","",0.3f);
}


void
Test::testFieldMatchExecutorImportance()
{
    assertFieldMatch("importance:0.75","a b c",    "a x x b x c c c",600);
    assertFieldMatch("importance:0.85","a b!500 c","a x x b x c c c",1000);

    // Twice as common - twice as weighty, but total weight has the extra 300 - less than the previous
    assertFieldMatch("importance:0.7857","a b!200%0.05 c","a x x b x c c c",700);
    // Here higher importancy exactly offsets the lowered uniqueness
    assertFieldMatch("importance:0.85","a b!500%0.5 c","a x x b x c c c",1000);
}


void
Test::testFieldMatchExecutorOccurrence()
{
    assertFieldMatch("occurrence:0","a","x");
    assertFieldMatch("occurrence:1","a","a");
    assertFieldMatch("occurrence:0","a a a","x");
    assertFieldMatch("occurrence:1","a a a","a");
    assertFieldMatch("occurrence:1","a a a","a a a");
    assertFieldMatch("occurrence:1","a a a","a a a a");
    assertFieldMatch("occurrence:0.3571","a","x x x a x x a x a x x x a a");
    assertFieldMatch("occurrence:1","a","a a a a a a a a a a a a a a");
    assertFieldMatch("occurrence:1","a b","a b b a a a a a b a a b a a");

    // tests going beyond the occurrence limit
    fieldmatch::Params params;
    params.setMaxOccurrences(10);
    assertFieldMatch("occurrence:1",     "a b","a a a a a a a a a a b b", &params);
    assertFieldMatch("occurrence:0.9231","a b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("occurrence:0.6",   "a b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("occurrence:1",     "a b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("occurrence:1",     "a b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length
}


void
Test::testFieldMatchExecutorAbsoluteOccurrence()
{
    assertFieldMatch("absoluteOccurrence:0",  "a","x");
    assertFieldMatch("absoluteOccurrence:0.01","a","a");
    assertFieldMatch("absoluteOccurrence:0","a a a","x");
    assertFieldMatch("absoluteOccurrence:0.01",  "a a a","a");
    assertFieldMatch("absoluteOccurrence:0.03",  "a a a","a a a");
    assertFieldMatch("absoluteOccurrence:0.04",  "a a a","a a a a");
    assertFieldMatch("absoluteOccurrence:0.05","a","x x x a x x a x a x x x a a");
    assertFieldMatch("absoluteOccurrence:0.14","a","a a a a a a a a a a a a a a");
    assertFieldMatch("absoluteOccurrence:0.07","a b","a b b a a a a a b a a b a a");

    // tests going beyond the occurrence limit
    fieldmatch::Params params;
    params.setMaxOccurrences(10);
    assertFieldMatch("absoluteOccurrence:0.6","a b","a a a a a a a a a a b b", &params);
    assertFieldMatch("absoluteOccurrence:0.6","a b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("absoluteOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("absoluteOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("absoluteOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length
}


void
Test::testFieldMatchExecutorWeightedOccurrence()
{
    assertFieldMatch("weightedOccurrence:0","a!200","x");
    assertFieldMatch("weightedOccurrence:1","a!200","a");
    assertFieldMatch("weightedOccurrence:0","a!200 a a","x");
    assertFieldMatch("weightedOccurrence:1","a!200 a a","a");
    assertFieldMatch("weightedOccurrence:1","a a a","a a a");
    assertFieldMatch("weightedOccurrence:1","a!200 a a","a a a a");
    assertFieldMatch("weightedOccurrence:0.3571","a!200","x x x a x x a x a x x x a a");
    assertFieldMatch("weightedOccurrence:1","a!200","a a a a a a a a a a a a a a");
    assertFieldMatch("weightedOccurrence:0.5","a b","a b b a a a a a b a a b a a");

    assertFieldMatch("weightedOccurrence:0.5714","a!200 b","a b b a a a a a b a a b a a");
    assertFieldMatch("weightedOccurrence:0.6753","a!1000 b","a b b a a a a a b a a b a a");  // Should be higher
    assertFieldMatch("weightedOccurrence:0.4286","a b!200","a b b a a a a a b a a b a a"); // Should be lower
    assertFieldMatch("weightedOccurrence:0.3061","a b!2000","a b b a a a a a b a a b a a"); // Should be even lower

    assertFieldMatch("weightedOccurrence:0.30","a b",    "a a b b b b x x x x");
    assertFieldMatch("weightedOccurrence:0.3333","a b!200","a a b b b b x x x x"); // More frequent is more important - higher
    assertFieldMatch("weightedOccurrence:0.2667","a!200 b","a a b b b b x x x x"); // Less frequent is more important - lower
    assertFieldMatch("weightedOccurrence:0.2667","a b!50", "a a b b b b x x x x"); // Same relative

    assertFieldMatch("weightedOccurrence:0","a!0 b!0", "a a b b b b x x x x");

    // tests going beyond the occurrence limit
    fieldmatch::Params params;
    params.setMaxOccurrences(10);
    assertFieldMatch("weightedOccurrence:0.6","a b","a a a a a a a a a a b b", &params);
    assertFieldMatch("weightedOccurrence:0.6","a b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("weightedOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("weightedOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("weightedOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length

    assertFieldMatch("weightedOccurrence:0.7333","a!200 b","a a a a a a a a a a b b", &params);
    assertFieldMatch("weightedOccurrence:0.4667","a b!200","a a a a a a a a a a b b", &params);
    assertFieldMatch("weightedOccurrence:0.7333","a!200 b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("weightedOccurrence:0.7333","a!200 b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("weightedOccurrence:1",     "a!200 b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("weightedOccurrence:1",     "a!200 b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length
}


void
Test::testFieldMatchExecutorWeightedAbsoluteOccurrence()
{
    assertFieldMatch("weightedAbsoluteOccurrence:0",    "a!200","x");
    assertFieldMatch("weightedAbsoluteOccurrence:0.01", "a!200","a");
    assertFieldMatch("weightedAbsoluteOccurrence:0",    "a!200 a a","x");
    assertFieldMatch("weightedAbsoluteOccurrence:0.01", "a!200 a a","a");
    assertFieldMatch("weightedAbsoluteOccurrence:0.03", "a a a","a a a");
    assertFieldMatch("weightedAbsoluteOccurrence:0.04", "a!200 a a","a a a a");
    assertFieldMatch("weightedAbsoluteOccurrence:0.05", "a!200","x x x a x x a x a x x x a a");
    assertFieldMatch("weightedAbsoluteOccurrence:0.14", "a!200","a a a a a a a a a a a a a a");
    assertFieldMatch("weightedAbsoluteOccurrence:0.07","a b","a b b a a a a a b a a b a a");

    assertFieldMatch("weightedAbsoluteOccurrence:0.08",  "a!200 b","a b b a a a a a b a a b a a");
    assertFieldMatch("weightedAbsoluteOccurrence:0.0945","a!1000 b","a b b a a a a a b a a b a a"); // Should be higher
    assertFieldMatch("weightedAbsoluteOccurrence:0.06",  "a b!200","a b b a a a a a b a a b a a");  // Should be lower
    assertFieldMatch("weightedAbsoluteOccurrence:0.0429","a b!2000","a b b a a a a a b a a b a a"); // Should be even lower

    assertFieldMatch("weightedAbsoluteOccurrence:0.03", "a b",    "a a b b b b x x x x");
    assertFieldMatch("weightedAbsoluteOccurrence:0.0333","a b!200","a a b b b b x x x x"); // More frequent is more important - higher
    assertFieldMatch("weightedAbsoluteOccurrence:0.0267","a!200 b","a a b b b b x x x x"); // Less frequent is more important - lower
    assertFieldMatch("weightedAbsoluteOccurrence:0.0267","a b!50", "a a b b b b x x x x"); // Same relative

    assertFieldMatch("weightedAbsoluteOccurrence:0","a!0 b!0", "a a b b b b x x x x");

    // tests going beyond the occurrence limit
    fieldmatch::Params params;
    params.setMaxOccurrences(10);
    assertFieldMatch("weightedAbsoluteOccurrence:0.6","a b","a a a a a a a a a a b b", &params);
    assertFieldMatch("weightedAbsoluteOccurrence:0.6","a b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("weightedAbsoluteOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("weightedAbsoluteOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("weightedAbsoluteOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length

    assertFieldMatch("weightedAbsoluteOccurrence:0.7333","a!200 b","a a a a a a a a a a b b", &params);
    assertFieldMatch("weightedAbsoluteOccurrence:0.4667","a b!200","a a a a a a a a a a b b", &params);
    assertFieldMatch("weightedAbsoluteOccurrence:0.7333","a!200 b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("weightedAbsoluteOccurrence:0.7333","a!200 b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("weightedAbsoluteOccurrence:1",     "a!200 b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("weightedAbsoluteOccurrence:1",     "a!200 b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length
}


void
Test::testFieldMatchExecutorSignificantOccurrence()
{
    assertFieldMatch("significantOccurrence:0","a%0.2","x");
    assertFieldMatch("significantOccurrence:1","a%0.2","a");
    assertFieldMatch("significantOccurrence:0","a%0.2 a a","x");
    assertFieldMatch("significantOccurrence:1","a%0.2 a a","a");
    assertFieldMatch("significantOccurrence:1","a a a","a a a");
    assertFieldMatch("significantOccurrence:1","a%0.2 a a","a a a a");
    assertFieldMatch("significantOccurrence:0.3571","a%0.2","x x x a x x a x a x x x a a");
    assertFieldMatch("significantOccurrence:1","a%0.2","a a a a a a a a a a a a a a");
    assertFieldMatch("significantOccurrence:0.5","a b","a b b a a a a a b a a b a a");

    assertFieldMatch("significantOccurrence:0.5714","a%0.2 b","a b b a a a a a b a a b a a");
    assertFieldMatch("significantOccurrence:0.6753","a%1 b","a b b a a a a a b a a b a a");   // Should be higher
    assertFieldMatch("significantOccurrence:0.4286","a b%0.2","a b b a a a a a b a a b a a"); // Should be lower
    assertFieldMatch("significantOccurrence:0.3247","a b%1","a b b a a a a a b a a b a a");   // Should be even lower

    assertFieldMatch("significantOccurrence:0.30","a b",    "a a b b b b x x x x");
    assertFieldMatch("significantOccurrence:0.3333","a b%0.2","a a b b b b x x x x"); // More frequent is more important - higher
    assertFieldMatch("significantOccurrence:0.2667","a%0.2 b","a a b b b b x x x x"); // Less frequent is more important - lower
    assertFieldMatch("significantOccurrence:0.2667","a b%0.05", "a a b b b b x x x x"); // Same relative

    assertFieldMatch("significantOccurrence:0","a%0 b%0", "a a b b b b x x x x");

    // tests going beyond the occurrence limit
    fieldmatch::Params params;
    params.setMaxOccurrences(10);
    assertFieldMatch("significantOccurrence:0.6","a b","a a a a a a a a a a b b", &params);
    assertFieldMatch("significantOccurrence:0.6","a b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("significantOccurrence:0.6","a b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("significantOccurrence:1",  "a b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("significantOccurrence:1",  "a b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length

    assertFieldMatch("significantOccurrence:0.7333","a%0.2 b","a a a a a a a a a a b b", &params);
    assertFieldMatch("significantOccurrence:0.4667","a b%0.2","a a a a a a a a a a b b", &params);
    assertFieldMatch("significantOccurrence:0.7333","a%0.2 b","a a a a a a a a a a a b b", &params); // Starting to cut off
    assertFieldMatch("significantOccurrence:0.7333","a%0.2 b","a a a a a a a a a a a a a a a a a a a a a b b", &params); // Way beyond cutoff for a
    assertFieldMatch("significantOccurrence:1",     "a%0.2 b","a a a a a a a a a a b b b b b b b b b b", &params); // Exactly no cutoff
    assertFieldMatch("significantOccurrence:1",     "a%0.2 b","a a a a a a a a a a a b b b b b b b b b b b", &params); // Field is too large to consider field length
}

void
Test::testFieldMatchExecutorUnweightedProximity()
{
    assertFieldMatch("unweightedProximity:1",    "a","a");
    assertFieldMatch("unweightedProximity:1",    "a b c","a b c");
    assertFieldMatch("unweightedProximity:1",    "a b c","a b c x");
    assertFieldMatch("unweightedProximity:1",    "y a b c","a b c x");
    assertFieldMatch("unweightedProximity:1",    "y a b c","a b c x");
    assertFieldMatch("unweightedProximity:0.855","y a b c","a b x c x");
    assertFieldMatch("unweightedProximity:0.750","y a b c","a b x x c x");
    assertFieldMatch("unweightedProximity:0.71", "y a b c","a x b x c x"); // Should be slightly worse than the previous one
    assertFieldMatch("unweightedProximity:0.605","y a b c","a x b x x c x");
    assertFieldMatch("unweightedProximity:0.53", "y a b c","a x b x x x c x");
    assertFieldMatch("unweightedProximity:0.5",  "y a b c","a x x b x x c x");
}


void
Test::testFieldMatchExecutorReverseProximity()
{
    assertFieldMatch("unweightedProximity:0.33",  "a b","b a");
    assertFieldMatch("unweightedProximity:0.62",  "a b c","c a b");
    assertFieldMatch("unweightedProximity:0.585", "y a b c","c x a b");
    assertFieldMatch("unweightedProximity:0.33",  "a b c","c b a");
    assertFieldMatch("unweightedProximity:0.6875","a b c d e","a b d c e");
    assertFieldMatch("unweightedProximity:0.9275","a b c d e","a b x c d e");
}


void
Test::testFieldMatchExecutorAbsoluteProximity()
{
    assertFieldMatch("absoluteProximity:0.1    proximity:1",     "a b","a b");
    assertFieldMatch("absoluteProximity:0.3    proximity:1",     "a 0.3:b","a b");
    assertFieldMatch("absoluteProximity:0.1    proximity:1",     "a 0.0:b","a b");
    assertFieldMatch("absoluteProximity:1      proximity:1",     "a 1.0:b","a b");
    assertFieldMatch("absoluteProximity:0.033  proximity:0.33",  "a b","b a");
    assertFieldMatch("absoluteProximity:0.0108 proximity:0.0359","a 0.3:b","b a"); // Should be worse than the previous one
    assertFieldMatch("absoluteProximity:0.1    proximity:1",     "a 0.0:b","b a");
    assertFieldMatch("absoluteProximity:0      proximity:0",     "a 1.0:b","b a");

    assertFieldMatch("absoluteProximity:0.0605 proximity:0.605", "a b c","a x b x x c");
    assertFieldMatch("absoluteProximity:0.0701 proximity:0.2003","a 0.5:b 0.2:c","a x b x x c"); // Most important is close, less important is far: Better
    assertFieldMatch("absoluteProximity:0.0605 proximity:0.605", "a b c","a x x b x c");
    assertFieldMatch("absoluteProximity:0.0582 proximity:0.1663","a 0.5:b 0.2:c","a x x b x c"); // Most important is far, less important is close: Worse

    assertFieldMatch("absoluteProximity:0.0727 proximity:0.7267","a b c d","a b x x x x x c d");
    assertFieldMatch("absoluteProximity:0.1   proximity:1",      "a b 0:c d","a b x x x x x c d"); // Should be better because the gap is unimportant

    // test with another proximity table
    std::vector<feature_t> pt;
    pt.push_back(0.2);
    pt.push_back(0.4);
    pt.push_back(0.6);
    pt.push_back(0.8);
    pt.push_back(1.0);
    pt.push_back(0.8);
    pt.push_back(0.6);
    pt.push_back(0.4);
    pt.push_back(0.2);
    fieldmatch::Params params;
    params.setProximityLimit(4);
    params.setProximityTable(pt);
    assertFieldMatch("absoluteProximity:0.07 proximity:0.7", "a b c","a x b x x c", &params);
    assertFieldMatch("absoluteProximity:0.1179 proximity:0.3369","a 0.5:b 0.2:c","a x b x x c", &params); // Most important is close, less important is far: Better
    assertFieldMatch("absoluteProximity:0.07 proximity:0.7", "a b c","a x x b x c", &params);
    assertFieldMatch("absoluteProximity:0.0834 proximity:0.2384","a 0.5:b 0.2:c","a x x b x c", &params); // Most important is far, less important is close: Worse
}


void
Test::testFieldMatchExecutorMultiSegmentProximity()
{
    assertFieldMatch("absoluteProximity:0.1    proximity:1",  "a b c",  "a b x x x x x x x x x x x x x x x x x x x x x x c");
    assertFieldMatch("absoluteProximity:0.05   proximity:0.5","a b c",  "a x x b x x x x x x x x x x x x x x x x x x x x x x c");
    assertFieldMatch("absoluteProximity:0.075 proximity:0.75","a b c d","a x x b x x x x x x x x x x x x x x x x x x x x x x c d");
}


void
Test::testFieldMatchExecutorSegmentDistance()
{
    assertFieldMatch("segmentDistance:13 absoluteProximity:0.1",  "a b c","a b x x x x x x x x x x c");
    assertFieldMatch("segmentDistance:13 absoluteProximity:0.5",  "a 0.5:b c","a b x x x x x x x x x x c");
    assertFieldMatch("segmentDistance:13 absoluteProximity:0.1",  "a b c","b c x x x x x x x x x x a");
    assertFieldMatch("segmentDistance:25 absoluteProximity:0.1",  "a b c","b x x x x x x x x x x x a x x x x x x x x x x c");
    assertFieldMatch("segmentDistance:13 absoluteProximity:0.006","a b c","a x x x x x x x x x x x b x x x x x x x x c");
    assertFieldMatch("segmentDistance:24 absoluteProximity:0.1",  "a b c","a x x x x x x x x x x x b x x x x x x x x x c");
    assertFieldMatch("segmentDistance:25 absoluteProximity:0.1",  "a b c","a x x x x x x x x x x x b x x x x x x x x x x c");
    assertFieldMatch("segmentDistance:25 absoluteProximity:0.1",  "a b c","c x x x x x x x x x x x b x x x x x x x x x x a");
}


void
Test::testFieldMatchExecutorSegmentProximity()
{
    assertFieldMatch("segmentProximity:1",  "a","a");
    assertFieldMatch("segmentProximity:0",  "a","x");
    assertFieldMatch("segmentProximity:1",  "a","a x");
    assertFieldMatch("segmentProximity:0",  "a b","a x x x x x x x x x x x x x x x x x x x x x x x b");
    assertFieldMatch("segmentProximity:0.4","a b","a x x x x x x x x x x x x x x x x x x x x x x b x x x x x x x x x x x x x x x x");
    assertFieldMatch("segmentProximity:0",  "a b c","a b x x x x x x x x x x x x x x x x x x x x x c");
    assertFieldMatch("segmentProximity:0.4","a b c","a b x x x x x x x x x x x x x x x x x x x x x c x x x x x x x x x x x x x x x x");
    assertFieldMatch("segmentProximity:0.4","a b c","b c x x x x x x x x x x x x x x x x x x x x x a x x x x x x x x x x x x x x x x");
}


void
Test::testFieldMatchExecutorSegmentStarts()
{
#ifdef FIELDMATCH_OUTPUTS_SEGMENTSTARTS
    // Test cases where we choose between multiple different segmentations
    { // test segmentSelection
        assertFieldMatch("segments:2 absoluteProximity:0.1 proximity:1 segmentStarts:19,41",
                         "a b c d e","x a b x c x x x x x x x x x x x x x x a b c x x x x x x x x x e x d x c d x x x c d e");
        //                         0 1 2 3 4 5 6 7 8 9�0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
        //                         0                   1                   2                   3                   4
        // Should choose                                                 - - -                                       - -

        assertFieldMatch("segments:1 absoluteProximity:0.0778 proximity:0.778","a b c d e f","x x a b b b c f e d a b c d x e x x x x x f d e f a b c a a b b c c d d e e f f");

        // Prefer one segment with ok proximity or two segments with great proximity
        assertFieldMatch("segments:1 segmentStarts:0","a b c d","a b x c d x x x x x x x x x x x a b x x x x x x x x x x x c d");
        assertFieldMatch("segments:1 segmentStarts:0","a b c d","a b x x x x x x x x c d x x x x x x x x x x x a b x x x x x x x x x x x c d");
    }
#endif
}


void
Test::testFieldMatchExecutorMoreThanASegmentLengthOfUnmatchedQuery()
{
    assertFieldMatch("absoluteProximity:0.1 proximity:1","a b y y y y y y y y y y y y y y y","a b");
    assertFieldMatch("segments:2 absoluteProximity:0.1 proximity:1","a b c d y y y y y y y y y y y y y y y","a b x x x x x x x x x x x x x x x x x x c d");
    assertFieldMatch("segments:2 absoluteProximity:0.1 proximity:1","a b y y y y y y y y y y y y y y y c d","a b x x x x x x x x x x x x x x x x x x c d");
}


void
Test::testFieldMatchExecutorQueryRepeats()
{
    // Not really handled perfectly, but good enough
    assertFieldMatch("absoluteProximity:0.1    proximity:1      head:0 tail:0",            "a a a","a");
    assertFieldMatch("absoluteProximity:0.1    proximity:1      head:0 tail:0 gapLength:0","a a b c c","a a b c c");
    assertFieldMatch("absoluteProximity:0.1    proximity:1      head:0 tail:0 gapLength:0","a a b c c","a b c");
    assertFieldMatch("absoluteProximity:0.1    proximity:1      head:0 tail:0 gapLength:0","a b a b","a b a b");
    assertFieldMatch("absoluteProximity:0.0903 proximity:0.9033 head:0 tail:0 gapLength:1","a b a b","a b x a b");
    // Both terms take the same segment:
    assertFieldMatch("absoluteProximity:0.1 proximity:1 segments:2 gapLength:0 head:3 tail:18","a a","x x x a x x x x x x x x x x x x x x a x x x");
    // But not when the second is preferable
    assertFieldMatch("absoluteProximity:0.1 proximity:1 segments:2 gapLength:0 head:3 tail:3","a b b a","x x x a b x x x x x x x x x x x x x x b a x x x");
    assertFieldMatch("matches:2 fieldCompleteness:1","a b b b","a b");
}


void
Test::testFieldMatchExecutorZeroCases()
{
    // Note: we have no hits -> absoluteProximity:0(0.1) proximity:0(1)
    assertFieldMatch("absoluteProximity:0 proximity:0 matches:0","y","a");
    assertFieldMatch("absoluteProximity:0 proximity:0 matches:0","a","x");
    assertFieldMatch("absoluteProximity:0 proximity:0 matches:0","","x");
    assertFieldMatch("absoluteProximity:0 proximity:0 matches:0","y","");
    assertFieldMatch("absoluteProximity:0 proximity:0 matches:0","","");
}


void
Test::testFieldMatchExecutorExceedingIterationLimit()
{
    // Segments found: a x x b   and   c d
    {
        fieldmatch::Params params;
        params.setMaxAlternativeSegmentations(0);
        assertFieldMatch("matches:4 tail:0 proximity:0.75 absoluteProximity:0.075","a b c d","a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d", &params);
    }

    // Segments found: a x b   and   c d
    {
        fieldmatch::Params params;
        params.setMaxAlternativeSegmentations(1);
        assertFieldMatch("matches:4 tail:0 proximity:0.855 absoluteProximity:0.0855","a b c d","a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d", &params);
    }

    // Segments found: a b   and   c d
    {
        fieldmatch::Params params;
        params.setMaxAlternativeSegmentations(2);
        assertFieldMatch("matches:4 tail:0 proximity:1 absoluteProximity:0.1","a b c d","a x x b x x x a x b x x x x x a b x x x x x x x x x x x x x x x x x c d", &params);
    }
}


void
Test::testFieldMatchExecutorRemaining()
{

    { // test match (aka score)
        // Ordered by decreasing match score per query
        assertFieldMatch("score:1",     "a","a");
        assertFieldMatch("score:0.9339","a","a x");
        assertFieldMatch("score:0",     "a","x");
        assertFieldMatch("score:0.9243","a","x a");
        assertFieldMatch("score:0.9025","a","x a x");

        assertFieldMatch("score:1",     "a b","a b");
        assertFieldMatch("score:0.9558","a b","a b x");
        assertFieldMatch("score:0.9463","a b","x a b");
        assertFieldMatch("score:0.1296","a b","a x x x x x x x x x x x x x x x x x x x x x x b");
        assertFieldMatch("score:0.1288","a b","a x x x x x x x x x x x x x x x x x x x x x x x x x x x b");

        assertFieldMatch("score:0.8647","a b c","x x a x b x x x x x x x x a b c x x x x x x x x c x x");
        assertFieldMatch("score:0.861", "a b c","x x a x b x x x x x x x x x x a b c x x x x x x c x x");
        assertFieldMatch("score:0.4869","a b c","a b x x x x x x x x x x x x x x x x x x x x x x c x x");
        assertFieldMatch("score:0.4853","a b c","x x a x b x x x x x x x x x x b a c x x x x x x c x x");
        assertFieldMatch("score:0.3621","a b c","a x b x x x x x x x x x x x x x x x x x x x x x c x x");
        assertFieldMatch("score:0.3619","a b c","x x a x b x x x x x x x x x x x x x x x x x x x c x x");
        assertFieldMatch("score:0.3584","a b c","x x a x b x x x x x x x x x x x x x x x x x x x x x c");
        assertFieldMatch("score:0.3421","a b c","x x a x b x x x x x x x x x x x x x x x x x x x x x x");

        assertFieldMatch("score:0.3474","a b c","x x a x b x x x x x x x x x x x x x x b x x x b x b x");
    }

    { // test repeated match
        // gap==1 caused by finding two possible segments due to repeated matching
        assertFieldMatch("fieldCompleteness:1 queryCompleteness:0.6667 segments:1 earliness:1 gaps:1",
                         "pizza hut pizza","pizza hut");
    }

    //------------------- extra tests -------------------//

    { // test with a query on an attribute field
        LOG(info, "Query on an attribute field");
        vespalib::string feature = "fieldMatch(foo)";
        FtFeatureTest ft(_factory, feature);
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");
        ft.getQueryEnv().getBuilder().addAttributeNode("bar");
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(toRankResult(feature, "score:0")));
    }


    { // test with query on another index field as well
        LOG(info, "Query on an another index field");
        FtFeatureTest ft(_factory, StringList().add("fieldMatch(foo)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")); // search on 'foo' (0)
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("bar")); // search on 'bar' (1)
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

        // add occurrence for 'foo' with query=a
        ASSERT_TRUE(mdb->setFieldLength("foo", 1));
        ASSERT_TRUE(mdb->addOccurence("foo", 0, 0)); // a

        // add occurrence for 'bar' with query=a
        ASSERT_TRUE(mdb->setFieldLength("bar", 2));
        ASSERT_TRUE(mdb->addOccurence("bar", 1, 1)); // x a

        ASSERT_TRUE(mdb->apply(1));

        ASSERT_TRUE(ft.execute(toRankResult("fieldMatch(foo)", "score:1 matches:1 queryCompleteness:1 fieldCompleteness:1")));
        ASSERT_TRUE(ft.execute(toRankResult("fieldMatch(foo)", "score:0"), 2)); // another docid -> no hit -> default values
    }

    { // search on more than one document
        LOG(info, "Query on more than one document");
        FtFeatureTest ft(_factory, StringList().add("fieldMatch(foo)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")); // 'a' (0)
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")); // 'b' (1)
        ASSERT_TRUE(ft.setup());

        // check that we get the same results as this
        // assertFieldMatch("score:1",     "a b","a b");
        // assertFieldMatch("score:0.9558","a b","a b x");
        // assertFieldMatch("score:0.932", "a b","x a b");

        { // docid 1: "a b"
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            ASSERT_TRUE(mdb->setFieldLength("foo", 2));
            ASSERT_TRUE(mdb->addOccurence("foo", 0, 0)); // 'a'
            ASSERT_TRUE(mdb->addOccurence("foo", 1, 1)); // 'b'
            ASSERT_TRUE(mdb->apply(1));
            ASSERT_TRUE(ft.execute(toRankResult("fieldMatch(foo)", "score:1 matches:2"), 1));
        }
        { // docid 2: "a b x"
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            ASSERT_TRUE(mdb->setFieldLength("foo", 3));
            ASSERT_TRUE(mdb->addOccurence("foo", 0, 0)); // 'a'
            ASSERT_TRUE(mdb->addOccurence("foo", 1, 1)); // 'b'
            ASSERT_TRUE(mdb->apply(2));
            RankResult rr = toRankResult("fieldMatch(foo)", "score:0.9558 matches:2");
            rr.setEpsilon(1e-4); // same as java tests
            ASSERT_TRUE(ft.execute(rr, 2));
        }
        { // docid 3: "x a b"
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            ASSERT_TRUE(mdb->setFieldLength("foo", 3));
            ASSERT_TRUE(mdb->addOccurence("foo", 0, 1)); // 'a'
            ASSERT_TRUE(mdb->addOccurence("foo", 1, 2)); // 'b'
            ASSERT_TRUE(mdb->apply(3));
            RankResult rr = toRankResult("fieldMatch(foo)", "score:0.9463 matches:2");
            rr.setEpsilon(1e-4); // same as java tests
            ASSERT_TRUE(ft.execute(rr, 3));
        }
    }

    { // test where not all hits have position information
        LOG(info, "Not all hits have position information");
        FtFeatureTest ft(_factory, StringList().add("fieldMatch(foo)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo"))->setWeight(search::query::Weight(200)); // search for 'a' (termId 0)
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo"))->setWeight(search::query::Weight(400)); // search for 'b' (termId 1)
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo"))->setWeight(search::query::Weight(600)); // search for 'c' (termId 2)
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo"))->setWeight(search::query::Weight(800)); // search for 'd' (termId 3)
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("bar"))->setWeight(search::query::Weight(1000)); // search for 'e' (termId 4)
        ASSERT_TRUE(ft.setup());

        assertFieldMatch("score:0.3389 completeness:0.5083 degradedMatches:0", "a b c d", "x a b");

        // field: x a b
        { // no pos occ for term b -> score is somewhat degraded (lower .occurrence)
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            // add occurrence with query term 'a'
            ASSERT_TRUE(mdb->setFieldLength("foo", 3));
            ASSERT_TRUE(mdb->addOccurence("foo", 0, 1));
            // add hit with query term 'b'
            mdb->getTermFieldMatchData(1, 0)->reset(1);
            ASSERT_TRUE(mdb->apply(1));
            ASSERT_TRUE(ft.execute(toRankResult("fieldMatch(foo)",
                                                "score:0.3231 completeness:0.5083 queryCompleteness:0.5 weight:0.2 matches:2 degradedMatches:1").
                                   setEpsilon(1e-4)));
        }
        { // no pos occ for term a & b
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            // add hit with query term 'a'
            mdb->getTermFieldMatchData(0, 0)->reset(1);
            // add hit with query term 'b'
            mdb->getTermFieldMatchData(1, 0)->reset(1);
            ASSERT_TRUE(mdb->apply(1));
            { // reset lazy evaluation
                RankResult dummy;
                ft.executeOnly(dummy, 0);
            }
            ASSERT_TRUE(ft.execute(toRankResult("fieldMatch(foo)",
                                                "score:0 completeness:0.475 queryCompleteness:0.5 weight:0.2 matches:2 degradedMatches:2").
                                   setEpsilon(1e-4)));
        }
    }

    { // invalid field length
        LOG(info, "We have an invalid field length");
        FtFeatureTest ft(_factory, StringList().add("fieldMatch(foo)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo"))->setWeight(search::query::Weight(100)); // search for 'a' (termId 0)
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

        // add occurrence with query term 'a'
        ASSERT_TRUE(mdb->setFieldLength("foo", search::fef::FieldPositionsIterator::UNKNOWN_LENGTH)); // invalid field length
        ASSERT_TRUE(mdb->addOccurence("foo", 0, 10));

        ASSERT_TRUE(mdb->apply(1));

        ASSERT_TRUE(ft.execute(toRankResult("fieldMatch(foo)", "score:0 matches:1 degradedMatches:0")));
    }

    { // test default values when we do not have hits in the field
        LOG(info, "Default values when we have no hits");
        FtFeatureTest ft(_factory, StringList().add("fieldMatch(foo)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")); // search on 'foo' (0)
        ASSERT_TRUE(ft.setup());

        // must create this so that term match data is configured with the term data object
        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

        RankResult rr = toRankResult("fieldMatch(foo)",
                                     "score:0 "
                                     "proximity:0 "
                                     "completeness:0 "
                                     "queryCompleteness:0 "
                                     "fieldCompleteness:0 "
                                     "orderness:0 "
                                     "relatedness:0 "
                                     "earliness:0 "
                                     "longestSequenceRatio:0 "
                                     "segmentProximity:0 "
                                     "unweightedProximity:0 "
                                     "absoluteProximity:0 "
                                     "occurrence:0 "
                                     "absoluteOccurrence:0 "
                                     "weightedOccurrence:0 "
                                     "weightedAbsoluteOccurrence:0 "
                                     "significantOccurrence:0 "
                                     "weight:0 "
                                     "significance:0 "
                                     "importance:0 "
                                     "segments:0 "
                                     "matches:0 "
                                     "outOfOrder:0 "
                                     "gaps:0 "
                                     "gapLength:0 "
                                     "longestSequence:0 "
                                     "head:0 "
                                     "tail:0 "
                                     "segmentDistance:0 ")
                        .setEpsilon(10e-6);

        ASSERT_TRUE(ft.execute(rr, 1)); // another docid -> no hit -> default values
    }
}
