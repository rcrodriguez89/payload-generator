#!/usr/bin/env bash
set -euo pipefail

./gradlew clean compileJava
./gradlew test
./gradlew test jacocoTestReport jacocoTestCoverageVerification
./gradlew checkstyleMain checkstyleTest
./gradlew spotbugsMain spotbugsTest
./gradlew pmdMain pmdTest

echo "Pipeline local OK"
