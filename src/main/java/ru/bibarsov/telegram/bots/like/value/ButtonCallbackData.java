package ru.bibarsov.telegram.bots.like.value;

import com.google.common.io.BaseEncoding;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkArgument;

@Immutable
@ParametersAreNonnullByDefault
public class ButtonCallbackData {

    private static final BaseEncoding B64 = BaseEncoding.base64Url().omitPadding();
    private static final String DELIMITER = ";";

    public final int buttonId;
    public final String publicationId;

    public static ButtonCallbackData ofValue(String value) {
        String decodedValue = new String(B64.decode(value), StandardCharsets.UTF_8);
        String[] split = decodedValue.split(DELIMITER);
        checkArgument(split.length == 2);
        return new ButtonCallbackData(Integer.parseInt(split[0]), split[1]);
    }

    public static ButtonCallbackData of(int buttonId, String internalPhotoId) {
        return new ButtonCallbackData(buttonId, internalPhotoId);
    }

    public String toValue() {
        return B64.encode((buttonId + DELIMITER + publicationId).getBytes());
    }

    private ButtonCallbackData(int buttonId, String publicationId) {
        this.buttonId = buttonId;
        this.publicationId = publicationId;
    }
}
