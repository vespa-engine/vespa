package ai.vespa.intellij.schema.psi;

import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.SdUtil;

/**
 * A function's declaration in the SD language.
 *
 * @author Shahar Ariel
 */
public interface SdFunctionDefinitionInterface extends SdDeclaration {

    default boolean isOverride() {
        String macroName = this.getName();
        
        SdRankProfileDefinition curRankProfile = PsiTreeUtil.getParentOfType(this, SdRankProfileDefinition.class);
        if (curRankProfile != null) {
            curRankProfile = (SdRankProfileDefinition) SdUtil.getRankProfileParent(curRankProfile);
        }
        while (curRankProfile != null) {
            for (SdFunctionDefinition macro : PsiTreeUtil.collectElementsOfType(curRankProfile, SdFunctionDefinition.class)) {
                if (macro.getName() != null && macro.getName().equals(macroName)) {
                    return true;
                }
            }
            curRankProfile = (SdRankProfileDefinition) SdUtil.getRankProfileParent(curRankProfile);
        }
        return false;
    }

}
