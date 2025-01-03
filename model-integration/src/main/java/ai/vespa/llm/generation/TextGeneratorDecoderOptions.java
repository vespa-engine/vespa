// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.generation;

import com.yahoo.api.annotations.Beta;

@Beta
public class TextGeneratorDecoderOptions {

    public enum SearchMethod {
        GREEDY,
        CONTRASTIVE,
        BEAM,
        SAMPLE,
    }

    private SearchMethod searchMethod = SearchMethod.GREEDY;
    private int maxLength = 20;

    public SearchMethod getSearchMethod() {
        return searchMethod;
    }

    public TextGeneratorDecoderOptions setSearchMethod(SearchMethod searchMethod) {
        this.searchMethod = searchMethod;
        return this;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public TextGeneratorDecoderOptions setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }


}
