// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import ai.vespa.intellij.schema.model.Function;
import ai.vespa.intellij.schema.model.Schema;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A general tree in the "Call Hierarchy" window.
 *
 * @author Shahar Ariel
 */
public abstract class SdCallTreeStructure extends HierarchyTreeStructure {

    protected final String myScopeType;
    protected final SdFile myFile;
    protected Map<String, List<Function>> functionsMap;
    protected Map<String, Set<SdRankProfileDefinition>> ranksHeritageMap;
    
    public SdCallTreeStructure(Project project, PsiElement element, String currentScopeType) {
        super(project, new SdCallHierarchyNodeDescriptor(null, element, true));
        myScopeType = currentScopeType;
        myFile = (SdFile) element.getContainingFile();
        functionsMap = new Schema(myFile).functions();
        ranksHeritageMap = new HashMap<>();
    }
    
    protected abstract Set<Function> getChildren(SdFunctionDefinition element);
    
    @Override
    protected Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        final List<SdCallHierarchyNodeDescriptor> descriptors = new ArrayList<>();
        
        if (descriptor instanceof SdCallHierarchyNodeDescriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
            }
            boolean isCallable = SdHierarchyUtil.isExecutable(element);
            HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
            if (!isCallable || nodeDescriptor == null) {
                return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
            }
            
            Set<Function> children = getChildren((SdFunctionDefinition) element);
            
            Map<PsiElement, SdCallHierarchyNodeDescriptor> callerToDescriptorMap = new HashMap<>();
            PsiElement baseClass = PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
            
            for (Function caller : children) {
                if (isInScope(baseClass, caller.definition(), myScopeType)) {
                    SdCallHierarchyNodeDescriptor callerDescriptor = callerToDescriptorMap.get(caller);
                    if (callerDescriptor == null) {
                        callerDescriptor = new SdCallHierarchyNodeDescriptor(descriptor, caller.definition(), false);
                        callerToDescriptorMap.put(caller.definition(), callerDescriptor);
                        descriptors.add(callerDescriptor);
                    }
                }
            }
        }
        return ArrayUtil.toObjectArray(descriptors);
    }

}
