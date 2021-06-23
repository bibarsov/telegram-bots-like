package ru.bibarsov.telegram.bots.like.service;

import ru.bibarsov.telegram.bots.like.value.ButtonCallbackData;
import ru.bibarsov.telegram.bots.client.dto.InlineKeyboardButton;
import ru.bibarsov.telegram.bots.client.dto.InlineKeyboardMarkup;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ParametersAreNonnullByDefault
public class KeyboardMarkupService {

    public static final String EMPTY_CALLBACK_DATA = "-";

    public static InlineKeyboardMarkup constructKeyboardMarkup(
        List<String> buttonLabels,
        @Nullable Map<Integer, Integer> counters,
        String publicationId,
        boolean previewMode
    ) {
        List<InlineKeyboardButton> buttonCounters = new ArrayList<>();
        for (int buttonId = 0; buttonId < buttonLabels.size(); buttonId++) {
            String buttonLabel = buttonLabels.get(buttonId);
            Integer counter = null;
            if (counters != null) {
                Integer value = counters.get(buttonId);
                if (value != null && value > 0L) {
                    counter = value;
                }
            }
            buttonCounters.add(new InlineKeyboardButton(
                buttonLabel + (counter != null ? " " + counter : ""), //text
                previewMode ? EMPTY_CALLBACK_DATA : ButtonCallbackData.of(buttonId, publicationId).toValue(), //callbackData
                null, //switchInlineQuery
                null //url
            ));
        }
        if (previewMode) {
            return new InlineKeyboardMarkup(List.of(
                buttonCounters,
                List.of(new InlineKeyboardButton(
                    "Share", //text
                    null, //callback data
                    "#" + publicationId, //switchInlineQuery
                    null //url
                ))
            ));
        }
        return new InlineKeyboardMarkup(List.of(buttonCounters));
    }
}
