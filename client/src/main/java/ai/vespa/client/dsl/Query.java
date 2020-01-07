// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Query extends QueryChain {

    Annotation annotation;
    Sources sources;

    List<QueryChain> queries = new ArrayList<>();

    Query(Sources sources, QueryChain queryChain) {
        this.sources = sources;
        queries.add(queryChain);
        nonEmpty = queryChain.nonEmpty;
    }

    Query(Sources sources) {
        this.sources = sources;
    }

    String toCommaSeparatedAndQueries() {
        return queries.stream()
            .filter(qc -> "and".equals(qc.getOp()))
            .map(Objects::toString)
            .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        // TODO: need to refactor
        if (!nonEmpty) {
            return "";
        }

        boolean hasAnnotation = A.hasAnnotation(annotation);

        StringBuilder sb = new StringBuilder();

        if (hasAnnotation) {
            sb.append("([").append(annotation).append("](");
        }

        boolean firstQuery = true;
        for (int i = 0; i < queries.size(); i++) {
            QueryChain qc = queries.get(i);
            if (!qc.nonEmpty) {
                continue;
            }

            boolean isNotAnd = "andnot".equals(qc.getOp());

            if (!firstQuery) {
                sb.append(" ");
                if (isNotAnd) {
                    sb.append("and !");
                } else {
                    sb.append(qc.getOp()).append(' ');
                }
            } else {
                firstQuery = false;
            }

            boolean appendBrackets =
                (qc instanceof Query && ((Query) qc).queries.size() > 1 && !A.hasAnnotation(((Query) qc).annotation))
                || isNotAnd;
            if (appendBrackets) {
                sb.append("(");
            }

            sb.append(qc);

            if (appendBrackets) {
                sb.append(")");
            }

        }

        if (hasAnnotation) {
            sb.append("))");
        }
        return sb.toString().trim();
    }

    public Field and(String fieldName) {
        Field f = new Field(this, fieldName);
        f.setOp("and");
        queries.add(f);
        nonEmpty = true;
        return f;
    }

    public Field andnot(String fieldName) {
        Field f = new Field(this, fieldName);
        f.setOp("andnot");
        queries.add(f);
        nonEmpty = true;
        return f;
    }

    public Field or(String fieldName) {
        Field f = new Field(this, fieldName);
        f.setOp("or");
        queries.add(f);
        nonEmpty = true;
        return f;
    }

    public Query and(QueryChain query) {
        query.setOp("and");
        queries.add(query);
        nonEmpty = nonEmpty || query.nonEmpty;
        return this;
    }

    public Query andnot(QueryChain query) {
        query.setOp("andnot");
        queries.add(query);
        nonEmpty = nonEmpty || query.nonEmpty;
        return this;
    }

    public Query or(QueryChain query) {
        query.setOp("or");
        queries.add(query);
        nonEmpty = nonEmpty || query.nonEmpty;
        return this;
    }

    public Query annotate(Annotation annotation) {
        this.annotation = annotation;
        return this;
    }

    public EndQuery offset(int offset) {
        return new EndQuery(this).offset(offset);
    }

    public EndQuery limit(int hits) {
        return new EndQuery(this).limit(hits);
    }

    public EndQuery timeout(int timeout) {
        return new EndQuery(this).timeout(timeout);
    }

    public EndQuery group(Group group) {
        return new EndQuery(this).group(group);
    }

    public EndQuery group(String groupStr) {
        return new EndQuery(this).group(groupStr);
    }

    public EndQuery orderByAsc(String fieldName) {
        return new EndQuery(this).orderByAsc(fieldName);
    }

    public EndQuery orderByDesc(String fieldName) {
        return new EndQuery(this).orderByDesc(fieldName);
    }

    public FixedQuery semicolon() {
        return new FixedQuery(new EndQuery(this));
    }

    @Override
    public Sources getSources() {
        return sources;
    }

    @Override
    public void setSources(Sources sources) {
        this.sources = sources;
    }

    @Override
    public Query getQuery() {
        return this;
    }

    @Override
    public Select getSelect() {
        return sources.select;
    }

    @Override
    public boolean hasPositiveSearchField(String fieldName) {
        boolean hasPositiveInSubqueries = queries.stream().anyMatch(q -> q.hasPositiveSearchField(fieldName));
        boolean hasNegativeInSubqueries = queries.stream().anyMatch(q -> q.hasNegativeSearchField(fieldName));
        return nonEmpty
               && ((!"andnot".equals(this.op) && hasPositiveInSubqueries)
                   || ("andnot".equals(this.op) && hasNegativeInSubqueries));
    }


    @Override
    public boolean hasPositiveSearchField(String fieldName, Object value) {
        boolean hasPositiveInSubqueries = queries.stream().anyMatch(q -> q.hasPositiveSearchField(fieldName, value));
        boolean hasNegativeInSubqueries = queries.stream().anyMatch(q -> q.hasNegativeSearchField(fieldName, value));
        return nonEmpty &&
               (!"andnot".equals(this.op) && hasPositiveInSubqueries)
               || ("andnot".equals(this.op) && hasNegativeInSubqueries);
    }

    @Override
    public boolean hasNegativeSearchField(String fieldName) {
        boolean hasPositiveInSubqueries = queries.stream().anyMatch(q -> q.hasPositiveSearchField(fieldName));
        boolean hasNegativeInSubqueries = queries.stream().anyMatch(q -> q.hasNegativeSearchField(fieldName));
        return nonEmpty
               && (!"andnot".equals(this.op) && hasNegativeInSubqueries)
               || ("andnot".equals(this.op) && hasPositiveInSubqueries);
    }

    @Override
    public boolean hasNegativeSearchField(String fieldName, Object value) {
        boolean hasPositiveInSubqueries = queries.stream().anyMatch(q -> q.hasPositiveSearchField(fieldName, value));
        boolean hasNegativeInSubqueries = queries.stream().anyMatch(q -> q.hasNegativeSearchField(fieldName, value));
        return nonEmpty
               && (!"andnot".equals(this.op) && hasNegativeInSubqueries)
               || ("andnot".equals(this.op) && hasPositiveInSubqueries);
    }
}
