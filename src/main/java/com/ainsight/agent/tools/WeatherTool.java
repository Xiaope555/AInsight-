package com.ainsight.agent.tools;

import com.ainsight.agent.core.AgentTool;
import com.ainsight.agent.core.ToolParam;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具二:调用外部 API 查实时天气(open-meteo,免费无需 Key)。
 * 流程:城市名 -> 地理编码拿经纬度 -> 查当前天气。
 * 这里演示 RestClient:简单同步 HTTP 调用它比 WebClient 更轻;需要流式才用 WebClient。
 */
@Component
@RequiredArgsConstructor
public class WeatherTool implements AgentTool {

    /** WMO 天气代码 -> 中文描述(常见项) */
    private static final Map<Integer, String> WEATHER_DESC = Map.ofEntries(
            Map.entry(0, "晴"), Map.entry(1, "基本晴朗"), Map.entry(2, "局部多云"), Map.entry(3, "阴"),
            Map.entry(45, "雾"), Map.entry(48, "冻雾"),
            Map.entry(51, "小毛毛雨"), Map.entry(53, "毛毛雨"), Map.entry(55, "大毛毛雨"),
            Map.entry(61, "小雨"), Map.entry(63, "中雨"), Map.entry(65, "大雨"),
            Map.entry(71, "小雪"), Map.entry(73, "中雪"), Map.entry(75, "大雪"),
            Map.entry(80, "小阵雨"), Map.entry(81, "阵雨"), Map.entry(82, "强阵雨"),
            Map.entry(95, "雷暴"), Map.entry(96, "雷暴伴小冰雹"), Map.entry(99, "雷暴伴大冰雹"));

    private final ObjectMapper objectMapper;

    /** 外部 API 必须设超时:第三方一卡,不能拖死我们整个 Agent 请求 */
    private final RestClient restClient = buildClient();

    private static RestClient buildClient() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Data
    public static class Params {
        @ToolParam(description = "城市名,例如:北京、上海、Hangzhou")
        private String city;
    }

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "查询指定城市当前的实时天气,包括温度、天气状况、风速。当用户询问天气时使用。";
    }

    @Override
    public Class<?> parameterType() {
        return Params.class;
    }

    @Override
    public String execute(Object args) throws Exception {
        Params params = (Params) args;

        // 1) 地理编码:城市名 -> 经纬度
        GeoResponse geo = restClient.get()
                .uri("https://geocoding-api.open-meteo.com/v1/search?name={city}&count=1&language=zh",
                        params.getCity())
                .retrieve()
                .body(GeoResponse.class);
        if (geo == null || geo.getResults() == null || geo.getResults().isEmpty()) {
            return "未找到城市「" + params.getCity() + "」,请确认城市名。";
        }
        GeoResponse.GeoResult place = geo.getResults().get(0);

        // 2) 查当前天气
        WeatherResponse weather = restClient.get()
                .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                                + "&current=temperature_2m,weather_code,wind_speed_10m",
                        place.getLatitude(), place.getLongitude())
                .retrieve()
                .body(WeatherResponse.class);
        if (weather == null || weather.getCurrent() == null) {
            return "天气服务暂时不可用,请稍后再试。";
        }
        WeatherResponse.Current current = weather.getCurrent();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", place.getName());
        result.put("temperature", current.getTemperature() + "°C");
        result.put("condition", WEATHER_DESC.getOrDefault(current.getWeatherCode(), "未知"));
        result.put("windSpeed", current.getWindSpeed() + " km/h");
        return objectMapper.writeValueAsString(result);
    }

    // ---- open-meteo 响应结构(只取需要的字段) ----

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoResponse {
        private List<GeoResult> results;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class GeoResult {
            private String name;
            private Double latitude;
            private Double longitude;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherResponse {
        private Current current;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Current {
            @JsonProperty("temperature_2m")
            private Double temperature;
            @JsonProperty("weather_code")
            private Integer weatherCode;
            @JsonProperty("wind_speed_10m")
            private Double windSpeed;
        }
    }
}
