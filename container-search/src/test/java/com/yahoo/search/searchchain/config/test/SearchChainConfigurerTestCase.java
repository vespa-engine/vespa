// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.config.test;

import com.yahoo.config.search.IntConfig;
import com.yahoo.config.search.StringConfig;
import com.yahoo.container.core.config.HandlersConfigurerDi;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.searchchain.SearchChain;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.searchchain.SearcherRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 * @author gjoranv
 */
public class SearchChainConfigurerTestCase {

    private static final Random random = new Random(1);
    private static final String topCfgDir = System.getProperty("java.io.tmpdir") + File.separator +
            "SearchChainConfigurerTestCase" + File.separator;

    private static final String testDir = "src/test/java/com/yahoo/search/searchchain/config/test/";

    public void cleanup(File cfgDir) {
        if (cfgDir.exists()) {
            for (File f : cfgDir.listFiles()) {
                f.delete();
            }
            cfgDir.delete();
        }
    }

    @BeforeAll
    public static void createDefaultComponentsConfigs() throws IOException {
        createComponentsConfig(testDir + "chains.cfg", testDir + "handlers.cfg", testDir + "components.cfg");
    }

    @AfterAll
    public static void removeDefaultComponentsConfigs() {
        new File(testDir + "components.cfg").delete();
    }

    private SearchChainRegistry getSearchChainRegistryFrom(HandlersConfigurerTestWrapper configurer) {
        return ((SearchHandler)configurer.getRequestHandlerRegistry().
                getComponent("com.yahoo.search.handler.SearchHandler")).getSearchChainRegistry();
    }

    @Test
    synchronized void testConfiguration() {
        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + testDir);

        SearchChain simple = getSearchChainRegistryFrom(configurer).getComponent("simple");
        assertNotNull(simple);
        assertEquals(List.of(1, 2, 3), getSearcherNumbers(simple));

        SearchChain child1 = getSearchChainRegistryFrom(configurer).getComponent("child:1");
        assertEquals(List.of(1, 2, 4, 5, 7, 8), getSearcherNumbers(child1));

        SearchChain child2 = getSearchChainRegistryFrom(configurer).getComponent("child");
        assertEquals(List.of(3, 6, 7, 9), getSearcherNumbers(child2));

        // Verify successful loading of an explicitly declared searcher that takes no user-defined configs.
        // assertNotNull(SearchChainRegistry.get().getSearcherRegistry().getComponent
        //        ("com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase$DeclaredTestSearcher"));
        configurer.shutdown();
    }

    private List<Integer> getSearcherNumbers(SearchChain chain) {
        List<Integer> numbers = new ArrayList<>();
        for (int i=0; i<chain.searchers().size(); i++) {
            String prefix=TestSearcher.class.getName();
            assertTrue(chain.searchers().get(i).getId().getName().startsWith(prefix));
            int value = Integer.parseInt(chain.searchers().get(i).getId().getName().substring(prefix.length()));
            numbers.add(value);
        }
        Collections.sort(numbers);
        return numbers;
    }

   public static abstract class TestSearcher extends Searcher {
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }
    public static final class TestSearcher1 extends TestSearcher {}
    public static final class TestSearcher2 extends TestSearcher {}
    public static final class TestSearcher3 extends TestSearcher {}
    public static final class TestSearcher4 extends TestSearcher {}
    public static final class TestSearcher5 extends TestSearcher {}
    public static final class TestSearcher6 extends TestSearcher {}
    public static final class TestSearcher7 extends TestSearcher {}
    public static final class TestSearcher8 extends TestSearcher {}
    public static final class TestSearcher9 extends TestSearcher {}
    public static final class DeclaredTestSearcher extends TestSearcher {}

    @Test
    void testConfigurableSearcher() {
        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + testDir);

        SearchChain configurable = getSearchChainRegistryFrom(configurer).getComponent("configurable");
        assertNotNull(configurable);

        Searcher s = configurable.searchers().get(0);
        assertTrue(s instanceof ConfigurableSearcher);
        ConfigurableSearcher searcher = (ConfigurableSearcher) s;
        assertEquals(7, searcher.intConfig.intVal(), "Value from int.cfg file");
        assertEquals("com.yahoo.search.searchchain.config.test", searcher.stringConfig.stringVal(), "Value from string.cfg file");
        configurer.shutdown();
    }

    /**
     * Verifies that only searchers with updated config are re-instantiated after a config update
     * that does not contain any bootstrap configs.
     */
    @Test
    void testSearcherConfigUpdate() throws IOException {
        File cfgDir = getCfgDir();
        copyFile(testDir + "handlers.cfg", cfgDir +  "/handlers.cfg");
        copyFile(testDir + "qr-search.cfg", cfgDir +  "/qr-search.cfg");
        copyFile(testDir + "qr-searchers.cfg", cfgDir +  "/qr-searchers.cfg");
        copyFile(testDir + "index-info.cfg", cfgDir +  "/index-info.cfg");
        copyFile(testDir + "schema-info.cfg", cfgDir +  "/schema-info.cfg");
        copyFile(testDir + "specialtokens.cfg", cfgDir +  "/specialtokens.cfg");
        copyFile(testDir + "three-searchers.cfg", cfgDir +  "/chains.cfg");
        copyFile(testDir + "container-http.cfg", cfgDir +  "/container-http.cfg");
        createComponentsConfig(testDir + "three-searchers.cfg", testDir + "handlers.cfg", cfgDir +  "/components.cfg");
        printFile(new File(cfgDir + "/int.cfg"), "intVal 16\n");
        printFile(new File(cfgDir + "/string.cfg"), "stringVal \"testSearcherConfigUpdate\"\n");

        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + cfgDir);
        SearcherRegistry searchers = getSearchChainRegistryFrom(configurer).getSearcherRegistry();
        assertEquals(3, searchers.getComponentCount());

        IntSearcher intSearcher = (IntSearcher) searchers.getComponent(IntSearcher.class.getName());
        assertEquals(16, intSearcher.intConfig.intVal());
        StringSearcher stringSearcher = (StringSearcher) searchers.getComponent(StringSearcher.class.getName());
        DeclaredTestSearcher noConfigSearcher =
                (DeclaredTestSearcher) searchers.getComponent(DeclaredTestSearcher.class.getName());

        // Update int config for IntSearcher,
        printFile(new File(cfgDir + "/int.cfg"), "intVal 17\n");
        configurer.reloadConfig();

        // Registry is rebuilt
        assertNotEquals(searchers, getSearchChainRegistryFrom(configurer).getSearcherRegistry());
        searchers = getSearchChainRegistryFrom(configurer).getSearcherRegistry();
        assertEquals(3, searchers.getComponentCount());

        // Searcher with updated config is re-instantiated.
        IntSearcher intSearcher2 = (IntSearcher) searchers.getComponent(IntSearcher.class.getName());
        assertNotSame(intSearcher, intSearcher2);
        assertEquals(17, intSearcher2.intConfig.intVal());

        // Searchers with unchanged config (or that takes no config) are the same as before.
        Searcher s = searchers.getComponent(DeclaredTestSearcher.class.getName());
        assertSame(noConfigSearcher, s);
        s = searchers.getComponent(StringSearcher.class.getName());
        assertSame(stringSearcher, s);

        configurer.shutdown();
        cleanup(cfgDir);
    }

    /**
     * Updates the chains config, while the searcher configs are unchanged.
     * Verifies that a new searcher that was not in the old config is instantiated,
     * and that a searcher that has been removed from the configuration is not in the new registry.
     */
    @Test
    void testChainsConfigUpdate() throws IOException {
        File cfgDir = getCfgDir();
        copyFile(testDir + "handlers.cfg", cfgDir +  "/handlers.cfg");
        copyFile(testDir + "qr-search.cfg", cfgDir +  "/qr-search.cfg");
        copyFile(testDir + "qr-searchers.cfg", cfgDir +  "/qr-searchers.cfg");
        copyFile(testDir + "index-info.cfg", cfgDir +  "/index-info.cfg");
        copyFile(testDir + "schema-info.cfg", cfgDir +  "/schema-info.cfg");
        copyFile(testDir + "specialtokens.cfg", cfgDir +  "/specialtokens.cfg");
        copyFile(testDir + "chainsConfigUpdate_1.cfg", cfgDir +  "/chains.cfg");
        copyFile(testDir + "container-http.cfg", cfgDir +  "/container-http.cfg");
        createComponentsConfig(testDir + "chainsConfigUpdate_1.cfg", testDir + "handlers.cfg", cfgDir +  "/components.cfg");

        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper("dir:" + cfgDir);

        SearchChainRegistry scReg = getSearchChainRegistryFrom(configurer);
        SearcherRegistry searchers = scReg.getSearcherRegistry();
        assertEquals(2, searchers.getComponentCount());
        assertTrue(searchers.getComponent(IntSearcher.class.getName()) instanceof IntSearcher);
        assertTrue(searchers.getComponent(StringSearcher.class.getName()) instanceof StringSearcher);
        assertNull(searchers.getComponent(ConfigurableSearcher.class.getName()));
        assertNull(searchers.getComponent(DeclaredTestSearcher.class.getName()));

        IntSearcher intSearcher = (IntSearcher) searchers.getComponent(IntSearcher.class.getName());

        // Update chains config
        copyFile(testDir + "chainsConfigUpdate_2.cfg", cfgDir +  "/chains.cfg");
        createComponentsConfig(testDir + "chainsConfigUpdate_2.cfg", testDir + "handlers.cfg", cfgDir +  "/components.cfg");
        configurer.reloadConfig();

        assertNotEquals(scReg, getSearchChainRegistryFrom(configurer));

        // In the new registry, the correct searchers are removed and added
        assertNotEquals(searchers, getSearchChainRegistryFrom(configurer).getSearcherRegistry());
        searchers = getSearchChainRegistryFrom(configurer).getSearcherRegistry();
        assertEquals(3, searchers.getComponentCount());
        assertSame(intSearcher, searchers.getComponent(IntSearcher.class.getName()));
        assertTrue(searchers.getComponent(ConfigurableSearcher.class.getName()) instanceof ConfigurableSearcher);
        assertTrue(searchers.getComponent(DeclaredTestSearcher.class.getName()) instanceof DeclaredTestSearcher);
        assertNull(searchers.getComponent(StringSearcher.class.getName()));
        configurer.shutdown();
        cleanup(cfgDir);
    }

    public static class ConfigurableSearcher extends Searcher {
        IntConfig intConfig;
        StringConfig stringConfig;

        public ConfigurableSearcher(IntConfig intConfig) {
            this.intConfig = intConfig;
        }
        public ConfigurableSearcher(IntConfig intConfig, StringConfig stringConfig) {
            this.intConfig = intConfig;
            this.stringConfig = stringConfig;
        }
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }

    public static class IntSearcher extends Searcher {
        IntConfig intConfig;
        public IntSearcher(IntConfig intConfig) {
            this.intConfig = intConfig;
        }
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }

    public static class StringSearcher extends Searcher {
        StringConfig stringConfig;
        public StringSearcher(StringConfig stringConfig) {
            this.stringConfig = stringConfig;
        }
        @Override
        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }
    }


    //// Helper methods

    public static void printFile(File f, String content) throws IOException {
        OutputStream out = new FileOutputStream(f);
        out.write(content.getBytes());
        out.close();

    }

    /**
     * Copies src file to dst file. If the dst file does not exist, it is created.
     */
    public static void copyFile(String srcName, String dstName) throws IOException {
        InputStream src = new FileInputStream(srcName);
        OutputStream dst = new FileOutputStream(dstName);
        byte[] buf = new byte[1024];
        int len;
        while ((len = src.read(buf)) > 0) {
            dst.write(buf, 0, len);
        }
        src.close();
        dst.close();
    }

    public static File getCfgDir() {
        String token = Long.toHexString(random.nextLong());
        File cfgDir = new File(topCfgDir + File.separator + token + File.separator);
        cfgDir.mkdirs();
        return cfgDir;
    }

    /**
     * Copies the ids from the 'search' array in chains to a 'components' array in a new components file.
     * Also adds the default SearchHandler.
     */
    public static void createComponentsConfig(String chainsFile, String handlersFile, String componentsFile) throws IOException {
        createComponentsConfig(handlersFile, componentsFile, "handler", false);
        createComponentsConfig(chainsFile, componentsFile, "components", true);
    }

    /**
     * Copies the component ids from another config, e.g. 'handlers' to a 'components' array in a new components file,
     * to avoid a manually written 'components' file for tests where the bundle spec is given by the component id.
     *
     * @param configFile  Full path to the original config file, e.g. 'handlers'
     * @param componentsFile  Full path to the new 'components' file
     * @param componentType   The type of component, e.g. 'handler'
     * @param append  'true' will append to an already existing 'componentsFile'
     */
    public static void createComponentsConfig(String configFile,
                                              String componentsFile,
                                              String componentType,
                                              boolean append) throws IOException {
        StringBuilder buf = new StringBuilder();
        String line;
        int i = 0;
        if (append) {
            Pattern p = Pattern.compile("^[a-z]+" + "\\[\\d+\\]\\.id (.+)");
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                                                       new FileInputStream(componentsFile), StandardCharsets.UTF_8));
            while ((line = reader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches() && !m.group(1).equals(HandlersConfigurerDi.RegistriesHack.class.getName())) {
                    buf.append("components[").append(i).append("].id ").append(m.group(1)).append("\n");
                    i++;
                }
            }
            reader.close();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8));
        Pattern component = Pattern.compile("^" + componentType + "\\[\\d+\\]\\.id (.+)");
        while ((line = reader.readLine()) != null) {
            Matcher m = component.matcher(line);
            if (m.matches()) {
                buf.append("components[").append(i).append("].id ").append(m.group(1)).append("\n");
                i++;
            }
        }
        reader.close();

        buf.append("components[").append(i++).append("].id ").append(HandlersConfigurerDi.RegistriesHack.class.getName()).append("\n");
        if (componentType.equals("components"))
            i = addStandardComponents(i, buf);
        buf.insert(0, "components["+i+"]\n");

        Writer writer = new OutputStreamWriter(new FileOutputStream(componentsFile), StandardCharsets.UTF_8);
        writer.write(buf.toString());
        writer.flush();
        writer.close();
    }

    private static int addStandardComponents(int i, StringBuilder builder) {
        addComponent(ExecutionFactory.class.getName(), i++, builder);
        return i;
    }

    private static void addComponent(String component, int i, StringBuilder builder) {
        builder.append("components[").append(i).append("].id ").append(component).append("\n");
    }

}
