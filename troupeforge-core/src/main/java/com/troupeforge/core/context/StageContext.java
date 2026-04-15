package com.troupeforge.core.context;

public record StageContext(String value) {
    public static final StageContext LIVE = new StageContext("live");
}
