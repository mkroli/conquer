FROM sbtscala/scala-sbt:eclipse-temurin-19.0.1_10_1.8.0_2.13.10 AS builder
ADD . /src
WORKDIR /src
RUN sbt clean "Universal / stage"

FROM eclipse-temurin:19
COPY --from=builder /src/target/universal/stage /opt/conquer
ENTRYPOINT /opt/conquer/bin/conquer
EXPOSE 8080
