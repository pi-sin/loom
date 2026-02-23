package io.loom.starter.web;

import io.loom.core.model.ApiDefinition;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Radix-trie router for O(segment-count) route matching, replacing the
 * previous O(n) linear scan + per-request regex compilation in
 * {@link LoomHandlerMapping}.
 *
 * <p>Routes are indexed by HTTP method, then by path segments.  Literal
 * segments take priority over parameter (wildcard) segments so that
 * {@code /api/users/me} is preferred over {@code /api/users/{id}}.
 */
public class RouteTrie {

    private final Map<String, TrieNode> methodRoots = new HashMap<>();

    public void insert(ApiDefinition api) {
        String method = api.method().toUpperCase();
        TrieNode root = methodRoots.computeIfAbsent(method, k -> new TrieNode());
        String[] segments = splitPath(api.path());

        TrieNode current = root;
        for (String segment : segments) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String paramName = segment.substring(1, segment.length() - 1);
                if (current.paramChild == null) {
                    current.paramChild = new TrieNode();
                }
                current.paramChild.paramName = paramName;
                current = current.paramChild;
            } else {
                current = current.literalChildren.computeIfAbsent(segment, k -> new TrieNode());
            }
        }
        current.api = api;
    }

    public RouteMatch find(String method, String path) {
        TrieNode root = methodRoots.get(method.toUpperCase());
        if (root == null) {
            return null;
        }

        String[] segments = splitPath(path);
        Map<String, String> pathVariables = new LinkedHashMap<>();
        TrieNode current = root;

        for (String segment : segments) {
            // Literal children take priority over parameter children
            TrieNode literal = current.literalChildren.get(segment);
            if (literal != null) {
                current = literal;
            } else if (current.paramChild != null) {
                pathVariables.put(current.paramChild.paramName, segment);
                current = current.paramChild;
            } else {
                return null;
            }
        }

        if (current.api == null) {
            return null;
        }
        return new RouteMatch(current.api, pathVariables);
    }

    static String[] splitPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return new String[0];
        }
        return path.split("/");
    }

    public record RouteMatch(ApiDefinition api, Map<String, String> pathVariables) {}

    private static class TrieNode {
        final Map<String, TrieNode> literalChildren = new HashMap<>();
        TrieNode paramChild;
        String paramName;
        ApiDefinition api;
    }
}
