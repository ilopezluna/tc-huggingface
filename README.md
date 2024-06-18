# tc-huggingface

## Description

Example project to demonstrate how to use Testcontainers to run Hugging Face models inside Ollama containers

## Examples

Here are the test classes in this project:

1. [Ollama vision model](src/test/java/OllamaVisionModelTest.java)
2. [Embedding model from Hugging Face](src/test/java/HuggingFaceEmbeddingModelTest.java)
3. [Chat model from Hugging Face](src/test/java/HuggingFaceChatModelTest.java)
4. [Vision model from Hugging Face](src/test/java/HuggingFaceVisionModelTest.java)

# Play-through

Recently Ollama announced that one can use models from Hugging Face in Ollama. 
It's a super exciting development because it shifts using rich ecosystem of AI/ML components from Hugging Face into hands of Ollama end-users, who are frequently developers.
To make this integration even more developer friendly we looked at "scripting" using Ollama by running in Docker containers via the Testcontainers API.
Testcontainers is available in several languages, including Python, but since the nature of the demo is to show using Hugging Face models by application developer who can be from different language ecosystems we chose Java.

Testcontainers libraries already [provide the Ollama module](https://testcontainers.com/modules/ollama/) so spinning up a container with Ollama is very straightforward even without knowing how to run Ollama in a Docker environment:

```java
import org.testcontainers.ollama.OllamaContainer;

var ollama = new OllamaContainer("ollama/ollama:0.1.44");
ollama.start();
```

Using a Testcontainers module not only gives us lifecycle API, and ability to programmatically obtain Ollama endpoint url, but also automatically include the GPU configuration when that is available. Here's a snippet from the `OllamaContainer` class:  
```java
Map<String, RuntimeInfo> runtimes = info.getRuntimes();
if (runtimes != null && runtimes.containsKey("nvidia")) {
    this.withCreateContainerCmdModifier((cmd) -> {
        cmd.getHostConfig().withDeviceRequests(Collections.singletonList((new DeviceRequest()).withCapabilities(Collections.singletonList(Collections.singletonList("gpu"))).withCount(-1)));
    });
}
```

Now running Ollama by itself is cool, but for any inference requests you need to provide a model.
There are a few options how you can do it. 
The most straightforward is to use a Docker image that pre-bakes Ollama with a model for example using Langchain4j's (like LangChain but for Java) images, like [langchain4j/ollama-phi](https://hub.docker.com/r/langchain4j/ollama-phi/tags). 

Another option is to execute a command in the running container to pull the image: 
```java
ollama.execInContainer("ollama", "pull", "moondream");
```

This of course changes the state of the container, so if you "accidentally" delete it, your program will need to recreate it from scratch. 
Conveniently, not only Testcontainers makes your program the single source of truth about the environment it runs in (because the environment is created from the program itself without relying on the pre-provisioned components), but also allows you to commit existing containers to a Docker image, so next time you're starting it, the filesystem changes are preserved, and the model will be available. 

```java
ollama.start();
ollama.execInContainer("ollama", "pull", "moondream");
ollama.commitToImage("ollama-with-moondream");
```

And you can encode using the changed Docker image into the setup code, so if the commited Docker image is available, it'll be used automatically: 

```java
var ollama = new OllamaContainer(DockerImageName.parse("tc-ollama-moondream")
                .asCompatibleSubstituteFor(OLLAMA_IMAGE_NAME)));
try {
    ollama.start();
} catch (ContainerFetchException ex) {
    createImageWithMoonDream();
    ollama.start();
}
```

So far so good, but it doesn't involve any Hugging Face models. Let's change that. 
To use a model from Hugging Face in Ollama you need: 
1. a `gguf` file for the model,
2. a `Modelfile` which describes how the model is to be used.

Conveniently, since we're writing code we can automate obtaining both of these.
In the `OllamaHuggingFaceContainer` class you can see, that if we specify the Hugging Face model, during the start of the container, we'll use `huggingface-cli` to pull the files: 

```java
ExecResult downloadModelFromHF = execInContainer(
    "huggingface-cli",
    "download",
    huggingFaceModel.repository,
    huggingFaceModel.model,
    "--local-dir",
    "."
);
```

And also create a modelfile, the contents of which you can either provide when configuring the `OllamaHuggingFaceContainer` or for simpler cases the default might work out of the box too. 
Here's the code snippet to create a container with a model and save the resulting Docker image for local use. Note that when satisfied with the results locally, you can always push that to your company's Docker registry later with `docker push $imagename`

```java
String repository = "DavidAU/DistiLabelOrca-TinyLLama-1.1B-Q8_0-GGUF";
String model = "distilabelorca-tinyllama-1.1b.Q8_0.gguf";
String imageName = "qa-model-from-hf";

var hfModel = new OllamaHuggingFaceContainer.HuggingFaceModel(repository, model);
try (var huggingFaceContainer = new OllamaHuggingFaceContainer(hfModel)) {
    huggingFaceContainer.start();
    huggingFaceContainer.commitToImage(imageName);
    huggingFaceContainer.stop();
}
```

Now in the `HuggingFaceVisionModelTest` class you can see a more complicated example of using a vision model to describe the image contents.
In this particular scenario the model consumes images as base64 string on the prompt. 

But all in all, once the Ollama container is set up, querying the LLM is a matter of sending a HTTP request: 

```java
 String response = given()
        .baseUri(ollama.getEndpoint())
        .header(new Header("Content-Type", "application/json"))
        .body(new CompletionRequest(model + ":latest", "Describe the image in max 10 words", List.of(image), false))
        .post("/api/generate")
        .getBody().as(CompletionResponse.class).response();

System.out.println("Response from LLM (🤖)-> " + response);
```

Now here are some advantages of this approach: 
* Developers get programmatic access to the Hugging Face ecosystem
* The configuration, from setup, to lifecycle, to querying stays in the code making the setup reproducible
  * by both different people on the team 
  * CI environment where you'll run tests
* The workflows use containers as the primitive and developers know and use containers already making AI/ML more familiar
* The other parts of the setup can also be provisioned similarly, for example vector databases and embedding models for RAG setups
* Using programmatic configuration allows creating libraries for particular models, and using libraries doesn't require intimate understanding how to actually prepare and serve a model in a container
* ML teams can provide `gguf` and the development/testing setups are automatic for developers 