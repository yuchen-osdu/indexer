FROM azul/zulu-openjdk:17

WORKDIR /app

ARG PROVIDER_NAME
ENV PROVIDER_NAME core-plus
ENV JAVA_TOOL_OPTIONS=""

ARG PORT
ENV PORT $PORT

# Copy plugins
COPY tmp/os-oqm-rabbitmq-*.jar plugins/oqm-rabbitmq.jar
COPY tmp/apd-openid-*.jar plugins/apd-openid.jar
COPY otel/opentelemetry-javaagent.jar /app/telemetry/opentelemetry-javaagent.jar

# Copy the jar to the production image from the builder stage.
COPY indexer-${PROVIDER_NAME}/target/indexer-${PROVIDER_NAME}-*-spring-boot.jar indexer-${PROVIDER_NAME}.jar

# Add a non-root user
RUN groupadd -g 10001 -r nonroot \
  && useradd -g 10001 -r -u 10001 nonroot

# Run as non-root user
USER 10001:10001

# Run the web service on container startup.
#--add-opens args used to open modules and allow illegal(reflection\private classes and fields) access from the code.
CMD java  $JAVA_TOOL_OPTIONS \
          --add-opens java.base/java.lang=ALL-UNNAMED \
          --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
          -Djava.security.egd=indexer:/dev/./urandom \
          -Dloader.path=plugins/ \
          -Dserver.port=${PORT} \
          -Dlog4j.formatMsgNoLookups=true \
          -Dloader.main=org.opengroup.osdu.indexer.IndexerCorePlusApplication \
          -jar /app/indexer-${PROVIDER_NAME}.jar
