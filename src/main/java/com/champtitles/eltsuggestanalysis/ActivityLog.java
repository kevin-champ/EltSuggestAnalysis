package com.champtitles.eltsuggestanalysis;

import java.time.LocalDateTime;

public record ActivityLog(LocalDateTime dateTime, 
		String code, 
		String name, 
		String description, 
		String address1) {

}
