package io.loom.core.model;

import io.loom.core.exception.LoomException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProxyPathTemplate {

    private final String template;
    private final String[] literals;
    private final String[] variables;
    private final int estimatedCapacity;

    private ProxyPathTemplate(String template, String[] literals, String[] variables, int estimatedCapacity) {
        this.template = template;
        this.literals = literals;
        this.variables = variables;
        this.estimatedCapacity = estimatedCapacity;
    }

    public static ProxyPathTemplate compile(String template) {
        List<String> literals = new ArrayList<>();
        List<String> variables = new ArrayList<>();

        int len = template.length();
        int pos = 0;

        while (pos < len) {
            int openBrace = template.indexOf('{', pos);
            if (openBrace == -1) {
                literals.add(template.substring(pos));
                pos = len;
            } else {
                int closeBrace = template.indexOf('}', openBrace + 1);
                if (closeBrace == -1) {
                    // Unclosed brace — treat rest as literal
                    literals.add(template.substring(pos));
                    pos = len;
                } else {
                    String varName = template.substring(openBrace + 1, closeBrace);
                    if (varName.isEmpty()) {
                        // Empty variable name — treat as literal
                        literals.add(template.substring(pos, closeBrace + 1));
                        pos = closeBrace + 1;
                    } else {
                        literals.add(template.substring(pos, openBrace));
                        variables.add(varName);
                        pos = closeBrace + 1;
                    }
                }
            }
        }

        // Ensure literals has variables.size() + 1 elements
        if (literals.size() == variables.size()) {
            literals.add("");
        }

        int literalLength = 0;
        for (String lit : literals) {
            literalLength += lit.length();
        }
        int estimatedCapacity = literalLength + variables.size() * 16;

        return new ProxyPathTemplate(
                template,
                literals.toArray(new String[0]),
                variables.toArray(new String[0]),
                estimatedCapacity
        );
    }

    public String resolve(Map<String, String> pathVariables) {
        return resolve(pathVariables, null);
    }

    public String resolve(Map<String, String> pathVariables, String queryString) {
        if (variables.length == 0 && queryString == null) {
            return literals[0];
        }

        int capacity = estimatedCapacity;
        if (queryString != null) {
            capacity += 1 + queryString.length();
        }
        StringBuilder sb = new StringBuilder(capacity);
        if (variables.length == 0) {
            sb.append(literals[0]);
        } else {
            for (int i = 0; i < variables.length; i++) {
                sb.append(literals[i]);
                String value = pathVariables.get(variables[i]);
                if (value == null) {
                    throw new LoomException(
                            "Missing path variable '{" + variables[i] + "}' in template: " + template);
                }
                sb.append(encodePathSegment(value));
            }
            sb.append(literals[variables.length]);
        }
        if (queryString != null) {
            sb.append('?').append(queryString);
        }
        return sb.toString();
    }

    public String template() {
        return template;
    }

    /**
     * Percent-encodes a path variable value per RFC 3986.
     * <p>
     * Servlet containers URL-decode incoming path variables, so values like "foo/bar"
     * arrive decoded. Without re-encoding, substituting them into the upstream URL
     * breaks its structure (e.g. /users/foo/bar/orders instead of /users/foo%2Fbar/orders).
     * This encodes reserved characters (/, ?, #, etc.) while leaving typical ID values
     * (alphanumerics, hyphens, underscores) untouched.
     * <p>
     * Uses URLEncoder (application/x-www-form-urlencoded) then converts '+' back to '%20'
     * for correct path-segment encoding.
     */
    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
