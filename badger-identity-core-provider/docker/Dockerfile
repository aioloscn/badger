FROM openjdk:17-jdk-alpine
COPY target/badger-identity-core-provider.jar app.jar
ENV JAVA_OPTS=""    
ENTRYPOINT java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar