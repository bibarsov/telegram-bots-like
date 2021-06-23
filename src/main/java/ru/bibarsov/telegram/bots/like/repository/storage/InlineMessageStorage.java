package ru.bibarsov.telegram.bots.like.repository.storage;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bibarsov.telegram.bots.like.repository.dao.InlineMessageDao;
import ru.bibarsov.telegram.bots.like.util.Action;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@ParametersAreNonnullByDefault
public class InlineMessageStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlineMessageStorage.class);

    private static final int RETRY_COUNT = 3;

    //<PublicationId, Set<InlineMessageId>>
    private final Map<String, Set<String>> data = new ConcurrentHashMap<>();

    private final ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Pair<String, String>> dataToPersist = new LinkedBlockingQueue<>();

    private final InlineMessageDao inlineMessageDao;

    public InlineMessageStorage(InlineMessageDao inlineMessageDao) {
        this.inlineMessageDao = inlineMessageDao;
    }

    public void onStart() {
        loadInlineMessages();
        singleThreadPool.submit(
            () -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Action.executeQuietly(this::doPersist);
                }
            }
        );
    }

    public void addInlineMessage(
        String publicationId,
        String inlineMessageId
    ) {
        data.computeIfAbsent(publicationId, (ignored) -> new CopyOnWriteArraySet<>()).add(inlineMessageId);
        dataToPersist.offer(Pair.of(publicationId, inlineMessageId));
    }

    public Set<String> getInlineMessageIds(String publicationId) {
        return data.getOrDefault(publicationId, Set.of());
    }

    private void doPersist() throws InterruptedException {
        Pair<String, String> inlineMsg = dataToPersist.take();

        int retryCount = RETRY_COUNT;
        while (--retryCount >= 0) {
            try {
                inlineMessageDao.upsertInlineMessage(
                    inlineMsg.getLeft(), //publicationId
                    inlineMsg.getRight() //inlineMessageId
                );
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

    private void loadInlineMessages() {
        Map<String, String> inlineMessages = inlineMessageDao.getInlineMessages();
        Map<String, Set<String>> tempData = new HashMap<>();
        for (var entry : inlineMessages.entrySet()) {
            tempData.computeIfAbsent(entry.getKey(), (ignore) -> new HashSet<>()).add(entry.getValue());
        }
        for (var entry : tempData.entrySet()) {
            data.put(entry.getKey(), new CopyOnWriteArraySet<>(entry.getValue()));
        }
    }
}
