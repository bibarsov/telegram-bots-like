package ru.bibarsov.telegram.bots.like;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import ru.bibarsov.telegram.bots.like.repository.dao.UserChoiceDao;
import ru.bibarsov.telegram.bots.like.repository.storage.InlineMessageStorage;
import ru.bibarsov.telegram.bots.like.repository.storage.UserChoiceStorage;
import ru.bibarsov.telegram.bots.client.repository.client.TelegramBotApi;
import ru.bibarsov.telegram.bots.client.serialization.JsonHelper;
import ru.bibarsov.telegram.bots.client.service.MessageService;
import ru.bibarsov.telegram.bots.client.service.SingleHandlerDispatcher;
import ru.bibarsov.telegram.bots.client.service.UpdatePollerService;
import ru.bibarsov.telegram.bots.client.service.handler.Handler;
import ru.bibarsov.telegram.bots.common.jdbc.repository.sqlite.JdbcRepository;
import ru.bibarsov.telegram.bots.common.util.PropertiesReader;
import ru.bibarsov.telegram.bots.like.repository.dao.InlineMessageDao;
import ru.bibarsov.telegram.bots.like.repository.dao.PublicationDao;
import ru.bibarsov.telegram.bots.like.repository.storage.PublicationStorage;
import ru.bibarsov.telegram.bots.like.service.UpdateCountersService;
import ru.bibarsov.telegram.bots.like.service.handler.DefaultHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@ParametersAreNonnullByDefault
public class LikeBotApplication {

    private static final String LIKEBOT_PROPERTIES_FILE_NAME = "likebot.properties";

    public static void main(String[] args) throws IOException, InterruptedException {
        String propertyFileName = LIKEBOT_PROPERTIES_FILE_NAME;
        if (args.length > 0) {
            propertyFileName = args[0];
        }

        //loggers
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);

        Logger logger = (Logger) LoggerFactory.getLogger(UpdatePollerService.class);
        logger.setLevel(Level.INFO);

        //load properties
        Properties prop = PropertiesReader.getPropertiesFromFileOrSystemResource(propertyFileName);
        String botApiKey = checkNotNull(prop.getProperty("common.bot-api-key"));
        int workersThreadCount = Integer.parseInt(checkNotNull(prop.getProperty("single-handler-dispatcher.thread-count")));
        String jdbConnectionString = checkNotNull(prop.getProperty("common.jdbc-connection-string"));
        List<Long> adminUserIds = Arrays.stream(checkNotNull(prop.getProperty("common.admin-user-ids")).split(","))
            .map(Long::parseLong)
            .collect(Collectors.toList());
        JsonHelper jsonHelper = new JsonHelper();

        TelegramBotApi telegramBotApi = new TelegramBotApi(
            jsonHelper,
            botApiKey
        );
        MessageService messageService = new MessageService(telegramBotApi);

        JdbcRepository jdbcRepository = new JdbcRepository(
            jdbConnectionString,
            Connection.TRANSACTION_READ_UNCOMMITTED //SQLite supports only TRANSACTION_SERIALIZABLE and TRANSACTION_READ_UNCOMMITTED
        );

        //SQLite supports only one simultaneous write operation at a time
        Semaphore globalDbUpdateSemaphore = new Semaphore(1);

        //check driver / DDL
        applyDdl(
            jdbcRepository,
            IOUtils.toString(getResourceFile("deploy.sql"), StandardCharsets.UTF_8),
            globalDbUpdateSemaphore
        );

        //DAOs
        PublicationDao publicationDao = new PublicationDao(
            jdbcRepository,
            jsonHelper,
            globalDbUpdateSemaphore
        );
        InlineMessageDao inlineMessageDao = new InlineMessageDao(
            jdbcRepository,
            globalDbUpdateSemaphore
        );
        UserChoiceDao userChoicesDao = new UserChoiceDao(
            jdbcRepository,
            globalDbUpdateSemaphore
        );

        //storages
        InlineMessageStorage inlineMessageStorage = new InlineMessageStorage(inlineMessageDao);
        PublicationStorage publicationDataStorage = new PublicationStorage(publicationDao);
        UserChoiceStorage userChoicesStorage = new UserChoiceStorage(userChoicesDao);

        //services
        UpdateCountersService updateCountersService = new UpdateCountersService(
            publicationDataStorage,
            inlineMessageStorage,
            userChoicesStorage,
            telegramBotApi
        );

        //command handlers
        Handler<?> defaultHandler = new DefaultHandler(
            updateCountersService,
            messageService,
            publicationDataStorage,
            userChoicesStorage,
            adminUserIds
        );

        //start application lifecycle
        publicationDataStorage.onStart();
        userChoicesStorage.onStart();
        inlineMessageStorage.onStart();
        updateCountersService.onStart();

        UpdatePollerService pollerService = new UpdatePollerService(
            new SingleHandlerDispatcher(
                workersThreadCount,//workerThreadCount
                defaultHandler
            ),
            botApiKey //botApiKey
        );
        pollerService.doJob();
    }

    private static void applyDdl(
        JdbcRepository jdbcRepository,
        String ddlStatements,
        Semaphore globalDbUpdateSemaphore
    ) throws InterruptedException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Db driver is not on classpath");
        }
        globalDbUpdateSemaphore.acquire();
        try {
            jdbcRepository.execute(ddlStatements);
        } finally {
            globalDbUpdateSemaphore.release();
        }
    }

    private static InputStream getResourceFile(String fileName) throws FileNotFoundException {
        File file = new File(fileName);
        if (file.exists()) {
            return new FileInputStream(file);
        }
        return checkNotNull(ClassLoader.getSystemResourceAsStream(fileName));
    }
}
