// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

public class GeoLocation extends QueryChain {

    private final String fieldName;
    private final Double longitude;
    private final Double latitude;
    private final String radius;

    public GeoLocation(String fieldName, Double longitude, Double latitude, String radius) {
        this.fieldName = fieldName;
        this.longitude = longitude;
        this.latitude = latitude;
        this.radius = radius;
        this.nonEmpty = true;
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        return this.fieldName.equals(fieldName);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        return false;
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
        return Text.format("geoLocation(%s, %f, %f, %s)", fieldName, longitude, latitude, Q.toJson(radius));
    }
}
