package smarticulous;

import smarticulous.db.Exercise;
import smarticulous.db.Submission;
import smarticulous.db.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Smarticulous class, implementing a grading system.
 */
public class Smarticulous {

    /**
     * The connection to the underlying DB.
     * <p>
     * null if the db has not yet been opened.
     */
    Connection db;

    /**
     * Open the {@link Smarticulous} SQLite database.
     * <p>
     * This should open the database, creating a new one if necessary, and set the {@link #db} field
     * to the new connection.
     * <p>
     * The open method should make sure the database contains the following tables, creating them if necessary:
     *
     * <table>
     *   <caption><em>Table name: <strong>User</strong></em></caption>
     *   <tr><th>Column</th><th>Type</th></tr>
     *   <tr><td>UserId</td><td>Integer (Primary Key)</td></tr>
     *   <tr><td>Username</td><td>Text</td></tr>
     *   <tr><td>Firstname</td><td>Text</td></tr>
     *   <tr><td>Lastname</td><td>Text</td></tr>
     *   <tr><td>Password</td><td>Text</td></tr>
     * </table>
     *
     * <p>
     * <table>
     *   <caption><em>Table name: <strong>Exercise</strong></em></caption>
     *   <tr><th>Column</th><th>Type</th></tr>
     *   <tr><td>ExerciseId</td><td>Integer (Primary Key)</td></tr>
     *   <tr><td>Name</td><td>Text</td></tr>
     *   <tr><td>DueDate</td><td>Integer</td></tr>
     * </table>
     *
     * <p>
     * <table>
     *   <caption><em>Table name: <strong>Question</strong></em></caption>
     *   <tr><th>Column</th><th>Type</th></tr>
     *   <tr><td>ExerciseId</td><td>Integer</td></tr>
     *   <tr><td>QuestionId</td><td>Integer</td></tr>
     *   <tr><td>Name</td><td>Text</td></tr>
     *   <tr><td>Desc</td><td>Text</td></tr>
     *   <tr><td>Points</td><td>Integer</td></tr>
     * </table>
     * In this table the combination of ExerciseId and QuestionId together comprise the primary key.
     *
     * <p>
     * <table>
     *   <caption><em>Table name: <strong>Submission</strong></em></caption>
     *   <tr><th>Column</th><th>Type</th></tr>
     *   <tr><td>SubmissionId</td><td>Integer (Primary Key)</td></tr>
     *   <tr><td>UserId</td><td>Integer</td></tr>
     *   <tr><td>ExerciseId</td><td>Integer</td></tr>
     *   <tr><td>SubmissionTime</td><td>Integer</td></tr>
     * </table>
     *
     * <p>
     * <table>
     *   <caption><em>Table name: <strong>QuestionGrade</strong></em></caption>
     *   <tr><th>Column</th><th>Type</th></tr>
     *   <tr><td>SubmissionId</td><td>Integer</td></tr>
     *   <tr><td>QuestionId</td><td>Integer</td></tr>
     *   <tr><td>Grade</td><td>Real</td></tr>
     * </table>
     * In this table the combination of SubmissionId and QuestionId together comprise the primary key.
     *
     * @param dburl The JDBC url of the database to open (will be of the form "jdbc:sqlite:...")
     * @return the new connection
     * @throws SQLException
     */
    public Connection openDB(String dburl) throws SQLException {
        // TODO: Implement
        this.db = null;
        try {
            this.db = DriverManager.getConnection(dburl);
            createTables();
            return this.db;
        } catch (SQLException e) {
            throw e;
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = this.db.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS User (UserId INTEGER PRIMARY KEY, Username TEXT UNIQUE, Firstname TEXT, Lastname TEXT, Password TEXT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Exercise (ExerciseId INTEGER PRIMARY KEY, Name TEXT, DueDate INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Question (ExerciseId INTEGER, QuestionId INTEGER, Name TEXT, Desc TEXT, Points INTEGER, PRIMARY KEY (ExerciseId, QuestionId))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Submission (SubmissionId INTEGER PRIMARY KEY, UserId INTEGER, ExerciseId INTEGER, SubmissionTime INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS QuestionGrade (SubmissionId INTEGER, QuestionId INTEGER, Grade REAL, PRIMARY KEY (QuestionId, SubmissionId))");
        }
    }


    /**
     * Close the DB if it is open.
     *
     * @throws SQLException
     */
    public void closeDB() throws SQLException {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    // =========== User Management =============

    /**
     * Add a user to the database / modify an existing user.
     * <p>
     * Add the user to the database if they don't exist. If a user with user.username does exist,
     * update their password and firstname/lastname in the database.
     *
     * @param user
     * @param password
     * @return the userid.
     * @throws SQLException
     */
    public int addOrUpdateUser(User user, String password) throws SQLException {
        // TODO: Implement
        PreparedStatement insertStatement = null;
        try {
            insertStatement = insertOrUpdateUserRecord(user, password);
            return getUserIdByUsername(user.username);
        } finally {
            closeResources(null, insertStatement); // Passing null for ResultSet since not used in insert
        }
    }

    private PreparedStatement insertOrUpdateUserRecord(User user, String password) throws SQLException {
        String insertQuery = "INSERT OR REPLACE INTO User (Username, Firstname, Lastname, Password) VALUES (?,?,?,?)";
        PreparedStatement insertStatement = db.prepareStatement(insertQuery);
        setInsertStatementParameters(insertStatement, user, password);
        insertStatement.executeUpdate();
        return insertStatement;
    }
    private int getUserIdByUsername(String username) throws SQLException {
        String selectQuery = "SELECT UserId FROM User WHERE Username = ?";
        try (PreparedStatement selectStatement = db.prepareStatement(selectQuery)) {
            selectStatement.setString(1, username);
            try (ResultSet results = selectStatement.executeQuery()) {
                if (results.next()) {
                    return results.getInt("UserId");
                } else {
                    throw new SQLException("Failed to retrieve UserId for user: " + username);
                }
            }
        }
    }

    private void closeResources(ResultSet results, PreparedStatement statement) {
        try {
            if (results != null) {
                results.close();
            }
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
        }
    }

    private void setInsertStatementParameters(PreparedStatement statement, User user, String password) throws SQLException {
        statement.setString(1, user.username);
        statement.setString(2, user.firstname);
        statement.setString(3, user.lastname);
        statement.setString(4, password);
    }



    /**
     * Verify a user's login credentials.
     *
     * @param username
     * @param password
     * @return true if the user exists in the database and the password matches; false otherwise.
     * @throws SQLException
     * <p>
     * Note: this is totally insecure. For real-life password checking, it's important to store only
     * a password hash
     * @see <a href="https://crackstation.net/hashing-security.htm">How to Hash Passwords Properly</a>
     */
    public boolean verifyLogin(String username, String password) throws SQLException {
        // TODO: Implement
        PreparedStatement statement = null;
        ResultSet resultSet = null;

        try {
            statement = this.db.prepareStatement("SELECT Password FROM User WHERE Username = ?");
            statement.setString(1, username);
            resultSet = statement.executeQuery();

            if (resultSet.next()) {
                String storedPassword = resultSet.getString("Password");
                return storedPassword.equals(password);
            } else {
                // No user found with the given username
                return false;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    
    

    // =========== Exercise Management =============

    /**
     * Add an exercise to the database.
     *
     * @param exercise
     * @return the new exercise id, or -1 if an exercise with this id already existed in the database.
     * @throws SQLException
     */
    public int addExercise(Exercise exercise) throws SQLException {
        // TODO: Implement
        PreparedStatement statement = null;

        try {
            insertExercise(exercise, this.db);
            for (int i = 0; i < exercise.questions.size(); i++) {
                statement = this.db.prepareStatement("INSERT INTO Question (ExerciseId, Name, Desc, Points) VALUES (?, ?, ?, ?)");
                statement.setInt(1, exercise.id);
                statement.setString(2, exercise.questions.get(i).name);
                statement.setString(3, exercise.questions.get(i).desc);
                statement.setInt(4, exercise.questions.get(i).points);
                statement.executeUpdate();
            }
            return getExerciseId(exercise, this.db);
        } catch (SQLException e) {
            return -1;
        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }
    
    
    private void insertExercise(Exercise exercise, Connection connection) throws SQLException {
        String insertExerciseQuery = "INSERT INTO Exercise (ExerciseID, Name, DueDate) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertExerciseQuery)) {
            statement.setInt(1, exercise.id);
            statement.setString(2, exercise.name);
            statement.setLong(3, exercise.dueDate.getTime());
            statement.executeUpdate();
        }
    }
    
    private int getExerciseId(Exercise exercise, Connection connection) throws SQLException {
        String selectExerciseIdQuery = "SELECT ExerciseId FROM Exercise WHERE ExerciseId = ?";
        try (PreparedStatement statement = connection.prepareStatement(selectExerciseIdQuery)) {
            statement.setInt(1, exercise.id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("ExerciseId");
                } else {
                    throw new SQLException();
                }
            }
        }
    }    
    


    /**
     * Return a list of all the exercises in the database.
     * <p>
     * The list should be sorted by exercise id.
     *
     * @return list of all exercises.
     * @throws SQLException
     */
    public List<Exercise> loadExercises() throws SQLException {
        PreparedStatement exerciseStatement = null;
        try {
            exerciseStatement = db.prepareStatement("SELECT ExerciseId, Name, DueDate FROM Exercise ORDER BY ExerciseId");
            ResultSet exerciseResultSet = exerciseStatement.executeQuery();
    
            List<Exercise> exercisesList = new ArrayList<>();
    
            while (exerciseResultSet.next()) {
                int exerciseId = exerciseResultSet.getInt("ExerciseId");
                String exerciseName = exerciseResultSet.getString("Name");
                Date dueDate = new Date(exerciseResultSet.getLong("DueDate"));
    
                Exercise exercise = new Exercise(exerciseId, exerciseName, dueDate);
    
                PreparedStatement questionStatement = db.prepareStatement("SELECT Name, Desc, Points FROM Question WHERE ExerciseId = ?");
                questionStatement.setInt(1, exerciseId);
                ResultSet questionResultSet = questionStatement.executeQuery();
    
                while (questionResultSet.next()) {
                    String questionName = questionResultSet.getString("Name");
                    String questionDesc = questionResultSet.getString("Desc");
                    int points = questionResultSet.getInt("Points");
                    exercise.addQuestion(questionName, questionDesc, points);
                }
    
                exercisesList.add(exercise);
            }
    
            return exercisesList;
        } catch (SQLException ex) {
            throw new SQLException("Error loading exercises from the database.", ex);
        } finally {
            if (exerciseStatement != null) {
                exerciseStatement.close();
            }
        }
    }
    
    
    // ========== Submission Storage ===============

    /**
     * Store a submission in the database.
     * The id field of the submission will be ignored if it is -1.
     * <p>
     * Return -1 if the corresponding user doesn't exist in the database.
     *
     * @param submission
     * @return the submission id.
     * @throws SQLException
     */
    public int storeSubmission(Submission submission) throws SQLException {
        PreparedStatement st = null;
    
        try {
            st = this.db.prepareStatement("INSERT INTO Submission (SubmissionId, UserId, ExerciseId, SubmissionTime) VALUES (?, ?, ?, ?)");
            st.setInt(1, submission.id);
    
            PreparedStatement userSt = this.db.prepareStatement("SELECT UserId FROM User WHERE Username = ?");
            userSt.setString(1, submission.user.username);
    
            ResultSet userResultSet = userSt.executeQuery();
            if (!userResultSet.next()) {
                throw new SQLException("User not found for username: " + submission.user.username);
            }
    
            int userId = userResultSet.getInt("UserId");
            st.setInt(2, userId);
    
            int exerciseId = addExercise(submission.exercise);
            if (exerciseId == -1) {
                exerciseId = submission.exercise.id;
            }
            st.setInt(3, exerciseId);
    
            st.setLong(4, submission.submissionTime.getTime());
    
            st.executeUpdate();
    
            insertQuestionGrades(submission);
    
            return submission.id;
        } catch (SQLException e) {
            return -1;
        } finally {
            if (st != null) {
                st.close();
            }
        }
    }
    
    public void insertQuestionGrades(Submission submission) throws SQLException {
        for (int i = 0; i < submission.exercise.questions.size(); i++) {
            PreparedStatement st = this.db.prepareStatement("INSERT INTO QuestionGrade (SubmissionId, QuestionId, Grade) VALUES (?, ?, ?)");
            st.setInt(1, submission.id);
            st.setString(2, submission.exercise.questions.get(i).name);

    
            float grade = submission.questionGrades[i] / submission.exercise.questions.get(i).points;
            st.setFloat(3, grade);
    
            st.executeUpdate();
            st.close();
        }
    }
    
    
    
    
    


    // ============= Submission Query ===============


    /**
     * Return a prepared SQL statement that, when executed, will
     * return one row for every question of the latest submission for the given exercise by the given user.
     * <p>
     * The rows should be sorted by QuestionId, and each row should contain:
     * - A column named "SubmissionId" with the submission id.
     * - A column named "QuestionId" with the question id,
     * - A column named "Grade" with the grade for that question.
     * - A column named "SubmissionTime" with the time of submission.
     * <p>
     * Parameter 1 of the prepared statement will be set to the User's username, Parameter 2 to the Exercise Id, and
     * Parameter 3 to the number of questions in the given exercise.
     * <p>
     * This will be used by {@link #getLastSubmission(User, Exercise)}
     *
     * @return
     */
    PreparedStatement getLastSubmissionGradesStatement() throws SQLException {
        try {
            String query = "SELECT Submission.SubmissionId, Question.QuestionId, QuestionGrade.Grade, Submission.SubmissionTime " +
                           "FROM User " +
                           "JOIN Submission ON User.UserId = Submission.UserId " +
                           "JOIN Question ON Submission.ExerciseId = Question.ExerciseId " +
                           "JOIN QuestionGrade ON Question.QuestionId = QuestionGrade.QuestionId " +
                           "AND Submission.SubmissionId = QuestionGrade.SubmissionId " +
                           "WHERE User.Username = ? AND Submission.ExerciseId = ? " +
                           "GROUP BY QuestionGrade.QuestionId " +
                           "HAVING MAX(Submission.SubmissionTime) " +
                           "ORDER BY Question.QuestionId " +
                           "LIMIT ?;";
            return db.prepareStatement(query);
        } catch (SQLException ex) {
            throw new SQLException("Error preparing statement for last submission grades", ex);
        }
    }
    

    /**
     * Return a prepared SQL statement that, when executed, will
     * return one row for every question of the <i>best</i> submission for the given exercise by the given user.
     * The best submission is the one whose point total is maximal.
     * <p>
     * The rows should be sorted by QuestionId, and each row should contain:
     * - A column named "SubmissionId" with the submission id.
     * - A column named "QuestionId" with the question id,
     * - A column named "Grade" with the grade for that question.
     * - A column named "SubmissionTime" with the time of submission.
     * <p>
     * Parameter 1 of the prepared statement will be set to the User's username, Parameter 2 to the Exercise Id, and
     * Parameter 3 to the number of questions in the given exercise.
     * <p>
     * This will be used by {@link #getBestSubmission(User, Exercise)}
     *
     */
    PreparedStatement getBestSubmissionGradesStatement() throws SQLException {
        // TODO: Implement
        return null;
    }

    /**
     * Return a submission for the given exercise by the given user that satisfies
     * some condition (as defined by an SQL prepared statement).
     * <p>
     * The prepared statement should accept the user name as parameter 1, the exercise id as parameter 2 and a limit on the
     * number of rows returned as parameter 3, and return a row for each question corresponding to the submission, sorted by questionId.
     * <p>
     * Return null if the user has not submitted the exercise (or is not in the database).
     *
     * @param user
     * @param exercise
     * @param stmt
     * @return
     * @throws SQLException
     */
    Submission getSubmission(User user, Exercise exercise, PreparedStatement stmt) throws SQLException {
        stmt.setString(1, user.username);
        stmt.setInt(2, exercise.id);
        stmt.setInt(3, exercise.questions.size());

        ResultSet res = stmt.executeQuery();

        boolean hasNext = res.next();
        if (!hasNext)
            return null;

        int sid = res.getInt("SubmissionId");
        Date submissionTime = new Date(res.getLong("SubmissionTime"));

        float[] grades = new float[exercise.questions.size()];

        for (int i = 0; hasNext; ++i, hasNext = res.next()) {
            grades[i] = res.getFloat("Grade");
        }

        return new Submission(sid, user, exercise, submissionTime, (float[]) grades);
    }

    /**
     * Return the latest submission for the given exercise by the given user.
     * <p>
     * Return null if the user has not submitted the exercise (or is not in the database).
     *
     * @param user
     * @param exercise
     * @return
     * @throws SQLException
     */
    public Submission getLastSubmission(User user, Exercise exercise) throws SQLException {
        return getSubmission(user, exercise, getLastSubmissionGradesStatement());
    }


    /**
     * Return the submission with the highest total grade
     *
     * @param user the user for which we retrieve the best submission
     * @param exercise the exercise for which we retrieve the best submission
     * @return
     * @throws SQLException
     */
    public Submission getBestSubmission(User user, Exercise exercise) throws SQLException {
        return getSubmission(user, exercise, getBestSubmissionGradesStatement());
    }
}
