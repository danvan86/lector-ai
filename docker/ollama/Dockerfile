# The model image: Ollama with one model's weights baked in, run as the job's `services:` sidecar (concept 8.4), so no
# job downloads a model. The model is a build argument; the release publishes qwen2.5-coder:7b and qwen3-coder:30b.
#
#   docker build --build-arg MODEL=qwen2.5-coder:7b -t lector-ollama:qwen2.5-coder-7b docker/ollama
#   docker build --build-arg MODEL=qwen3-coder:30b --build-arg GPU=true -t lector-ollama:qwen3-coder-30b docker/ollama
# The Ollama version is also the one the real-model test runs (`:cli:realModelTest` reads it from here).
FROM ollama/ollama:0.40.6 AS ollama

# The weights. Pulled through a server that runs only for this step. Fail if it doesn't come up within a minute.
FROM ollama AS model
ARG MODEL=qwen2.5-coder:7b
RUN ollama serve > /tmp/ollama.log 2>&1 & \
    server=$!; \
    for attempt in $(seq 60); do ollama list > /dev/null 2>&1 && break; sleep 1; done; \
    ollama pull "$MODEL" && kill "$server"

# Ollama's GPU libraries (CUDA, MLX, Vulkan) are 4.8 GB. A CPU image leaves them out: with them, pulling the 7B image
# takes about 17 GB, more than a standard runner has free, and the service container is pulled before any step could
# make room.
FROM ollama AS system
ARG GPU=false
RUN case "$GPU" in \
      true) ;; \
      false) rm -rf /usr/lib/ollama/cuda_* /usr/lib/ollama/mlx_* /usr/lib/ollama/vulkan ;; \
      *) echo "GPU must be true or false, not '$GPU'" >&2; exit 1 ;; \
    esac

# Ollama's filesystem without what the stage removed, and the weights in a layer of their own. The configuration is the
# base image's (docker image inspect ollama/ollama), plus OLLAMA_KEEP_ALIVE.
FROM scratch
COPY --from=system / /
COPY --from=model /root/.ollama /root/.ollama
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LD_LIBRARY_PATH=/usr/local/nvidia/lib:/usr/local/nvidia/lib64 \
    NVIDIA_DRIVER_CAPABILITIES=compute,utility \
    NVIDIA_VISIBLE_DEVICES=all \
    OLLAMA_HOST=0.0.0.0:11434 \
    # Keep the model in memory between the fix loop's attempts, which are minutes of Maven builds apart.
    OLLAMA_KEEP_ALIVE=-1
EXPOSE 11434
ENTRYPOINT ["/bin/ollama"]
CMD ["serve"]
