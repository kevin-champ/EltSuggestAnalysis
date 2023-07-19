package com.champtitles.eltsuggestanalysis;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EltSuggestAnalysis {

    public static void main(String[] args) throws IOException {
		var app = new EltSuggestAnalysis();
		app.run();
    }
	
	record Pair(LogMsg entry, LogMsg exit) {}
	
	class LogComparator implements Comparator<Object> {

		@Override
		public int compare(Object o1, Object o2) {
			LocalDateTime dt1;
			LocalDateTime dt2;
			
			dt1 = switch (o1) {
				case ActivityLog a -> a.dateTime();
				case Pair p -> p.exit().dateTime();
				default -> throw new IllegalArgumentException();
			};
			
			dt2 = switch(o2) {
				case ActivityLog a -> a.dateTime();
				case Pair p -> p.exit().dateTime();
				default -> throw new IllegalArgumentException();
			};
			
			return dt1.compareTo(dt2);
		}
	}
	
	public void run() throws IOException {
		List<Pair> matchedLogs = new ArrayList<>();
		matchedLogs.addAll(matchedLogMsgs("may_week_1_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("may_week_2_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("may_week_3_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("may_week_4_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("may_week_5_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("jun_week_1_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("jun_week_2_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("jun_week_3_logs.json"));
		matchedLogs.addAll(matchedLogMsgs("jun_week_4_logs.json"));
		
		List<ActivityLog> actLogs = activityLogs("may_jun_activity_logs.json");
		
		List combinedLogs = new ArrayList(actLogs);
		combinedLogs.addAll(matchedLogs);
		Collections.sort(combinedLogs, new LogComparator());
		
		List<SuggestEvent> events = createSuggestEvents(combinedLogs);
		
		Pattern entryPattern = Pattern.compile("^.*LenderSearchSubmittedDto\\(name=(.*)address1=.*$");
		Pattern exitPattern = Pattern.compile("^.*LenderSearchMatchDto\\(name=(.*)normalizedName=.*$");
		
		int exactMatchCount = 0;
		int exactMatchAccepted = 0;
		int inexactMatchCount = 0;
		int inexactMatchAccepted = 0;
		int suggestionAccepted = 0;
		
		for (SuggestEvent event : events) {
			var entryName = parseName(event.entry().realMsg(), entryPattern);
			var exitName = parseName(event.exit().realMsg(), exitPattern);
			if (!event.actLog().code().isBlank()) {
				suggestionAccepted++;
			}
			
			if (entryName.equalsIgnoreCase(exitName)) {
//				System.out.println(String.format("%-50s %-50s", entryName, exitName));
				exactMatchCount++;
				if (!event.actLog().code().isBlank()) {
					exactMatchAccepted++;
				}
			} else {
				inexactMatchCount++;
				if (!event.actLog().code().isBlank()) {
					inexactMatchAccepted++;
				}
			}
		}
		
		System.out.println("Suggestion count        : " + events.size());
		System.out.println("Suggestions accepted    : " + suggestionAccepted);
		System.out.println("Suggestions accepted %  : " + percent(suggestionAccepted, events.size()));
		System.out.println("");
		System.out.println("Exact matches           : " + exactMatchCount);
		System.out.println("Exact matches accepted  : " + exactMatchAccepted);
		System.out.println("Exact match accepted %  : " + percent(exactMatchAccepted, exactMatchCount));
		System.out.println("");
		System.out.println("Inexact matches         : " + inexactMatchCount);
		System.out.println("Inexact matches accepted: " + inexactMatchAccepted);
		System.out.println("Inexact match accepted %: " + percent(inexactMatchAccepted, inexactMatchCount));
	}
	
	public String percent (int numerator, int denominator) {
		return BigDecimal
				.valueOf(numerator)
				.divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
				.movePointRight(2)
				.toString() + "%";
	}
	
	public String parseName(String realMsg, Pattern p) {
		var matcher = p.matcher(realMsg);
		String name;
		if (matcher.find()) {
			name = matcher.group(1);
			name = name.substring(0, name.lastIndexOf(",")).trim();
		} else {
			name = "NOT FOUND";
			System.out.println(realMsg);
		}
		
		return name;
	}
	
	public List<SuggestEvent> createSuggestEvents(List combinedLogs) {
		List<SuggestEvent> events = new ArrayList<>();
		int index = 0;
		for (Object combinedLog : combinedLogs) {
			if (combinedLog instanceof Pair p) {
				findMatchingActivityLog(p, index, combinedLogs).ifPresent(x -> events.add(new SuggestEvent(p.entry(), p.exit(), x)));
			}
			index++;
		}
		
		return events;
	}
	
	public Optional<ActivityLog> findMatchingActivityLog(Pair pair, int startIndex, List combinedLogs) {
		for (int i = startIndex + 1; i < startIndex + 10; i++) {
			if (combinedLogs.get(i) instanceof ActivityLog actlog) {
				if (pair.exit().realMsg().contains(actlog.code())) {
					return Optional.of(actlog);
				}
				
				int wordCount = 0;
				String[] words = actlog.name().split(" ");
				for (String word : words) {
					if (pair.entry().realMsg().contains(word)) {
						wordCount++;
					}
				}
				
				if (wordCount*2 > words.length) {
					return Optional.of(actlog);
				}
			}
		}
		
		return Optional.empty();
	}
	
	public List<Pair> matchedLogMsgs(String fileName) throws IOException {
		try (Reader rdr = new InputStreamReader(ClassLoader.getSystemResourceAsStream(fileName))) {
			JsonReader jsonReader = Json.createReader(rdr);
			JsonArray jsonArray = jsonReader.readArray();
			List<LogMsg> msgs = jsonArray.stream()
					.map(x -> x.asJsonObject())
					.map(x -> new LogMsg(LocalDateTime.parse(x.getString("Date", "") + "T" + x.getString("Time", "")).truncatedTo(ChronoUnit.SECONDS), 
							x.getString("Class", ""), 
							x.getString("RealMsg", "")))
					.collect(Collectors.toList());

			List<Pair> msgPairs = new ArrayList<>();
			LogMsg entry = null;
			for (LogMsg msg : msgs) {
				if (entry == null) {
					if (msg.realMsg().startsWith("suggestLender: entry")) {
						entry = msg;
					}
				} else {
					if (msg.realMsg().startsWith("suggestLender: exit")) {
						msgPairs.add(new Pair(entry, msg));
					}
					entry = null;
				}
			}

			return msgPairs.stream()
					.filter(x -> x.exit().realMsg().contains("match=true"))
					.collect(Collectors.toList());
		}
	}
	
	public List<ActivityLog> activityLogs(String fileName) throws IOException {
		try (Reader rdr = new InputStreamReader(ClassLoader.getSystemResourceAsStream(fileName))) {
			JsonReader jsonReader = Json.createReader(rdr);
			JsonArray jsonArray = jsonReader.readArray();
			List<ActivityLog> actLogs = jsonArray.stream()
					.map(x -> x.asJsonObject())
					
					.map(x -> new ActivityLog(LocalDateTime.parse(x.getString("created_date", "")).truncatedTo(ChronoUnit.SECONDS),
									x.getString("code", ""), 
									x.getString("name", ""),
									x.getString("description", ""),
									x.getString("address1", "")))
					.collect(Collectors.toList());
			
			return actLogs;
		}
	}
}
