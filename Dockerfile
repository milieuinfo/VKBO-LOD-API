FROM openjdk:17-jdk-alpine
COPY target/vkbolodapi-0.0.2-SNAPSHOT.jar vkbolodapi-0.0.2-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/vkbolodapi-0.0.2-SNAPSHOT.jar"]
