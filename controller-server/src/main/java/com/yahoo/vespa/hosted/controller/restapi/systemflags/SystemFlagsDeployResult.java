// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.systemflags;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.flags.FlagDefinition;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.FlagsTarget;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult.WireFlagDataChange;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult.WireOperationFailure;
import com.yahoo.vespa.hosted.controller.api.systemflags.v1.wire.WireSystemFlagsDeployResult.WireWarning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author bjorncs
 */
class SystemFlagsDeployResult {

    private final List<FlagDataChange> flagChanges;
    private final List<OperationError> errors;
    private final List<Warning> warnings;

    SystemFlagsDeployResult(List<FlagDataChange> flagChanges, List<OperationError> errors, List<Warning> warnings) {
        this.flagChanges = flagChanges;
        this.errors = errors;
        this.warnings = warnings;
    }

    SystemFlagsDeployResult(List<OperationError> errors) {
        this(List.of(), errors, List.of());
    }

    List<FlagDataChange> flagChanges() {
        return flagChanges;
    }

    List<OperationError> errors() {
        return errors;
    }

    List<Warning> warnings() { return warnings; }

    static SystemFlagsDeployResult merge(List<SystemFlagsDeployResult> results) {
        List<FlagDataChange> mergedChanges = mergeChanges(results);
        List<OperationError> mergedErrors = mergeErrors(results);
        List<Warning> mergedWarnings = mergeWarnings(results);
        return new SystemFlagsDeployResult(mergedChanges, mergedErrors, mergedWarnings);
    }

    private static List<OperationError> mergeErrors(List<SystemFlagsDeployResult> results) {
        return merge(results, SystemFlagsDeployResult::errors, OperationError::targets,
                OperationErrorWithoutTarget::new, OperationErrorWithoutTarget::toOperationError);
    }

    private static List<FlagDataChange> mergeChanges(List<SystemFlagsDeployResult> results) {
        return merge(results, SystemFlagsDeployResult::flagChanges, FlagDataChange::targets,
                FlagDataChangeWithoutTarget::new, FlagDataChangeWithoutTarget::toFlagDataChange);
    }

    private static List<Warning> mergeWarnings(List<SystemFlagsDeployResult> results) {
        return merge(results, SystemFlagsDeployResult::warnings, Warning::targets,
                WarningWithoutTarget::new, WarningWithoutTarget::toWarning);
    }

    private static <VALUE, VALUE_WITHOUT_TARGET> List<VALUE> merge(
            List<SystemFlagsDeployResult> results,
            Function<SystemFlagsDeployResult, List<VALUE>> valuesGetter,
            Function<VALUE, Set<FlagsTarget>> targetsGetter,
            Function<VALUE, VALUE_WITHOUT_TARGET> transformer,
            BiFunction<VALUE_WITHOUT_TARGET, Set<FlagsTarget>, VALUE> reverseTransformer) {
        Map<VALUE_WITHOUT_TARGET, Set<FlagsTarget>> targetsForValue = new HashMap<>();
        for (SystemFlagsDeployResult result : results) {
            for (VALUE value : valuesGetter.apply(result)) {
                VALUE_WITHOUT_TARGET valueWithoutTarget = transformer.apply(value);
                targetsForValue.computeIfAbsent(valueWithoutTarget, k -> new HashSet<>())
                        .addAll(targetsGetter.apply(value));
            }
        }
        List<VALUE> mergedValues = new ArrayList<>();
        targetsForValue.forEach(
                (value, targets) -> mergedValues.add(reverseTransformer.apply(value, targets)));
        return mergedValues;
    }

    WireSystemFlagsDeployResult toWire() {
        var wireResult = new WireSystemFlagsDeployResult();
        wireResult.changes = new ArrayList<>();
        for (FlagDataChange change : flagChanges) {
            var wireChange = new WireFlagDataChange();
            wireChange.flagId = change.flagId().toString();
            wireChange.owners = owners(change.flagId());
            wireChange.operation = change.operation().asString();
            wireChange.targets = change.targets().stream().map(FlagsTarget::asString).toList();
            wireChange.data = change.data().map(FlagData::toWire).orElse(null);
            wireChange.previousData = change.previousData().map(FlagData::toWire).orElse(null);
            wireResult.changes.add(wireChange);
        }
        wireResult.errors = new ArrayList<>();
        for (OperationError error : errors) {
            var wireError = new WireOperationFailure();
            wireError.message = error.message();
            wireError.operation = error.operation().asString();
            wireError.targets = error.targets().stream().map(FlagsTarget::asString).toList();
            wireError.flagId = error.flagId().map(FlagId::toString).orElse(null);
            wireError.owners = error.flagId().map(id -> owners(id)).orElse(List.of());
            wireError.data = error.flagData().map(FlagData::toWire).orElse(null);
            wireResult.errors.add(wireError);
        }
        wireResult.warnings = new ArrayList<>();
        for (Warning warning : warnings) {
            var wireWarning = new WireWarning();
            wireWarning.message = warning.message();
            wireWarning.flagId = warning.flagId().toString();
            wireWarning.owners = owners(warning.flagId());
            wireWarning.targets = warning.targets().stream().map(FlagsTarget::asString).toList();
            wireResult.warnings.add(wireWarning);
        }
        return wireResult;
    }

    private static List<String> owners(FlagId id) {
        return Flags.getFlag(id).map(FlagDefinition::getOwners).orElse(List.of());
    }

    static class FlagDataChange {

        private final FlagId flagId;
        private final Set<FlagsTarget> targets;
        private final OperationType operationType;
        private final FlagData data;
        private final FlagData previousData;

        private FlagDataChange(
                FlagId flagId, Set<FlagsTarget> targets, OperationType operationType, FlagData data, FlagData previousData) {
            this.flagId = flagId;
            this.targets = targets;
            this.operationType = operationType;
            this.data = data;
            this.previousData = previousData;
        }

        static FlagDataChange created(FlagId flagId, FlagsTarget target, FlagData data) {
            return new FlagDataChange(flagId, Set.of(target), OperationType.CREATE, data, null);
        }

        static FlagDataChange deleted(FlagId flagId, FlagsTarget target) {
            return new FlagDataChange(flagId, Set.of(target), OperationType.DELETE, null, null);
        }

        static FlagDataChange updated(FlagId flagId, FlagsTarget target, FlagData data, FlagData previousData) {
            return new FlagDataChange(flagId, Set.of(target), OperationType.UPDATE, data, previousData);
        }

        FlagId flagId() {
            return flagId;
        }

        Set<FlagsTarget> targets() {
            return targets;
        }

        OperationType operation() {
            return operationType;
        }

        Optional<FlagData> data() {
            return Optional.ofNullable(data);
        }

        Optional<FlagData> previousData() {
            return Optional.ofNullable(previousData);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlagDataChange that = (FlagDataChange) o;
            return Objects.equals(flagId, that.flagId) &&
                    Objects.equals(targets, that.targets) &&
                    operationType == that.operationType &&
                    Objects.equals(data, that.data) &&
                    Objects.equals(previousData, that.previousData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flagId, targets, operationType, data, previousData);
        }

        @Override
        public String toString() {
            return "FlagDataChange{" +
                    "flagId=" + flagId +
                    ", targets=" + targets +
                    ", operationType=" + operationType +
                    ", data=" + data +
                    ", previousData=" + previousData +
                    '}';
        }
    }

    static class OperationError {

        final String message;
        final Set<FlagsTarget> targets;
        final OperationType operation;
        final FlagId flagId;
        final FlagData flagData;

        private OperationError(
                String message, Set<FlagsTarget> targets, OperationType operation, FlagId flagId, FlagData flagData) {
            this.message = message;
            this.targets = targets;
            this.operation = operation;
            this.flagId = flagId;
            this.flagData = flagData;
        }

        static OperationError listFailed(String message, FlagsTarget target) {
            return new OperationError(message, Set.of(target), OperationType.LIST, null, null);
        }

        static OperationError createFailed(String message, FlagsTarget target, FlagData flagData) {
            return new OperationError(message, Set.of(target), OperationType.CREATE, flagData.id(), flagData);
        }

        static OperationError updateFailed(String message, FlagsTarget target, FlagData flagData) {
            return new OperationError(message, Set.of(target), OperationType.UPDATE, flagData.id(), flagData);
        }

        static OperationError deleteFailed(String message, FlagsTarget target, FlagId id) {
            return new OperationError(message, Set.of(target), OperationType.DELETE, id, null);
        }

        static OperationError archiveValidationFailed(String message) {
            return new OperationError(message, Set.of(), OperationType.VALIDATE_ARCHIVE, null, null);
        }

        static OperationError dataForUndefinedFlag(FlagsTarget target, FlagId id) {
            return new OperationError("Flag data present for undefined flag. Remove flag data files if flag's definition " +
                                      "is already removed from Flags / PermanentFlags. Consult ModelContext.FeatureFlags " +
                                      "for safe removal of flag used by config-model.",
                                      Set.of(), OperationType.DATA_FOR_UNDEFINED_FLAG, id, null);
        }

        String message() { return message; }
        Set<FlagsTarget> targets() { return targets; }
        OperationType operation() { return operation; }
        Optional<FlagId> flagId() { return Optional.ofNullable(flagId); }
        Optional<FlagData> flagData() { return Optional.ofNullable(flagData); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OperationError that = (OperationError) o;
            return Objects.equals(message, that.message) &&
                    Objects.equals(targets, that.targets) &&
                    operation == that.operation &&
                    Objects.equals(flagId, that.flagId) &&
                    Objects.equals(flagData, that.flagData);
        }

        @Override public int hashCode() { return Objects.hash(message, targets, operation, flagId, flagData); }

        @Override
        public String toString() {
            return "OperationFailure{" +
                    "message='" + message + '\'' +
                    ", targets=" + targets +
                    ", operation=" + operation +
                    ", flagId=" + flagId +
                    ", flagData=" + flagData +
                    '}';
        }
    }

    enum OperationType {
        CREATE("create"), DELETE("delete"), UPDATE("update"), LIST("list"), VALIDATE_ARCHIVE("validate-archive"),
        DATA_FOR_UNDEFINED_FLAG("data-for-undefined-flag");

        private final String stringValue;

        OperationType(String stringValue) { this.stringValue = stringValue; }

        String asString() { return stringValue; }
    }

    static class Warning {
        final String message;
        final Set<FlagsTarget> targets;
        final FlagId flagId;

        private Warning(String message, Set<FlagsTarget> targets, FlagId flagId) {
            this.message = message;
            this.targets = targets;
            this.flagId = flagId;
        }

        static Warning dataForUndefinedFlag(FlagsTarget target, FlagId flagId) {
            return new Warning(
                    "Flag data present for undefined flag. Remove flag data files if flag's definition is already removed from Flags/PermanentFlags. " +
                            "Consult ModelContext.FeatureFlags for safe removal of flag used by config-model.", Set.of(target), flagId);
        }

        String message() { return message; }
        Set<FlagsTarget> targets() { return targets; }
        FlagId flagId() { return flagId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Warning warning = (Warning) o;
            return Objects.equals(message, warning.message) &&
                    Objects.equals(targets, warning.targets) &&
                    Objects.equals(flagId, warning.flagId);
        }

        @Override public int hashCode() { return Objects.hash(message, targets, flagId); }
    }

    private static class FlagDataChangeWithoutTarget {
        final FlagId flagId;
        final OperationType operationType;
        final FlagData data;
        final FlagData previousData;
        final JsonNode jsonData; // needed for FlagData equality check
        final JsonNode jsonPreviousData; // needed for FlagData equality check


        FlagDataChangeWithoutTarget(FlagDataChange change) {
            this.flagId = change.flagId();
            this.operationType = change.operation();
            this.data = change.data().orElse(null);
            this.previousData = change.previousData().orElse(null);
            this.jsonData = Optional.ofNullable(data).map(FlagData::toJsonNode).orElse(null);
            this.jsonPreviousData = Optional.ofNullable(previousData).map(FlagData::toJsonNode).orElse(null);
        }

        FlagDataChange toFlagDataChange(Set<FlagsTarget> targets) {
            return new FlagDataChange(flagId, targets, operationType, data, previousData);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FlagDataChangeWithoutTarget that = (FlagDataChangeWithoutTarget) o;
            return Objects.equals(flagId, that.flagId) &&
                    operationType == that.operationType &&
                    Objects.equals(jsonData, that.jsonData) &&
                    Objects.equals(jsonPreviousData, that.jsonPreviousData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flagId, operationType, jsonData, jsonPreviousData);
        }
    }

    private static class OperationErrorWithoutTarget {
        final String message;
        final OperationType operation;
        final FlagId flagId;
        final FlagData flagData;

        OperationErrorWithoutTarget(OperationError operationError) {
            this.message = operationError.message();
            this.operation = operationError.operation();
            this.flagId = operationError.flagId().orElse(null);
            this.flagData = operationError.flagData().orElse(null);
        }

        OperationError toOperationError(Set<FlagsTarget> targets) {
            return new OperationError(message, targets, operation, flagId, flagData);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OperationErrorWithoutTarget that = (OperationErrorWithoutTarget) o;
            return Objects.equals(message, that.message) &&
                    operation == that.operation &&
                    Objects.equals(flagId, that.flagId) &&
                    Objects.equals(flagData, that.flagData);
        }

        @Override public int hashCode() { return Objects.hash(message, operation, flagId, flagData); }
    }

    private static class WarningWithoutTarget {
        final String message;
        final FlagId flagId;

        WarningWithoutTarget(Warning warning) {
            this.message = warning.message();
            this.flagId = warning.flagId();
        }

        Warning toWarning(Set<FlagsTarget> targets) { return new Warning(message, targets, flagId); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WarningWithoutTarget that = (WarningWithoutTarget) o;
            return Objects.equals(message, that.message) &&
                    Objects.equals(flagId, that.flagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, flagId);
        }
    }
}
