# Smarticulous — Academic Grading System

A Java/SQLite educational backend for managing users, exercises, questions, graded submissions, and retrieving latest/best submissions. Reworked from an Advanced Programming course project for portfolio presentation.

## Technology

Java 8+ · JDBC · SQLite · Gradle · JUnit 4 · PBKDF2-HMAC-SHA256

## Build and test

Requires Java 17 and Gradle 7.6.4 (or an equivalent supported setup):

```sh
gradle test
```

The repository deliberately omits university testing JARs. The self-contained `SmarticulousTest` suite uses an in-memory SQLite database. CI runs the same tests on Ubuntu.

## Design

- SQLite tables: `User`, `Exercise`, `Question`, `Submission`, `QuestionGrade`
- Prepared statements and parameter binding
- Transactional exercise and submission writes
- Generated submission IDs and fixed integer question IDs (1-based)
- Latest submission by timestamp; best submission by total points earned
- Salted PBKDF2 password hashes, constant-time comparison

## Important limitations

- **Not production-ready.** This is a course-derived backend component rather than a complete deployed service; there is no REST API or UI.
- Databases created with the original assignment's plaintext passwords are **not automatically migrated**. Existing users must reset their passwords using the updated `addOrUpdateUser` method or use an audited migration workflow before switching to this version.
- Existing databases made by the old implementation may have incomplete or malformed question IDs and submissions, requiring data cleanup/migration. Back up those databases before opening them with new application code.
- Confirm source attribution and collaborators' contributions before publishing or assigning an open-source license.

## Authorship

Adapted from university coursework; attribution and public licensing to be determined. No explicit license is granted.
