# Smarticulous — Academic Grading System

Java/JDBC course project implementing a SQLite-backed academic grading system, with database operations for users, exercises, questions, submissions, and grades.

## Technology
Java 8 · SQLite · JDBC · SQL prepared statements · Gradle · JUnit 4

## Source structure
- `src/main/java/smarticulous/Smarticulous.java` — database operations and application logic
- `src/main/java/smarticulous/db/` — model classes
- `src/test/java/smarticulous/SmarticulousTest.java` — original course tests

## Getting started
This source package includes a Gradle build specification but omits the original course-specific testing libraries and the Gradle wrapper JAR. The original test suite may require the missing course libraries before it compiles or runs. Use a locally installed compatible version of Gradle and JDK to inspect the project; do not assume tests pass.

## Security and publishing status
**Educational coursework, not production-ready.** The original implementation stores passwords in plaintext; secure password hashing and revised authentication tests would be required before any real deployment. Before changing the repository from private to public, verify academic publication permissions, source attribution, and collaborators' contributions. Third-party and course-specific binary dependencies have been excluded pending licensing review.

No performance, security, or test-passing claims are made here.
