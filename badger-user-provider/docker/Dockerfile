FROM openjdk:17-jdk-alpine
COPY target/badger-user-provider.jar app.jar
ENV JAVA_OPTS="-server -Xms256m -Xmx512m -Xmn256m"    
ENTRYPOINT java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar