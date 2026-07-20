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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(SpringRunner.class)
public class DateTimeParserTest {
    @InjectMocks
    private DateTimeParser sut;

    @Test
    public void should_returnCorrectUtc_given_validDateFormat_convertDateObjectToUtcTest() {
        // parse "yyyy-MM-dd HH:mm:ss"
        assertEquals("2000-01-02T10:10:44+0000", this.sut.convertDateObjectToUtc("2000-01-02 10:10:44"));

        // test "yyyy-MM-dd'T'HH:mm:ss"
        assertEquals("2000-01-02T10:10:44+0000", this.sut.convertDateObjectToUtc("2000-01-02T10:10:44"));

        // parse "yyyy-MM-dd HH:mm:ss.SSS"
        assertEquals("2000-01-02T10:10:44.123+0000", this.sut.convertDateObjectToUtc("2000-01-02 10:10:44.123"));

        // parse "yyyy-MM-dd HH:mm:ss.SSSSSS"
        assertEquals("2000-01-02T10:10:44.123+0000", this.sut.convertDateObjectToUtc("2000-01-02 10:10:44.123000"));

        // parse "yyyy-MM-dd HH:mm:ss.SSSSSS"
        assertEquals("2000-01-02T10:10:44.123+0000", this.sut.convertDateObjectToUtc("2000-01-02 10:10:44.1230000"));

        // parse "yyyy-MM-dd"
        assertEquals("2000-01-02T00:00:00+0000", this.sut.convertDateObjectToUtc("2000-01-02"));
        assertEquals("0001-01-01T00:00:00+0000", this.sut.convertDateObjectToUtc("0001-01-01"));

        // parse "yyyyMMdd"
        assertEquals("2000-01-02T00:00:00+0000", this.sut.convertDateObjectToUtc("20000102"));

        // parse string with zulu indicator: "yyyy-MM-dd'T'HH:mm:ssz"
        assertEquals("2018-11-06T19:37:11.128+0000", this.sut.convertDateObjectToUtc("2018-11-06T19:37:11.128Z"));

        // parse with offset indicator: "yyyy-MM-dd HH:mm:ss.SSSXXX"
        assertEquals("1968-11-01T00:00:00+0000", this.sut.convertDateObjectToUtc("1968-11-01T00:00:00+00:00"));
        assertEquals("1968-11-01T00:00:45.56+0000", this.sut.convertDateObjectToUtc("1968-11-01T00:00:45.56+00:00"));

        // parse string with offset: "yyyy-MM-dd'T'HH:mm:ssXXX"
        assertEquals("2000-01-02T10:10:44-0830", this.sut.convertDateObjectToUtc("2000-01-02T10:10:44-08:30"));

        // parse "EEE MMM dd HH:mm:ss zzz yyyy"
        assertEquals("2018-07-18T10:10:44+0000", this.sut.convertDateObjectToUtc("Wed Jul 18 10:10:44 PST 2018"));

        // parse string with offset: "yyyy-MM-dd'T'HH:mm:ssXXX"
        assertEquals("2000-01-02T10:10:44-0830", this.sut.convertDateObjectToUtc("2000-01-02T10:10:44-08:30"));

        // parse string in already in our output format "yyyy-MM-dd'T'HH:mm:ss(.SSS):<timezone without colon>"
        assertEquals("2023-08-07T18:52:39+0000", this.sut.convertDateObjectToUtc("2023-08-07T18:52:39+0000"));
        assertEquals("1968-11-01T00:00:45.56+0000", this.sut.convertDateObjectToUtc("1968-11-01T00:00:45.56+0000"));
    }

    @Test
    public void should_returnNull_given_emptyOrNull_covertDateObjectToUtcTest() {
        assertNull(this.sut.convertDateObjectToUtc(""));
        assertNull(this.sut.convertDateObjectToUtc(null));
    }

    @Test
    public void should_returnNull_given_invalidYearMonthDay_covertDateObjectToUtcTest() {
        assertNull(this.sut.convertDateObjectToUtc("2000-00-02 10:10:44.123"));
        assertNull(this.sut.convertDateObjectToUtc("2000-01-40 10:10:44.123"));
        assertNull(this.sut.convertDateObjectToUtc("3000-14-02 10:10:44.123"));
    }

    @Test
    public void should_returnNull_given_invalidDateFormat_convertDateObjectToUtcTest() {
        assertNull(this.sut.convertDateObjectToUtc("07/01/2010"));
    }

    @Test
    public void should_returnNull_given_invalidDate_convertDateObjectToUtcTest() {
        assertNull(this.sut.convertDateObjectToUtc("N/A"));
        assertNull(this.sut.convertDateObjectToUtc(".2190851121908511EE44"));
        assertNull(this.sut.convertDateObjectToUtc("E.2131"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_throwException_given_invalidDate_parseDateTest() {
        this.sut.parseDate("testDateTimeAttribute", "N/A");
    }

    @Test
    public void should_returnNull_given_emptyOrNull_parseDateTest() {
        assertNull(this.sut.parseDate("testDateTimeAttribute", ""));
        assertNull(this.sut.parseDate("testDateTimeAttribute", null));
    }
}
