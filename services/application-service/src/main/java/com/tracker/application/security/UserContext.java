package com.tracker.application.security;

/** Request-scoped holder for the authenticated userId, populated by {@link AuthFilter}. */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private UserContext() { }

    public static void set(String userId) { CURRENT.set(userId); }

    public static String getUserId() {
        String uid = CURRENT.get();
        if (uid == null) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return uid;
    }

    public static void clear() { CURRENT.remove(); }
}
