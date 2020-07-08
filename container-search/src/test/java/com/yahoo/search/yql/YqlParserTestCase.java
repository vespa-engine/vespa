// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

import com.yahoo.component.chain.Chain;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.language.Language;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.BoolItem;
import com.yahoo.prelude.query.IndexedItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.MarkerWordItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.RegExpItem;
import com.yahoo.prelude.query.SegmentingRule;
import com.yahoo.prelude.query.Substring;
import com.yahoo.prelude.query.SubstringItem;
import com.yahoo.prelude.query.SuffixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordAlternativesItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.IndexInfoConfig.Indexinfo;
import com.yahoo.search.config.IndexInfoConfig.Indexinfo.Alias;
import com.yahoo.search.config.IndexInfoConfig.Indexinfo.Command;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Sorting.AttributeSorter;
import com.yahoo.search.query.Sorting.FieldOrder;
import com.yahoo.search.query.Sorting.LowerCaseSorter;
import com.yahoo.search.query.Sorting.Order;
import com.yahoo.search.query.Sorting.UcaSorter;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Specification for the conversion of YQL+ expressions to Vespa search queries.
 *
 * @author Steinar Knutsen
 * @author Stian Kristoffersen
 */
public class YqlParserTestCase {

    private final YqlParser parser = new YqlParser(new ParserEnvironment());

    @Test
    public void testParserDefaults() {
        assertTrue(parser.isQueryParser());
        assertNull(parser.getDocTypes());
    }
    
    @Test
    public void testLanguageDetection() {
        // SimpleDetector used here can detect japanese and will set that as language at the root of the user input
        QueryTree tree = parse("select * from sources * where userInput(\"\u30ab\u30bf\u30ab\u30ca\");");
        assertEquals(Language.JAPANESE, tree.getRoot().getLanguage());
    }

    @Test
    public void testGroupingStep() {
        assertParse("select foo from bar where baz contains 'cox';",
                    "baz:cox");
        assertEquals("[]",
                     toString(parser.getGroupingSteps()));

        assertParse("select foo from bar where baz contains 'cox' " +
                    "| all(group(a) each(output(count())));",
                    "baz:cox");
        assertEquals("[[]all(group(a) each(output(count())))]",
                     toString(parser.getGroupingSteps()));

        assertParse("select foo from bar where baz contains 'cox' " +
                    "| all(group(a) each(output(count()))) " +
                    "| all(group(b) each(output(count())));",
                    "baz:cox");
        assertEquals("[[]all(group(a) each(output(count())))," +
                     " []all(group(b) each(output(count())))]",
                     toString(parser.getGroupingSteps()));
    }

    @Test
    public void testGroupingContinuation() {
        assertParse("select foo from bar where baz contains 'cox' " +
                    "| [{ 'continuations': ['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]all(group(a) each(output(count())));",
                    "baz:cox");
        assertEquals("[[BCBCBCBEBG, BCBKCBACBKCCK]all(group(a) each(output(count())))]",
                     toString(parser.getGroupingSteps()));

        assertParse("select foo from bar where baz contains 'cox' " +
                    "| [{ 'continuations': ['BCBCBCBEBG', 'BCBKCBACBKCCK'] }]all(group(a) each(output(count()))) " +
                    "| [{ 'continuations': ['BCBBBBBDBF', 'BCBJBPCBJCCJ'] }]all(group(b) each(output(count())));",
                    "baz:cox");
        assertEquals("[[BCBCBCBEBG, BCBKCBACBKCCK]all(group(a) each(output(count())))," +
                     " [BCBBBBBDBF, BCBJBPCBJCCJ]all(group(b) each(output(count())))]",
                     toString(parser.getGroupingSteps()));
    }

    @Test
    public void test() {
        assertParse("select foo from bar where title contains \"madonna\";",
                    "title:madonna");
    }

    @Test
    public void testComplexExpression() {
        String queryTreeYql = "rank((((filter contains ([{\"origin\": {\"original\": \"filter:VideoAdsCappingTestCPM\", \"offset\": 7, \"length\": 22}, \"normalizeCase\": false, \"id\": 1}]\"videoadscappingtestcpm\") AND hasRankRestriction contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 2}]\"0\") AND ((objective contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 3}]\"install_app\") AND availableExtendedFields contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 4}]\"cpiparams\")) OR (availableExtendedFields contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 5}]\"appinstallinfo\") AND availableExtendedFields contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 6}]\"appmetroplexinfo\")) OR (dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 7}]\"default\")) AND !(objective contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 8}]\"install_app\"))) AND advt_age = ([{\"id\": 9}]2147483647) AND advt_gender contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 10}]\"all\") AND advt_all_segments = ([{\"id\": 11}]2147483647) AND advt_keywords contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 12}]\"all\") AND advMobilePlatform = ([{\"id\": 13}]2147483647) AND advMobileDeviceType = ([{\"id\": 14}]2147483647) AND advMobileCon = ([{\"id\": 15}]2147483647) AND advMobileOSVersions = ([{\"id\": 16}]2147483647) AND advCarrier = ([{\"id\": 17}]2147483647) AND ([{\"id\": 18}]weightedSet(advt_supply, {\"all\": 1, \"pub223\": 1, \"sec223\": 1, \"site223\": 1})) AND (advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 19, \"weight\": 1}]\"adv_tuesday\") OR advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 20, \"weight\": 1}]\"adv_tuesday_17\") OR advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 21, \"weight\": 1}]\"adv_tuesday_17_forty_five\") OR advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 22}]\"all\")) AND isAppReengagementAd = ([{\"id\": 23}]0) AND dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 24}]\"default\") AND serveWithPromotionOnly = ([{\"id\": 26}]0) AND budgetAdvertiserThrottleRateFilter = ([{\"id\": 27}]0) AND budgetResellerThrottleRateFilter = ([{\"id\": 28}]0) AND (isMystiqueRequired = ([{\"id\": 29}]0) OR (isMystiqueRequired = ([{\"id\": 30}]1) AND useBcFactorFilter = ([{\"id\": 31}]1))) AND (((budgetCampaignThrottleRateBits = ([{\"id\": 32}]55) AND dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 33}]\"default\"))) AND !(useBcFactorFilter = ([{\"id\": 34}]1)) OR ((useBcFactorFilter = ([{\"id\": 35}]1) AND dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 36}]\"default\") AND (bcFactorTiers = ([{\"id\": 38}]127) OR bcFactorTiers = ([{\"id\": 39}]0)) AND ((firstPriceEnforced = ([{\"id\": 40}]0) AND (secondPriceEnforced = ([{\"id\": 41}]1) OR isPrivateDeal = ([{\"id\": 42}]0) OR (dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 43}]\"default\")) AND !(bcActiveTier = ([{\"id\": 44}]0)))) OR mystiqueCampaignThrottleRateBits = ([{\"id\": 45}]18)))) AND !(isOutOfDailyBudget = ([{\"id\": 37}]1))) AND testCreative = ([{\"id\": 46}]0) AND advt_geo = ([{\"id\": 47}]2147483647) AND ((adType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 48}]\"strm_video\") AND isPortraitVideo = ([{\"id\": 49}]0)) OR adType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 50}]\"stream_ad\")) AND ((isCPM = ([{\"id\": 51}]0) AND isOCPC = ([{\"id\": 52}]0) AND isECPC = ([{\"id\": 53}]0) AND ((priceType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 54}]\"cpcv\") AND bid >= ([{\"id\": 55}]0.005)) OR (priceType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 56}]\"cpv\") AND bid >= ([{\"id\": 57}]0.01)) OR (priceType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 58}]\"cpc\") AND bid >= ([{\"id\": 59}]0.05)) OR (objective contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 60}]\"promote_content\") AND bid >= ([{\"id\": 61}]0.01)) OR hasFloorPriceUsd = ([{\"id\": 62}]1))) OR isECPC = ([{\"id\": 63}]1) OR (isCPM = ([{\"id\": 64}]1) AND isOCPM = ([{\"id\": 65}]0) AND (([{\"id\": 66}]range(bid, 0.25, Infinity)) OR hasFloorPriceUsd = ([{\"id\": 67}]1)))) AND start_date <= ([{\"id\": 68}]1572976776299L) AND end_date >= ([{\"id\": 69}]1572976776299L))) AND !(isHoldoutAd = ([{\"id\": 25}]1))) AND !((disclaimerExtensionsTypes contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 70}]\"pharma\") OR ([{\"id\": 71}]weightedSet(exclusion_advt_supply, {\"extsite223\": 1, \"pub223\": 1, \"sec223\": 1, \"site223\": 1})) OR isPersonalized = ([{\"id\": 72}]1) OR blocked_section_ids contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 73}]\"223\") OR blocked_publisher_ids contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 74}]\"223\") OR blocked_site_ids contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 75}]\"223\"))), [{\"id\": 76, \"label\": \"ad_ocpc_max_cpc\"}]dotProduct(ocpc_max_cpc, {\"0\": 1}), [{\"id\": 77, \"label\": \"ad_ocpc_min_cpc\"}]dotProduct(ocpc_min_cpc, {\"0\": 1}), [{\"id\": 78, \"label\": \"ad_ocpc_max_alpha\"}]dotProduct(ocpc_max_alpha, {\"0\": 1}), [{\"id\": 79, \"label\": \"ad_ocpc_min_alpha\"}]dotProduct(ocpc_min_alpha, {\"0\": 1}), [{\"id\": 80, \"label\": \"ad_ocpc_alpha_0\"}]dotProduct(ocpc_alpha_0, {\"0\": 1}), [{\"id\": 81, \"label\": \"ad_ocpc_alpha_1\"}]dotProduct(ocpc_alpha_1, {\"0\": 1}), (bidAdjustmentDayParting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 82, \"weight\": 1}]\"adv_tuesday\") OR bidAdjustmentDayParting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 83, \"weight\": 1}]\"adv_tuesday_17\") OR bidAdjustmentDayParting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 84, \"weight\": 1}]\"adv_tuesday_17_forty_five\") OR bidAdjustmentDayPartingForCostCap contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 85, \"weight\": 1}]\"adv_tuesday\") OR bidAdjustmentDayPartingForCostCap contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 86, \"weight\": 1}]\"adv_tuesday_17\") OR bidAdjustmentDayPartingForCostCap contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 87, \"weight\": 1}]\"adv_tuesday_17_forty_five\")), bidAdjustmentForCpi = ([{\"id\": 88, \"weight\": 1}]223), [{\"id\": 89, \"label\": \"boostingForBackfill\"}]dotProduct(boostingForBackfill, {\"priority\": 1000})) limit 0 timeout 3980 | all(group(adTypeForGrouping) each(group(advertiser_id) max(11) output(count() as(groupingCounter)) each(max(1) each(output(summary())))))";
        QueryTree parsed = assertParse("select * from sources * where " + queryTreeYql + ";",
                                       "RANK (+(+(AND filter:VideoAdsCappingTestCPM hasRankRestriction:0 (OR (AND objective:install_app availableExtendedFields:cpiparams) (AND availableExtendedFields:appinstallinfo availableExtendedFields:appmetroplexinfo) (+dummyField:default -objective:install_app)) advt_age:2147483647 advt_gender:all advt_all_segments:2147483647 advt_keywords:all advMobilePlatform:2147483647 advMobileDeviceType:2147483647 advMobileCon:2147483647 advMobileOSVersions:2147483647 advCarrier:2147483647 WEIGHTEDSET advt_supply{[1]:\"site223\",[1]:\"pub223\",[1]:\"all\",[1]:\"sec223\"} (OR advt_day_parting:adv_tuesday!1 advt_day_parting:adv_tuesday_17!1 advt_day_parting:adv_tuesday_17_forty_five!1 advt_day_parting:all) isAppReengagementAd:0 dummyField:default serveWithPromotionOnly:0 budgetAdvertiserThrottleRateFilter:0 budgetResellerThrottleRateFilter:0 (OR isMystiqueRequired:0 (AND isMystiqueRequired:1 useBcFactorFilter:1)) (OR (+(AND budgetCampaignThrottleRateBits:55 dummyField:default) -useBcFactorFilter:1) (+(AND useBcFactorFilter:1 dummyField:default (OR bcFactorTiers:127 bcFactorTiers:0) (OR (AND firstPriceEnforced:0 (OR secondPriceEnforced:1 isPrivateDeal:0 (+dummyField:default -bcActiveTier:0))) mystiqueCampaignThrottleRateBits:18)) -isOutOfDailyBudget:1)) testCreative:0 advt_geo:2147483647 (OR (AND adType:strm_video isPortraitVideo:0) adType:stream_ad) (OR (AND isCPM:0 isOCPC:0 isECPC:0 (OR (AND priceType:cpcv bid:[0.005;]) (AND priceType:cpv bid:[0.01;]) (AND priceType:cpc bid:[0.05;]) (AND objective:promote_content bid:[0.01;]) hasFloorPriceUsd:1)) isECPC:1 (AND isCPM:1 isOCPM:0 (OR bid:[0.25;] hasFloorPriceUsd:1))) start_date:[;1572976776299] end_date:[1572976776299;]) -isHoldoutAd:1) -(OR disclaimerExtensionsTypes:pharma WEIGHTEDSET exclusion_advt_supply{[1]:\"extsite223\",[1]:\"site223\",[1]:\"pub223\",[1]:\"sec223\"} isPersonalized:1 blocked_section_ids:223 blocked_publisher_ids:223 blocked_site_ids:223)) DOTPRODUCT ocpc_max_cpc{[1]:\"0\"} DOTPRODUCT ocpc_min_cpc{[1]:\"0\"} DOTPRODUCT ocpc_max_alpha{[1]:\"0\"} DOTPRODUCT ocpc_min_alpha{[1]:\"0\"} DOTPRODUCT ocpc_alpha_0{[1]:\"0\"} DOTPRODUCT ocpc_alpha_1{[1]:\"0\"} (OR bidAdjustmentDayParting:adv_tuesday!1 bidAdjustmentDayParting:adv_tuesday_17!1 bidAdjustmentDayParting:adv_tuesday_17_forty_five!1 bidAdjustmentDayPartingForCostCap:adv_tuesday!1 bidAdjustmentDayPartingForCostCap:adv_tuesday_17!1 bidAdjustmentDayPartingForCostCap:adv_tuesday_17_forty_five!1) bidAdjustmentForCpi:223!1 DOTPRODUCT boostingForBackfill{[1000]:\"priority\"}");
        String serializedQueryTreeYql = VespaSerializer.serialize(parsed);

        // Note: All the details here are not verified
        assertEquals("rank((((filter contains ([{\"normalizeCase\": false, \"id\": 1}]\"VideoAdsCappingTestCPM\") AND hasRankRestriction contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 2}]\"0\") AND ((objective contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 3}]\"install_app\") AND availableExtendedFields contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 4}]\"cpiparams\")) OR (availableExtendedFields contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 5}]\"appinstallinfo\") AND availableExtendedFields contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 6}]\"appmetroplexinfo\")) OR (dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 7}]\"default\")) AND !(objective contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 8}]\"install_app\"))) AND advt_age = ([{\"id\": 9}]2147483647) AND advt_gender contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 10}]\"all\") AND advt_all_segments = ([{\"id\": 11}]2147483647) AND advt_keywords contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 12}]\"all\") AND advMobilePlatform = ([{\"id\": 13}]2147483647) AND advMobileDeviceType = ([{\"id\": 14}]2147483647) AND advMobileCon = ([{\"id\": 15}]2147483647) AND advMobileOSVersions = ([{\"id\": 16}]2147483647) AND advCarrier = ([{\"id\": 17}]2147483647) AND ([{\"id\": 18}]weightedSet(advt_supply, {\"all\": 1, \"pub223\": 1, \"sec223\": 1, \"site223\": 1})) AND (advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 19, \"weight\": 1}]\"adv_tuesday\") OR advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 20, \"weight\": 1}]\"adv_tuesday_17\") OR advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 21, \"weight\": 1}]\"adv_tuesday_17_forty_five\") OR advt_day_parting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 22}]\"all\")) AND isAppReengagementAd = ([{\"id\": 23}]0) AND dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 24}]\"default\") AND serveWithPromotionOnly = ([{\"id\": 26}]0) AND budgetAdvertiserThrottleRateFilter = ([{\"id\": 27}]0) AND budgetResellerThrottleRateFilter = ([{\"id\": 28}]0) AND (isMystiqueRequired = ([{\"id\": 29}]0) OR (isMystiqueRequired = ([{\"id\": 30}]1) AND useBcFactorFilter = ([{\"id\": 31}]1))) AND (((budgetCampaignThrottleRateBits = ([{\"id\": 32}]55) AND dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 33}]\"default\"))) AND !(useBcFactorFilter = ([{\"id\": 34}]1)) OR ((useBcFactorFilter = ([{\"id\": 35}]1) AND dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 36}]\"default\") AND (bcFactorTiers = ([{\"id\": 38}]127) OR bcFactorTiers = ([{\"id\": 39}]0)) AND ((firstPriceEnforced = ([{\"id\": 40}]0) AND (secondPriceEnforced = ([{\"id\": 41}]1) OR isPrivateDeal = ([{\"id\": 42}]0) OR (dummyField contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 43}]\"default\")) AND !(bcActiveTier = ([{\"id\": 44}]0)))) OR mystiqueCampaignThrottleRateBits = ([{\"id\": 45}]18)))) AND !(isOutOfDailyBudget = ([{\"id\": 37}]1))) AND testCreative = ([{\"id\": 46}]0) AND advt_geo = ([{\"id\": 47}]2147483647) AND ((adType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 48}]\"strm_video\") AND isPortraitVideo = ([{\"id\": 49}]0)) OR adType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 50}]\"stream_ad\")) AND ((isCPM = ([{\"id\": 51}]0) AND isOCPC = ([{\"id\": 52}]0) AND isECPC = ([{\"id\": 53}]0) AND ((priceType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 54}]\"cpcv\") AND bid >= ([{\"id\": 55}]0.005)) OR (priceType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 56}]\"cpv\") AND bid >= ([{\"id\": 57}]0.01)) OR (priceType contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 58}]\"cpc\") AND bid >= ([{\"id\": 59}]0.05)) OR (objective contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 60}]\"promote_content\") AND bid >= ([{\"id\": 61}]0.01)) OR hasFloorPriceUsd = ([{\"id\": 62}]1))) OR isECPC = ([{\"id\": 63}]1) OR (isCPM = ([{\"id\": 64}]1) AND isOCPM = ([{\"id\": 65}]0) AND ([{\"id\": 66}]range(bid, 0.25, Infinity) OR hasFloorPriceUsd = ([{\"id\": 67}]1)))) AND start_date <= ([{\"id\": 68}]1572976776299L) AND end_date >= ([{\"id\": 69}]1572976776299L))) AND !(isHoldoutAd = ([{\"id\": 25}]1))) AND !((disclaimerExtensionsTypes contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 70}]\"pharma\") OR ([{\"id\": 71}]weightedSet(exclusion_advt_supply, {\"extsite223\": 1, \"pub223\": 1, \"sec223\": 1, \"site223\": 1})) OR isPersonalized = ([{\"id\": 72}]1) OR blocked_section_ids contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 73}]\"223\") OR blocked_publisher_ids contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 74}]\"223\") OR blocked_site_ids contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 75}]\"223\"))), ([{\"id\": 76, \"label\": \"ad_ocpc_max_cpc\"}]dotProduct(ocpc_max_cpc, {\"0\": 1})), ([{\"id\": 77, \"label\": \"ad_ocpc_min_cpc\"}]dotProduct(ocpc_min_cpc, {\"0\": 1})), ([{\"id\": 78, \"label\": \"ad_ocpc_max_alpha\"}]dotProduct(ocpc_max_alpha, {\"0\": 1})), ([{\"id\": 79, \"label\": \"ad_ocpc_min_alpha\"}]dotProduct(ocpc_min_alpha, {\"0\": 1})), ([{\"id\": 80, \"label\": \"ad_ocpc_alpha_0\"}]dotProduct(ocpc_alpha_0, {\"0\": 1})), ([{\"id\": 81, \"label\": \"ad_ocpc_alpha_1\"}]dotProduct(ocpc_alpha_1, {\"0\": 1})), (bidAdjustmentDayParting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 82, \"weight\": 1}]\"adv_tuesday\") OR bidAdjustmentDayParting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 83, \"weight\": 1}]\"adv_tuesday_17\") OR bidAdjustmentDayParting contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 84, \"weight\": 1}]\"adv_tuesday_17_forty_five\") OR bidAdjustmentDayPartingForCostCap contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 85, \"weight\": 1}]\"adv_tuesday\") OR bidAdjustmentDayPartingForCostCap contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 86, \"weight\": 1}]\"adv_tuesday_17\") OR bidAdjustmentDayPartingForCostCap contains ([{\"normalizeCase\": false, \"implicitTransforms\": false, \"id\": 87, \"weight\": 1}]\"adv_tuesday_17_forty_five\")), bidAdjustmentForCpi = ([{\"id\": 88, \"weight\": 1}]223), ([{\"id\": 89, \"label\": \"boostingForBackfill\"}]dotProduct(boostingForBackfill, {\"priority\": 1000})))",
                     serializedQueryTreeYql);
    }

    @Test
    public void testDottedFieldNames() {
        assertParse("select foo from bar where my.nested.title contains \"madonna\";",
                "my.nested.title:madonna");
    }
    @Test
    public void testDottedNestedFieldNames() {
        assertParse("select foo from bar where my.title contains \"madonna\";",
                "my.title:madonna");
    }

    @Test
    public void testOr() {
        assertParse("select foo from bar where title contains \"madonna\" or title contains \"saint\";",
                    "OR title:madonna title:saint");
        assertParse("select foo from bar where title contains \"madonna\" or title contains \"saint\" or title " +
                    "contains \"angel\";",
                    "OR title:madonna title:saint title:angel");
    }

    @Test
    public void testAnd() {
        assertParse("select foo from bar where title contains \"madonna\" and title contains \"saint\";",
                    "AND title:madonna title:saint");
        assertParse("select foo from bar where title contains \"madonna\" and title contains \"saint\" and title " +
                    "contains \"angel\";",
                    "AND title:madonna title:saint title:angel");
    }

    @Test
    public void testAndNot() {
        assertParse("select foo from bar where title contains \"madonna\" and !(title contains \"saint\");",
                    "+title:madonna -title:saint");
    }

    @Test
    public void testLessThan() {
        assertParse("select foo from bar where price < 500;", "price:<500");
        assertParse("select foo from bar where 500 < price;", "price:>500");
    }

    @Test
    public void testGreaterThan() {
        assertParse("select foo from bar where price > 500;", "price:>500");
        assertParse("select foo from bar where 500 > price;", "price:<500");
    }

    @Test
    public void testLessThanOrEqual() {
        assertParse("select foo from bar where price <= 500;", "price:[;500]");
        assertParse("select foo from bar where 500 <= price;", "price:[500;]");
    }

    @Test
    public void testGreaterThanOrEqual() {
        assertParse("select foo from bar where price >= 500;", "price:[500;]");
        assertParse("select foo from bar where 500 >= price;", "price:[;500]");
    }

    @Test
    public void testEquality() {
        assertParse("select foo from bar where price = 500;", "price:500");
        assertParse("select foo from bar where 500 = price;", "price:500");
    }

    @Test
    public void testNegativeLessThan() {
        assertParse("select foo from bar where price < -500;", "price:<-500");
        assertParse("select foo from bar where -500 < price;", "price:>-500");
    }

    @Test
    public void testNegativeGreaterThan() {
        assertParse("select foo from bar where price > -500;", "price:>-500");
        assertParse("select foo from bar where -500 > price;", "price:<-500");
    }

    @Test
    public void testNegativeLessThanOrEqual() {
        assertParse("select foo from bar where price <= -500;", "price:[;-500]");
        assertParse("select foo from bar where -500 <= price;", "price:[-500;]");
    }

    @Test
    public void testNegativeGreaterThanOrEqual() {
        assertParse("select foo from bar where price >= -500;", "price:[-500;]");
        assertParse("select foo from bar where -500 >= price;", "price:[;-500]");
    }

    @Test
    public void testNegativeEquality() {
        assertParse("select foo from bar where price = -500;", "price:-500");
        assertParse("select foo from bar where -500 = price;", "price:-500");
    }

    @Test
    public void testAnnotatedLessThan() {
        assertParse("select foo from bar where price < ([{\"filter\": true}](-500));", "|price:<-500");
        assertParse("select foo from bar where ([{\"filter\": true}]500) < price;", "|price:>500");
    }

    @Test
    public void testAnnotatedGreaterThan() {
        assertParse("select foo from bar where price > ([{\"filter\": true}]500);", "|price:>500");
        assertParse("select foo from bar where ([{\"filter\": true}](-500)) > price;", "|price:<-500");
    }

    @Test
    public void testAnnotatedLessThanOrEqual() {
        assertParse("select foo from bar where price <= ([{\"filter\": true}](-500));", "|price:[;-500]");
        assertParse("select foo from bar where ([{\"filter\": true}]500) <= price;", "|price:[500;]");
    }

    @Test
    public void testAnnotatedGreaterThanOrEqual() {
        assertParse("select foo from bar where price >= ([{\"filter\": true}]500);", "|price:[500;]");
        assertParse("select foo from bar where ([{\"filter\": true}](-500)) >= price;", "|price:[;-500]");
    }

    @Test
    public void testAnnotatedEquality() {
        assertParse("select foo from bar where price = ([{\"filter\": true}](-500));", "|price:-500");
        assertParse("select foo from bar where ([{\"filter\": true}]500) = price;", "|price:500");
    }

    @Test
    public void testBoolean() {
        assertParse("select foo from bar where flag = true;", "flag:true");
        QueryTree query = assertParse("select foo from bar where flag = false;", "flag:false");
        assertEquals(BoolItem.class, query.getRoot().getClass());
        BoolItem item = (BoolItem)query.getRoot();
        assertEquals("flag", item.getIndexName());
        assertEquals(false, item.value());
    }

    @Test
    public void testTermAnnotations() {
        assertEquals("merkelapp",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"label\": \"merkelapp\"} ]\"colors\");").getLabel());
        assertEquals("another",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"annotations\": {\"cox\": \"another\"}} ]\"colors\");").getAnnotation("cox"));
        assertEquals(23.0, getRootWord("select foo from bar where baz contains " +
                                       "([ {\"significance\": 23.0} ]\"colors\");").getSignificance(), 1E-6);
        assertEquals(23, getRootWord("select foo from bar where baz contains " +
                                     "([ {\"id\": 23} ]\"colors\");").getUniqueID());
        assertEquals(150, getRootWord("select foo from bar where baz contains " +
                                      "([ {\"weight\": 150} ]\"colors\");").getWeight());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"usePositionData\": false} ]\"colors\");").usePositionData());
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"filter\": true} ]\"colors\");").isFilter());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"ranked\": false} ]\"colors\");").isRanked());

        Substring origin = getRootWord("select foo from bar where baz contains " +
                                       "([ {\"origin\": {\"original\": \"abc\", \"offset\": 1, \"length\": 2}} ]" +
                                       "\"colors\");").getOrigin();
        assertEquals("abc", origin.string);
        assertEquals(1, origin.start);
        assertEquals(3, origin.end);
    }

    @Test
    public void testSameElement() {
        assertParse("select foo from bar where baz contains sameElement(f1 contains \"a\", f2 contains \"b\");",
                "baz:{f1:a f2:b}");
        assertParse("select foo from bar where baz contains sameElement(f1 contains \"a\", f2 = 10);",
                "baz:{f1:a f2:10}");
        assertParse("select foo from bar where baz contains sameElement(key contains \"a\", value.f2 = 10);",
                "baz:{key:a value.f2:10}");
        assertCanonicalParse("select foo from bar where baz contains sameElement(key contains \"a\", value.f2 = 10);",
                "baz:{key:a value.f2:10}");
        assertCanonicalParse("select foo from bar where baz contains sameElement(key contains \"a\");",
                "baz.key:a");
    }

    @Test
    public void testPhrase() {
        assertParse("select foo from bar where baz contains phrase(\"a\", \"b\");",
                    "baz:\"a b\"");
    }

    @Test
    public void testNestedPhrase() {
        assertParse("select foo from bar where baz contains phrase(\"a\", \"b\", phrase(\"c\", \"d\"));",
                    "baz:\"a b c d\"");
    }

    @Test
    public void testNestedPhraseSegment() {
        assertParse("select foo from bar where baz contains " +
                    "phrase(\"a\", \"b\", [ {\"origin\": {\"original\": \"c d\", \"offset\": 0, \"length\": 3}} ]" +
                    "phrase(\"c\", \"d\"));",
                    "baz:\"a b 'c d'\"");
    }

    @Test
    public void testStemming() {
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"stem\": false} ]\"colors\");").isStemmed());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"stem\": true} ]\"colors\");").isStemmed());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "\"colors\";").isStemmed());
    }

    @Test
    public void testRaw() {
        // Default: Not raw, for comparison
        Item root = parse("select foo from bar where baz contains (\"yoni jo dima\");").getRoot();
        assertEquals("baz:'yoni jo dima'", root.toString());
        assertFalse(root instanceof WordItem);
        assertTrue(root instanceof PhraseSegmentItem);

        root = parse("select foo from bar where baz contains ([{\"grammar\":\"raw\"}]\"yoni jo dima\");").getRoot();
        assertEquals("baz:yoni jo dima", root.toString());
        assertTrue(root instanceof WordItem);
        assertFalse(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem)root).getWord());

        root = parse("select foo from bar where userInput(\"yoni jo dima\");").getRoot();
        assertTrue(root instanceof AndItem);
        AndItem andItem = (AndItem) root;
        assertEquals(3, andItem.getItemCount());

        root = parse("select foo from bar where [{\"grammar\":\"raw\"}]userInput(\"yoni jo dima\");").getRoot();
        assertTrue(root instanceof WordItem);
        assertTrue(root instanceof ExactStringItem);
        assertEquals("yoni jo dima", ((WordItem)root).getWord());
    }

    @Test
    public void testAccentDropping() {
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"accentDrop\": false} ]\"colors\");").isNormalizable());
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"accentDrop\": true} ]\"colors\");").isNormalizable());
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "\"colors\";").isNormalizable());
    }

    @Test
    public void testCaseNormalization() {
        assertTrue(getRootWord("select foo from bar where baz contains " +
                               "([ {\"normalizeCase\": false} ]\"colors\");").isLowercased());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "([ {\"normalizeCase\": true} ]\"colors\");").isLowercased());
        assertFalse(getRootWord("select foo from bar where baz contains " +
                                "\"colors\";").isLowercased());
    }

    @Test
    public void testSegmentingRule() {
        assertEquals(SegmentingRule.PHRASE,
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"andSegmenting\": false} ]\"colors\");").getSegmentingRule());
        assertEquals(SegmentingRule.BOOLEAN_AND,
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"andSegmenting\": true} ]\"colors\");").getSegmentingRule());
        assertEquals(SegmentingRule.LANGUAGE_DEFAULT,
                     getRootWord("select foo from bar where baz contains " +
                                 "\"colors\";").getSegmentingRule());
    }

    @Test
    public void testNfkc() {
        assertEquals("a\u030a",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"nfkc\": false} ]\"a\\u030a\");").getWord());
        assertEquals("\u00e5",
                     getRootWord("select foo from bar where baz contains " +
                                 "([ {\"nfkc\": true} ]\"a\\u030a\");").getWord());
        assertEquals("No NKFC by default", 
                     "a\u030a",
                     getRootWord("select foo from bar where baz contains " +
                                 "(\"a\\u030a\");").getWord());
    }

    @Test
    public void testImplicitTransforms() {
        assertFalse(getRootWord("select foo from bar where baz contains ([ {\"implicitTransforms\": " +
                                "false} ]\"cox\");").isFromQuery());
        assertTrue(getRootWord("select foo from bar where baz contains ([ {\"implicitTransforms\": " +
                               "true} ]\"cox\");").isFromQuery());
        assertTrue(getRootWord("select foo from bar where baz contains \"cox\";").isFromQuery());
    }

    @Test
    public void testConnectivity() {
        QueryTree parsed = parse("select foo from bar where " +
                                 "title contains ([{\"id\": 1, \"connectivity\": {\"id\": 3, \"weight\": 7.0}}]\"madonna\") " +
                                 "and title contains ([{\"id\": 2}]\"saint\") " +
                                 "and title contains ([{\"id\": 3}]\"angel\");");
        assertEquals("AND title:madonna title:saint title:angel",
                     parsed.toString());
        AndItem root = (AndItem)parsed.getRoot();
        WordItem first = (WordItem)root.getItem(0);
        WordItem second = (WordItem)root.getItem(1);
        WordItem third = (WordItem)root.getItem(2);
        assertTrue(first.getConnectedItem() == third);
        assertEquals(first.getConnectivity(), 7.0d, 1E-6);
        assertNull(second.getConnectedItem());

        assertParseFail("select foo from bar where " +
                        "title contains ([{\"id\": 1, \"connectivity\": {\"id\": 4, \"weight\": 7.0}}]\"madonna\") " +
                        "and title contains ([{\"id\": 2}]\"saint\") " +
                        "and title contains ([{\"id\": 3}]\"angel\");",
                        new NullPointerException("Item 'title:madonna' was specified to connect to item with ID 4, " +
                                                 "which does not exist in the query."));
    }

    @Test
    public void testAnnotatedPhrase() {
        QueryTree parsed =
                parse("select foo from bar where baz contains ([{\"label\": \"hello world\"}]phrase(\"a\", \"b\"));");
        assertEquals("baz:\"a b\"", parsed.toString());
        PhraseItem phrase = (PhraseItem)parsed.getRoot();
        assertEquals("hello world", phrase.getLabel());
    }

    @Test
    public void testRange() {
        QueryTree parsed = parse("select foo from bar where range(baz,1,8);");
        assertEquals("baz:[1;8]", parsed.toString());
    }

    @Test
    public void testRangeWithEndInfinity() {
        QueryTree parsed = parse("select foo from bar where range(baz,1,Infinity);");
        assertEquals("baz:[1;]", parsed.toString());
    }

    @Test
    public void testRangeWithStartInfinity() {
        QueryTree parsed = parse("select foo from bar where range(baz,-Infinity,8);");
        assertEquals("baz:[;8]", parsed.toString());
    }

    @Test
    public void testNegativeRange() {
        QueryTree parsed = parse("select foo from bar where range(baz,-8,-1);");
        assertEquals("baz:[-8;-1]", parsed.toString());
    }

    @Test
    public void testRangeIllegalArguments() {
        assertParseFail("select foo from bar where range(baz,cox,8);",
                        new IllegalArgumentException("Expected a numerical argument (or 'Infinity') to range but got 'cox'"));
    }

    @Test
    public void testNear() {
        assertParse("select foo from bar where description contains near(\"a\", \"b\");",
                    "NEAR(2) description:a description:b");
        assertParse("select foo from bar where description contains ([ {\"distance\": 100} ]near(\"a\", \"b\"));",
                    "NEAR(100) description:a description:b");
    }

    @Test
    public void testOrderedNear() {
        assertParse("select foo from bar where description contains onear(\"a\", \"b\");",
                    "ONEAR(2) description:a description:b");
        assertParse("select foo from bar where description contains ([ {\"distance\": 100} ]onear(\"a\", \"b\"));",
                    "ONEAR(100) description:a description:b");
    }

    //This test is order dependent. Fix this!!
    @Test
    public void testWand() {
        assertParse("select foo from bar where wand(description, {\"a\":1, \"b\":2});",
                "WAND(10,0.0,1.0) description{[1]:\"a\",[2]:\"b\"}");
        assertParse("select foo from bar where [ {\"scoreThreshold\": 13.3, \"targetHits\": 7, " +
                    "\"thresholdBoostFactor\": 2.3} ]wand(description, {\"a\":1, \"b\":2});",
                    "WAND(7,13.3,2.3) description{[1]:\"a\",[2]:\"b\"}");
    }

    @Test
    public void testNumericWand() {
        String numWand = "WAND(10,0.0,1.0) description{[1]:\"11\",[2]:\"37\"}";
        assertParse("select foo from bar where wand(description, [[11,1], [37,2]]);", numWand);
        assertParse("select foo from bar where wand(description, [[11L,1], [37L,2]]);", numWand);
        assertParseFail("select foo from bar where wand(description, 12);",
                        new IllegalArgumentException("Expected ARRAY or MAP, got LITERAL."));
    }

    @Test
    //This test is order dependent. Fix it!
    public void testWeightedSet() {
        assertParse("select foo from bar where weightedSet(description, {\"a\":1, \"b\":2});",
                    "WEIGHTEDSET description{[1]:\"a\",[2]:\"b\"}");
        assertParseFail("select foo from bar where weightedSet(description, {\"a\":g, \"b\":2});",
                        new IllegalArgumentException("Expected operator LITERAL, got READ_FIELD."));
        assertParseFail("select foo from bar where weightedSet(description);",
                        new IllegalArgumentException("Expected 2 arguments, got 1."));
    }

    //This test is order dependent. Fix it!
    @Test
    public void testDotProduct() {
        assertParse("select foo from bar where dotProduct(description, {\"a\":1, \"b\":2});",
                    "DOTPRODUCT description{[1]:\"a\",[2]:\"b\"}");
        assertParse("select foo from bar where dotProduct(description, {\"a\":2});",
                    "DOTPRODUCT description{[2]:\"a\"}");
    }

    @Test
    public void testGeoLocation() {
        assertParse("select foo from bar where geoLocation(workplace, 63.418417, 10.433033, 500000);",
                    "GEO_LOCATION workplace:(2,10433033,63418417,500000,0,1,0,1921876103)");
        assertParse("select foo from bar where geoLocation(headquarters, \"37.416383\", \"-122.024683\", \"100 miles\");",
                    "GEO_LOCATION headquarters:(2,-122024683,37416383,1450561,0,1,0,3411238761)");
        assertParse("select foo from bar where geoLocation(home, \"E10.433033\", \"N63.418417\", \"5km\");",
                    "GEO_LOCATION home:(2,10433033,63418417,45066,0,1,0,1921876103)");

        assertParseFail("select foo from bar where geoLocation(qux, 1, 2);",
                        new IllegalArgumentException("Expected 4 arguments, got 3."));
        assertParseFail("select foo from bar where geoLocation(qux, 1.0, \"N1.0\", 100);",
                        new IllegalArgumentException(
                                "Invalid geoLocation coordinates '1.0 -> latitude(1.0)' and 'N1.0 -> latitude(1.0)'"));
        assertParse("select foo from bar where geoLocation(workplace, -12, -34, -77);",
                    "GEO_LOCATION workplace:(2,-34000000,-12000000,-1,0,1,0,4201111954)");
    }

    @Test
    public void testNearestNeighbor() {
        assertParse("select foo from bar where nearestNeighbor(semantic_embedding, my_vector);",
                    "NEAREST_NEIGHBOR {field=semantic_embedding,queryTensorName=my_vector,hnsw.exploreAdditionalHits=0,approximate=true,targetHits=0}");
        assertParse("select foo from bar where [{\"targetHits\": 37}]nearestNeighbor(semantic_embedding, my_vector);",
                    "NEAREST_NEIGHBOR {field=semantic_embedding,queryTensorName=my_vector,hnsw.exploreAdditionalHits=0,approximate=true,targetHits=37}");
        assertParse("select foo from bar where [{\"approximate\": false, \"hnsw.exploreAdditionalHits\": 8, \"targetHits\": 3}]nearestNeighbor(semantic_embedding, my_vector);",
                    "NEAREST_NEIGHBOR {field=semantic_embedding,queryTensorName=my_vector,hnsw.exploreAdditionalHits=8,approximate=false,targetHits=3}");
    }

    @Test
    public void testPredicate() {
        assertParse("select foo from bar where predicate(predicate_field, " +
                "{\"gender\":\"male\", \"hobby\":[\"music\", \"hiking\"]}, {\"age\":23L});",
                "PREDICATE_QUERY_ITEM gender=male, hobby=music, hobby=hiking, age:23");
        assertParse("select foo from bar where predicate(predicate_field, " +
                "{\"gender\":\"male\", \"hobby\":[\"music\", \"hiking\"]}, {\"age\":23});",
                "PREDICATE_QUERY_ITEM gender=male, hobby=music, hobby=hiking, age:23");
        assertParse("select foo from bar where predicate(predicate_field, 0, void);",
                "PREDICATE_QUERY_ITEM ");
    }

    @Test
    public void testPredicateWithSubQueries() {
        assertParse("select foo from bar where predicate(predicate_field, " +
                "{\"0x03\":{\"gender\":\"male\"},\"0x01\":{\"hobby\":[\"music\", \"hiking\"]}}, {\"0x80ffffffffffffff\":{\"age\":23L}});",
                "PREDICATE_QUERY_ITEM gender=male[0x3], hobby=music[0x1], hobby=hiking[0x1], age:23[0x80ffffffffffffff]");
        assertParseFail("select foo from bar where predicate(foo, null, {\"0x80000000000000000\":{\"age\":23}});",
                new NumberFormatException("Too long subquery string: 0x80000000000000000"));
        assertParse("select foo from bar where predicate(predicate_field, " +
                        "{\"[0,1]\":{\"gender\":\"male\"},\"[0]\":{\"hobby\":[\"music\", \"hiking\"]}}, {\"[62, 63]\":{\"age\":23L}});",
                "PREDICATE_QUERY_ITEM gender=male[0x3], hobby=music[0x1], hobby=hiking[0x1], age:23[0xc000000000000000]");
    }

    @Test
    public void testRank() {
        assertParse("select foo from bar where rank(a contains \"A\", b contains \"B\");",
                    "RANK a:A b:B");
        assertParse("select foo from bar where rank(a contains \"A\", b contains \"B\", c " +
                    "contains \"C\");",
                    "RANK a:A b:B c:C");
        assertParse("select foo from bar where rank(a contains \"A\", b contains \"B\"  or c " +
                    "contains \"C\");",
                    "RANK a:A (OR b:B c:C)");
    }

    @Test
    public void testWeakAnd() {
        assertParse("select foo from bar where weakAnd(a contains \"A\", b contains \"B\");",
                    "WAND(100) a:A b:B");
        assertParse("select foo from bar where [{\"targetHits\": 37}]weakAnd(a contains \"A\", " +
                    "b contains \"B\");",
                    "WAND(37) a:A b:B");

        QueryTree tree = parse("select foo from bar where [{\"scoreThreshold\": 41}]weakAnd(a " +
                               "contains \"A\", b contains \"B\");");
        assertEquals("WAND(100) a:A b:B", tree.toString());
        assertEquals(WeakAndItem.class, tree.getRoot().getClass());
        assertEquals(41, ((WeakAndItem)tree.getRoot()).getScoreThreshold());
    }

    @Test
    public void testEquiv() {
        assertParse("select foo from bar where fieldName contains equiv(\"A\",\"B\");",
                    "EQUIV fieldName:A fieldName:B");
        assertParse("select foo from bar where fieldName contains " +
                    "equiv(\"ny\",phrase(\"new\",\"york\"));",
                    "EQUIV fieldName:ny fieldName:\"new york\"");
        assertParseFail("select foo from bar where fieldName contains equiv(\"ny\");",
                        new IllegalArgumentException("Expected 2 or more arguments, got 1."));
        assertParseFail("select foo from bar where fieldName contains equiv(\"ny\", nalle(void));",
                        new IllegalArgumentException("Expected function 'phrase', got 'nalle'."));
        assertParseFail("select foo from bar where fieldName contains equiv(\"ny\", 42);",
                        new ClassCastException("Cannot cast java.lang.Integer to java.lang.String"));
    }

    @Test
    public void testAffixItems() {
        assertRootClass("select foo from bar where baz contains ([ {\"suffix\": true} ]\"colors\");",
                        SuffixItem.class);
        assertRootClass("select foo from bar where baz contains ([ {\"prefix\": true} ]\"colors\");",
                        PrefixItem.class);
        assertRootClass("select foo from bar where baz contains ([ {\"substring\": true} ]\"colors\");",
                        SubstringItem.class);
        assertParseFail("select foo from bar where description contains ([ {\"suffix\": true, " +
                        "\"prefix\": true} ]\"colors\");",
                        new IllegalArgumentException("Only one of prefix, substring and suffix can be set."));
        assertParseFail("select foo from bar where description contains ([ {\"suffix\": true, " +
                        "\"substring\": true} ]\"colors\");",
                        new IllegalArgumentException("Only one of prefix, substring and suffix can be set."));
    }

    @Test
    public void testLongNumberInSimpleExpression() {
        assertParse("select foo from bar where price = 8589934592L;",
                    "price:8589934592");
    }

    @Test
    public void testNegativeLongNumberInSimpleExpression() {
        assertParse("select foo from bar where price = -8589934592L;",
                "price:-8589934592");
    }

    @Test
    public void testSources() {
        assertSources("select foo from sourceA where price <= 500;",
                      Arrays.asList("sourceA"));
    }

    @Test
    public void testWildCardSources() {
        assertSources("select foo from sources * where price <= 500;",
                      Collections.<String>emptyList());
    }

    @Test
    public void testMultiSources() {
        assertSources("select foo from sources sourceA, sourceB where price <= 500;",
                      Arrays.asList("sourceA", "sourceB"));
    }

    @Test
    public void testFields() {
        assertSummaryFields("select fieldA from bar where price <= 500;",
                            Arrays.asList("fieldA"));
        assertSummaryFields("select fieldA, fieldB from bar where price <= 500;",
                            Arrays.asList("fieldA", "fieldB"));
        assertSummaryFields("select fieldA, fieldB, fieldC from bar where price <= 500;",
                            Arrays.asList("fieldA", "fieldB", "fieldC"));
        assertSummaryFields("select * from bar where price <= 500;",
                            Collections.<String>emptyList());
    }

    @Test
    public void testFieldsRoot() {
        assertParse("select * from bar where price <= 500;",
                    "price:[;500]");
    }

    @Test
    public void testOffset() {
        assertParse("select foo from bar where title contains \"madonna\" offset 37;",
                    "title:madonna");
        assertEquals(Integer.valueOf(37), parser.getOffset());
    }

    @Test
    public void testLimit() {
        assertParse("select foo from bar where title contains \"madonna\" limit 29;",
                    "title:madonna");
        assertEquals(Integer.valueOf(29), parser.getHits());
    }

    @Test
    public void testOffsetAndLimit() {
        assertParse("select foo from bar where title contains \"madonna\" limit 31 offset 29;",
                    "title:madonna");
        assertEquals(Integer.valueOf(29), parser.getOffset());
        assertEquals(Integer.valueOf(2), parser.getHits());

        assertParse("select * from bar where title contains \"madonna\" limit 41 offset 37;",
                    "title:madonna");
        assertEquals(Integer.valueOf(37), parser.getOffset());
        assertEquals(Integer.valueOf(4), parser.getHits());
    }

    @Test
    public void testTimeout() {
        assertParse("select * from bar where title contains \"madonna\" timeout 7;",
                    "title:madonna");
        assertEquals(Integer.valueOf(7), parser.getTimeout());

        assertParse("select foo from bar where title contains \"madonna\" limit 600 timeout 3;",
                    "title:madonna");
        assertEquals(Integer.valueOf(3), parser.getTimeout());
    }

    @Test
    public void testOrdering() {
        assertParse("select foo from bar where title contains \"madonna\" order by something asc, " +
                    "shoesize desc limit 600 timeout 3;",
                    "title:madonna");
        assertEquals(2, parser.getSorting().fieldOrders().size());
        assertEquals("something", parser.getSorting().fieldOrders().get(0).getFieldName());
        assertEquals(Order.ASCENDING, parser.getSorting().fieldOrders().get(0).getSortOrder());
        assertEquals("shoesize", parser.getSorting().fieldOrders().get(1).getFieldName());
        assertEquals(Order.DESCENDING, parser.getSorting().fieldOrders().get(1).getSortOrder());

        assertParse("select foo from bar where title contains \"madonna\" order by other limit 600 " +
                    "timeout 3;",
                    "title:madonna");
        assertEquals("other", parser.getSorting().fieldOrders().get(0).getFieldName());
        assertEquals(Order.ASCENDING, parser.getSorting().fieldOrders().get(0).getSortOrder());
    }

    @Test
    public void testAnnotatedOrdering() {
        assertParse(
                "select foo from bar where title contains \"madonna\""
                        + " order by [{\"function\": \"uca\", \"locale\": \"en_US\", \"strength\": \"IDENTICAL\"}]other desc"
                        + " limit 600" + " timeout 3;", "title:madonna");
        final FieldOrder fieldOrder = parser.getSorting().fieldOrders().get(0);
        assertEquals("other", fieldOrder.getFieldName());
        assertEquals(Order.DESCENDING, fieldOrder.getSortOrder());
        final AttributeSorter sorter = fieldOrder.getSorter();
        assertEquals(UcaSorter.class, sorter.getClass());
        final UcaSorter uca = (UcaSorter) sorter;
        assertEquals("en_US", uca.getLocale());
        assertEquals(UcaSorter.Strength.IDENTICAL, uca.getStrength());
    }

    @Test
    public void testMultipleAnnotatedOrdering() {
        assertParse(
                "select foo from bar where title contains \"madonna\""
                        + " order by [{\"function\": \"uca\", \"locale\": \"en_US\", \"strength\": \"IDENTICAL\"}]other desc,"
                        + " [{\"function\": \"lowercase\"}]something asc"
                        + " limit 600" + " timeout 3;", "title:madonna");
        {
            final FieldOrder fieldOrder = parser.getSorting().fieldOrders()
                    .get(0);
            assertEquals("other", fieldOrder.getFieldName());
            assertEquals(Order.DESCENDING, fieldOrder.getSortOrder());
            final AttributeSorter sorter = fieldOrder.getSorter();
            assertEquals(UcaSorter.class, sorter.getClass());
            final UcaSorter uca = (UcaSorter) sorter;
            assertEquals("en_US", uca.getLocale());
            assertEquals(UcaSorter.Strength.IDENTICAL, uca.getStrength());
        }
        {
            final FieldOrder fieldOrder = parser.getSorting().fieldOrders()
                    .get(1);
            assertEquals("something", fieldOrder.getFieldName());
            assertEquals(Order.ASCENDING, fieldOrder.getSortOrder());
            final AttributeSorter sorter = fieldOrder.getSorter();
            assertEquals(LowerCaseSorter.class, sorter.getClass());
        }
    }

    @Test
    public void testSegmenting() {
        assertParse("select * from bar where title contains 'foo.bar';",
                    "title:'foo bar'");

        assertParse("select * from bar where title contains 'foo&123';",
                    "title:'foo 123'");
    }

    @Test
    public void testNegativeHitLimit() {
        assertParse("select * from sources * where [{\"hitLimit\": -38}]range(foo, 0, 1);",
                    "foo:[0;1;-38]");
    }

    @Test
    public void testRangeSearchHitPopulationOrdering() {
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"ascending\": true}]range(foo, 0, 1);", "foo:[0;1;38]");
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"ascending\": false}]range(foo, 0, 1);", "foo:[0;1;-38]");
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"descending\": true}]range(foo, 0, 1);", "foo:[0;1;-38]");
        assertParse("select * from sources * where [{\"hitLimit\": 38, \"descending\": false}]range(foo, 0, 1);", "foo:[0;1;38]");

        boolean gotExceptionFromParse = false;
        try {
            parse("select * from sources * where [{\"hitLimit\": 38, \"ascending\": true, \"descending\": false}]range(foo, 0, 1);");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected information about abuse of settings.",
                    e.getMessage().contains("both ascending and descending ordering set"));
            gotExceptionFromParse = true;
        }
        assertTrue(gotExceptionFromParse);
    }

    @Test
    public void testOpenIntervals() {
        assertParse("select * from sources * where range(title, 0.0, 500.0);",
                "title:[0.0;500.0]");
        assertParse(
                "select * from sources * where [{\"bounds\": \"open\"}]range(title, 0.0, 500.0);",
                "title:<0.0;500.0>");
        assertParse(
                "select * from sources * where [{\"bounds\": \"leftOpen\"}]range(title, 0.0, 500.0);",
                "title:<0.0;500.0]");
        assertParse(
                "select * from sources * where [{\"bounds\": \"rightOpen\"}]range(title, 0.0, 500.0);",
                "title:[0.0;500.0>");
    }

    @Test
    public void testInheritedAnnotations() {
        {
            QueryTree x = parse("select * from sources * where ([{\"ranked\": false}](foo contains \"a\" and bar contains \"b\")) or foor contains ([{\"ranked\": false}]\"c\");");
            List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
            assertEquals(3, terms.size());
            for (IndexedItem term : terms) {
                assertFalse(((Item) term).isRanked());
            }
        }
        {
            QueryTree x = parse("select * from sources * where [{\"ranked\": false}](foo contains \"a\" and bar contains \"b\");");
            List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
            assertEquals(2, terms.size());
            for (IndexedItem term : terms) {
                assertFalse(((Item) term).isRanked());
            }
        }
    }

    @Test
    public void testMoreInheritedAnnotations() {
        String yqlQuery = "select * from sources * where " +
                          "([{\"ranked\": false}](foo contains \"a\" " +
                          "and ([{\"ranked\": true}](bar contains \"b\" " +
                          "or ([{\"ranked\": false}](foo contains \"c\" " +
                          "and foo contains ([{\"ranked\": true}]\"d\")))))));";
        QueryTree x = parse(yqlQuery);
        List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
        assertEquals(4, terms.size());
        for (IndexedItem term : terms) {
            switch (term.getIndexedString()) {
                case "a":
                case "c":
                    assertFalse(((Item) term).isRanked());
                    break;
                case "b":
                case "d":
                    assertTrue(((Item) term).isRanked());
                    break;
                default:
                    fail();
            }
        }
    }

    @Test
    public void testFieldAliases() {
        IndexInfoConfig modelConfig = new IndexInfoConfig(new IndexInfoConfig.Builder().indexinfo(new Indexinfo.Builder()
                .name("music").command(new Command.Builder().indexname("title").command("index"))
                .alias(new Alias.Builder().alias("song").indexname("title"))));
        IndexModel model = new IndexModel(modelConfig, (QrSearchersConfig)null);

        IndexFacts indexFacts = new IndexFacts(model);
        ParserEnvironment parserEnvironment = new ParserEnvironment().setIndexFacts(indexFacts);
        YqlParser configuredParser = new YqlParser(parserEnvironment);
        QueryTree x = configuredParser.parse(new Parsable()
                .setQuery("select * from sources * where title contains \"a\" and song contains \"b\";"));
        List<IndexedItem> terms = QueryTree.getPositiveTerms(x);
        assertEquals(2, terms.size());
        for (IndexedItem term : terms) {
            assertEquals("title", term.getIndexName());
        }
    }

    @Test
    public void testRegexp() {
        QueryTree x = parse("select * from sources * where foo matches \"a b\";");
        Item root = x.getRoot();
        assertSame(RegExpItem.class, root.getClass());
        assertEquals("a b", ((RegExpItem) root).stringValue());
    }

    @Test
    public void testWordAlternatives() {
        QueryTree x = parse("select * from sources * where foo contains alternatives({\"trees\": 1.0, \"tree\": 0.7});");
        Item root = x.getRoot();
        assertSame(WordAlternativesItem.class, root.getClass());
        WordAlternativesItem alternatives = (WordAlternativesItem) root;
        checkWordAlternativesContent(alternatives);
    }

    @Test
    public void testWordAlternativesWithOrigin() {
        QueryTree q = parse("select * from sources * where foo contains" +
                            " ([{\"origin\": {\"original\": \" trees \", \"offset\": 1, \"length\": 5}}]" +
                            "alternatives({\"trees\": 1.0, \"tree\": 0.7}));");
        Item root = q.getRoot();
        assertSame(WordAlternativesItem.class, root.getClass());
        WordAlternativesItem alternatives = (WordAlternativesItem) root;
        checkWordAlternativesContent(alternatives);
        Substring origin = alternatives.getOrigin();
        assertEquals(1, origin.start);
        assertEquals(6, origin.end);
        assertEquals("trees", origin.getValue());
        assertEquals(" trees ", origin.getSuperstring());
    }

    @Test
    public void testWordAlternativesInPhrase() {
        QueryTree q = parse("select * from sources * where" +
                            " foo contains phrase(\"forest\", alternatives({\"trees\": 1.0, \"tree\": 0.7}));");
        Item root = q.getRoot();
        assertSame(PhraseItem.class, root.getClass());
        PhraseItem phrase = (PhraseItem) root;
        assertEquals(2, phrase.getItemCount());
        assertEquals("forest", ((WordItem) phrase.getItem(0)).getWord());
        checkWordAlternativesContent((WordAlternativesItem) phrase.getItem(1));
        assertEquals("foo:\"forest WORD_ALTERNATIVES foo:[ tree(0.7) trees(1.0) ]\"", root.toString());
    }

    /** Verifies that we can search for a backslash */
    @Test
    public void testBackslash() {
        {
            String queryString = "select * from testtype where title contains \"\\\\\";"; // Java escaping * YQL escaping

            QueryTree query = parse(queryString);

            assertEquals("title:\\", query.toString());
        }

        {
            Query query = new Query("search?yql=select%20*%20from%20testtype%20where%20title%20contains%20%22%5C%5C%22;");

            // Cause parsing :-\
            Chain<Searcher> searchChain = new Chain<>(new MinimalQueryInserter());
            Execution.Context context = Execution.Context.createContextStub(null, null, new SimpleLinguistics());
            Execution execution = new Execution(searchChain, context);
            execution.search(query);

            assertEquals("title:\\", query.getModel().getQueryTree().toString());
        }
    }

    @Test
    public void testUrlHostSearchingDefaultAnchors() {
        // Simple query syntax, for reference
        assertUrlQuery("urlfield.hostname", new Query("?query=urlfield.hostname:google.com"), false, true, true);

        // YQL query
        Query yql = new Query();
        yql.properties().set("yql", "select * from sources * where urlfield.hostname contains uri(\"google.com\");");
        assertUrlQuery("urlfield.hostname", yql, false, true, true);
    }

    @Test
    public void testUrlHostSearchingNoAnchors() {
        // Simple query syntax, for reference
        assertUrlQuery("urlfield.hostname", new Query("?query=urlfield.hostname:google.com*"), false, false, true);

        // YQL query
        Query yql = new Query();
        yql.properties().set("yql", "select * from sources * where urlfield.hostname contains ([{\"endAnchor\": false }]uri(\"google.com\"));");
        assertUrlQuery("urlfield.hostname", yql, false, false, true);
    }

    @Test
    public void testUrlHostSearchingBothAnchors() {
        // Simple query syntax, for reference
        assertUrlQuery("urlfield.hostname", new Query("?query=urlfield.hostname:%5Egoogle.com"), true, true, true); // %5E = ^

        // YQL query
        Query yql = new Query();
        yql.properties().set("yql", "select * from sources * where urlfield.hostname contains ([{\"startAnchor\": true }] uri(\"google.com\"));");
        assertUrlQuery("urlfield.hostname", yql, true, true, true);
    }

    @Test
    public void testUriNonHostDoesNotCreateAnchors() {
        // Simple query syntax, for reference
        assertUrlQuery("urlfield", new Query("?query=urlfield:google.com"), false, false, false);

        // YQL query
        Query yql = new Query();
        yql.properties().set("yql", "select * from sources * where urlfield contains uri(\"google.com\");");
        assertUrlQuery("urlfield", yql, false, false, false);
    }

    @Test
    public void testReservedWordInSource() {
        parse("select * from sources like where text contains \"test\";");
        // success: parsed without exception
    }

    private void assertUrlQuery(String field, Query query, boolean startAnchor, boolean endAnchor, boolean endAnchorIsDefault) {
        boolean startAnchorIsDefault = false; // Always

        // Set up
        SearchDefinition test = new SearchDefinition("test");
        Index urlField = new Index("urlfield");
        urlField.setUriIndex(true);
        test.addIndex(urlField);
        Index hostField = new Index("urlfield.hostname");
        hostField.setHostIndex(true);
        test.addIndex(hostField);

        Chain<Searcher> searchChain = new Chain<>(new MinimalQueryInserter());
        Execution.Context context = Execution.Context.createContextStub(null,
                                                                        new IndexFacts(new IndexModel(test)),
                                                                        new SimpleLinguistics());
        Execution execution = new Execution(searchChain, context);
        execution.search(query);

        // Check parsing and serial forms
        if (endAnchor && startAnchor)
            assertEquals(field + ":\"^ google com $\"", query.getModel().getQueryTree().toString());
        else if (startAnchor)
            assertEquals(field + ":\"^ google com\"", query.getModel().getQueryTree().toString());
        else if (endAnchor)
            assertEquals(field + ":\"google com $\"", query.getModel().getQueryTree().toString());
        else
            assertEquals(field + ":\"google com\"", query.getModel().getQueryTree().toString());


        boolean hasAnnotations = startAnchor != startAnchorIsDefault || endAnchor != endAnchorIsDefault;
        StringBuilder expectedYql = new StringBuilder("select * from sources * where ");
        expectedYql.append(field).append(" contains ");
        if (hasAnnotations)
            expectedYql.append("([{");
        if (startAnchor != startAnchorIsDefault)
            expectedYql.append("\"startAnchor\": " + startAnchor);
        if (endAnchor != endAnchorIsDefault) {
            if (startAnchor != startAnchorIsDefault)
                expectedYql.append(", ");
            expectedYql.append("\"endAnchor\": " + endAnchor);
        }
        if (hasAnnotations)
            expectedYql.append("}]");
        expectedYql.append("uri(");
        if (query.properties().get("yql") != null)
            expectedYql.append("\"google.com\")"); // source string is preserved when parsing YQL
        else
            expectedYql.append("\"google com\")"); // but not with the simple syntax
        if (hasAnnotations)
            expectedYql.append(")");
        expectedYql.append(";");
        assertEquals(expectedYql.toString(), query.yqlRepresentation());

        assertTrue(query.getModel().getQueryTree().getRoot() instanceof PhraseItem);
        PhraseItem root = (PhraseItem)query.getModel().getQueryTree().getRoot();
        int expectedLength = 2;
        if (startAnchor)
            expectedLength++;
        if (endAnchor)
            expectedLength++;
        assertEquals(expectedLength, root.getNumWords());

        if (startAnchor)
            assertEquals(MarkerWordItem.createStartOfHost("urlfield.hostname"), root.getItem(0));
        if (endAnchor)
            assertEquals(MarkerWordItem.createEndOfHost("urlfield.hostname"), root.getItem(expectedLength-1));

        // Check YQL parser-serialization roundtrip
        Query reserialized = new Query();
        reserialized.properties().set("yql", query.yqlRepresentation());
        execution = new Execution(searchChain, context);
        execution.search(reserialized);
        assertEquals(query.yqlRepresentation(), reserialized.yqlRepresentation());
    }

    private void checkWordAlternativesContent(WordAlternativesItem alternatives) {
        boolean seenTree = false;
        boolean seenForest = false;
        String forest = "trees";
        String tree = "tree";
        assertEquals(2, alternatives.getAlternatives().size());
        for (WordAlternativesItem.Alternative alternative : alternatives.getAlternatives()) {
            if (tree.equals(alternative.word)) {
                assertFalse("Duplicate term introduced", seenTree);
                seenTree = true;
                assertEquals(.7d, alternative.exactness, 1e-15d);
            } else if (forest.equals(alternative.word)) {
                assertFalse("Duplicate term introduced", seenForest);
                seenForest = true;
                assertEquals(1.0d, alternative.exactness, 1e-15d);
            } else {
                fail("Unexpected term: " + alternative.word);
            }
        }
    }

    private QueryTree assertParse(String yqlQuery, String expectedQueryTree) {
        QueryTree query = parse(yqlQuery);
        assertEquals(expectedQueryTree, query.toString());
        return query;
    }

    private void assertCanonicalParse(String yqlQuery, String expectedQueryTree) {
        QueryTree qt  = parse(yqlQuery);
        assertNull(QueryCanonicalizer.canonicalize(qt));
        Query q = new Query();
        q.getModel().getQueryTree().setRoot(qt.getRoot());
        QueryRewrite.collapseSingleComposites(q);
        assertEquals(q.getModel().getQueryTree().toString(), expectedQueryTree);
    }

    private QueryTree assertParseFail(String yqlQuery, Throwable expectedException) {
        QueryTree query = null;
        try {
            query = parse(yqlQuery);
        } catch (Throwable t) {
            assertEquals(expectedException.getClass(), t.getClass());
            assertEquals(expectedException.getMessage(), t.getMessage());
            return query;
        }
        fail("Parse succeeded: " + yqlQuery);
        return query;
    }

    private void assertSources(String yqlQuery, Collection<String> expectedSources) {
        parse(yqlQuery);
        assertEquals(new HashSet<>(expectedSources), parser.getYqlSources());
    }

    private void assertSummaryFields(String yqlQuery, Collection<String> expectedSummaryFields) {
        parse(yqlQuery);
        assertEquals(new HashSet<>(expectedSummaryFields), parser.getYqlSummaryFields());
    }

    private WordItem getRootWord(String yqlQuery) {
        Item root = parse(yqlQuery).getRoot();
        assertTrue(root instanceof WordItem);
        return (WordItem)root;
    }

    private void assertRootClass(String yqlQuery, Class<? extends Item> expectedRootClass) {
        assertEquals(expectedRootClass, parse(yqlQuery).getRoot().getClass());
    }

    private QueryTree parse(String yqlQuery) {
        return parser.parse(new Parsable().setQuery(yqlQuery));
    }

    private static String toString(List<VespaGroupingStep> steps) {
        List<String> actual = new ArrayList<>(steps.size());
        for (VespaGroupingStep step : steps)
            actual.add(step.continuations().toString() + step.getOperation());
        return actual.toString();
    }
}
