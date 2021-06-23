package ru.bibarsov.telegram.bots.like.repository.dao;

import ru.bibarsov.telegram.bots.like.entity.Publication;
import ru.bibarsov.telegram.bots.client.serialization.JsonHelper;
import ru.bibarsov.telegram.bots.common.jdbc.repository.sqlite.JdbcRepository;

import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkNotNull;

@ParametersAreNonnullByDefault
public class PublicationDao {

    private static final String INSERT_PUBLICATION_QUERY = "" +
        "  INSERT INTO Publication (id, photoId, caption, buttonLabels) " +
        "  VALUES(?, ?, ?, ?)";

    private static final String SELECT_USERINFO_QUERY = "" +
        "  SELECT id, photoId, caption, buttonLabels FROM Publication";

    private final JdbcRepository jdbcRepository;
    private final JsonHelper jsonHelper;
    private final Semaphore globalDbUpdateSemaphore;

    public PublicationDao(
        JdbcRepository jdbcRepository,
        JsonHelper jsonHelper,
        Semaphore globalDbUpdateSemaphore
    ) {
        this.jdbcRepository = jdbcRepository;
        this.jsonHelper = jsonHelper;
        this.globalDbUpdateSemaphore = globalDbUpdateSemaphore;
    }

    public void createPublication(Publication publication) throws InterruptedException {
        globalDbUpdateSemaphore.acquire();
        try {
            jdbcRepository.update(
                INSERT_PUBLICATION_QUERY,
                stmt -> {
                    stmt.setString(1, publication.id);
                    stmt.setString(2, publication.photoId);
                    stmt.setString(3, publication.caption);
                    stmt.setString(4, jsonHelper.serialize(publication.buttonLabels));
                }
            );
        } finally {
            globalDbUpdateSemaphore.release();
        }
    }

    public List<Publication> getPublications() {
        return jdbcRepository.query(
            SELECT_USERINFO_QUERY,
            rs -> {
                List<Publication> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(toPublication(rs, jsonHelper));
                }
                return result;
            }
        );
    }

    private static Publication toPublication(ResultSet rs, JsonHelper jsonHelper) throws SQLException {
        //noinspection unchecked
        return new Publication(
            checkNotNull(rs.getString("id")),
            checkNotNull(rs.getString("photoId")),
            rs.getString("caption"),
            (List<String>) jsonHelper.deserialize(checkNotNull(rs.getString("buttonLabels")), List.class)
        );
    }
}
