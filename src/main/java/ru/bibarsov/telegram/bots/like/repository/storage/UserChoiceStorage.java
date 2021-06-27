package ru.bibarsov.telegram.bots.like.repository.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bibarsov.telegram.bots.common.util.Action;
import ru.bibarsov.telegram.bots.like.entity.UserChoice;
import ru.bibarsov.telegram.bots.like.repository.dao.UserChoiceDao;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.concurrent.*;

@ParametersAreNonnullByDefault
public class UserChoiceStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserChoiceStorage.class);

    private static final int RETRY_COUNT = 3;

    private static class DataToPersistWrapper {
        public final boolean deletion;
        public final UserChoice userChoice;

        private DataToPersistWrapper(boolean deletion, UserChoice userChoice) {
            this.deletion = deletion;
            this.userChoice = userChoice;
        }
    }

    //<UserId, Map<PublicationId, ButtonId>>
    private final Map<Long, Map<String, Integer>> data = new ConcurrentHashMap<>();

    private final ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();
    private final BlockingQueue<DataToPersistWrapper> dataToPersist = new LinkedBlockingQueue<>();

    private final UserChoiceDao userChoicesDao;

    public UserChoiceStorage(UserChoiceDao userChoicesDao) {
        this.userChoicesDao = userChoicesDao;
    }

    public void onStart() {
        loadUserChoices();
        singleThreadPool.submit(
            () -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Action.executeQuietly(this::doPersist);
                }
            }
        );
    }

    @Nullable
    public Integer findUserChoice(long userId, String publicationId) {
        Map<String, Integer> choice = data.get(userId);
        if (choice != null) {
            return choice.get(publicationId);
        }
        return null;
    }

    //<ButtonId, Counter>
    public Map<Integer, Integer> findCounters(String publicationId) {
        //<ButtonId, Set<UserId>>
        Map<Integer, Set<Long>> buttonToUsers = new HashMap<>();
        for (var entry : data.entrySet()) {
            long userId = entry.getKey();
            Map<String, Integer> entryValue = entry.getValue();
            if (entryValue != null && entryValue.containsKey(publicationId)) {
                for (var entryValueEntry : entryValue.entrySet()) {
                    if (entryValueEntry.getKey().equals(publicationId)) {
                        Integer buttonId = entryValueEntry.getValue();
                        if (buttonId != null) {
                            buttonToUsers.computeIfAbsent(buttonId, (ignored) -> new HashSet<>()).add(userId);
                        }
                    }
                }
            }
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, Set<Long>> entry : buttonToUsers.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    public void setUserChoice(UserChoice userChoice) {
        var publicationIdToButtonId = data.computeIfAbsent(userChoice.userId, (ignored) -> new ConcurrentHashMap<>());
        Integer chosenButtoinId = publicationIdToButtonId.get(userChoice.publicationId);
        boolean deletion = false;
        if (chosenButtoinId != null && chosenButtoinId == userChoice.buttonId) {
            publicationIdToButtonId.remove(userChoice.publicationId);
            deletion = true;
        } else {
            publicationIdToButtonId.put(userChoice.publicationId, userChoice.buttonId);
        }
        dataToPersist.offer(new DataToPersistWrapper(deletion, userChoice));
    }

    private void doPersist() throws InterruptedException {
        DataToPersistWrapper wrapper = dataToPersist.take();
        UserChoice userChoice = wrapper.userChoice;

        int retryCount = RETRY_COUNT;
        while (--retryCount >= 0) {
            try {
                if (wrapper.deletion) {
                    userChoicesDao.removeUserChoice(userChoice.userId, userChoice.publicationId);
                } else {
                    userChoicesDao.upsertUserChoice(userChoice);
                }
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

    private void loadUserChoices() {
        List<UserChoice> userChoices = userChoicesDao.getUserChoices();
        Map<Long, Map<String, Integer>> tempData = new HashMap<>();
        for (UserChoice userChoice : userChoices) {
            tempData.computeIfAbsent(userChoice.userId, (ignored) -> new HashMap<>()).put(
                userChoice.publicationId,
                userChoice.buttonId
            );
        }
        for (var entry : tempData.entrySet()) {
            data.put(entry.getKey(), entry.getValue());
        }
    }
}
