FROM --platform=$BUILDPLATFORM alpine:3.22 AS download

ARG CAMUNDA_VERSION=7.24.0
ARG CAMUNDA_SHA256=b4bc0583ad992718629df34e54e7deb256d39c3fe45b46362c35fd80004ed634

RUN wget -q -O /tmp/camunda-bpm-run.tar.gz \
      "https://downloads.camunda.cloud/release/camunda-bpm/run/${CAMUNDA_VERSION%.*}/camunda-bpm-run-${CAMUNDA_VERSION}.tar.gz" \
    && echo "${CAMUNDA_SHA256}  /tmp/camunda-bpm-run.tar.gz" | sha256sum -c - \
    && mkdir /camunda \
    && tar xzf /tmp/camunda-bpm-run.tar.gz -C /camunda

COPY camunda.sh /camunda/camunda.sh
RUN chmod +x /camunda/camunda.sh


FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.source="https://github.com/bpm-crafters/process-engine-adapters-camunda-7"
LABEL org.opencontainers.image.description="Camunda 7 Run Community Edition, built from the official distribution"
LABEL org.opencontainers.image.licenses="Apache-2.0"

COPY --from=download --chown=1000:1000 /camunda /camunda
WORKDIR /camunda
USER 1000:1000

EXPOSE 8080
CMD ["./camunda.sh"]
