FROM debian:stable-slim
WORKDIR /app
COPY audiobook-server ./
COPY static ./static
ENV MEDIA_ROOT=/media
ENV LISTEN_ADDR=0.0.0.0:8086
ENV GIN_MODE=release
RUN chmod +x ./audiobook-server
EXPOSE 8086
CMD ["./audiobook-server"]
