// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.content.utils.ApplicationPackageBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.SchemaBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.assertEqualActions;
import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRefeedAction;
import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRestartAction;
import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.normalizeServicesInActions;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IndexedSchemaClusterChangeValidatorTest {

    static class Fixture {
        VespaModel currentModel;
        VespaModel nextModel;
        IndexedSearchClusterChangeValidator validator;

        public Fixture(VespaModel currentModel, VespaModel nextModel) {
            this.currentModel = currentModel;
            this.nextModel = nextModel;
            validator = new IndexedSearchClusterChangeValidator();
        }

        public static Fixture newOneDocFixture(String currentSd, String nextSd) {
            return new Fixture(newOneDocModel(currentSd), newOneDocModel(nextSd));
        }

        public static VespaModel newOneDocModel(String sdContent) {
            return new ApplicationPackageBuilder().
                    addCluster(new ContentClusterBuilder().name("foo").docTypes("d1"))
                                                  .addSchemas(new SchemaBuilder().name("d1").content(sdContent).build())
                                                  .buildCreator().create();
        }

        public static Fixture newTwoDocFixture(String currentSd, String nextSd) {
            return new Fixture(newTwoDocModel(currentSd, currentSd), newTwoDocModel(nextSd, nextSd));
        }

        public static VespaModel newTwoDocModel(String d1Content, String d2Content) {
            return new ApplicationPackageBuilder().
                    addCluster(new ContentClusterBuilder().name("foo").docTypes("d1", "d2"))
                                                  .addSchemas(new SchemaBuilder().name("d1").content(d1Content).build())
                                                  .addSchemas(new SchemaBuilder().name("d2").content(d2Content).build()).
                    buildCreator().create();
        }

        public static Fixture newTwoClusterFixture(String currentSd, String nextSd) {
            return new Fixture(newTwoClusterModel(currentSd, currentSd), newTwoClusterModel(nextSd, nextSd));
        }

        public static VespaModel newTwoClusterModel(String d1Content, String d2Content) {
            return new ApplicationPackageBuilder().
                    addCluster(new ContentClusterBuilder().name("foo").docTypes("d1")).
                    addCluster(new ContentClusterBuilder().name("bar").docTypes("d2"))
                                                  .addSchemas(new SchemaBuilder().name("d1").content(d1Content).build())
                                                  .addSchemas(new SchemaBuilder().name("d2").content(d2Content).build()).
                    buildCreator().create();
        }

        private List<ConfigChangeAction> validate() {
            return normalizeServicesInActions(validator.validate(currentModel, nextModel,
                                                                 ValidationOverrides.empty, Instant.now()));
        }

        public void assertValidation() {
            assertTrue(validate().isEmpty());
        }

        public void assertValidation(ConfigChangeAction exp) {
            assertValidation(List.of(exp));
        }

        public void assertValidation(List<ConfigChangeAction> exp) {
            assertEqualActions(exp, validate());
        }
    }

    static String STRING_FIELD = "field f1 type string { indexing: summary }";
    static String ATTRIBUTE_FIELD = "field f1 type string { indexing: attribute | summary }";
    static String ATTRIBUTE_CHANGE_MSG = "Field 'f1' changed: add attribute aspect";
    static String INT_FIELD = "field f1 type int { indexing: summary }";
    static String FIELD_TYPE_CHANGE_MSG = "Field 'f1' changed: data type: 'string' -> 'int'";
    private static final List<ServiceInfo> FOO_SERVICE = List.of(
            new ServiceInfo("searchnode", "null", null, null, "foo/search/cluster.foo/0", "null"));
    private static final List<ServiceInfo> BAR_SERVICE = List.of(
            new ServiceInfo("searchnode2", "null", null, null, "bar/search/cluster.bar/0", "null"));

    @Test
    void requireThatDocumentDatabaseChangeIsDiscovered() {
        Fixture.newOneDocFixture(STRING_FIELD, ATTRIBUTE_FIELD).
                assertValidation(newRestartAction(ClusterSpec.Id.from("test"),
                "Document type 'd1': " + ATTRIBUTE_CHANGE_MSG, FOO_SERVICE));
    }

    @Test
    void requireThatChangeInSeveralDocumentDatabasesAreDiscovered() {
        Fixture.newTwoDocFixture(STRING_FIELD, ATTRIBUTE_FIELD).
                assertValidation(List.of(newRestartAction(ClusterSpec.Id.from("test"),
                "Document type 'd1': " + ATTRIBUTE_CHANGE_MSG, FOO_SERVICE),
                newRestartAction(ClusterSpec.Id.from("test"),
                        "Document type 'd2': " + ATTRIBUTE_CHANGE_MSG, FOO_SERVICE)));
    }

    @Test
    void requireThatChangeInSeveralContentClustersAreDiscovered() {
        Fixture.newTwoClusterFixture(STRING_FIELD, ATTRIBUTE_FIELD).
                assertValidation(List.of(newRestartAction(ClusterSpec.Id.from("test"),
                "Document type 'd1': " + ATTRIBUTE_CHANGE_MSG, FOO_SERVICE),
                newRestartAction(ClusterSpec.Id.from("test"),
                        "Document type 'd2': " + ATTRIBUTE_CHANGE_MSG, BAR_SERVICE)));
    }

    @Test
    void requireThatAddingDocumentDatabaseIsOk() {
        new Fixture(Fixture.newOneDocModel(STRING_FIELD), Fixture.newTwoDocModel(STRING_FIELD, STRING_FIELD)).assertValidation();
    }

    @Test
    void requireThatRemovingDocumentDatabaseIsOk() {
        new Fixture(Fixture.newTwoDocModel(STRING_FIELD, STRING_FIELD), Fixture.newOneDocModel(STRING_FIELD)).assertValidation();
    }

    @Test
    void requireThatAddingContentClusterIsOk() {
        new Fixture(Fixture.newOneDocModel(STRING_FIELD), Fixture.newTwoClusterModel(STRING_FIELD, STRING_FIELD)).assertValidation();
    }

    @Test
    void requireThatRemovingContentClusterIsOk() {
        new Fixture(Fixture.newTwoClusterModel(STRING_FIELD, STRING_FIELD), Fixture.newOneDocModel(STRING_FIELD)).assertValidation();
    }

    @Test
    void requireThatChangingFieldTypeIsDiscovered() {
        Fixture f = Fixture.newOneDocFixture(STRING_FIELD, INT_FIELD);
        f.assertValidation(List.of(newRefeedAction(ClusterSpec.Id.from("test"),
                ValidationId.fieldTypeChange,
                "Document type 'd1': " + FIELD_TYPE_CHANGE_MSG, FOO_SERVICE, "d1")));
    }

}
