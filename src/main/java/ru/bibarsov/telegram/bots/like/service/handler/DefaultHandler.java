package ru.bibarsov.telegram.bots.like.service.handler;

import ru.bibarsov.telegram.bots.like.entity.Publication;
import ru.bibarsov.telegram.bots.like.repository.storage.UserChoiceStorage;
import ru.bibarsov.telegram.bots.like.value.PhotoCaptionForm;
import ru.bibarsov.telegram.bots.client.dto.*;
import ru.bibarsov.telegram.bots.client.service.MessageService;
import ru.bibarsov.telegram.bots.client.service.handler.Handler;
import ru.bibarsov.telegram.bots.client.value.ChatType;
import ru.bibarsov.telegram.bots.like.repository.storage.PublicationStorage;
import ru.bibarsov.telegram.bots.like.service.KeyboardMarkupService;
import ru.bibarsov.telegram.bots.like.service.UpdateCountersService;
import ru.bibarsov.telegram.bots.like.value.ButtonCallbackData;

import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static ru.bibarsov.telegram.bots.like.service.KeyboardMarkupService.constructKeyboardMarkup;

public class DefaultHandler extends Handler {

    private static final String PHOTO_TYPE = "photo";

    private final UpdateCountersService updateCountersService;
    private final MessageService messageService;
    private final PublicationStorage publicationDataStorage;
    private final UserChoiceStorage userChoiceStorage;
    private final List<Long> adminUserIds;

    public DefaultHandler(
        UpdateCountersService updateCountersService,
        MessageService messageService,
        PublicationStorage publicationDataStorage,
        UserChoiceStorage userChoiceStorage,
        List<Long> adminUserIds
    ) {
        this.updateCountersService = updateCountersService;
        this.messageService = messageService;
        this.publicationDataStorage = publicationDataStorage;
        this.userChoiceStorage = userChoiceStorage;
        this.adminUserIds = adminUserIds;
        checkArgument(!adminUserIds.isEmpty());
    }

    @Override
    public void handle(Update update) {
        Message message = update.message;
        CallbackQuery callbackQuery = update.callbackQuery;
        InlineQuery inlineQuery = update.inlineQuery;
        if (message != null) {
            if (message.chat.type == ChatType.PRIVATE) {
                Chat chat = message.chat;
                long userId = chat.id;
                if (adminUserIds.contains(userId)) {
                    if (message.photo != null && !message.photo.isEmpty()) {
                        String publicationId = UUID.randomUUID().toString();

                        PhotoSize photoSize = getWithBestQuality(message.photo);
                        String fileId = photoSize.fileId;

                        if (message.caption == null) {
                            //holder for buttons / nullable caption should be set
                            return;
                        }

                        PhotoCaptionForm photoCaptionForm = PhotoCaptionForm.ofValue(message.caption);
                        publicationDataStorage.putPublication(publicationId, new Publication(
                            publicationId,
                            fileId,
                            photoCaptionForm.caption,
                            photoCaptionForm.buttonLabels
                        ));
                        messageService.schedulePhoto(new SendPhotoRequest(
                            userId, //chatId
                            fileId, //photo
                            photoCaptionForm.caption,
                            null, //parseMode
                            constructKeyboardMarkup(
                                photoCaptionForm.buttonLabels,
                                null,//counters
                                publicationId, //publicationId
                                true //previewMode
                            )
                        ));
                    }
                }
            }
        } else if (callbackQuery != null) {
            if (callbackQuery.data != null && !callbackQuery.data.equals(KeyboardMarkupService.EMPTY_CALLBACK_DATA)) {
                ButtonCallbackData buttonCallbackData = ButtonCallbackData.ofValue(callbackQuery.data);
                updateCountersService.processUpdate(
                    callbackQuery.from.id, //userId
                    buttonCallbackData.buttonId,
                    buttonCallbackData.publicationId,
                    checkNotNull(callbackQuery.inlineMessageId), //inlineMessageId
                    (reactionMode) -> {
                        Publication publication = checkNotNull(publicationDataStorage.findPublication(buttonCallbackData.publicationId));
                        String alertText;
                        switch (reactionMode) {
                            case CREATED:
                            case CHANGED:
                                String buttonLabel = checkNotNull(publication.buttonLabels.get(buttonCallbackData.buttonId));
                                alertText = "You " + buttonLabel + " this";
                                break;
                            case TOOK_BACK:
                                alertText = "You took your reaction back";
                                break;
                            default:
                                throw new IllegalStateException("Unexpected ReactionMode: " + reactionMode);
                        }
                        messageService.scheduleAnswerCallbackQuery(new CallbackQueryAnswer(
                            callbackQuery.id,
                            alertText,
                            true //showAlert
                        ));
                    }
                );
            }
        } else if (inlineQuery != null) {
            if (inlineQuery.query.startsWith("#")) {
                Publication publication = publicationDataStorage.findPublication(inlineQuery.query.substring(1));
                if (publication != null) {
                    messageService.scheduleAnswerInlineQuery(new InlineQueryAnswer(
                        inlineQuery.id, //id
                        List.of(
                            new InlineQueryResultCachedPhoto(
                                PHOTO_TYPE, //type
                                UUID.randomUUID().toString(), //id
                                publication.photoId,
                                publication.caption,
                                constructKeyboardMarkup(
                                    publication.buttonLabels,
                                    userChoiceStorage.findCounters(publication.id),//counters
                                    publication.id,
                                    false //previewMode
                                )
                            )
                        )
                    ));
                } else {
                    //TODO Answer via some text
                    System.out.println("Is not found");
                }
            }
            //ignore other types
        }
    }

    private static PhotoSize getWithBestQuality(List<PhotoSize> photo) {
        //best quality photoSize is always at the end of array
        return photo.get(photo.size() - 1);
    }
}
