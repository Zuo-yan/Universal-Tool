package org.gwfx.universaltool.phoenix;

import net.minecraft.util.StringRepresentable;

public enum PodState implements StringRepresentable {
    EMPTY("empty"),
    CULTIVATING("cultivating"),
    READY("ready");

    private final String name;

    PodState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
