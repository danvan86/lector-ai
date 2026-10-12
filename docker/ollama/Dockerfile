# The model image: Ollama with one model's weights baked in, run as the job's `services:` sidecar (concept 8.4), so no
# job downloads a model. The model is a build argument; the release publishes qwen2.5-coder:7b and qwen3-coder:30b.
#
#   docker build --build-arg MODEL=qwen2.5-coder:7b -t lector-ollama:qwen2.5-coder-7b docker/ollama
# The Ollama version is also the one the real-model test runs (`:cli:realModelTest` reads it from here).
FROM ollama/ollama:0.40.3
ARG MODEL=qwen2.5-coder:7b
# Pull through a server that runs only for this step. Fail if it doesn't come up within a minute.
RUN ollama serve > /tmp/ollama.log 2>&1 & \
    server=$!; \
    for attempt in $(seq 60); do ollama list > /dev/null 2>&1 && break; sleep 1; done; \
    ollama pull "$MODEL" && kill "$server"
# Keep the model in memory between the fix loop's attempts, which are minutes of Maven builds apart.
ENV OLLAMA_KEEP_ALIVE=-1
