import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;

public class OllamaVisionModelTest {

    @Test
    public void visionModelWithOllama() throws IOException, InterruptedException {
        String imageName = "tc-ollama-moondream";
        OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.44"));
        try {
            ollama.start();
        } catch (ContainerFetchException ex) {
            // If image doesn't exist, create it. Subsequent runs will reuse the image.
            createImage(imageName);
            ollama.start();
        }

        var image = getImageInBase64();

        String response = given()
                .baseUri(ollama.getEndpoint())
                .header(new Header("Content-Type", "application/json"))
                .body(new CompletionRequest("moondream:latest", "Describe the image.", Collections.singletonList(image), false))
                .post("/api/generate")
                .getBody().as(CompletionResponse.class).response();

        System.out.println("Response from LLM (🤖)-> " + response);
    }

    public void createImage(String imageName) throws IOException, InterruptedException {
        OllamaContainer ollama = new OllamaContainer("ollama/ollama:0.1.44");
        ollama.start();
        ollama.execInContainer("ollama", "pull", "moondream");
        ollama.commitToImage(imageName);
    }

    private static String getImageInBase64() throws IOException {
        URL resourceUrl = OllamaVisionModelTest.class.getResource("/whale.jpeg");
        byte[] fileContent = FileUtils.readFileToByteArray(new File(resourceUrl.getFile()));
        return Base64.getEncoder().encodeToString(fileContent);
    }

    record CompletionRequest(String model, String prompt, List<String> images, boolean stream) {
    }

    record CompletionResponse(String response) {
    }
}
