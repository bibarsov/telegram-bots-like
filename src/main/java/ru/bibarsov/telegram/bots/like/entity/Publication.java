package ru.bibarsov.telegram.bots.like.entity;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import java.util.List;

@Immutable
@ParametersAreNonnullByDefault
public class Publication {

    public final String id;
    public final String photoId;
    @Nullable
    public final String caption;
    public final List<String> buttonLabels;

    public Publication(
        String id,
        String photoId,
        @Nullable String caption,
        List<String> buttonLabels
    ) {
        this.id = id;
        this.photoId = photoId;
        this.caption = caption;
        this.buttonLabels = buttonLabels;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
