package com.dpe.common.model;

public record Project(
    String id,
    String name,
    String namespace,
    String description,
    long createdAt,
    long updatedAt
) {
    public Project withUpdatedAt(long updatedAt) {
        return new Project(id, name, namespace, description, createdAt, updatedAt);
    }

    public static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return false;
        }
        return namespace.matches("^[a-z][a-z0-9_]*$");
    }
}
