package ru.bibarsov.telegram.bots.like.repository.dao;

import ru.bibarsov.telegram.bots.common.jdbc.repository.sqlite.JdbcRepository;
import ru.bibarsov.telegram.bots.like.entity.UserChoice;

import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkNotNull;

@ParametersAreNonnullByDefault
public class UserChoiceDao {

    private static final String UPSERT_USER_CHOICE_QUERY = "" +
        "  INSERT INTO UserChoice (userId, publicationId, buttonId) " +
        "  VALUES(?, ?, ?)" +
        "  ON CONFLICT (userId, publicationId) DO UPDATE SET buttonId = ?";

    private static final String SELECT_USER_CHOICE_QUERY = "" +
        "  SELECT userId, publicationId, buttonId FROM UserChoice";

    private static final String DELETE_FROM_USER_CHOICE = "" +
        "  DELETE FROM UserChoice WHERE userId = ? AND publicationId = ?";

    private final JdbcRepository jdbcRepository;
    private final Semaphore globalDbUpdateSemaphore;

    public UserChoiceDao(JdbcRepository jdbcRepository, Semaphore globalDbUpdateSemaphore) {
        this.jdbcRepository = jdbcRepository;
        this.globalDbUpdateSemaphore = globalDbUpdateSemaphore;
    }

    public void upsertUserChoice(UserChoice userChoice) throws InterruptedException {
        globalDbUpdateSemaphore.acquire();
        try {
            jdbcRepository.update(
                UPSERT_USER_CHOICE_QUERY,
                stmt -> {
                    stmt.setLong(1, userChoice.userId);
                    stmt.setString(2, userChoice.publicationId);
                    stmt.setInt(3, userChoice.buttonId);
                    //for update in case of conflict
                    stmt.setInt(4, userChoice.buttonId);
                }
            );
        } finally {
            globalDbUpdateSemaphore.release();
        }
    }

    public List<UserChoice> getUserChoices() {
        return jdbcRepository.query(
            SELECT_USER_CHOICE_QUERY,
            rs -> {
                List<UserChoice> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(toUserChoice(rs));
                }
                return result;
            }
        );
    }

    public void removeUserChoice(long userId, String publicationId) throws InterruptedException {
        globalDbUpdateSemaphore.acquire();
        try {
            jdbcRepository.update(
                DELETE_FROM_USER_CHOICE,
                stmt -> {
                    stmt.setLong(1, userId);
                    stmt.setString(2, publicationId);
                }
            );
        } finally {
            globalDbUpdateSemaphore.release();
        }
    }

    private static UserChoice toUserChoice(ResultSet rs) throws SQLException {
        Long userId = rs.getLong("userId");
        if (rs.wasNull()) {
            userId = null;
        }
        Integer buttonId = rs.getInt("buttonId");
        if (rs.wasNull()) {
            buttonId = null;
        }
        return new UserChoice(
            checkNotNull(userId),
            checkNotNull(rs.getString("publicationId")),
            checkNotNull(buttonId)
        );
    }
}
