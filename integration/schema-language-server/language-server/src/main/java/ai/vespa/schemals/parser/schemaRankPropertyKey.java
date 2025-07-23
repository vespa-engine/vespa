package ai.vespa.schemals.parser;

import ai.vespa.schemals.parser.ast.BaseNode;

public class schemaRankPropertyKey extends BaseNode {
    private SubLanguageData propertyName = null;

    public String getPropertyName() {
        return propertyName == null ? "" : propertyName.content();
    }

    public void setPropertyName(SubLanguageData data) {
        this.propertyName = data;
    }
}
