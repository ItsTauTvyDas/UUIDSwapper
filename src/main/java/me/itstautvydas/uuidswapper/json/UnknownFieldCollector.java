package me.itstautvydas.uuidswapper.json;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

public class UnknownFieldCollector {
    @Getter
    Map<String, Object> unknownFields = new LinkedHashMap<>();
}
