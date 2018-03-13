package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Template;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

public class PromptContainerData {

    private static final Path promptPath = Paths.get("etc/profile.d/prompt.sh");
    private static final String templateString = "# Make sure we get UTC/GMT all over\n" +
                                                 "export TZ=UTC\n" +
                                                 "\n" +
                                                 "# Skip the rest for non-interactice shells\n" +
                                                 "[ -z \"$PS1\" ] && return\n" +
                                                 "\n" +
                                                 "# Check the window size after each command and, if necessary,\n" +
                                                 "# Update the values of LINES and COLUMNS.\n" +
                                                 "shopt -s checkwinsize\n" +
                                                 "\n" +
                                                 "# Colors; see https://wiki.archlinux.org/index.php/Color_Bash_Prompt\n" +
                                                 "color_off='\\[\\e[0m\\]'       # Text Reset\n" +
                                                 "color_bold='\\[\\e[1m\\]'      # Bold text\n" +
                                                 "\n" +
                                                 "env_colour=#if($zone.getSystem() == \"main\")#if($zone.getEnvironment() == \"prod\")'\\e[0;91m'#else'\\e[0;33m'#end#else$green#end\n" +
                                                 "\n" +
                                                 "\n" +
                                                 "PS1=\"${env_colour}$zone.getRegion().toUpperCase()${color_off} [\\u@${color_bold}\\h${color_off}:\\w]\\$ \"\n" +
                                                 "\n" +
                                                 "# Fix colors\n" +
                                                 "if type dircolors > /dev/null 2>&1; then\n" +
                                                 "    eval $(dircolors -b)\n" +
                                                 "fi\n" +
                                                 "\n" +
                                                 "# Make PS1 available in sub-shells\n" +
                                                 "export PS1\n" +
                                                 "\n";

    private final String renderedString;

    public PromptContainerData(Environment environment) {
        renderedString = Template.of(templateString)
                .set("zone", environment)
                .render();
    }

    public void writeTo(ContainerData containerData) {
        writeTo(containerData::addFile);
    }

    void writeTo(BiConsumer<Path, String> fileWriter) {
        fileWriter.accept(promptPath, renderedString);
    }

}
