package me.itstautvydas.uuidswapper.config;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import me.itstautvydas.uuidswapper.annotation.RequiredProperty;
import me.itstautvydas.uuidswapper.enums.ConditionsMode;
import me.itstautvydas.uuidswapper.enums.FallbackUsage;
import me.itstautvydas.uuidswapper.enums.ResponseHandlerState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Data
public class Configuration {
    @Data
    public static class DatabaseConfiguration {
        @RequiredProperty
        private boolean enabled;
        private boolean downloadDriver = true;
        @SerializedName("download-link")
        private String driverDownloadLink;
        @RequiredProperty
        @SerializedName("driver")
        private String driverName;
        @SerializedName("file")
        private String fileName;
        private long timeout = 5000;
        private long keepOpenTime = 10;
        private long timerRepeatTime = 1;
        @SerializedName("debug")
        private boolean debugEnabled = false;
    }

    @Data
    public static class CachingConfiguration {
        @RequiredProperty
        private boolean enabled;
        private long keepTime = 7200;
        private boolean useCreatedAt = true;
        private boolean useUpdatedAt = true;
    }

    @Data
    public static class UsernameChangesConfiguration {
        private boolean checkDependingOnIpAddress = false;
        private boolean noRequestsWithExistingUsername = true;
    }

    @SuppressWarnings("ConstantValue")
    @Data
    public static class ResponseHandlerConfiguration {
        @RequiredProperty
        private long order = 9999;
        @RequiredProperty
        private ResponseHandlerState state;
        private Boolean allowPlayerToJoin = null;
        private Boolean useFallback = null;
        private boolean applyProperties;
        private String disconnectMessage;
        private ConditionsMode conditionsMode = ConditionsMode.AND;
        private boolean ignoreConditionsCase = false;
        private Map<String, Object> conditions = new HashMap<>();

        public boolean testConditions(Map<String, Object> placeholders) {
            if (conditions.isEmpty())
                return true;
            Boolean result = null;
            for (var entry : conditions.entrySet()) {
                @Nullable var conditionValue = entry.getValue();
                boolean conditionResult;
                if (entry.getKey().startsWith("::")) {
                    if (entry.getValue() instanceof Boolean bool)
                        conditionResult = placeholders.containsKey(entry.getKey().substring(2)) == bool;
                    else
                        conditionResult = false;
                } else {
                    @Nullable var value = placeholders.get(entry.getKey());
                    if (conditionValue == null || value == null) {
                        conditionResult = Objects.equals(conditionValue, value);
                    } else {
                        if (ignoreConditionsCase)
                            conditionResult = conditionValue.toString().equalsIgnoreCase(value.toString());
                        else
                            conditionResult = conditionValue.equals(value);
                    }
                }
                if (result == null) {
                    result = conditionResult;
                } else {
                    if (conditionsMode == ConditionsMode.AND)
                        result = result && conditionResult;
                    else
                        result = result || conditionResult;
                }
            }
            return result != null && result;
        }
    }

    @Data
    public static class DefaultServiceConfiguration {
        private String requestMethod = "GET";
        private String badUuidDisconnectMessage;
        private String defaultDisconnectMessage;
        private String connectionErrorDisconnectMessage;
        private String serviceBadStatusDisconnectMessage;
        private String unknownErrorDisconnectMessage;
        private String serviceTimeoutDisconnectMessage;
        private int expectStatusCode = 200;
        private long timeout = 3000;
        private boolean allowCaching = true;
        private boolean ignoreStatusCode = false;
        @SerializedName("debug")
        private boolean debugEnabled = false;
        @RequiredProperty
        private List<FallbackUsage> useFallbacks = new ArrayList<>();
        private Map<String, String> postData = new HashMap<>();
        private Map<String, String> queryData = new HashMap<>();
        private Map<String, String> headers = new HashMap<>();
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ServiceConfiguration extends DefaultServiceConfiguration {
        @RequiredProperty
        private String name;
        @RequiredProperty
        private String endpoint;
        @RequiredProperty
        private String jsonPathToUuid;
        private String jsonPathToProperties;
        private String jsonPathToTextures;
        private List<ResponseHandlerConfiguration> responseHandlers = new ArrayList<>();

        public void sortResponseHandlers() {
            responseHandlers.sort(Comparator.comparingLong(ResponseHandlerConfiguration::getOrder));
        }

        public ResponseHandlerConfiguration executeResponseHandlers(ResponseHandlerState state, Map<String, Object> placeholders) {
            for (var handler : responseHandlers) {
                if (handler.state == state && handler.testConditions(placeholders))
                    return handler;
            }
            return null;
        }
    }

    @Data
    public static class OnlineAuthenticationConfiguration {
        @RequiredProperty
        private boolean enabled;
        @RequiredProperty
        @SerializedName("use-service")
        private String serviceName;
        @RequiredProperty
        private List<String> fallbackServices;
        private long fallbackServiceRememberTime = 21600;
        private long maxTimeout = 6000;
        private long minTimeout = 1000;
        private boolean checkForOnlineUuid = true;
        private boolean sendMessagesToConsole = true;
        private boolean sendErrorMessagesToConsole = true;
        private long serviceConnectionThrottle = 5000;
        @RequiredProperty
        private String serviceConnectionThrottledMessage;
        @RequiredProperty
        private CachingConfiguration caching;
        @RequiredProperty
        private UsernameChangesConfiguration usernameChanges;
        @RequiredProperty
        private DefaultServiceConfiguration serviceDefaults;
        private List<ServiceConfiguration> services = new ArrayList<>();

        public ServiceConfiguration getService(String name) {
            return services.stream()
                    .filter(s -> s.getName().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Data
    public static class RandomizerConfiguration {
        @Data
        public static class UuidRandomizer {
            @RequiredProperty
            private boolean randomize;
            @RequiredProperty
            private boolean save;
        }

        @EqualsAndHashCode(callSuper = true)
        @Data
        public static class UsernameRandomizer extends UuidRandomizer {
            @RequiredProperty
            private String characters;
        }

        @RequiredProperty
        private boolean enabled;
        @SerializedName("username") @RequiredProperty
        private UsernameRandomizer usernameSettings;
        @SerializedName("uuid") @RequiredProperty
        private UuidRandomizer uuidSettings;
    }

    @Data
    public static class CommandMessagesConfiguration {
        @RequiredProperty
        private String prefix;
        @RequiredProperty
        private String noArguments;
        @RequiredProperty
        private String reloadSuccess;
        @RequiredProperty
        private String reloadDatabaseDriverFailed;
        @RequiredProperty
        private String reloadFetcherBusy;
        @RequiredProperty
        private String reloadFailed;
        @RequiredProperty
        private String playerPretendSuccess;
        @RequiredProperty
        private String playerPretendFailed;
        @RequiredProperty
        private String debugHeader;
    }

    @RequiredProperty
    private DatabaseConfiguration database;
    @RequiredProperty
    private OnlineAuthenticationConfiguration onlineAuthentication;
    @RequiredProperty
    private RandomizerConfiguration playerRandomizer;
    @RequiredProperty
    private Map<String, String> customPlayerNames;
    @RequiredProperty
    private Map<String, String> swappedUuids;
    @RequiredProperty
    private CommandMessagesConfiguration commandMessages;
}
