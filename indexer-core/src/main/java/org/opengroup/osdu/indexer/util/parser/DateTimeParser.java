// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.indexer.util.parser;

import com.google.common.base.Strings;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

@Component
@RequestScope
public class DateTimeParser {

    private final static DateTimeFormatter UTC_FORMATTER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            // optional offset - prints +0000 when it's zero (instead of Z)
            .optionalStart().appendOffset("+HHMM", "+0000").optionalStart()
            .optionalStart()
            // optional zone id (so it parses "Z")
            .appendZoneId()
            // add default value for offset seconds when field is not present
            .parseDefaulting(ChronoField.OFFSET_SECONDS, 0).optionalEnd()
            .toFormatter();

    private final static List<DateTimeFormatter> KNOWN_FORMATTERS = new ArrayList<>();
    static {
        KNOWN_FORMATTERS.add(new DateTimeFormatterBuilder()
                .appendValue(ChronoField.YEAR, 4)
                .appendPattern("[-]MM[-]dd['T'][ ][HH][:][mm][:][ss]")
                .optionalStart().appendFraction(ChronoField.MICRO_OF_SECOND, 0, 7, true).optionalEnd()
                .optionalStart().appendPattern("[,S][XXX]").optionalEnd()
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
                .toFormatter());
        KNOWN_FORMATTERS.add(new DateTimeFormatterBuilder()
                .appendPattern("EEE MMM dd HH:mm:ss zzz yyyy")
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
                .toFormatter());
        KNOWN_FORMATTERS.add(UTC_FORMATTER);
    }

    public String convertDateObjectToUtc(String candidate) {
        if (Strings.isNullOrEmpty(candidate)) return null;

        OffsetDateTime parsedDateTime = null;
        for(DateTimeFormatter formatter: KNOWN_FORMATTERS) {
            try {
                parsedDateTime = OffsetDateTime.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                continue;
            }
            break;
        }
        if (parsedDateTime != null) {
            try {
                return parsedDateTime.format(UTC_FORMATTER);
            } catch (DateTimeException ignored) {
            }
        }
        return null;
    }

    public String parseDate(String attributeName, Object attributeVal) {
        String val = attributeVal == null ? null : String.valueOf(attributeVal);
        if (Strings.isNullOrEmpty(val)) {
            // skip indexing
            return null;
        }
        String utcDate = this.convertDateObjectToUtc(val);
        if (Strings.isNullOrEmpty(utcDate)) {
            throw new IllegalArgumentException(String.format("datetime parsing error: unknown format for attribute: %s | value: %s", attributeName, attributeVal));
        }
        return utcDate;
    }
}