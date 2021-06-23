package ru.bibarsov.telegram.bots.like.repository.dao;

import ru.bibarsov.telegram.bots.common.jdbc.repository.sqlite.JdbcRepository;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkNotNull;

@ParametersAreNonnullByDefault
public class InlineMessageDao {

    private static final String UPSERT_INLINE_MESSAGE_QUERY = "" +
        "  INSERT INTO InlineMessage (publicationId, inlineMessageId) " +
        "  VALUES(?, ?)" +
        "  ON CONFLICT DO NOTHING";

    private static final String SELECT_INLINE_MESSAGES_QUERY = "" +
        "  SELECT publicationId, inlineMessageId FROM InlineMessage";

    private final JdbcRepository jdbcRepository;
    private final Semaphore globalDbUpdateSemaphore;

    public InlineMessageDao(JdbcRepository jdbcRepository, Semaphore globalDbUpdateSemaphore) {
        this.jdbcRepository = jdbcRepository;
        this.globalDbUpdateSemaphore = globalDbUpdateSemaphore;
    }

    public void upsertInlineMessage(String publicationId, String inlineMessageId) throws InterruptedException {
        globalDbUpdateSemaphore.acquire();
        try {
            jdbcRepository.update(
                UPSERT_INLINE_MESSAGE_QUERY,
                stmt -> {
                    stmt.setString(1, publicationId);
                    stmt.setString(2, inlineMessageId);
                }
            );
        } finally {
            globalDbUpdateSemaphore.release();
        }
    }

    public Map<String, String> getInlineMessages() {
        return jdbcRepository.query(
            SELECT_INLINE_MESSAGES_QUERY,
            rs -> {
                Map<String, String> result = new HashMap<>();
                while (rs.next()) {
                    result.put(
                        checkNotNull(rs.getString("publicationId")),
                        checkNotNull(rs.getString("inlineMessageId"))
                    );
                }
                return result;
            }
        );
    }
}
