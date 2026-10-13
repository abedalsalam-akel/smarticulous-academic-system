package smarticulous;

import smarticulous.db.Exercise;
import smarticulous.db.Submission;
import smarticulous.db.User;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** A small SQLite-backed academic grading system. */
public class Smarticulous {
    private Connection db;
    private static final int ITERATIONS = 310000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public Connection openDB(String dburl) throws SQLException {
        closeDB();
        db = DriverManager.getConnection(dburl);
        try (Statement st = db.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE IF NOT EXISTS User (UserId INTEGER PRIMARY KEY, Username TEXT UNIQUE NOT NULL, Firstname TEXT, Lastname TEXT, Password TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS Exercise (ExerciseId INTEGER PRIMARY KEY, Name TEXT, DueDate INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS Question (ExerciseId INTEGER NOT NULL, QuestionId INTEGER NOT NULL, Name TEXT, \"Desc\" TEXT, Points INTEGER NOT NULL, PRIMARY KEY (ExerciseId, QuestionId), FOREIGN KEY (ExerciseId) REFERENCES Exercise(ExerciseId))");
            st.execute("CREATE TABLE IF NOT EXISTS Submission (SubmissionId INTEGER PRIMARY KEY, UserId INTEGER NOT NULL, ExerciseId INTEGER NOT NULL, SubmissionTime INTEGER NOT NULL, FOREIGN KEY (UserId) REFERENCES User(UserId), FOREIGN KEY (ExerciseId) REFERENCES Exercise(ExerciseId))");
            st.execute("CREATE TABLE IF NOT EXISTS QuestionGrade (SubmissionId INTEGER NOT NULL, QuestionId INTEGER NOT NULL, Grade REAL NOT NULL, PRIMARY KEY (SubmissionId, QuestionId), FOREIGN KEY (SubmissionId) REFERENCES Submission(SubmissionId))");
        } catch (SQLException ex) {
            closeDB();
            throw ex;
        }
        return db;
    }

    public void closeDB() throws SQLException {
        if (db != null) {
            try { db.close(); } finally { db = null; }
        }
    }

    private Connection connection() throws SQLException {
        if (db == null || db.isClosed()) throw new SQLException("Database is not open");
        return db;
    }

    private interface Transaction<T> { T run() throws SQLException; }
    private <T> T transaction(Transaction<T> action) throws SQLException {
        Connection c = connection();
        boolean prior = c.getAutoCommit();
        if (!prior) throw new SQLException("Nested transaction not supported");
        c.setAutoCommit(false);
        try {
            T result = action.run();
            c.commit();
            return result;
        } catch (SQLException | RuntimeException ex) {
            try { c.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        } finally {
            c.setAutoCommit(prior);
        }
    }

    private static String hashPassword(String password) throws SQLException {
        if (password == null) throw new SQLException("Password must not be null");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return "pbkdf2_sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) { throw new SQLException("Unable to hash password", e); }
    }

    private static boolean matchesPassword(String provided, String stored) throws SQLException {
        if (provided == null || stored == null) return false;
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) return false;
        try {
            int rounds = Integer.parseInt(parts[1]);
            if (rounds < 100000 || rounds > 1000000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            KeySpec spec = new PBEKeySpec(provided.toCharArray(), salt, rounds, expected.length * 8);
            byte[] calculated = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(calculated, expected);
        } catch (Exception e) { return false; }
    }

    public int addOrUpdateUser(User user, String password) throws SQLException {
        if (user == null || user.username == null || user.username.isEmpty()) throw new SQLException("Invalid user");
        String hash = hashPassword(password);
        return transaction(() -> {
            try (PreparedStatement st = connection().prepareStatement("INSERT INTO User (Username, Firstname, Lastname, Password) VALUES (?, ?, ?, ?) ON CONFLICT(Username) DO UPDATE SET Firstname=excluded.Firstname, Lastname=excluded.Lastname, Password=excluded.Password")) {
                st.setString(1, user.username);
                st.setString(2, user.firstname);
                st.setString(3, user.lastname);
                st.setString(4, hash);
                st.executeUpdate();
            }
            try (PreparedStatement st = connection().prepareStatement("SELECT UserId FROM User WHERE Username=?")) {
                st.setString(1, user.username);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Missing user after insert");
                    return rs.getInt(1);
                }
            }
        });
    }

    public boolean verifyLogin(String username, String password) throws SQLException {
        try (PreparedStatement st = connection().prepareStatement("SELECT Password FROM User WHERE Username=?")) {
            st.setString(1, username);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && matchesPassword(password, rs.getString(1));
            }
        }
    }

    public int addExercise(Exercise exercise) throws SQLException {
        if (exercise == null || exercise.dueDate == null || exercise.questions == null) throw new SQLException("Invalid exercise");
        try (PreparedStatement exists = connection().prepareStatement("SELECT 1 FROM Exercise WHERE ExerciseId=?")) {
            exists.setInt(1, exercise.id);
            try (ResultSet rs = exists.executeQuery()) { if (rs.next()) return -1; }
        }
        return transaction(() -> {
            try (PreparedStatement st = connection().prepareStatement("INSERT INTO Exercise (ExerciseId, Name, DueDate) VALUES (?, ?, ?)")) {
                st.setInt(1, exercise.id);
                st.setString(2, exercise.name);
                st.setLong(3, exercise.dueDate.getTime());
                st.executeUpdate();
            }
            try (PreparedStatement st = connection().prepareStatement("INSERT INTO Question (ExerciseId, QuestionId, Name, \"Desc\", Points) VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < exercise.questions.size(); i++) {
                    Exercise.Question q = exercise.questions.get(i);
                    if (q.points <= 0) throw new SQLException("Question points must be positive");
                    st.setInt(1, exercise.id);
                    st.setInt(2, i + 1);
                    st.setString(3, q.name);
                    st.setString(4, q.desc);
                    st.setInt(5, q.points);
                    st.executeUpdate();
                }
            }
            return exercise.id;
        });
    }

    public List<Exercise> loadExercises() throws SQLException {
        List<Exercise> exercises = new ArrayList<>();
        try (PreparedStatement st = connection().prepareStatement("SELECT ExerciseId, Name, DueDate FROM Exercise ORDER BY ExerciseId");
             ResultSet rs = st.executeQuery()) {
            try (PreparedStatement qs = connection().prepareStatement("SELECT Name, \"Desc\", Points FROM Question WHERE ExerciseId=? ORDER BY QuestionId")) {
                while (rs.next()) {
                    Exercise ex = new Exercise(rs.getInt(1), rs.getString(2), new Date(rs.getLong(3)));
                    qs.setInt(1, ex.id);
                    try (ResultSet qr = qs.executeQuery()) {
                        while (qr.next()) ex.addQuestion(qr.getString(1), qr.getString(2), qr.getInt(3));
                    }
                    exercises.add(ex);
                }
            }
        }
        return exercises;
    }

    public int storeSubmission(Submission submission) throws SQLException {
        if (submission == null || submission.user == null || submission.exercise == null || submission.submissionTime == null || submission.questionGrades == null) throw new SQLException("Invalid submission");
        Exercise ex = submission.exercise;
        if (submission.questionGrades.length != ex.questions.size()) throw new SQLException("Grade/question count mismatch");
        for (int i = 0; i < ex.questions.size(); i++) {
            float grade = submission.questionGrades[i];
            if (!Float.isFinite(grade) || grade < 0 || grade > ex.questions.get(i).points) throw new SQLException("Invalid grade");
        }
        try (PreparedStatement usr = connection().prepareStatement("SELECT UserId FROM User WHERE Username=?")) {
            usr.setString(1, submission.user.username);
            try (ResultSet users = usr.executeQuery()) { if (!users.next()) return -1; }
        }
        return transaction(() -> {
            int uid;
            try (PreparedStatement usr = connection().prepareStatement("SELECT UserId FROM User WHERE Username=?")) {
                usr.setString(1, submission.user.username);
                try (ResultSet rs = usr.executeQuery()) {
                    if (!rs.next()) throw new SQLException("User no longer exists");
                    uid = rs.getInt(1);
                }
            }
            try (PreparedStatement exercise = connection().prepareStatement("SELECT 1 FROM Exercise WHERE ExerciseId=?")) {
                exercise.setInt(1, ex.id);
                try (ResultSet rs = exercise.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Exercise must be created before submission");
                }
            }
            int id;
            String sql = submission.id == -1
                    ? "INSERT INTO Submission (UserId, ExerciseId, SubmissionTime) VALUES (?, ?, ?)"
                    : "INSERT INTO Submission (UserId, ExerciseId, SubmissionTime, SubmissionId) VALUES (?, ?, ?, ?)";
            try (PreparedStatement st = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                st.setInt(1, uid);
                st.setInt(2, ex.id);
                st.setLong(3, submission.submissionTime.getTime());
                if (submission.id != -1) st.setInt(4, submission.id);
                st.executeUpdate();
                try (ResultSet keys = st.getGeneratedKeys()) {
                    if (keys.next()) id = keys.getInt(1);
                    else if (submission.id != -1) id = submission.id;
                    else throw new SQLException("Missing generated submission ID");
                }
            }
            try (PreparedStatement st = connection().prepareStatement("INSERT INTO QuestionGrade (SubmissionId, QuestionId, Grade) VALUES (?, ?, ?)")) {
                for (int i = 0; i < submission.questionGrades.length; i++) {
                    st.setInt(1, id);
                    st.setInt(2, i + 1);
                    st.setFloat(3, submission.questionGrades[i]);
                    st.executeUpdate();
                }
            }
            return id;
        });
    }

    private PreparedStatement getLastSubmissionGradesStatement() throws SQLException {
        return submissionQuery("ORDER BY s.SubmissionTime DESC, s.SubmissionId DESC");
    }

    private PreparedStatement getBestSubmissionGradesStatement() throws SQLException {
        return submissionQuery("ORDER BY (SELECT COALESCE(SUM(qg2.Grade),0) FROM QuestionGrade qg2 WHERE qg2.SubmissionId=s.SubmissionId) DESC, s.SubmissionTime DESC, s.SubmissionId DESC");
    }

    private PreparedStatement submissionQuery(String order) throws SQLException {
        return connection().prepareStatement(
            "SELECT s.SubmissionId, q.QuestionId, qg.Grade, s.SubmissionTime " +
            "FROM Submission s JOIN User u ON s.UserId=u.UserId " +
            "JOIN Question q ON q.ExerciseId=s.ExerciseId " +
            "JOIN QuestionGrade qg ON qg.SubmissionId=s.SubmissionId AND qg.QuestionId=q.QuestionId " +
            "WHERE u.Username=? AND s.ExerciseId=? " +
            "AND s.SubmissionId=(SELECT s2.SubmissionId FROM Submission s2 " +
            "WHERE s2.UserId=s.UserId AND s2.ExerciseId=s.ExerciseId " + order.replace("s.","s2.").replace("qg2.","qg2.") + " LIMIT 1) " +
            "ORDER BY q.QuestionId LIMIT ?");
    }

    private Submission getSubmission(User user, Exercise exercise, PreparedStatement st) throws SQLException {
        try (PreparedStatement stmt = st) {
            stmt.setString(1, user.username);
            stmt.setInt(2, exercise.id);
            stmt.setInt(3, exercise.questions.size());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                int id = rs.getInt("SubmissionId");
                Date timestamp = new Date(rs.getLong("SubmissionTime"));
                float[] grades = new float[exercise.questions.size()];
                int seen = 0;
                do {
                    int qid = rs.getInt("QuestionId");
                    if (qid < 1 || qid > grades.length) throw new SQLException("Unexpected QuestionId");
                    grades[qid - 1] = rs.getFloat("Grade");
                    seen++;
                } while (rs.next());
                if (seen != grades.length) throw new SQLException("Incomplete grades for submission " + id);
                return new Submission(id, user, exercise, timestamp, grades);
            }
        }
    }

    public Submission getLastSubmission(User user, Exercise exercise) throws SQLException {
        return getSubmission(user, exercise, getLastSubmissionGradesStatement());
    }

    public Submission getBestSubmission(User user, Exercise exercise) throws SQLException {
        return getSubmission(user, exercise, getBestSubmissionGradesStatement());
    }
}
