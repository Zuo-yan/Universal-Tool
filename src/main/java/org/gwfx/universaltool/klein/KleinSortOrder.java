package org.gwfx.universaltool.klein;

public record KleinSortOrder(int mode, boolean descending) {
    public static final KleinSortOrder DEFAULT = new KleinSortOrder(KleinMenu.SORT_QUANTITY, true);

    public KleinSortOrder {
        if (mode < KleinMenu.SORT_QUANTITY || mode > KleinMenu.SORT_MOD) mode = KleinMenu.SORT_QUANTITY;
    }
}
