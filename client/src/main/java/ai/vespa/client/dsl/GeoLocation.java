package ai.vespa.client.dsl;

import org.apache.commons.text.StringEscapeUtils;

public class GeoLocation extends QueryChain {

    private String fieldName;
    private Double longitude;
    private Double latitude;
    private String radius;

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
        return Text.format("geoLocation(%s, %f, %f, \"%s\")", fieldName, longitude, latitude, StringEscapeUtils.escapeJava(radius));
    }
}
