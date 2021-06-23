package ru.bibarsov.telegram.bots.like.entity;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;

@Immutable
@ParametersAreNonnullByDefault
public class UserChoice {

    public final long userId;
    public final String publicationId;
    public final int buttonId;

    public UserChoice(long userId, String publicationId, int buttonId) {
        this.userId = userId;
        this.publicationId = publicationId;
        this.buttonId = buttonId;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
