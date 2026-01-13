FROM gradle:6.9.2-jdk11-alpine AS build
WORKDIR /app
#COPY . ./

# Copy the Gradle configuration files first to leverage Docker cache
# This helps Docker reuse the dependency download step if only source changes
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Download dependencies (this step is cached efficiently)
RUN gradle dependencies --no-daemon --stacktrace

# Copy the rest of the source code
COPY src src

RUN gradle clean bootJar --info

FROM openjdk:11.0.4-jre-slim
COPY --from=build /app/build/libs/invest-robot-*.jar application.jar
ENTRYPOINT exec java $JAVA_OPTS -jar application.jar
