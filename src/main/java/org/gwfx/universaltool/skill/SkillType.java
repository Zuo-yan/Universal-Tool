package org.gwfx.universaltool.skill;

public enum SkillType {
    ACTIVE,
    PASSIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
