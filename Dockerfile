FROM openjdk:12-jdk-alpine
WORKDIR /usr/src/app
EXPOSE 8080
CMD [ "java","-jar","myapp.jar" ]
COPY target/*-jar-with-dependencies.jar myapp.jar
