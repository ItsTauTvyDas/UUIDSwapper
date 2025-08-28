package me.itstautvydas.uuidswapper.config;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.annotation.RequiredProperty;
import me.itstautvydas.uuidswapper.crossplatform.PluginWrapper;
import me.itstautvydas.uuidswapper.enums.ConditionsMode;
import me.itstautvydas.uuidswapper.enums.ConsoleMessageType;
import me.itstautvydas.uuidswapper.enums.FallbackUsage;
import me.itstautvydas.uuidswapper.enums.ServiceStateEvent;
import me.itstautvydas.uuidswapper.interfaces.PostProcessable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
@ToString
@Getter
public class Configuration {
    @ToString
    @Getter
    public static class PaperConfiguration {
        private boolean useMiniMessages;
    }

    @ToString
    @Getter
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

    @ToString
    @Getter
    public static class CachingConfiguration {
        @RequiredProperty
        private boolean enabled;
        private long keepTime = 7200;
    }

    @ToString
    @Getter
    public static class ResponseHandlerConfiguration implements PostProcessable {
        @RequiredProperty
        private long order = 9999;
        @RequiredProperty
        private ServiceStateEvent event;
        private Boolean allowPlayerToJoin;
        private Boolean applyProperties;
        private String disconnectMessage;
        private String fetchProperties;
        private String messageToConsole;
        private ConsoleMessageType consoleMessageType;

        private ConditionsMode conditionsMode;
        private boolean ignoreConditionsCase;
        private boolean forceStringOnConditions;
        private Map<String, Object> conditions;

        public boolean testConditions(Map<String, Object> placeholders) {
            if (conditions == null || conditions.isEmpty())
                return true;
            Boolean result = null;
            for (var entry : conditions.entrySet()) {
                boolean conditionResult;
                if (entry.getKey().startsWith("?")) {
                    if (entry.getValue() instanceof Boolean bool)
                        conditionResult = placeholders.containsKey(entry.getKey().substring(1)) == bool;
                    else
                        conditionResult = false;
                } else if (entry.getKey().startsWith("config::")) {
                    try {
                        var jsonValue = Utils.getJsonValue(
                                PluginWrapper.getCurrent().getRawConfiguration(),
                                entry.getKey().substring(8)
                        );
                        if (entry.getValue() == null || jsonValue == null) {
                            conditionResult = Objects.equals(entry.getValue(), jsonValue);
                        } else {
                            if (ignoreConditionsCase)
                                conditionResult = jsonValue.toString().equalsIgnoreCase(entry.getValue().toString());
                            else if (forceStringOnConditions)
                                conditionResult = jsonValue.toString().equals(entry.getValue().toString());
                            else
                                conditionResult = jsonValue.equals(entry.getValue());
                        }
                    } catch (Exception e) {
                        conditionResult = false;
                    }
                } else {
                    @Nullable var value = placeholders.get(entry.getKey());
                    if (entry.getValue() == null || value == null) {
                        conditionResult = Objects.equals(entry.getValue(), value);
                    } else {
                        if (ignoreConditionsCase)
                            conditionResult = entry.getValue().toString().equalsIgnoreCase(value.toString());
                        else if (forceStringOnConditions)
                            conditionResult = entry.getValue().toString().equals(value.toString());
                        else
                            conditionResult = entry.getValue().equals(value);
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
            return result;
        }

        @Override
        public void postProcessed() {
            if (consoleMessageType == null)
                consoleMessageType = ConsoleMessageType.INFO;
            if (conditionsMode == null)
                conditionsMode = ConditionsMode.AND;
        }

        public String resultToString() {
            var json = new JsonObject();
            json.addProperty("event", event.toString());
            json.addProperty("order", order);
            json.addProperty("mode", conditionsMode.toString());
            if (allowPlayerToJoin != null)
                json.addProperty("allowPlayerToJoin", allowPlayerToJoin);
            if (allowPlayerToJoin != null)
                json.addProperty("useFallback", allowPlayerToJoin);
            if (applyProperties != null)
                json.addProperty("applyProperties", applyProperties);
            return Utils.DEFAULT_GSON.toJson(json);
        }
    }

    @ToString
    @Getter
    public static class DefaultServiceConfiguration {
        protected String requestMethod;
        protected String badUuidDisconnectMessage;
        protected String defaultDisconnectMessage;
        protected String connectionErrorDisconnectMessage;
        protected String serviceBadStatusDisconnectMessage;
        protected String unknownErrorDisconnectMessage;
        protected String serviceTimeoutDisconnectMessage;
        protected Integer expectStatusCode;
        protected Long timeout;
        protected Boolean allowCaching;
        @SerializedName("debug")
        protected Boolean debugEnabled;
        protected LinkedHashSet<FallbackUsage> useFallbacks;
        protected Map<String, String> postData;
        protected Map<String, String> queryData;
        protected Map<String, String> headers;

        public long getTimeout() {
            return timeout;
        }

        public boolean isDebugEnabled() {
            return Boolean.TRUE.equals(debugEnabled);
        }

        public boolean isAllowCaching() {
            return Boolean.TRUE.equals(allowCaching);
        }
    }

    @ToString(callSuper = true)
    @Getter
    public static class ServiceConfiguration extends DefaultServiceConfiguration implements PostProcessable {
        private boolean enabled;
        @RequiredProperty
        private String name;
        @RequiredProperty
        private String endpoint;
        private String jsonPathToUuid;
        private String jsonPathToProperties;
        private LinkedHashSet<String> requestServicesForProperties = new LinkedHashSet<>();
        private List<ResponseHandlerConfiguration> responseHandlers = new ArrayList<>();

        public void setDefaults(DefaultServiceConfiguration service) {
            this.requestMethod = defaultValue(requestMethod, service.getRequestMethod(), "GET");
            this.badUuidDisconnectMessage = defaultValue(badUuidDisconnectMessage, service.getBadUuidDisconnectMessage(), null);
            this.defaultDisconnectMessage = defaultValue(defaultDisconnectMessage, service.getDefaultDisconnectMessage(), null);
            this.connectionErrorDisconnectMessage = defaultValue(connectionErrorDisconnectMessage, service.getConnectionErrorDisconnectMessage(), null);
            this.serviceBadStatusDisconnectMessage = defaultValue(serviceBadStatusDisconnectMessage, service.getServiceBadStatusDisconnectMessage(), null);
            this.unknownErrorDisconnectMessage = defaultValue(unknownErrorDisconnectMessage, service.getUnknownErrorDisconnectMessage(), null);
            this.serviceTimeoutDisconnectMessage = defaultValue(serviceTimeoutDisconnectMessage, service.getServiceTimeoutDisconnectMessage(), null);
            this.expectStatusCode = defaultValue(expectStatusCode, service.expectStatusCode, 200);
            this.timeout = defaultValue(timeout, service.timeout, 3000L);
            this.allowCaching = defaultValue(allowCaching, service.allowCaching, true);
            this.debugEnabled = defaultValue(debugEnabled, service.debugEnabled, false);
            this.useFallbacks = defaultValue(useFallbacks, service.getUseFallbacks(), new LinkedHashSet<>());
            this.postData = defaultValue(postData, service.getPostData(), new HashMap<>());
            this.queryData = defaultValue(queryData, service.getQueryData(), new HashMap<>());
            this.headers = defaultValue(headers, service.getHeaders(), new HashMap<>());
        }

        private <T> T defaultValue(T current, T defaultValue, T defaultValueIfFail) {
            if (current == null) {
                if (defaultValue == null)
                    return defaultValueIfFail;
                return defaultValue;
            }
            return current;
        }

        public ResponseHandlerConfiguration executeResponseHandlers(ServiceStateEvent state, Map<String, Object> placeholders) {
            for (var handler : responseHandlers) {
                if (handler.event == state && handler.testConditions(placeholders))
                    return handler;
            }
            return null;
        }

        public boolean isForProperties() {
            return jsonPathToUuid == null && canRetrieveProperties();
        }

        public boolean isForUniqueId() {
            return jsonPathToUuid != null;
        }

        public boolean canRetrieveProperties() {
            return jsonPathToProperties != null;
        }

        @Override
        public void postProcessed() {
            responseHandlers.sort(Comparator.comparingLong(ResponseHandlerConfiguration::getOrder));
        }
    }

    @ToString
    @Getter
    public static class OnlineAuthenticationConfiguration implements PostProcessable {
        @RequiredProperty
        private boolean enabled;
        @RequiredProperty
        @SerializedName("use-service")
        private String serviceName;
        @RequiredProperty
        private LinkedHashSet<String> fallbackServices;
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
        private DefaultServiceConfiguration serviceDefaults;
        @RequiredProperty
        private List<ServiceConfiguration> services;

        public ServiceConfiguration getService(String name) {
            if (name == null) return null;
            return services.stream()
                    .filter(s -> Objects.equals(s.getName(), name))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void postProcessed() {
            serviceConnectionThrottle = Math.max(serviceConnectionThrottle, 0);
            minTimeout = Math.max(minTimeout, 0);
            maxTimeout = Math.max(maxTimeout, 500);
            fallbackServiceRememberTime = Math.max(fallbackServiceRememberTime, -1);
        }
    }

    @ToString
    @Getter
    public static class RandomizerConfiguration {
        @Getter
        public static class UniqueIdRandomizer {
            @RequiredProperty
            private boolean randomize;
            @RequiredProperty
            private boolean save;
        }

        @ToString(callSuper = true)
        @Getter
        public static class UsernameRandomizer extends UniqueIdRandomizer {
            @RequiredProperty
            private String outOfUsernamesDisconnectMessage;
            @RequiredProperty
            private String characters;
            @RequiredProperty
            private int fromLength;
            @RequiredProperty
            private int toLength;
        }

        @RequiredProperty
        private boolean enabled;
        @RequiredProperty
        private boolean useProperties;
        @RequiredProperty
        private boolean fetchPropertiesFromServices;
        @RequiredProperty @SerializedName("username")
        private UsernameRandomizer usernameSettings;
        @RequiredProperty @SerializedName("unique-id")
        private UniqueIdRandomizer uniqueIdSettings;

        public boolean isFetchPropertiesFromServices() {
            return fetchPropertiesFromServices && useProperties;
        }
    }

    @ToString
    @Getter
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

    @ToString
    @Getter
    public static class SwappedUniqueIdsConfiguration {
        @RequiredProperty
        private boolean enabled;
        @RequiredProperty
        private Map<String, String> swap;
    }

    @ToString(callSuper = true)
    public static class SwappedPlayerNamesConfiguration extends SwappedUniqueIdsConfiguration {
    }

    @RequiredProperty
    private PaperConfiguration paper;
    @RequiredProperty
    private DatabaseConfiguration database;
    @RequiredProperty
    private OnlineAuthenticationConfiguration onlineAuthentication;
    @RequiredProperty
    private RandomizerConfiguration playerRandomizer;
    @RequiredProperty
    private SwappedUniqueIdsConfiguration swappedUniqueIds;
    @RequiredProperty
    private SwappedPlayerNamesConfiguration swappedPlayerNames;
    @RequiredProperty
    private CommandMessagesConfiguration commandMessages;
}
