package ai.vespa.search.llm.interfaces;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;
import ai.vespa.search.llm.LocalLlmInterfaceConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalLLMInterfaceTest {

    private static String model = "/Users/lesters/dev/openai/models/mistral-7b-instruct-v0.1.Q8_0.gguf";
    private static Prompt prompt = StringPrompt.from("Why are ducks better than cats? Be concise, " +
            "but use the word 'spoon' somewhere in your answer.");

    @Test
    public void testGeneration() {
        var result = createLLM(model).complete(prompt, defaultOptions());
        assertEquals(Completion.FinishReason.stop, result.get(0).finishReason());
        assertTrue(result.get(0).text().contains("spoon"));
    }

    @Test
    public void testAsyncGeneration() {
        var executor = Executors.newFixedThreadPool(1);
        var sb = new StringBuilder();
        Prompt prompt = StringPrompt.from("sddocname: passage\n" +
                "id: 2\n" +
                "text: Essay on The Manhattan Project - The Manhattan Project The Manhattan Project was to see if making an atomic bomb possible. The success of this project would forever change the world forever making it known that something this powerful can be manmade.\n" +
                "documentid: id:msmarco:passage::2\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 0\n" +
                "text: The presence of communication amid scientific minds was equally important to the success of the Manhattan Project as scientific intellect was. The only cloud hanging over the impressive achievement of the atomic researchers and engineers is what their success truly meant; hundreds of thousands of innocent lives obliterated.\n" +
                "documentid: id:msmarco:passage::0\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 7\n" +
                "text: Manhattan Project. The Manhattan Project was a research and development undertaking during World War II that produced the first nuclear weapons. It was led by the United States with the support of the United Kingdom and Canada. From 1942 to 1946, the project was under the direction of Major General Leslie Groves of the U.S. Army Corps of Engineers. Nuclear physicist Robert Oppenheimer was the director of the Los Alamos Laboratory that designed the actual bombs. The Army component of the project was designated the\n" +
                "documentid: id:msmarco:passage::7\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 3\n" +
                "text: The Manhattan Project was the name for a project conducted during World War II, to develop the first atomic bomb. It refers specifically to the period of the project from 194 … 2-1946 under the control of the U.S. Army Corps of Engineers, under the administration of General Leslie R. Groves.\n" +
                "documentid: id:msmarco:passage::3\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 9\n" +
                "text: One of the main reasons Hanford was selected as a site for the Manhattan Project's B Reactor was its proximity to the Columbia River, the largest river flowing into the Pacific Ocean from the North American coast.\n" +
                "documentid: id:msmarco:passage::9\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 5\n" +
                "text: The Manhattan Project. This once classified photograph features the first atomic bomb — a weapon that atomic scientists had nicknamed Gadget.. The nuclear age began on July 16, 1945, when it was detonated in the New Mexico desert.\n" +
                "documentid: id:msmarco:passage::5\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 8\n" +
                "text: In June 1942, the United States Army Corps of Engineersbegan the Manhattan Project- The secret name for the 2 atomic bombs.\n" +
                "documentid: id:msmarco:passage::8\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 1\n" +
                "text: The Manhattan Project and its atomic bomb helped bring an end to World War II. Its legacy of peaceful uses of atomic energy continues to have an impact on history and science.\n" +
                "documentid: id:msmarco:passage::1\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 6\n" +
                "text: Nor will it attempt to substitute for the extraordinarily rich literature on the atomic bombs and the end of World War II. This collection does not attempt to document the origins and development of the Manhattan Project.\n" +
                "documentid: id:msmarco:passage::6\n" +
                "\n" +
                "sddocname: passage\n" +
                "id: 4\n" +
                "text: versions of each volume as well as complementary websites. The first website–The Manhattan Project: An Interactive History–is available on the Office of History and Heritage Resources website, http://www.cfo. doe.gov/me70/history. The Office of History and Heritage Resources and the National Nuclear Security\n" +
                "documentid: id:msmarco:passage::4\n" +
                "\n" +
                "\n" +
//                "Given the documents above, what was the id of the last passage given?");
                "Rank the documents above according to their relevance to the Manhattan Project. Answer using a json structure.");
        try {
            var future = createLLM(model, executor).completeAsync(prompt, defaultOptions(), completion -> {
                sb.append(completion.text());
                System.out.println(completion.text());
            }).exceptionally(exception -> Completion.FinishReason.error);

            assertFalse(future.isDone());
            var reason = future.join();
            assertTrue(future.isDone());
            assertNotEquals(reason, Completion.FinishReason.error);
        } finally {
            executor.shutdownNow();
        }
        System.out.println(sb);
        assertTrue(sb.toString().contains("spoon"));
    }

    private static InferenceParameters defaultOptions() {
        final Map<String, String> options = Map.of(
                "temperature", "0.0",
                "npredict", "10"
        );
        return new InferenceParameters(options::get);
    }

    private static LocalLLMInterface createLLM(String modelPath) {
        var config = new LocalLlmInterfaceConfig.Builder().llmfile(modelPath).build();
        return new LocalLLMInterface(config);
    }

    private static LocalLLMInterface createLLM(String modelPath, ExecutorService executor) {
        var config = new LocalLlmInterfaceConfig.Builder().llmfile(modelPath).build();
        return new LocalLLMInterface(config, executor);
    }
}
