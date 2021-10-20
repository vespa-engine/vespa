package org.intellij.sdk.language.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is used for the extension (in plugin.xml) to the class SdStructureViewModel. It make the IDE use our 
 * plugin's code when creating the "Structure View" window.
 * @author shahariel
 */
public class SdStructureViewFactory implements PsiStructureViewFactory {

    @Nullable
    @Override
    public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @NotNull
            @Override
            public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new SdStructureViewModel(psiFile);
            }
        };
    }

}
