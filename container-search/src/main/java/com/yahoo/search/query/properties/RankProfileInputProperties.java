// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.api.annotations.Beta;
import com.yahoo.language.process.Embedder;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.schema.internal.TensorConverter;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Instant;
import java.util.Map;

/**
 * Verifies and converts properties according to any input declarations in the rank profile set on the query.
 *
 * @author bratseth
 */
@Beta
public class RankProfileInputProperties extends Properties {

    private final SchemaInfo schemaInfo;
    private final Query query;
    private final TensorConverter tensorConverter;

    private SchemaInfo.Session session = null;

    public RankProfileInputProperties(SchemaInfo schemaInfo, Query query, Map<String, Embedder> embedders) {
        this.schemaInfo = schemaInfo;
        this.query = query;
        this.tensorConverter = new TensorConverter(embedders);
    }

    @Override
    public void set(CompoundName name, Object value, Map<String, String> context) {
        if (RankFeatures.isFeatureName(name.toString())) {
            try {
                var expectedType = typeOf(name);
                if (expectedType != null && ! expectedType.declaredString()) {
                    long timeLeftMs = query.getTimeLeft();
                    var deadline = timeLeftMs > 0
                            ? Instant.now().plusMillis(timeLeftMs)
                            : null;
                    value = tensorConverter.convertTo(expectedType.tensorType(),
                                                      name.last(),
                                                      value,
                                                      query.getModel().getLanguage(),
                                                      context,
                                                      query.properties(),
                                                      deadline);
                    if (value == null) return; // Blank text to embed - skip setting the feature
                }
            }
            catch (IllegalArgumentException e) {
                throw new IllegalAssignmentException(name, value, e);
            }
        }
        super.set(name, value, context);
    }

    @Override
    public void requireSettable(CompoundName name, Object value, Map<String, String> context) {
        if (RankFeatures.isFeatureName(name.toString())) {
            var expectedType = typeOf(name);
            if (expectedType != null && ! expectedType.declaredString())
                verifyType(name, value, expectedType.tensorType());
        }
        super.requireSettable(name, value, context);
    }

    private RankProfile.InputType typeOf(CompoundName name) {
        // Session is lazily resolved because order matters:
        // model.sources+restrict must be set in the query before this is done
        if (session == null)
            session = schemaInfo.newSession(query);
        // In addition, the rank profile must be set before features
        return session.rankProfileInput(name.last(), query.getRanking().getProfile());
    }

    private void verifyType(CompoundName name, Object value, TensorType expectedType) {
        if (value instanceof Tensor) {
            TensorType valueType = ((Tensor)value).type();
            if ( ! valueType.isAssignableTo(expectedType))
                throwIllegalInput(name, value, expectedType);
        }
        else if (expectedType.rank() > 0) { // rank 0 tensor may also be represented as a scalar or string
            throwIllegalInput(name, value, expectedType);
        }
    }

    private void throwIllegalInput(CompoundName name, Object value, TensorType expectedType) {
        throw new IllegalAssignmentException(name, value,
                                             "This input is declared in rank profile '" + query.getRanking().getProfile() +
                                             "' as " + expectedType);
    }

}
