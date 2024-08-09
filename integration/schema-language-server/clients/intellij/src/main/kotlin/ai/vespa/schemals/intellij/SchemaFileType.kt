package ai.vespa.schemals.intellij

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object SchemaFileType : LanguageFileType(SchemaLanguage) {
    override fun getName(): String {
        return "Schema"
    }

    override fun getDescription(): String {
        return "Schema file"
    }

    override fun getDefaultExtension(): String {
        return "sd"
    }

    override fun getIcon(): Icon {
        return IconLoader.getIcon("/icons/icon.svg", this.javaClass)
    }
}

object SchemaLanguage : Language("Schema") {
    private fun readResolve(): Any = SchemaLanguage
    override fun getDisplayName() = "Schema"
}
