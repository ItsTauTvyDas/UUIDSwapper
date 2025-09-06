package me.itstautvydas.uuidswapper.config;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import me.itstautvydas.uuidswapper.Utils;
import me.itstautvydas.uuidswapper.annotation.RequiredProperty;
import me.itstautvydas.uuidswapper.multiplatform.MultiPlatform;
import me.itstautvydas.uuidswapper.database.DriverImplementation;
import me.itstautvydas.uuidswapper.database.driver.JsonImplementation;
import me.itstautvydas.uuidswapper.database.driver.MemoryCacheImplementation;
import me.itstautvydas.uuidswapper.database.driver.SQLiteImplementation;
import me.itstautvydas.uuidswapper.enums.ConditionsMode;
import me.itstautvydas.uuidswapper.enums.ConsoleMessageType;
import me.itstautvydas.uuidswapper.enums.FallbackUsage;
import me.itstautvydas.uuidswapper.enums.ServiceStateEvent;
import me.itstautvydas.uuidswapper.json.PostProcessable;
import me.itstautvydas.uuidswapper.processor.*;
import me.itstautvydas.uuidswapper.service.RateLimitable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"FieldMayBeFinal", "unused"})
@ToString
@Getter
public class Configuration {
    @ToString @Getter
    @ReadMeTitle(order = -999)
    @ReadMeDescription("This only works on paper and velocity!")
    public static class PaperConfiguration {
        @ReadMeDescription("Should plugin use Paper's MiniMessages (color codes with `&` won't work if this is enabled)")
        private boolean useMiniMessages;
    }

    @ToString @Getter
    @ReadMeTitle(order = -998)
    @ReadMeDescription("Database is used for caching fetched player data.")
    public static class DatabaseConfiguration {
        @RequiredProperty
        @ReadMeDescription("Should database be enabled")
        private boolean enabled;
        @RequiredProperty @SerializedName("driver")
        @ReadMeDescription("Which driver to use from `drivers` array")
        private String driverName;
        @SerializedName("debug")
        @ReadMeDescription("Should debug message to console be enabled (shows when connection was open/close etc.)")
        private boolean debugEnabled = false;
        @ReadMeDescription("Defined drivers implementations")
        @ReadMeLinkTo({
                SQLiteImplementation.class,
                JsonImplementation.class,
                MemoryCacheImplementation.class
        })
        private List<DriverImplementation> drivers = new ArrayList<>();

        public DriverImplementation getDriver(String name) {
            return drivers.stream()
                    .filter(driver -> driver.getName().equals(name))
                    .findFirst()
                    .orElse(null);
        }
    }

    @ToString @Getter
    @ReadMeTitle()
    @ReadMeDescription("Request services to get player's UUID (unique id) or online properties, player can get disconnected " +
            "if something fails, unless response handlers are specified in the service's configuration.")
    public static class OnlineAuthenticationConfiguration implements PostProcessable {
        @RequiredProperty
        @ReadMeDescription("Should online authentication be enabled")
        private boolean enabled;
        @ReadMeDescription("Should offline players be allowed on online/secure server (Velocity/BungeeCord only)")
        private boolean allowOfflinePlayers;
        @RequiredProperty @SerializedName("use-service")
        @ReadMeDescription("Which service to use")
        private String serviceName;
        @RequiredProperty
        @ReadMeDescription("Which services to use next (in order) if above one fails (`array`)")
        private LinkedHashSet<String> fallbackServices;
        @ReadMeDescription("For how much time should last used successful service be remembered")
        private long fallbackServiceRememberTime = 21600;
        @ReadMeDescription("Max timeout for all requests summed up (-1 to disable)")
        private long maxTimeout = 6000;
        @ReadMeDescription("Min timeout for a single request (0 to disable)")
        private long minTimeout = 1000;
        @ReadMeDescription("Check if player connects with online UUID (skips service requests). This works by comparing generated offline UUID to player's UUID")
        private boolean checkForOnlineUniqueId = true;
        @ReadMeDescription("Send plugin messages to console (e.g., when successfully fetched)")
        private boolean sendMessagesToConsole = true;
        @ReadMeDescription("Send plugin error messages")
        private boolean sendErrorMessagesToConsole = true;
        @ReadMeDescription("How much time to wait (milliseconds) before requesting already used service")
        private long serviceConnectionThrottle = 5000;
        @RequiredProperty
        @ReadMeDescription("Connection throttled disconnect message")
        private String serviceConnectionThrottledMessage;
        @RequiredProperty
        @ReadMeDescription("Cache configuration")
        private CachingConfiguration caching;
        @RequiredProperty
        @ReadMeDescription("Everything that is defined here will be copied over all services (unless some service override that value)")
        private DefaultServiceConfiguration serviceDefaults;
        @RequiredProperty
        @ReadMeDescription("Service list (`array`)")
        @ReadMeLinkTo(ServiceConfiguration.class)
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
            if (maxTimeout > 0)
                maxTimeout = Math.max(maxTimeout, 500);
            fallbackServiceRememberTime = Math.max(fallbackServiceRememberTime, -1);
        }
    }

    @ToString @Getter
    @ReadMeTitle("Service Cache Options")
    @ReadMeDescription("Fetched player data caching")
    public static class CachingConfiguration {
        @RequiredProperty
        @ReadMeDescription("Should fetched data be saved")
        private boolean enabled;
        @ReadMeDescription("Player data expiration time (minutes)")
        private long keepTime = 7200;
    }

    @ToString @Getter
    @ReadMeTitle("Default Service Options")
    @ReadMeDescription("Whatever is defined in this section is also going to be available in [service's configuration](#service-configuration).")
    public static class DefaultServiceConfiguration extends RateLimitable {
        @ReadMeDescription("Request method to use (GET or POST)")
        protected String requestMethod;
        @ReadMeDescription("Disconnect message when service failed to get UUID from specified JSON path")
        protected String badUniqueIdDisconnectMessage;
        @ReadMeDescription("Default disconnect message")
        protected String defaultDisconnectMessage;
        @ReadMeDescription("Unknown connection error disconnect message")
        protected String connectionErrorDisconnectMessage;
        @ReadMeDescription("Unexpected request's returned status code disconnect message")
        protected String badStatusDisconnectMessage;
        @ReadMeDescription("Internal unknown/overlooked error disconnect message")
        protected String unknownErrorDisconnectMessage;
        @ReadMeDescription("Service timed-out disconnect message")
        protected String timeoutDisconnectMessage;
        @ReadMeDescription("Service rate limited disconnect message")
        protected String rateLimitedDisconnectMessage;
        @ReadMeDescription("Failed to get properties disconnect message")
        protected String propertiesFailedDisconnectMessage;
        @ReadMeDescription("Max request per minute for the service")
        protected Integer maxRequestsPerMinute;
        @ReadMeDescription("Expected status code from service's endpoint")
        protected Integer expectStatusCode;
        @ReadMeDescription("Service's time-out time in milliseconds")
        protected long timeout;
        @Getter(AccessLevel.NONE)
        @ReadMeDescription("Should service's fetched player data be cached?")
        protected Boolean allowCaching;
        @Getter(AccessLevel.NONE)
        @ReadMeDescription("Should properties be required (disconnect otherwise)")
        protected Boolean requireProperties;
        @ReadMeDescription("Custom placeholders `(key -> value)` to use in disconnect messages and response handlers")
        protected Map<String, Object> customPlaceholders;
        @ReadMeDescription("Custom disconnect messages `(key -> value)` based on returned service's status code")
        protected Map<String, Object> customStatusCodeDisconnectMessages;
        @SerializedName("debug") @Getter(AccessLevel.NONE)
        @ReadMeDescription("Should debug messages to console be enabled")
        protected Boolean debugEnabled;
        @ReadMeDescription("On which circumstances should next (fallback) service be used")
        protected LinkedHashSet<FallbackUsage> useFallbacks;
        @ReadMeDescription("Post `(key -> value)` data for service's request to the endpoint")
        protected Map<String, String> postData;
        @ReadMeDescription("Query `(key -> value)` data for service's request to the endpoint")
        protected Map<String, String> queryData;
        @ReadMeDescription("Headers `(key -> value)` for service's request to the endpoint")
        protected Map<String, String> headers;

        public boolean isDebugEnabled() {
            return Boolean.TRUE.equals(debugEnabled);
        }

        public boolean isAllowCaching() {
            return Boolean.TRUE.equals(allowCaching);
        }

        public boolean isRequireProperties() {
            return Boolean.TRUE.equals(requireProperties);
        }
    }

    @ToString(callSuper = true) @Getter
    @ReadMeTitle()
    @ReadMeDescription("A service is used for fetching player's data.")
    public static class ServiceConfiguration extends DefaultServiceConfiguration implements PostProcessable {
        @ReadMeDescription("Should this service be enabled")
        private boolean enabled = true;
        @RequiredProperty
        @ReadMeDescription("Name for the service that can be used in `use-service` or `fallback-services`")
        private String name;
        @RequiredProperty
        @ReadMeDescription("Endpoint to where request should be sent")
        private String endpoint;
        @ReadMeDescription("JSON path to player's unique ID (support dashless UUIDs too), leave empty if response is suppose to be text only")
        private String jsonPathToUuid;
        @ReadMeDescription("JSON path to player's properties")
        private String jsonPathToProperties;
        @ReadMeDescription("Which services should be used for fetching player's properties, `json-path-to-properties` is also included if defined")
        private LinkedHashSet<String> requestServicesForProperties = new LinkedHashSet<>();
        @ReadMeDescription("Custom response handlers")
        @ReadMeLinkTo(ResponseHandlerConfiguration.class)
        private List<ResponseHandlerConfiguration> responseHandlers = new ArrayList<>();

        public void setDefaults(DefaultServiceConfiguration service) {
            this.requestMethod = defaultValue(requestMethod, service.getRequestMethod(), "GET");
            this.badUniqueIdDisconnectMessage = defaultValue(badUniqueIdDisconnectMessage, service.badUniqueIdDisconnectMessage, null);
            this.defaultDisconnectMessage = defaultValue(defaultDisconnectMessage, service.defaultDisconnectMessage, null);
            this.connectionErrorDisconnectMessage = defaultValue(connectionErrorDisconnectMessage, service.connectionErrorDisconnectMessage, null);
            this.badStatusDisconnectMessage = defaultValue(badStatusDisconnectMessage, service.badStatusDisconnectMessage, null);
            this.unknownErrorDisconnectMessage = defaultValue(unknownErrorDisconnectMessage, service.unknownErrorDisconnectMessage, null);
            this.timeoutDisconnectMessage = defaultValue(timeoutDisconnectMessage, service.timeoutDisconnectMessage, null);
            this.rateLimitedDisconnectMessage = defaultValue(rateLimitedDisconnectMessage, service.rateLimitedDisconnectMessage, null);
            this.propertiesFailedDisconnectMessage = defaultValue(propertiesFailedDisconnectMessage, service.propertiesFailedDisconnectMessage, null);
            this.expectStatusCode = defaultValue(expectStatusCode, service.expectStatusCode, 200);
            this.allowCaching = defaultValue(allowCaching, service.allowCaching, true);
            this.requireProperties = defaultValue(requireProperties, service.requireProperties, false);
            this.debugEnabled = defaultValue(debugEnabled, service.debugEnabled, false);
            this.useFallbacks = defaultValue(useFallbacks, service.useFallbacks, new LinkedHashSet<>());
            this.postData = defaultValue(postData, service.postData, new HashMap<>());
            this.queryData = defaultValue(queryData, service.queryData, new HashMap<>());
            this.headers = defaultValue(headers, service.headers, new HashMap<>());
            this.maxRequestsPerMinute = defaultValue(maxRequestsPerMinute, service.maxRequestsPerMinute, null);
            this.customPlaceholders = combineMap(customPlaceholders, service.customPlaceholders);
            this.customStatusCodeDisconnectMessages = combineMap(customStatusCodeDisconnectMessages, service.customStatusCodeDisconnectMessages);
        }

        private Map<String, Object> combineMap(Map<String, Object> current, Map<String, Object> defaultMap) {
            if (current == null && defaultMap == null)
                return new HashMap<>();
            return new HashMap<>() {
                {
                    if (current != null)
                        putAll(current);
                    if (defaultMap != null)
                        putAll(defaultMap);
                }
            };
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

        public boolean canRetrieveUniqueId() {
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

    @ToString @Getter
    @ReadMeTitle("Response Handler")
    @ReadMeDescription("Handle request's response - allow player to join/disconnect them, set properties required and " +
            "etc., based on conditions. Response handlers are checked one by one until it finds the matching one, and executes it.")
    public static class ResponseHandlerConfiguration implements PostProcessable {
        @RequiredProperty
        @ReadMeDescription("In which order should this response handler be executed (ascending)")
        private long order = 9999;
        @RequiredProperty
        @ReadMeDescription("""
                At what event should this response handler be executed. Available events:
                \t`PRE_REQUEST` - action before request
                \t`POST_REQUEST` - action after request
                \t`FETCHED_UUID` - action when UUID was fetched
                \t`PRE_PROPERTIES_FETCH` - action when properties are about to be fetched (and checked if they should be fetched)
                \t`FETCHED_PROPERTIES` - action when properties were fetched
                \t`PRE_FALLBACK_USE` - action on before any fallback use
                \t`PLAYER_DATA_FETCHED` - last action when player's UUID and/or properties were fetched""")
        private ServiceStateEvent event;
        @ReadMeDescription("Should the player be allowed to join (this is forceful option, meaning if it's true, no matter " +
                "what, the player will be able to join, if false - instant disconnect, null - allow plugin to decide internally)")
        private Boolean allowPlayerToJoin;
        @ReadMeDescription("Should properties be applied for the player")
        private boolean applyProperties;
        @ReadMeDescription("Should properties be required to be requested and applied (disconnect if no properties or fallback could be used)")
        private Boolean requireProperties;
        @ReadMeDescription("Custom disconnect message if `allow-player-to-join` is set to false")
        private String disconnectMessage;
        @ReadMeDescription("Send custom message to console (extra debugging?)")
        private String messageToConsole;
        @ReadMeDescription("Message type to send to console (INFO/WARNING/ERROR)")
        private ConsoleMessageType consoleMessageType;
        @ReadMeDescription("Message type to send to console (AND / OR), default is AND")
        private ConditionsMode conditionsMode;
        @ReadMeDescription("Should conditions ignore placeholder value casing")
        private boolean ignoreConditionsCase;
        @ReadMeDescription("When comparing, should placeholder's value and specified value be converted to string for comparison")
        private boolean forceStringOnConditions;
        @ReadMeDescription("Conditions (placeholder -> value) to check if response handler should be executed. Note that not everything can be a text (in \"\" quotes), " +
                "comparison is done checking if both objects are equal,\nor if you really want simplicity, enable above setting")
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
                                MultiPlatform.get().getRawConfiguration(),
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
            if (applyProperties)
                json.addProperty("applyProperties", true);
            return Utils.DEFAULT_GSON.toJson(json);
        }
    }

    @ToString @Getter
    @ReadMeTitle()
    @ReadMeDescription("Randomize player's username and UUID. This will be prioritized if online authentication is " +
            "enabled (it also depends if `fetch-properties-from-services` is enabled)!")
    public static class RandomizerConfiguration {
        @ToString() @Getter
        public static class UniqueIdRandomizer {
            @RequiredProperty
            @ReadMeDescription("Should plugin randomize player's unique ID (UUID)")
            private boolean randomize;
            @RequiredProperty
            @ReadMeDescription("Should player's random unique ID be remembered")
            private boolean save;
            @RequiredProperty
            @ReadMeDescription("When should saved unique ID expire (seconds)")
            private boolean expire;
        }

        @ToString(callSuper = true) @Getter
        @ReadMeCallSuperClass({
                "randomize", "Should plugin randomize player's username",
                "save", "Should player's random username be remembered.",
                "expire", "When should saved username expire (seconds)"
        })
        public static class UsernameRandomizer extends UniqueIdRandomizer {
            @RequiredProperty
            private String outOfUsernamesDisconnectMessage;
            @RequiredProperty
            @ReadMeDescription("Random characters to pick from")
            private String characters;
            @RequiredProperty
            @ReadMeDescription("Minimum length of randomized username")
            private int fromLength;
            @RequiredProperty
            @ReadMeDescription("Maximum length of randomized username")
            private int toLength;
        }

        @RequiredProperty
        @ReadMeDescription("Should randomizer be enabled")
        private boolean enabled;
        @RequiredProperty
        @ReadMeDescription("Should properties be applied (skin textures)")
        private boolean useProperties;
        @RequiredProperty
        @ReadMeDescription("Should properties first be fetched from services (doesn't matter if online authentication is disabled")
        private boolean fetchPropertiesFromServices;
        @RequiredProperty @SerializedName("username")
        @ReadMeMergeClass
        private UsernameRandomizer usernameSettings;
        @RequiredProperty @SerializedName("unique-id")
        @ReadMeMergeClass
        private UniqueIdRandomizer uniqueIdSettings;

        public boolean isFetchPropertiesFromServices() {
            return fetchPropertiesFromServices && useProperties;
        }
    }

    @ToString @Getter
    @ReadMeTitle("Swapped Unique IDs Configuration")
    @ReadMeDescription("Swap player's unique id/username to another unique id.")
    public static class SwappedUniqueIdsConfiguration {
        @RequiredProperty
        @ReadMeDescription("Should player unique id swapping be enabled")
        private boolean enabled;
        @RequiredProperty
        @ReadMeDescription("A map `(uuid/username -> uuid)` for swapped unique ids")
        private Map<String, String> swap;
    }

    @ToString(callSuper = true)
    @ReadMeCallSuperClass({
            "enabled", "Should player username swapping be enabled",
            "swap", "A map `(uuid/username -> username)` for swapped unique ids"
    })
    @ReadMeTitle()
    @ReadMeDescription("Swap player's UUID/username to a new username.")
    public static class SwappedPlayerNamesConfiguration extends SwappedUniqueIdsConfiguration {
    }

    @ToString @Getter
    @ReadMeTitle()
    @ReadMeDescription("""
            Messages for command outputs. Placeholders available in all messages:
            `{command}` - command name
            `{prefix}` - prefix defined in `prefix`""")
    public static class CommandMessagesConfiguration {
        @RequiredProperty @ReadMeDescription("Command's prefix")
        private String prefix;
        @RequiredProperty @ReadMeDescription("Message when no arguments")
        private String noArguments;
        @RequiredProperty @ReadMeDescription("Message when reload was successful. Available placeholders:\n" +
                "`{took}` - how many milliseconds took to ")
        private String reloadSuccess;
        @RequiredProperty @ReadMeDescription("Message when database driver failed to load. Available placeholders:\n" +
                "`{driver}` - driver's name that failed to load")
        private String reloadDatabaseDriverFailed;
        @RequiredProperty @ReadMeDescription("""
                Message when plugin failed to reload. Available placeholders:
                `{error.class}` - exception's full (with package) class
                `{error.class-name}` - exception's class name
                `{error.message}` - exception's message""")
        private String reloadFailed;
        @RequiredProperty @ReadMeDescription("""
                Message when player successfully pretends to be another player on next server join. Available placeholders:
                `{new_username}` - player's new username
                `{new_uuid}` - player's new unique ID""")
        private String playerPretendSuccess;
        @RequiredProperty @ReadMeDescription("Message when plugin wasn't able to fake another player")
        private String playerPretendFailed;
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
