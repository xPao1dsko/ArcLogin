#!/bin/sh
# Requiere JDK 21 y Maven
mvn clean package && echo "Listo: target/ArcLogin.jar"
