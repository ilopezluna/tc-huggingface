FROM ollama/ollama:0.1.26
RUN apt-get update && apt-get upgrade -y && apt-get install -y python3-pip
RUN pip install huggingface-hub
ENTRYPOINT ["/bin/ollama"]
CMD ["serve"]
