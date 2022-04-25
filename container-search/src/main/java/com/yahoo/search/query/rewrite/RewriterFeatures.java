// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.rewrite;

import java.util.*;
import java.util.logging.Logger;

import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.parser.CustomParser;
import com.yahoo.search.*;
import com.yahoo.search.query.*;
import com.yahoo.prelude.query.*;
import com.yahoo.prelude.querytransform.PhraseMatcher;
import com.yahoo.prelude.querytransform.PhraseMatcher.Phrase;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;

/**
 * Contains commonly used rewriter features
 *
 * @author Karen Sze Wing Lee
 */
public class RewriterFeatures {

    private static final Logger logger = Logger.getLogger(RewriterFeatures.class.getName());

    /**
     * <p>Add proximity boosting to original query by modifying
     * the query tree directly</p>
     * e.g. original Query Tree: (AND aa bb)<br>
     *      if keepOriginalQuery: true<br>
     *      new Query tree: (OR (AND aa bb) "aa bb")<br>
     *      if keepOriginalQuery: false<br>
     *      new Query Tree: "aa bb"<br><br>
     *
     *      original Query Tree: (OR (AND aa bb) (AND cc dd))<br>
     *      boostingQuery: cc dd<br>
     *      if keepOriginalQuery: true<br>
     *      new Query Tree: (OR (AND aa bb) (AND cc dd) "cc dd")<br>
     *      if keepOriginalQuery: false<br>
     *      new Query Tree: (OR (AND aa bb) "cc dd") <br>
     *
     * @param query Query object from searcher
     * @param boostingQuery query to be boosted
     * @param keepOriginalQuery whether to keep original unboosted query as equiv
     * @return Modified Query object, return original query object
     *          on error
     */
    public static Query addUnitToOriginalQuery(Query query, String boostingQuery,
                                               boolean keepOriginalQuery)
                                               throws RuntimeException {
        RewriterUtils.log(logger, query, "Adding proximity boosting to [" + boostingQuery + "]");

        Model queryModel = query.getModel();
        QueryTree qTree = queryModel.getQueryTree();
        Item oldRoot = qTree.getRoot();

        if (oldRoot == null) {
            RewriterUtils.error(logger, query, "Error retrieving query tree root");
            throw new RuntimeException("Error retrieving query tree root");
        }

        // Convert original query to query tree item
        Item origQueryItem = convertStringToQTree(query, boostingQuery);

        // Boost proximity by phrasing the original query
        // query tree structure: (AND aa bb)
        if (oldRoot instanceof AndItem &&
            oldRoot.equals(origQueryItem)) {
            PhraseItem phrase = convertAndToPhrase((AndItem)oldRoot);

            if (!keepOriginalQuery) {
                qTree.setRoot(phrase);
            } else {
                OrItem newRoot = new OrItem();
                newRoot.addItem(oldRoot);
                newRoot.addItem(phrase);
                qTree.setRoot(newRoot);
                queryModel.setType(Query.Type.ADVANCED); //set type=adv
            }
            RewriterUtils.log(logger, query, "Added proximity boosting successfully");
            return query;

        // query tree structure: (OR (AND aa bb) (AND cc dd))
        } else if (oldRoot instanceof OrItem &&
                   ((OrItem)oldRoot).getItemIndex(origQueryItem)!=-1 &&
                   origQueryItem instanceof AndItem) {

            // Remove original unboosted query
            if(!keepOriginalQuery)
                ((OrItem)oldRoot).removeItem(origQueryItem);

            // Check if the tree already contained the phrase item
            PhraseItem pI = convertAndToPhrase((AndItem)origQueryItem);
            if(((OrItem)oldRoot).getItemIndex(pI)==-1) {
                ((OrItem)oldRoot).addItem(convertAndToPhrase((AndItem)origQueryItem));
                RewriterUtils.log(logger, query, "Added proximity boosting successfully");
                return query;
            }
        }
        RewriterUtils.log(logger, query, "No proximity boosting added");
        return query;
    }

    /**
     * <p>Add query expansion to the query tree</p>
     * e.g. origQuery: aa bb<br>
     *      matchingStr: aa bb<br>
     *      rewrite: cc dd, ee ff<br>
     *      if addUnitToRewrites: false<br>
     *      new query tree: (OR (AND aa bb) (AND cc dd) (AND ee ff))<br>
     *      if addUnitToRewrites: true<br>
     *      new query tree: (OR (AND aa bb) "cc dd" "ee ff") <br>
     *
     * @param query Query object from searcher
     * @param matchingStr string used to retrieve the rewrite
     * @param rewrites The rewrite string retrieved from
     *                 dictionary
     * @param addUnitToRewrites Whether to add unit to rewrites
     * @param maxNumRewrites Max number of rewrites to be added,
     *                       0 if no limit
     * @return Modified Query object, return original query object
     *         on error
     */
    public static Query addRewritesAsEquiv(Query query, String matchingStr,
                                           String rewrites,
                                           boolean addUnitToRewrites,
                                           int maxNumRewrites) throws RuntimeException {
        String normalizedQuery = RewriterUtils.getNormalizedOriginalQuery(query);

        RewriterUtils.log(logger, query,
                          "Adding rewrites [" + rewrites +
                          "] to the query [" + normalizedQuery + "]");
        if (rewrites.equalsIgnoreCase(normalizedQuery) || rewrites.equalsIgnoreCase("n/a")) {
            RewriterUtils.log(logger, query, "No rewrite added");
            return query;
        }

        Model queryModel = query.getModel();
        QueryTree qTree = queryModel.getQueryTree();
        Item oldRoot = qTree.getRoot();

        if (oldRoot == null) {
            RewriterUtils.error(logger, query, "Error retrieving query tree root");
            throw new RuntimeException("Error retrieving query tree root");
        }

        StringTokenizer rewrite_list = new StringTokenizer(rewrites, "\t");
        Item rI;

        // Convert matching string to query tree item
        Item matchingStrItem = convertStringToQTree(query, matchingStr);
        PhraseItem matchingStrPhraseItem = null;
        if (matchingStrItem instanceof AndItem) {
            matchingStrPhraseItem = convertAndToPhrase(((AndItem)matchingStrItem));
        }

        // Add rewrites as OR item to the query tree
        // Only should rewrite in this case:
        // - origQuery: (OR (AND aa bb) (AND cc dd))
        // - matchingStr: (AND aa bb)
        // Or in this case:
        // - origQuery: (AND aa bb)
        // - matching Str: (AND aa bb)
        // Should not rewrite in this case:
        // - origQuery: (OR (AND cc (OR dd (AND aa bb)) ee)
        // - matchingStr: (AND aa bb)
        // - for this case, should use getNonOverlappingMatches instead
        OrItem newRoot;
        if (oldRoot instanceof OrItem) {
            if (((OrItem)oldRoot).getItemIndex(matchingStrItem)==-1) {
                RewriterUtils.log(logger, query, "Whole query matching is used, skipping rewrite");
                return query;
            }
            newRoot = (OrItem)oldRoot;
        }
        else if(oldRoot.equals(matchingStrItem) || oldRoot.equals(matchingStrPhraseItem)) {
            newRoot = new OrItem();
            newRoot.addItem(oldRoot);
        }
        else {
            RewriterUtils.log(logger, query, "Whole query matching is used, skipping rewrite");
            return query;
        }
        int numRewrites = 0;
        while (rewrite_list.hasMoreTokens() && (maxNumRewrites == 0 || numRewrites < maxNumRewrites)) {
            rI = convertStringToQTree(query, rewrite_list.nextToken());
            if (addUnitToRewrites && rI instanceof AndItem) {
                rI = convertAndToPhrase((AndItem)rI);
            }
            if(newRoot.getItemIndex(rI) == -1) {
                newRoot.addItem(rI);
                numRewrites++;
            }
            else {
                RewriterUtils.log(logger, query, "Rewrite already exist, skipping");
            }
        }
        qTree.setRoot(newRoot);
        queryModel.setType(Query.Type.ADVANCED); //set type=adv
        RewriterUtils.log(logger, query, "Added rewrite successfully");

        return query;
    }

    /**
     * <p>Retrieve the longest, from left to right non overlapping full
     * phrase substrings in query based on FSA dictionary</p>
     *
     * e.g. query: ((modern AND new AND york AND city AND travel) OR travel) AND
     *             ((sunny AND travel AND agency) OR nyc)<br>
     *      dictionary: <br>
     *                  mny\tmodern new york<br>
     *                  mo\tmodern<br>
     *                  modern\tn/a<br>
     *                  modern\tnew york\tn/a<br>
     *                  new york\tn/a<br>
     *                  new york city\tn/a<br>
     *                  new york city travel\tn/a<br>
     *                  new york company\tn/a<br>
     *                  ny\tnew york<br>
     *                  nyc\tnew york city\tnew york company<br>
     *                  nyct\tnew york city travel<br>
     *                  ta\ttravel agency<br>
     *                  travel agency\tn/a<br>
     *      return: nyc
     * @param phraseMatcher PhraseMatcher object loaded with FSA dict
     * @param query Query object from the searcher
     * @return Matching phrases
     */
    public static Set<PhraseMatcher.Phrase> getNonOverlappingFullPhraseMatches(PhraseMatcher phraseMatcher,
                                                                               Query query)
                                                                               throws RuntimeException {
        RewriterUtils.log(logger, query, "Retrieving longest non-overlapping full phrase matches");
        if (phraseMatcher == null)
            return null;

        Item root = query.getModel().getQueryTree().getRoot();
        List<PhraseMatcher.Phrase> matches = phraseMatcher.matchPhrases(root);
        if (matches == null || matches.isEmpty())
            return null;

        Set<PhraseMatcher.Phrase> resultMatches = new HashSet<>();
        ListIterator<Phrase> matchesIter = matches.listIterator();

        // Iterate through all matches
        while (matchesIter.hasNext()) {
            PhraseMatcher.Phrase phrase = matchesIter.next();
            RewriterUtils.log(logger, query, "Working on phrase: " + phrase);
            CompositeItem currOwner = phrase.getOwner();

            // Check if this is full phrase
            // If phrase is not an AND item, only keep those that are single word
            // in order to eliminate cases such as (new RANK york) from being treated
            // as match if only new york but not new or york is in the dictionary
            if((currOwner != null &&
                ((phrase.isComplete() && currOwner instanceof AndItem) ||
                 (phrase.getLength() == 1 && currOwner instanceof OrItem) ||
                 (phrase.getLength() == 1 && currOwner instanceof RankItem && phrase.getStartIndex() == 0))) ||
               (currOwner == null && phrase.getLength() == 1)) {
               resultMatches.add(phrase);
               RewriterUtils.log(logger, query, "Keeping phrase: " + phrase);
            }
        }

        RewriterUtils.log(logger, query, "Successfully Retrieved longest non-overlapping full phrase matches");
        return resultMatches;
    }


    /**
     * <p>Retrieve the longest, from left to right non overlapping partial
     * phrase substrings in query based on FSA dictionary</p>
     *
     * e.g. query: ((modern AND new AND york AND city AND travel) OR travel) AND
     *             ((sunny AND travel AND agency) OR nyc)<br>
     *      dictionary: <br>
     *                  mny\tmodern new york<br>
     *                  mo\tmodern<br>
     *                  modern\tn/a<br>
     *                  modern new york\tn/a<br>
     *                  new york\tn/a<br>
     *                  new york city\tn/a<br>
     *                  new york city travel\tn/a<br>
     *                  new york company\tn/a<br>
     *                  ny\tnew york<br>
     *                  nyc\tnew york city\tnew york company<br>
     *                  nyct\tnew york city travel<br>
     *                  ta\ttravel agency<br>
     *                  travel agency\tn/a<br>
     *      return: <br>
     *              modern<br>
     *              new york city travel<br>
     *              travel agency<br>
     *              nyc<br>
     * @param phraseMatcher PhraseMatcher object loaded with FSA dict
     * @param query Query object from the searcher
     * @return Matching phrases
     */
    public static Set<PhraseMatcher.Phrase> getNonOverlappingPartialPhraseMatches(PhraseMatcher phraseMatcher,
                                                                                  Query query)
                                                                                  throws RuntimeException {
        RewriterUtils.log(logger, query, "Retrieving longest non-overlapping partial phrase matches");
        if (phraseMatcher == null)
            return null;

        Item root = query.getModel().getQueryTree().getRoot();
        List<PhraseMatcher.Phrase> matches = phraseMatcher.matchPhrases(root);
        if (matches == null || matches.isEmpty())
            return null;

        Set<PhraseMatcher.Phrase> resultMatches = new HashSet<>();
        ArrayList<PhraseMatcher.Phrase> phrasesInSubTree = new ArrayList<>();
        CompositeItem prevOwner = null;
        ListIterator<PhraseMatcher.Phrase> matchesIter = matches.listIterator();

        // Iterate through all matches
        while (matchesIter.hasNext()) {
            PhraseMatcher.Phrase phrase = matchesIter.next();
            RewriterUtils.log(logger, query, "Working on phrase: " + phrase);
            CompositeItem currOwner = phrase.getOwner();

            // Check if previous is AND item and this phrase is in a different item
            // If so, work on the previous set to eliminate overlapping matches
            if (!phrasesInSubTree.isEmpty() && currOwner!=null &&
               prevOwner!=null && !currOwner.equals(prevOwner)) {
                RewriterUtils.log(logger, query, "Previous phrase is in different AND item");
                List<PhraseMatcher.Phrase> subTreeMatches
                    = getNonOverlappingMatchesInAndItem(phrasesInSubTree, query);
                if(subTreeMatches==null) {
                    RewriterUtils.error(logger, query, "Error retrieving matches from subtree");
                    throw new RuntimeException("Error retrieving matches from subtree");
                }
                resultMatches.addAll(subTreeMatches);
                phrasesInSubTree.clear();
            }

            // Check if this is an AND item
            if (currOwner instanceof AndItem) {
                phrasesInSubTree.add(phrase);
                // If phrase is not an AND item, only keep those that are single word
                // in order to eliminate cases such as (new RANK york) from being treated
                // as match if only new york but not new or york is in the dictionary
            }
            else if (phrase.getLength() == 1 && !(currOwner instanceof RankItem && phrase.getStartIndex() != 0)) {
                resultMatches.add(phrase);
            }

            prevOwner = currOwner;
        }

        // Check if last item is AND item
        // If so, work on the previous set to elimate overlapping matches
        if(!phrasesInSubTree.isEmpty()) {
            RewriterUtils.log(logger, query, "Last phrase is in AND item");
            List<PhraseMatcher.Phrase> subTreeMatches
                = getNonOverlappingMatchesInAndItem(phrasesInSubTree, query);
            if(subTreeMatches==null) {
                RewriterUtils.error(logger, query, "Error retrieving matches from subtree");
                throw new RuntimeException("Error retrieving matches from subtree");
            }
            resultMatches.addAll(subTreeMatches);
        }
        RewriterUtils.log(logger, query, "Successfully Retrieved longest non-overlapping partial phrase matches");
        return resultMatches;
    }

    /**
     * <p>Retrieve the longest, from left to right non overlapping substrings in
     * AndItem based on FSA dictionary</p>
     *
     * e.g. subtree: (modern AND new AND york AND city AND travel)<br>
     *      dictionary:<br>
     *                  mny\tmodern new york<br>
     *                  mo\tmodern<br>
     *                  modern\tn/a<br>
     *                  modern new york\tn/a<br>
     *                  new york\tn/a<br>
     *                  new york city\tn/a<br>
     *                  new york city travel\tn/a<br>
     *                  new york company\tn/a<br>
     *                  ny\tnew york<br>
     *                  nyc\tnew york city\tnew york company<br>
     *                  nyct\tnew york city travel<br>
     *      allMatches:<br>
     *                  modern<br>
     *                  modern new york<br>
     *                  new york<br>
     *                  new york city<br>
     *                  new york city travel<br>
     *      return: <br>
     *              modern<br>
     *              new york city travel<br>
     * @param allMatches All matches within the subtree
     * @param query Query object from the searcher
     * @return Matching phrases
     */
    public static List<PhraseMatcher.Phrase> getNonOverlappingMatchesInAndItem(
                                             List<PhraseMatcher.Phrase> allMatches,
                                             Query query)
                                             throws RuntimeException {
        RewriterUtils.log(logger, query, "Retrieving longest non-overlapping matches in subtree");

        if (allMatches==null || allMatches.isEmpty())
            return null;

        if(allMatches.size()==1) {
            RewriterUtils.log(logger, query, "Only one match in subtree");
            return allMatches;
        }

        // Phrase are sorted based on length, if both have the
        // same length, the lefter one ranks higher
        RewriterUtils.log(logger, query, "Sorting the phrases");
        PhraseLength phraseLength = new PhraseLength();
        Collections.sort(allMatches, phraseLength);

        // Create a bitset with length equal to the number of
        // items in the subtree
        int numWords = allMatches.get(0).getOwner().getItemCount();
        BitSet matchPos = new BitSet(numWords);

        // Removing matches that are overlapping with previously selected ones
        RewriterUtils.log(logger, query, "Removing matches that are overlapping " +
                                         "with previously selected ones");
        ListIterator<Phrase> allMatchesIter = allMatches.listIterator();
        while(allMatchesIter.hasNext()) {
            PhraseMatcher.Phrase currMatch = allMatchesIter.next();
            PhraseMatcher.Phrase.MatchIterator matchIter = currMatch.itemIterator();
            if(matchIter.hasNext() && matchIter.next().isFilter()) {
                RewriterUtils.log(logger, query, "Removing filter item" + currMatch);
                allMatchesIter.remove();
                continue;
            }

            BitSet currMatchPos = new BitSet(numWords);
            currMatchPos.set(currMatch.getStartIndex(),
                             currMatch.getLength()+currMatch.getStartIndex());
            if(currMatchPos.intersects(matchPos)) {
                RewriterUtils.log(logger, query, "Removing " + currMatch);
                allMatchesIter.remove();
            } else {
                RewriterUtils.log(logger, query, "Keeping " + currMatch);
                matchPos.or(currMatchPos);
            }
        }
        return allMatches;
    }

    /**
     * <p>Add Expansions to the matching phrases</p>
     *
     * e.g. Query: nyc travel agency<br>
     *      matching phrase: nyc\tnew york city\tnew york company
     *                       travel agency\tn/a<br>
     *      if expandIndex is not null and removeOriginal is true<br>
     *      New Query: ((new york city) OR ([expandIndex]:new york city)
     *                  OR (new york company) OR
     *                  ([expandIndex]:new york company)) AND
     *                 ((travel agency) OR ([expandIndex]:travel agency))<br>
     *      if expandIndex is null and removeOriginal is true<br>
     *      New Query: ((new york city) OR (new york company)) AND
     *                 travel agency<br>
     *      if expandIndex is null and removeOriginal is false<br>
     *      New Query: (nyc OR (new york city) OR (new york company)) AND
     *                 travel agency<br>
     *
     * @param query Query object from searcher
     * @param matches Set of longest non-overlapping matches
     * @param expandIndex Name of expansion index or null if
     *                    default index
     * @param maxNumRewrites Max number of rewrites to be added,
     *                       0 if no limit
     * @param removeOriginal Whether to remove the original matching phrase
     * @param addUnitToRewrites Whether to add rewrite as phrase
     */
    public static Query addExpansions(Query query, Set<PhraseMatcher.Phrase> matches,
                                      String expandIndex, int maxNumRewrites,
                                      boolean removeOriginal, boolean addUnitToRewrites)
                                      throws RuntimeException {

        if(matches == null) {
            RewriterUtils.log(logger, query, "No expansions to be added");
            return query;
        }

        RewriterUtils.log(logger, query, "Adding expansions to matching phrases");
        Model queryModel = query.getModel();
        QueryTree qTree = queryModel.getQueryTree();
        Iterator<Phrase> matchesIter = matches.iterator();
        CompositeItem parent = null;

        // Iterate through all matches
        while(matchesIter.hasNext()) {
            PhraseMatcher.Phrase match = matchesIter.next();
            RewriterUtils.log(logger, query, "Working on phrase: " + match);

            // Retrieve expansion phrases
            String expansionStr = match.getData();
            if (expansionStr.equalsIgnoreCase("n/a") && expandIndex == null) {
                continue;
            }
            StringTokenizer expansions = new StringTokenizer(expansionStr,"\t");

            // Create this structure for all expansions of this match
            // (OR (AND expandsion1) indexName:expansion1
            //  (AND expansion2) indexName:expansion2..)
            OrItem expansionGrp = new OrItem();
            int numRewrites = 0;
            String matchStr = convertMatchToString(match);
            while(expansions.hasMoreTokens() &&
                  (maxNumRewrites==0 || numRewrites < maxNumRewrites)) {
       	        String expansion = expansions.nextToken();
                RewriterUtils.log(logger, query, "Working on expansion: " + expansion);
                if (expansion.equalsIgnoreCase("n/a")) {
                    expansion = matchStr;
                }
                // (AND expansion) or "expansion"
                Item expansionItem = convertStringToQTree(query, expansion);
                if (addUnitToRewrites && expansionItem instanceof AndItem) {
                   expansionItem = convertAndToPhrase((AndItem)expansionItem);
                }
                expansionGrp.addItem(expansionItem);

                if (expandIndex!=null) {
                    // indexName:expansion
                    WordItem expansionIndexItem = new WordItem(expansion, expandIndex);
                    expansionGrp.addItem(expansionIndexItem);
                }
                numRewrites++;
                RewriterUtils.log(logger, query, "Adding expansion: " + expansion);
            }

            if (!removeOriginal) {
                //(AND original)
                Item matchItem = convertStringToQTree(query, matchStr);
                if (expansionGrp.getItemIndex(matchItem)==-1) {
                    expansionGrp.addItem(matchItem);
                }
            }

            parent = match.getOwner();
            int matchIndex = match.getStartIndex();
            if (parent!=null) {
                // Remove matching phrase from original query
                for (int i=0; i<match.getLength(); i++) {
                    parent.removeItem(matchIndex);
                }
                // Adding back expansions
                parent.addItem(matchIndex, expansionGrp);
            } else {
               RewriterUtils.log(logger, query, "Single root item");
               // If there's no parent, i.e. single root item
               qTree.setRoot(expansionGrp);
               break;
            }
        }

        // Not root single item
        if (parent != null) {
            // Cleaning up the query after rewrite to remove redundant tags
            // e.g. (AND (OR (AND a b) c)) => (OR (AND a b) c)
            String cleanupError = QueryCanonicalizer.canonicalize(qTree);
            if (cleanupError!=null) {
                RewriterUtils.error(logger, query, "Error canonicalizing query tree");
                throw new RuntimeException("Error canonicalizing query tree");
            }
        }
        queryModel.setType(Query.Type.ADVANCED); //set type=adv
        RewriterUtils.log(logger, query, "Successfully added expansions to matching phrases");
        return query;
    }

    /**
     * Convert Match to String
     *
     * @param phrase Match from PhraseMatcher
     * @return String format of the phrase
     */
    public static String convertMatchToString(PhraseMatcher.Phrase phrase) {
        StringBuilder buffer = new StringBuilder();
        for (Iterator<Item> i = phrase.itemIterator(); i.hasNext();) {
            buffer.append(i.next().toString());
            if (i.hasNext()) {
                buffer.append(" ");
            }
        }
        return buffer.toString();
    }

    /**
     * Convert String to query tree
     *
     * @param stringToParse The string to be converted to a
     *                      query tree
     * @param query Query object from searcher
     * @return Item The resulting query tree
     */
    static Item convertStringToQTree(Query query, String stringToParse) {
        RewriterUtils.log(logger, query, "Converting string [" + stringToParse + "] to query tree");
        if (stringToParse == null) {
            return new NullItem();
        }
        Model model = query.getModel();
        CustomParser parser = (CustomParser) ParserFactory.newInstance(model.getType(),
                ParserEnvironment.fromExecutionContext(query.getModel().getExecution().context()));
        IndexFacts indexFacts = new IndexFacts();
        Item item = parser.parse(stringToParse, null, model.getParsingLanguage(),
                                 indexFacts.newSession(model.getSources(), model.getRestrict()),
                                 model.getDefaultIndex());
        RewriterUtils.log(logger, query, "Converted string: [" + item.toString() + "]");
        return item;
    }

    /**
     * Convert AndItem to PhraseItem<br>
     *
     * e.g. (AND a b) to "a b"
     * @param andItem query tree to be converted
     * @return converted PhraseItem
     */
    private static PhraseItem convertAndToPhrase(AndItem andItem) {
        PhraseItem result = new PhraseItem();
        Iterator<Item> subItems = andItem.getItemIterator();
        while(subItems.hasNext()) {
            Item curr = (subItems.next());
            if (curr instanceof IntItem) {
                WordItem numItem = new WordItem(((IntItem)curr).stringValue());
                result.addItem(numItem);
            } else {
                result.addItem(curr);
            }
        }
        return result;
    }

    /**
     * Class for comparing phrase.
     * A phrase is larger if its length is longer.
     * If both phrases are of the same length, the lefter one
     * is considered larger
     */
    private static class PhraseLength implements Comparator<PhraseMatcher.Phrase> {
        public int compare(PhraseMatcher.Phrase phrase1, PhraseMatcher.Phrase phrase2) {
            if ((phrase2.getLength()>phrase1.getLength()) ||
               (phrase2.getLength()==phrase1.getLength() &&
                phrase2.getStartIndex()<=phrase1.getStartIndex())) {
                return 1;
            } else {
                return -1;
            }
        }
    }

}
