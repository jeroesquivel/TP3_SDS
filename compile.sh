#!/usr/bin/env bash
# Compila todos los fuentes Java en src/ → out/
set -e

mkdir -p out
javac -d out -sourcepath src src/*.java
echo "Compilación exitosa. Binarios en out/"