// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.debug;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.ForkingSearcher;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;

import java.util.Collection;

/**
 * Text representation of a given search chain intended for debugging purposes.
 *
 * @author tonytv
 */
public class SearchChainTextRepresentation {

    private final SearchChainRegistry searchChainRegistry;

    private static class Block {
        private static final String openBlock = " {";
        private static final char closeBlock = '}';
        private final IndentStringBuilder str;
        private final int level;

        Block(IndentStringBuilder str) {
            this.str = str;
            level = str.append(openBlock).newlineAndIndent();
        }

        void close() {
            str.resetIndentLevel(level);
            str.append(closeBlock).newline();
        }
    }

    private final String textRepresentation;

    private void outputChain(IndentStringBuilder str, Chain<Searcher> chain) {
        if (chain == null) {
            str.append(" [Unresolved Searchchain]");
        } else {
            str.append(chain.getId()).append(" [Searchchain] ");
            Block block = new Block(str);

            for (Searcher searcher : chain.components())
                outputSearcher(str, searcher);

            block.close();
        }
    }

    private void outputSearcher(IndentStringBuilder str, Searcher searcher) {
        str.append(searcher.getId()).append(" [Searcher]");
        if ( ! (searcher instanceof ForkingSearcher) ) {
            str.newline();
            return;
        }
        Collection<ForkingSearcher.CommentedSearchChain> chains =
                ((ForkingSearcher)searcher).getSearchChainsForwarded(searchChainRegistry);
        if (chains.isEmpty()) {
            str.newline();
            return;
        }
        Block block = new Block(str);
        for (ForkingSearcher.CommentedSearchChain chain : chains) {
            if (chain.comment != null)
                str.append(chain.comment).newline();
            outputChain(str, chain.searchChain);
        }
        block.close();
    }

    @Override
    public String toString() {
        return textRepresentation;
    }

    public SearchChainTextRepresentation(SearchChain searchChain, SearchChainRegistry searchChainRegistry) {
        this.searchChainRegistry = searchChainRegistry;

        IndentStringBuilder stringBuilder = new IndentStringBuilder();
        outputChain(stringBuilder, searchChain);
        textRepresentation = stringBuilder.toString();
    }

}
