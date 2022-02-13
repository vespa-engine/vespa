package ai.vespa.intellij.schema.psi;

import ai.vespa.intellij.schema.model.RankProfile;
import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.SdUtil;

/**
 * A function's declaration in the SD language.
 *
 * @author Shahar Ariel
 */
public interface SdFunctionDefinitionInterface extends SdDeclaration {

    default boolean isOverride() {
        String functionName = this.getName();
        SdRankProfileDefinition thisRankProfile = PsiTreeUtil.getParentOfType(this, SdRankProfileDefinition.class);
        if (thisRankProfile == null) return false;
        for (var parentProfile : new RankProfile(thisRankProfile, null).inherited().values()) {
            if (containsFunction(functionName, parentProfile.definition()))
                return true;
        }
        return false;
    }

    default boolean containsFunction(String functionName, SdRankProfileDefinition rankProfile) {
        if (SdUtil.functionsIn(new RankProfile(rankProfile, null)).containsKey(functionName))
            return true;
        for (var parentProfile : new RankProfile(rankProfile, null).inherited().values()) {
            if (containsFunction(functionName, parentProfile.definition()))
                return true;
        }
        return false;
    }

}
