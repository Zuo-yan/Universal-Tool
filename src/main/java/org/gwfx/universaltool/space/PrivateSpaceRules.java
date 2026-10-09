package org.gwfx.universaltool.space;

/**
 * Rules shared by the client screen and the authoritative server handler.
 *
 * <p>Kept out of {@link PrivateSpaceManager} so the client screen can pre-validate input without
 * referencing server-side event plumbing.
 */
public final class PrivateSpaceRules {
    public static final int MIN_PASSWORD_LENGTH = 4;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private PrivateSpaceRules() {}
}
