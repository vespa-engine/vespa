// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileWriter;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Keep a destination file in sync with a template file and dynamic inputs.
 *
 * @author hakonhall
 */
public class TemplateFileSync<T> {

    private final Path templatePath;
    private final TemplateDescriptor descriptor;
    private final Optional<Rendering<T>> rendering;
    private final FileWriter outputWriter;

    private Optional<Template> baseTemplate = Optional.empty();
    private Optional<T> previousInput = Optional.empty();
    private Optional<String> contentCache = Optional.empty();

    @FunctionalInterface
    public interface Rendering<U> {
        /**
         * @param oldInputs may be null (only) if null has been passed as {@code inputsOrNull} to
         *                  {@link #converge(TaskContext, Consumer, Object, BiConsumer)}.
         * @param newInputs may be null (only) if null has been passed as {@code inputsOrNull} to
         *                  {@link #converge(TaskContext, Consumer, Object, BiConsumer)}.
         * @return true if the new inputs will produce a different {@link Template#render()} than the old.
         */
        boolean shouldRender(U oldInputs, U newInputs);
    }

    /**
     * @param templatePath path to the template file, and read once on the first invocation of {@code converge()}.
     * @param descriptor   descriptor of the template
     * @param outputPath   the file to keep in sync.
     * @param rendering    used to decide whether a new set of inputs will produce a different render
     *                     than current/old.  May be null to always force rendering.
     */
    public TemplateFileSync(Path templatePath, TemplateDescriptor descriptor, Path outputPath,
                            Rendering<T> rendering) {
        this.templatePath = Objects.requireNonNull(templatePath);
        this.descriptor = new TemplateDescriptor(Objects.requireNonNull(descriptor));
        this.rendering = Optional.ofNullable(rendering);
        this.outputWriter = new FileWriter(Objects.requireNonNull(outputPath));
    }

    /** Returns the FileWriter used to write the output file.  Can be used to set various attributes. */
    public FileWriter outputFileWriter() { return outputWriter; }

    /**
     * Ensure the file at {@code outputPath} have correct content and attributes.
     *
     * <p>The first time this method is called, the {@code once} callback is invoked with the
     * mutable base template as an argument:  {@code once} should fill the base template
     * according to variables that do not change been invocations of {@code converge()}.</p>
     *
     * <p>If this is the first invocation, or the inputs would render different content via
     * {@code rendering.renderWillBeDifferent()}, the {@code fill} callback is invoked to
     * fill the template argument (which is a snapshot of the base template), before this method
     * renders the template and writes the file.</p>
     *
     * @param context task context
     * @param once    callback only invoked on the first invocation of this method.  It will
     *                then have an opportunity to change the passed-in base template, and whose
     *                snapshots will be passed to the {@code fill} callback.  A null {@code once}
     *                is ignored.
     * @param inputs  an instance containing all dynamic inputs that may change between invocations
     *                of this method.  If {@link #rendering}.{@link Rendering#shouldRender(Object, Object)}
     *                returns true when invoked on the previous successful converge and this
     *                invocation's inputs.
     * @param fill    invoked if the template needs to be rendered.  The arguments are a snapshot
     *                of the base template, and {@code inputs}.
     * @return true if the file was modified.
     */
    public boolean converge(TaskContext context, Consumer<Template> once, T inputs,
                            BiConsumer<Template, T> fill) {
        if (baseTemplate.isEmpty()) {
            var template = Template.at(templatePath, descriptor);
            if (once != null) once.accept(template);
            baseTemplate = Optional.of(template);
        }

        final String content;
        if (contentCache.isEmpty() || rendering.isEmpty() ||
            rendering.get().shouldRender(previousInput.orElse(null), inputs)) {
            if (fill == null) {
                content = contentCache.orElseGet(baseTemplate.get()::render);
            } else {
                Template snapshot = baseTemplate.get().snapshot();
                fill.accept(snapshot, inputs);
                content = snapshot.render();
            }
        } else {
            content = contentCache.get();
        }

        boolean modified = outputWriter.converge(context, content);
        previousInput = Optional.ofNullable(inputs);
        contentCache = Optional.of(content);
        return modified;
    }
}
