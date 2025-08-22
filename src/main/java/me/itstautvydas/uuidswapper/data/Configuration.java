package me.itstautvydas.uuidswapper.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import me.itstautvydas.uuidswapper.annotation.RequiredProperty;
import me.itstautvydas.uuidswapper.enums.ConditionsMode;
import me.itstautvydas.uuidswapper.enums.FallbackUsage;
import me.itstautvydas.uuidswapper.enums.ResponseHandlerState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Configuration {
    @Data
    public static class DatabaseConfiguration {
        @RequiredProperty
        private boolean enabled;
        private boolean downloadDriver = true;
        @RequiredProperty
        private String driver;
        private String file;
        private long timeout = 5000;
        private long keepOpenFor = 10;
        private long timerRepeatTime = 1;
        private boolean debug = false;
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
        private boolean ignoreStatusCode = false;
        private ConditionsMode conditionsMode = ConditionsMode.AND;
        private boolean ignoreConditionsCase = false;
        private Map<String, Object> conditions = new HashMap<>();
    }

    @Data static class DefaultServiceConfiguration {
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
        private boolean debug = false;
        private List<FallbackUsage> useFallbacks = new ArrayList<>();
        private List<ResponseHandlerConfiguration> responseHandlers = new ArrayList<>();
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
    }

    @Data
    public static class OnlineAuthenticationConfiguration {
        @RequiredProperty
        private boolean enabled;
        @RequiredProperty
        private String useService;
        private List<String> fallbackServices = new ArrayList<>();
        private long fallbackServiceRememberTime = 21600;
        private long maxTimeout = 6000;
        private long minTimeout = 1000;
        private boolean checkForOnlineUuid = true;
        private boolean sendMessagesToConsole = true;
        private boolean sendErrorMessagesToConsole = true;
        private long serviceConnectionThrottle = 5000;
        private String serviceConnectionThrottledMessage;
        @RequiredProperty
        private CachingConfiguration caching;
        @RequiredProperty
        private UsernameChangesConfiguration usernameChanges;
        @RequiredProperty
        private DefaultServiceConfiguration serviceDefaults;
        private List<ServiceConfiguration> services = new ArrayList<>();
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
            private String characters;
        }

        @RequiredProperty
        private boolean enabled;
        @SerializedName("username") @RequiredProperty
        private UsernameRandomizer usernameSettings;
        @SerializedName("uuid") @RequiredProperty
        private UuidRandomizer uuidSettings;
    }

    @RequiredProperty
    private OnlineAuthenticationConfiguration onlineAuthentication;
    @RequiredProperty
    private DatabaseConfiguration database;
    @RequiredProperty
    private RandomizerConfiguration playerRandomizer;
    private Map<String, String> swappedUuids = new HashMap<>();
    private Map<String, String> customPlayerNames = new HashMap<>();
}
