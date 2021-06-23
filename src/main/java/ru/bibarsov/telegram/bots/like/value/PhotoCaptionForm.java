package ru.bibarsov.telegram.bots.like.value;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

@Immutable
@ParametersAreNonnullByDefault
public class PhotoCaptionForm {

    private static final String BASE_DELIMITER = ";";
    private static final String BUTTONS_DELIMITER = "\\s+";

    public final List<String> buttonLabels;
    @Nullable
    public final String caption;

    public static PhotoCaptionForm ofValue(String value) {
        String[] split = value.split(BASE_DELIMITER, 2);
        checkArgument(split.length <= 2 && split.length > 0);

        String[] buttonLabels = split[0].split(BUTTONS_DELIMITER);
        return new PhotoCaptionForm(
            Arrays.stream(buttonLabels)
                .sequential()
                .map(String::trim)
                .collect(Collectors.toList()), //buttons
            split.length == 1 ? null : split[1] //caption
        );
    }

    private PhotoCaptionForm(List<String> buttonLabels, @Nullable String caption) {
        this.buttonLabels = buttonLabels;
        this.caption = caption;
    }
}
