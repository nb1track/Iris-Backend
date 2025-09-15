# --- Stufe 1: Die Bau-Umgebung ---
# Wir verwenden ein offizielles Maven-Image mit Java 21, um den Code zu kompilieren.
FROM maven:3.9-eclipse-temurin-21 AS build

# Setze das Arbeitsverzeichnis im Container
WORKDIR /app

# Kopiere zuerst nur die pom.xml, um Mavens Caching zu nutzen
COPY pom.xml .

# Lade die Dependencies herunter
RUN mvn dependency:go-offline

# Kopiere den restlichen Source-Code
COPY src ./src

# Baue die Anwendung. Das Ergebnis ist eine .jar-Datei in /app/target/
# Wir überspringen die Tests, da sie idealerweise in einem separaten Schritt laufen.
RUN mvn clean package -DskipTests


# --- Stufe 2: Die Laufzeit-Umgebung ---
# Wir starten mit einem sehr kleinen, sicheren Java-Image, das nur zum Ausführen nötig ist.
FROM eclipse-temurin:21-jre-jammy

# Setze das Arbeitsverzeichnis im finalen Container
WORKDIR /app

# Kopiere NUR die gebaute .jar-Datei aus der "build"-Stufe in unseren finalen Container
# ACHTUNG: Der Name der .jar-Datei muss dem in deiner pom.xml entsprechen!
# Normalerweise ist das <artifactId>-<version>.jar
COPY --from=build /app/target/Iris-Backend-0.0.1-SNAPSHOT.jar app.jar

# Gib an, welchen Port die Anwendung im Container nach außen verfügbar macht (Standard bei Spring Boot ist 8080)
EXPOSE 8080

# Der Befehl, der beim Starten des Containers ausgeführt wird
ENTRYPOINT ["java", "-jar", "app.jar"]