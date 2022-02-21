// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

/**
 * Usage finder base class.
 *
 * @author bratseth
 */
public abstract class UsageFinder {

    private final SearchScope scope;
    private final Processor<? super UsageInfo> processor;

    protected UsageFinder(SearchScope scope, Processor<? super UsageInfo> processor) {
        this.scope = scope;
        this.processor = processor;
    }

    protected SearchScope scope() { return scope; }
    protected Processor<? super UsageInfo> processor() { return processor; }

    /** Returns the schema logically containing this element. */
    protected Schema resolveSchema(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file.getVirtualFile().getPath().endsWith(".profile")) {
            Path schemaFile = Path.fromString(file.getVirtualFile().getParent().getPath() + ".sd");
            return ReadAction.compute(() -> Schema.fromProjectFile(element.getProject(), schemaFile));
        }
        else { // schema
            return ReadAction.compute(() -> Schema.fromProjectFile(element.getProject(), Path.fromString(file.getVirtualFile().getPath())));
        }
    }

}
