package com.github.denisspec989.strategy_launches_analyzer.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public final class JsonNodePath {
    private JsonNodePath() {
    }

    public static JsonNode at(JsonNode root, String dotPath) {
        if (root == null) {
            return MissingNode.getInstance();
        }

        JsonNode current = root;
        for (String segment : dotPath.split("\\.")) {
            if (current == null || current.isMissingNode() || !current.has(segment)) {
                return MissingNode.getInstance();
            }
            current = current.get(segment);
        }
        return current == null ? MissingNode.getInstance() : current;
    }

    public static boolean isPresent(JsonNode node) {
        return node != null && !node.isMissingNode();
    }

    public static String parentPath(String path) {
        int lastDotIndex = path.lastIndexOf('.');
        return lastDotIndex < 0 ? "" : path.substring(0, lastDotIndex);
    }
}
