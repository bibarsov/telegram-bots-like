package ru.bibarsov.telegram.bots.like.repository.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bibarsov.telegram.bots.common.util.Action;
import ru.bibarsov.telegram.bots.like.entity.Publication;
import ru.bibarsov.telegram.bots.like.repository.dao.PublicationDao;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
public class PublicationStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationStorage.class);

    private static final int RETRY_COUNT = 3;

    //<PublicationId, PublicationData>
    private final Map<String, Publication> data = new ConcurrentHashMap<>();

    private final ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Publication> dataToPersist = new LinkedBlockingQueue<>();

    private final PublicationDao publicationDao;

    public PublicationStorage(PublicationDao publicationDao) {
        this.publicationDao = publicationDao;
    }

    public void onStart() {
        loadPublication();
        singleThreadPool.submit(
            () -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Action.executeQuietly(this::doPersist);
                }
            }
        );
    }

    public void putPublication(String publicationId, Publication publication) {
        data.put(publicationId, publication);
        dataToPersist.offer(publication);
    }

    @Nullable
    public Publication findPublication(String publicationId) {
        return data.get(publicationId);
    }

    private void doPersist() throws InterruptedException {
        Publication publication = dataToPersist.take();

        int retryCount = RETRY_COUNT;
        while (--retryCount >= 0) {
            try {
                publicationDao.createPublication(publication);
                return;
            } catch (Throwable t) {
                if (retryCount > 0) {
                    LOGGER.warn("Couldn't persist data, remaining retries: " + retryCount, t);
                    TimeUnit.SECONDS.sleep(1L);
                } else {
                    LOGGER.error("Failed to persist data.", t);
                }
            }
        }
    }

    private void loadPublication() {
        data.putAll(publicationDao.getPublications().stream().collect(Collectors.toMap(i -> i.id, i -> i)));
    }
}
