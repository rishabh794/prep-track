package com.preptrack.catalog;

/** Comparison with the declared sheet size; not a certification of external curriculum completeness. */
public enum CatalogCountStatus {
    COMPLETE, INCOMPLETE, COUNT_MISMATCH, UNVERIFIED;
    public static CatalogCountStatus compare(long actual, Integer expected) {
        if (expected == null) return UNVERIFIED;
        if (actual == expected) return COMPLETE;
        return actual < expected ? INCOMPLETE : COUNT_MISMATCH;
    }
}
