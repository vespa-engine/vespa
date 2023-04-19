package ai.vespa.llm;

import com.yahoo.api.annotations.Beta;

@Beta
public class GeneratorOptions {

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

    public GeneratorOptions setSearchMethod(SearchMethod searchMethod) {
        this.searchMethod = searchMethod;
        return this;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public GeneratorOptions setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }


}
