// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Class to converge file/directory attributes like owner and permissions to wanted values.
 * Typically used by higher abstraction layers working on files (FileSync/FileWriter) or
 * directories (MakeDirectory).
 *
 * @author hakonhall
 */
public class AttributeSync {
    private static final Logger logger = Logger.getLogger(AttributeSync.class.getName());

    private final UnixPath path;

    private Optional<Integer> ownerId = Optional.empty();
    private Optional<Integer> groupId = Optional.empty();
    private Optional<String> permissions = Optional.empty();

    public AttributeSync(Path path) {
        this.path = new UnixPath(path);
    }

    public Optional<String> getPermissions() {
        return permissions;
    }

    public AttributeSync withPermissions(String permissions) {
        this.permissions = Optional.of(permissions);
        return this;
    }

    public Optional<Integer> ownerId() {
        return ownerId;
    }

    public AttributeSync withOwnerId(int ownerId) {
        this.ownerId = Optional.of(ownerId);
        return this;
    }

    public Optional<Integer> groupId() {
        return groupId;
    }

    public AttributeSync withGroupId(int groupId) {
        this.groupId = Optional.of(groupId);
        return this;
    }

    public AttributeSync with(PartialFileData fileData) {
        ownerId = fileData.getOwnerId();
        groupId = fileData.getGroupId();
        permissions = fileData.getPermissions();
        return this;
    }

    public boolean converge(TaskContext context) {
        return converge(context, new FileAttributesCache(path));
    }

    /**
     * Path must exist before calling converge.
     */
    public boolean converge(TaskContext context, FileAttributesCache currentAttributes) {
        boolean systemModified = updateAttribute(
                context,
                "user ID",
                ownerId,
                () -> currentAttributes.getOrThrow().ownerId(),
                path::setOwnerId);

        systemModified |= updateAttribute(
                context,
                "group ID",
                groupId,
                () -> currentAttributes.getOrThrow().groupId(),
                path::setGroupId);

        systemModified |= updateAttribute(
                context,
                "permissions",
                permissions,
                () -> currentAttributes.getOrThrow().permissions(),
                path::setPermissions);

        return systemModified;
    }

    private <T> boolean updateAttribute(TaskContext context,
                                    String attributeName,
                                    Optional<T> wantedValue,
                                    Supplier<T> currentValueSupplier,
                                    Consumer<T> valueSetter) {
        if (wantedValue.isEmpty()) {
            return false;
        }

        T currentValue = currentValueSupplier.get();
        if (Objects.equals(currentValue, wantedValue.get())) {
            return false;
        }

        context.recordSystemModification(
                logger,
                String.format("Changing %s of %s from %s to %s",
                        attributeName,
                        path,
                        currentValue,
                        wantedValue.get()));

        valueSetter.accept(wantedValue.get());

        return true;
    }
}
