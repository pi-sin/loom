package io.loom.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProxyPathTemplate {

    private final String template;
    private final String[] literals;
    private final String[] variables;

    private ProxyPathTemplate(String template, String[] literals, String[] variables) {
        this.template = template;
        this.literals = literals;
        this.variables = variables;
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

        return new ProxyPathTemplate(
                template,
                literals.toArray(new String[0]),
                variables.toArray(new String[0])
        );
    }

    public String resolve(Map<String, String> pathVariables) {
        if (variables.length == 0) {
            return literals[0];
        }

        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < variables.length; i++) {
            sb.append(literals[i]);
            String value = pathVariables.get(variables[i]);
            if (value != null) {
                sb.append(value);
            }
        }
        sb.append(literals[variables.length]);
        return sb.toString();
    }

    public String template() {
        return template;
    }
}
