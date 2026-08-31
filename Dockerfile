FROM eclipse-temurin:25-jdk-alpine

WORKDIR /app
COPY lib/ lib/
COPY out/ out/

ENTRYPOINT ["java", "-cp", "/app/lib/*:/app/out", "--enable-preview", "org.gribok777.lab.Main"]
