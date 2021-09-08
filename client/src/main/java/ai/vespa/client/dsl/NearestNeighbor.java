package ai.vespa.client.dsl;

import java.util.stream.Collectors;

public class NearestNeighbor extends QueryChain {

    private Annotation annotation;
    private String docVectorName;
    private String queryVectorName;


    public NearestNeighbor(String docVectorName, String queryVectorName) {
        this.docVectorName = docVectorName;
        this.queryVectorName = queryVectorName;
        this.nonEmpty = true;
    }

    NearestNeighbor annotate(Annotation annotation) {
        this.annotation = annotation;
        return this;
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        return this.docVectorName.equals(fieldName);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        return this.docVectorName.equals(fieldName) && queryVectorName.equals(value);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        return false;
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        return false;
    }

    @Override
    public String toString() {
        boolean hasAnnotation = A.hasAnnotation(annotation);
        if (!hasAnnotation || !annotation.contains("targetHits")) {
            throw new IllegalArgumentException("must specify target hits in nearest neighbor query");
        }
        String s = Text.format("nearestNeighbor(%s, %s)", docVectorName, queryVectorName);
        return Text.format("([%s]%s)", annotation, s);
    }
}
