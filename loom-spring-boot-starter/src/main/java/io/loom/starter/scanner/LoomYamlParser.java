package io.loom.starter.scanner;

import io.loom.core.model.PassthroughDefinition;
import io.loom.core.registry.ApiRegistry;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
public class LoomYamlParser {

    private final ApiRegistry apiRegistry;

    public LoomYamlParser(ApiRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    @SuppressWarnings("unchecked")
    public void parse(String resourcePath) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            log.debug("[Loom] No {} found on classpath, skipping YAML config", resourcePath);
            return;
        }

        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(is);

        if (config == null) {
            log.debug("[Loom] Empty YAML config: {}", resourcePath);
            return;
        }

        // Parse passthrough routes
        List<Map<String, Object>> passthroughs =
                (List<Map<String, Object>>) config.get("passthrough");
        if (passthroughs != null) {
            for (Map<String, Object> pt : passthroughs) {
                String path = (String) pt.get("path");
                String method = (String) pt.getOrDefault("method", "GET");
                String upstream = (String) pt.get("upstream");
                String upstreamPath = (String) pt.getOrDefault("upstream-path", path);
                String summary = (String) pt.getOrDefault("summary", "");
                String description = (String) pt.getOrDefault("description", "");

                String[] tags = new String[0];
                Object tagsObj = pt.get("tags");
                if (tagsObj instanceof List<?> tagList) {
                    tags = tagList.stream()
                            .map(Object::toString)
                            .toArray(String[]::new);
                }

                PassthroughDefinition definition = new PassthroughDefinition(
                        method, path, upstream, upstreamPath,
                        summary, description, tags);
                apiRegistry.registerPassthrough(definition);
                log.info("[Loom] Parsed YAML passthrough: {} {} -> {}{}",
                        method, path, upstream, upstreamPath);
            }
        }

        log.info("[Loom] Parsed YAML config: {}", resourcePath);
    }
}
