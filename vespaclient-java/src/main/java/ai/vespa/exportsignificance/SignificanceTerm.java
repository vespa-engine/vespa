// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.exportsignificance;

import com.yahoo.data.JsonProducer;

/**
 * Represents a term and significance with Json producer.
 *
 * @author johsol
 */
public record SignificanceTerm(String term, Long count) implements JsonProducer {

    @Override
    public StringBuilder writeJson(StringBuilder target) {
        return null;
    }

    @Override
    public String toJson() {
        return JsonProducer.super.toJson();
    }
}
