import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.huggingface.OllamaHuggingFaceContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static io.restassured.RestAssured.given;

public class HuggingFaceChatModelTest {

    @Test
    public void chatModelWithHuggingFace() {
        String repository = "DavidAU/DistiLabelOrca-TinyLLama-1.1B-Q8_0-GGUF";
        String model = "distilabelorca-tinyllama-1.1b.Q8_0.gguf";
        String imageName = "qa-model-from-hf";
        OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.44"));
        try {
            ollama.start();
        } catch (ContainerFetchException ex) {
            createImage(imageName, repository, model);
            ollama.start();
        }

        String response = given()
                .baseUri(ollama.getEndpoint())
                .header(new Header("Content-Type", "application/json"))
                .body(new CompletionRequest(model + ":latest", List.of(new Message("user", "What is the capital of France?")), false))
                .post("/api/chat")
                .getBody().as(ChatResponse.class).message.content;

        System.out.println("Response from LLM (🤖)-> " + response);
    }

    private static void createImage(String imageName, String repository, String model) {
        var hfModel = new OllamaHuggingFaceContainer.HuggingFaceModel(repository, model);
        var huggingFaceContainer = new OllamaHuggingFaceContainer(hfModel);
        huggingFaceContainer.start();
        huggingFaceContainer.commitToImage(imageName);
    }

    record CompletionRequest(String model, List<Message> messages, boolean stream) {
    }

    record Message(String role, String content) {
    }

    record ChatResponse(Message message) {
    }
}
