package ru.bibarsov.telegram.bots.like.service;

import ru.bibarsov.telegram.bots.like.entity.Publication;
import ru.bibarsov.telegram.bots.like.entity.UserChoice;
import ru.bibarsov.telegram.bots.like.repository.storage.InlineMessageStorage;
import ru.bibarsov.telegram.bots.like.repository.storage.UserChoiceStorage;
import ru.bibarsov.telegram.bots.like.service.handler.DefaultHandler;
import ru.bibarsov.telegram.bots.like.value.ReactionMode;
import ru.bibarsov.telegram.bots.client.dto.EditMessageReplyMarkup;
import ru.bibarsov.telegram.bots.client.repository.client.TelegramBotApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bibarsov.telegram.bots.like.repository.storage.PublicationStorage;
import ru.bibarsov.telegram.bots.like.util.Action;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static ru.bibarsov.telegram.bots.like.service.KeyboardMarkupService.constructKeyboardMarkup;

@ParametersAreNonnullByDefault
public class UpdateCountersService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHandler.class);

    private final BlockingQueue<String> dataToProcess = new LinkedBlockingQueue<>();
    private final ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();

    private final PublicationStorage publicationDataStorage;
    private final InlineMessageStorage inlineMessageStorage;
    private final UserChoiceStorage userChoicesStorage;
    private final TelegramBotApi telegramBotApi;

    public UpdateCountersService(
        PublicationStorage publicationDataStorage,
        InlineMessageStorage inlineMessageStorage,
        UserChoiceStorage userChoicesStorage,
        TelegramBotApi telegramBotApi
    ) {
        this.publicationDataStorage = publicationDataStorage;
        this.inlineMessageStorage = inlineMessageStorage;
        this.userChoicesStorage = userChoicesStorage;
        this.telegramBotApi = telegramBotApi;
    }

    public void onStart() {
        singleThreadPool.submit(
            () -> {
                while (!Thread.currentThread().isInterrupted()) {
                    Action.executeQuietly(this::doUpdate);
                }
            }
        );
    }

    public void processUpdate(
        long userId,
        int buttonId,
        String publicationId,
        String inlineMessageId,
        Consumer<ReactionMode> onReaction
    ) {
        Publication publication = publicationDataStorage.findPublication(publicationId);
        if (publication == null) {
            LOGGER.warn("Couldn't find publication with id: {}", publicationId);
            return;
        }
        Integer existingLike = userChoicesStorage.findUserChoice(userId, publicationId);
        LOGGER.info("existingLike: {}, currentButtonId: {}", existingLike, buttonId);
        ReactionMode reactionMode;
        if (existingLike != null && existingLike == buttonId) {
            reactionMode = ReactionMode.TOOK_BACK;
        } else if (existingLike != null) {
            reactionMode = ReactionMode.CHANGED;
        } else {
            reactionMode = ReactionMode.CREATED;
        }
        userChoicesStorage.setUserChoice(new UserChoice(userId, publication.id, buttonId));
        inlineMessageStorage.addInlineMessage(publication.id, inlineMessageId);
        dataToProcess.offer(publicationId);
        onReaction.accept(reactionMode);
    }

    private void doUpdate() throws InterruptedException {
        String publicationId = dataToProcess.take();
        Publication publication = checkNotNull(publicationDataStorage.findPublication(publicationId));

        for (String inlineMessageId : inlineMessageStorage.getInlineMessageIds(publicationId)) {
            telegramBotApi.editMessageReplyMarkup(new EditMessageReplyMarkup(
                inlineMessageId,
                constructKeyboardMarkup(
                    publication.buttonLabels,
                    checkNotNull(userChoicesStorage.findCounters(publicationId)),
                    publication.id,
                    false
                )
            ));
        }
    }
}
