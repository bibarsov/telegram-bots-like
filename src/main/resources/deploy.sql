CREATE TABLE IF NOT EXISTS Publication
(
 id             TEXT      NOT NULL,
 photoId        TEXT      NOT NULL,
 caption        TEXT      NULL,
 buttonLabels   TEXT      NOT NULL,

 PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS UserChoice
(
 userId           BIGINT      NOT NULL,
 publicationId    TEXT        NOT NULL,
 buttonId         INTEGER     NOT NULL,

 PRIMARY KEY (userId, publicationId)
);
CREATE TABLE IF NOT EXISTS InlineMessage
(
 publicationId    TEXT      NOT NULL,
 inlineMessageId  TEXT      NOT NULL,

 PRIMARY KEY (publicationId, inlineMessageId)
);
