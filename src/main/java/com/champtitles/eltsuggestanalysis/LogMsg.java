package com.champtitles.eltsuggestanalysis;

import java.time.LocalDateTime;

public record LogMsg(LocalDateTime dateTime, String clazz, String realMsg) {

}
