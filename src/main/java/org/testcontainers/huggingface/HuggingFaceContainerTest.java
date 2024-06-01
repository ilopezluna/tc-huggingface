package org.testcontainers.huggingface;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.MountableFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HuggingFaceContainerTest {
    public static void main(String[] args) throws IOException, InterruptedException {

        OllamaContainer ollama = new OllamaContainer("ollama/ollama:0.1.26")
                .withCopyFileToContainer(MountableFile.forClasspathResource("bge-small-en-v1.5-q4_k_m.gguf"), "/")
                .withCopyFileToContainer(MountableFile.forClasspathResource("Modelfile"), "/");
        ollama.start();
        ollama.execInContainer("ollama", "create", "example", "-f", "Modelfile");
        try {
            HttpURLConnection connection = getHttpURLConnection(ollama);
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            InputStream responseStream = connection.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(responseStream));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            System.out.println("Response: " + content);

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        ollama.stop();
    }

    private static @NotNull HttpURLConnection getHttpURLConnection(OllamaContainer ollama) throws IOException {
        URL url = new URL(ollama.getEndpoint() + "/api/embeddings");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        String jsonInputString = "{ \"model\": \"example:latest\", \"prompt\": \"Llamas are members of the camelid family\" }";
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }
}