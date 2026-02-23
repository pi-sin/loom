package io.loom.starter.web;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathMatcher {

    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)}");
    private final String template;
    private final Pattern compiledPattern;
    private final List<String> variableNames;

    public PathMatcher(String template) {
        this.template = template;
        this.variableNames = new ArrayList<>();

        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(template);
        StringBuilder regex = new StringBuilder("^");
        int lastEnd = 0;

        while (matcher.find()) {
            regex.append(Pattern.quote(template.substring(lastEnd, matcher.start())));
            variableNames.add(matcher.group(1));
            regex.append("([^/]+)");
            lastEnd = matcher.end();
        }

        regex.append(Pattern.quote(template.substring(lastEnd)));
        regex.append("$");
        this.compiledPattern = Pattern.compile(regex.toString());
    }

    public boolean matches(String path) {
        return compiledPattern.matcher(path).matches();
    }

    public Map<String, String> extractVariables(String path) {
        Matcher matcher = compiledPattern.matcher(path);
        if (!matcher.matches()) {
            return Map.of();
        }

        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < variableNames.size(); i++) {
            variables.put(variableNames.get(i), matcher.group(i + 1));
        }
        return variables;
    }

    public String getTemplate() {
        return template;
    }

    public static boolean matches(String template, String path) {
        return new PathMatcher(template).matches(path);
    }

    public static Map<String, String> extract(String template, String path) {
        return new PathMatcher(template).extractVariables(path);
    }
}
