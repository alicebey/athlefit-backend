package com.athlefit.backend.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GeoapifyPlace(
        String placeId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Set<String> categories,
        Set<String> taggedSports,
        String openingHours,
        String phone
) {
    private static final Pattern PERIOD = Pattern.compile(
            "^([A-Za-z,-]+)\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})$"
    );
    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "mo", DayOfWeek.MONDAY,
            "tu", DayOfWeek.TUESDAY,
            "we", DayOfWeek.WEDNESDAY,
            "th", DayOfWeek.THURSDAY,
            "fr", DayOfWeek.FRIDAY,
            "sa", DayOfWeek.SATURDAY,
            "su", DayOfWeek.SUNDAY
    );

    public Optional<OpeningSchedule> suggestedSchedule() {
        if (openingHours == null || openingHours.isBlank()) {
            return Optional.empty();
        }
        if ("24/7".equals(openingHours.trim())) {
            return Optional.of(new OpeningSchedule(
                    List.of(DayOfWeek.values()),
                    LocalTime.MIN,
                    LocalTime.of(23, 59)
            ));
        }

        Set<DayOfWeek> openDays = new LinkedHashSet<>();
        LocalTime sharedOpen = null;
        LocalTime sharedClose = null;
        for (String rawPeriod : openingHours.split(";")) {
            Matcher matcher = PERIOD.matcher(rawPeriod.trim());
            if (!matcher.matches()) {
                return Optional.empty();
            }
            LocalTime open = LocalTime.parse(matcher.group(2));
            LocalTime close = LocalTime.parse(matcher.group(3));
            if (!close.isAfter(open)) {
                return Optional.empty();
            }
            if (sharedOpen != null
                    && (!sharedOpen.equals(open) || !sharedClose.equals(close))) {
                return Optional.empty();
            }
            sharedOpen = open;
            sharedClose = close;
            Optional<List<DayOfWeek>> parsedDays = parseDays(matcher.group(1));
            if (parsedDays.isEmpty()) {
                return Optional.empty();
            }
            openDays.addAll(parsedDays.get());
        }
        return openDays.isEmpty()
                ? Optional.empty()
                : Optional.of(new OpeningSchedule(
                        openDays.stream().sorted().toList(),
                        sharedOpen,
                        sharedClose
                ));
    }

    private Optional<List<DayOfWeek>> parseDays(String value) {
        List<DayOfWeek> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String[] range = part.toLowerCase(Locale.ROOT).split("-");
            DayOfWeek start = DAYS.get(range[0]);
            DayOfWeek end = range.length == 2 ? DAYS.get(range[1]) : start;
            if (range.length > 2 || start == null || end == null) {
                return Optional.empty();
            }
            DayOfWeek current = start;
            while (true) {
                result.add(current);
                if (current == end) {
                    break;
                }
                current = current.plus(1);
                if (current == start) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(result);
    }

    public record OpeningSchedule(
            List<DayOfWeek> openDays,
            LocalTime openTime,
            LocalTime closeTime
    ) {
    }
}
