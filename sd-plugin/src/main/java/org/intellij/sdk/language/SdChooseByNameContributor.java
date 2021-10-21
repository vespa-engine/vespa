// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.intellij.sdk.language.psi.SdDeclaration;
import org.intellij.sdk.language.psi.SdFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class is used for the extension (in plugin.xml) to enable "Go To Symbol" feature.
 * @author shahariel
 */
public class SdChooseByNameContributor implements ChooseByNameContributor {
    
    @Override
    public String @NotNull [] getNames(Project project, boolean includeNonProjectItems) {
        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance().getContainingFiles(
            FileTypeIndex.NAME,
            SdFileType.INSTANCE,
            GlobalSearchScope.allScope(project)
        );
        
        List<SdDeclaration> declarations = new ArrayList<>();
        
        for (VirtualFile file : virtualFiles) {
            SdFile sdFile = (SdFile) PsiManager.getInstance(project).findFile(file);
            declarations.addAll(SdUtil.findDeclarations(sdFile));
        }
        
        List<String> names = new ArrayList<>(declarations.size());
        for (SdDeclaration declaration : declarations) {
            names.add(declaration.getName());
        }
        return names.toArray(new String[names.size()]);
    }
    
    @Override
    public NavigationItem @NotNull [] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
        Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance().getContainingFiles(
            FileTypeIndex.NAME,
            SdFileType.INSTANCE,
            GlobalSearchScope.allScope(project)
        );
        
        List<SdDeclaration> declarations = new ArrayList<>();
        
        for (VirtualFile file : virtualFiles) {
            SdFile sdFile = (SdFile) PsiManager.getInstance(project).findFile(file);
            declarations.addAll(SdUtil.findDeclarationsByName(sdFile, name));
        }
        
        return declarations.toArray(new NavigationItem[declarations.size()]);
    }
    
}
