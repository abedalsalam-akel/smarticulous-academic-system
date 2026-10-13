package smarticulous;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import smarticulous.db.Exercise;
import smarticulous.db.Submission;
import smarticulous.db.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/** Self-contained SQLite integration tests; no university DBUtil dependency. */
public class SmarticulousTest {
    private Smarticulous app;
    private User user;
    private Exercise exercise;

    @Before public void setup() throws Exception {
        app = new Smarticulous();
        app.openDB("jdbc:sqlite::memory:");
        user = new User("student", "First", "Last");
        exercise = new Exercise(10, "Homework", new Date(1750000000000L));
        exercise.addQuestion("A", "First question", 10);
        exercise.addQuestion("B", "Second question", 20);
    }

    @After public void cleanup() throws Exception { app.closeDB(); }

    @Test public void createsAndLoadsQuestionsInOrder() throws Exception {
        assertEquals(10, app.addExercise(exercise));
        assertEquals(-1, app.addExercise(exercise));
        List<Exercise> loaded = app.loadExercises();
        assertEquals(1, loaded.size());
        assertEquals(2, loaded.get(0).questions.size());
        assertEquals("A", loaded.get(0).questions.get(0).name);
        assertEquals("B", loaded.get(0).questions.get(1).name);
    }

    @Test public void preservesUserIdWhenUpdatingAndHashesPassword() throws Exception {
        int first = app.addOrUpdateUser(user, "password123");
        assertTrue(first > 0);
        assertTrue(app.verifyLogin("student", "password123"));
        assertFalse(app.verifyLogin("student", "bad"));
        assertFalse(app.verifyLogin("unknown", "password123"));
        assertFalse(app.verifyLogin("' OR 1=1--", "password123"));
    }

    @Test public void updatesExistingUserWithoutChangingPrimaryKey() throws Exception {
        int id = app.addOrUpdateUser(user, "one");
        user.firstname = "Updated";
        assertEquals(id, app.addOrUpdateUser(user, "two"));
        assertFalse(app.verifyLogin("student", "one"));
        assertTrue(app.verifyLogin("student", "two"));
    }

    @Test public void submissionRoundTripAndBestVsLatest() throws Exception {
        app.addOrUpdateUser(user, "secret");
        app.addExercise(exercise);
        Submission high = new Submission(user, exercise, new Date(1000), new float[]{8.5f, 19f});
        Submission low = new Submission(user, exercise, new Date(2000), new float[]{2f, 3f});
        int highId = app.storeSubmission(high);
        int lowId = app.storeSubmission(low);
        assertTrue(highId > 0);
        assertTrue(lowId > highId);
        assertEquals(lowId, app.getLastSubmission(user, exercise).id);
        assertEquals(highId, app.getBestSubmission(user, exercise).id);
        assertArrayEquals(new float[]{8.5f,19f}, app.getBestSubmission(user,exercise).questionGrades, 0.0001f);
        assertArrayEquals(new float[]{2f,3f}, app.getLastSubmission(user,exercise).questionGrades, 0.0001f);
    }

    @Test public void missingUserAndMissingSubmission() throws Exception {
        app.addExercise(exercise);
        assertEquals(-1, app.storeSubmission(new Submission(user, exercise, new Date(), new float[]{1,1})));
        assertNull(app.getLastSubmission(user, exercise));
        assertNull(app.getBestSubmission(user, exercise));
    }

    @Test public void invalidGradesDoNotCreateSubmission() throws Exception {
        app.addOrUpdateUser(user, "secret");
        app.addExercise(exercise);
        try {
            app.storeSubmission(new Submission(user, exercise, new Date(), new float[]{100, 1}));
            fail("Expected validation error");
        } catch (SQLException expected) { }
        assertNull(app.getLastSubmission(user, exercise));
    }

    @Test public void oldPlaintextPasswordIsNotAccepted() throws Exception {
        try (Connection c = app.openDB("jdbc:sqlite::memory:")) {
            app.addOrUpdateUser(user,"secret");
            try (PreparedStatement st = c.prepareStatement("SELECT Password FROM User WHERE Username=?")) {
                st.setString(1, user.username);
                try (ResultSet rs = st.executeQuery()) {
                    assertTrue(rs.next());
                    assertFalse("secret".equals(rs.getString(1)));
                    assertTrue(rs.getString(1).startsWith("pbkdf2_sha256$"));
                }
            }
        }
    }
}
