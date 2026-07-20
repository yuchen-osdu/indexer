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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.matchers.JUnitMatchers.containsString;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class NumberParserTest {

    @InjectMocks
    private NumberParser sut;

    @Test
    public void should_parseInteger() {
        int result = this.sut.parseInteger("lat", "");
        assertEquals(result, 0);

        result = this.sut.parseInteger("lat", null);
        assertEquals(result, 0);

        result = this.sut.parseInteger("lat", "101959.1");
        assertEquals(result, 101959);

        result = this.sut.parseInteger("lat", 139);
        assertEquals(result, 139);
    }

    @Test
    public void should_throwException_parseInvalidInteger() {
        final List<Object> outOfRangeInputs = Arrays.asList(
                "2147483648",
                2147483648L,
                -2147483649L
        );
        this.validateInput(this.sut::parseInteger, outOfRangeInputs, "int", "number parsing error, integer out of range: attribute: %s | value: %s");

        final List<Object> invalidInputs = Arrays.asList(
                "garbage",
                "135.5ga"
        );
        this.validateInput(this.sut::parseInteger, invalidInputs, "int", "number parsing error: attribute: %s | value: %s");
    }

    @Test
    public void should_parseLong() {
        long result = this.sut.parseLong("lat", "");
        assertEquals(result, 0);

        result = this.sut.parseLong("lat", null);
        assertEquals(result, 0);

        result = this.sut.parseLong("lat", 4115420654264075766L);
        assertEquals(result, 4115420654264075766L);

        result = this.sut.parseLong("lat", "4115420");
        assertEquals(result, 4115420);

//        result = this.sut.parseLong("lat", "4L");
//        assertEquals(result, 4L);

        result = this.sut.parseLong("lat", 4L);
        assertEquals(result, 4L);

        result = this.sut.parseLong("lat", 139);
        assertEquals(result, 139);
    }

    @Test
    public void should_throwException_parseInvalidLong() {
        final List<Object> outOfRangeInputs = Arrays.asList(
                "9223372036854775808",
                new BigInteger("9223372036854775808"),
                new BigInteger("-9223372036854775809")
        );
        for (Object val : outOfRangeInputs) {
            try {
                this.sut.parseLong("dummyAttribute", val);
            } catch (IllegalArgumentException e) {
                assertEquals(NumberFormatException.class, e.getClass());
            }
        }
        final List<Object> invalidInputs = Arrays.asList(
                "garbage",
                "135.5ga"
        );
        this.validateInput(this.sut::parseLong, invalidInputs, "long", "number parsing error: attribute: %s | value: %s");
    }

    @Test
    public void should_parseFloat() {
        float result = this.sut.parseFloat("lon", "");
        assertEquals(result, 0, 0);

        result = this.sut.parseFloat("lon", null);
        assertEquals(result, 0, 0);

        result = this.sut.parseFloat("lon", "101959.1");
        assertEquals(result, 101959.1, 0.01);

        result = this.sut.parseFloat("lon", "1.1f");
        assertEquals(result, 1.1, 0.01);

        result = this.sut.parseFloat("lon", 1.1f);
        assertEquals(result, 1.1, 0.01);

        result = this.sut.parseFloat("lon", 1.1);
        assertEquals(result, 1.1, 0.01);

        result = this.sut.parseFloat("lon", 139);
        assertEquals(result, 139, 0);
    }

    @Test
    public void should_throwException_parseInvalidFloat() {
        final List<Object> outOfRangeInputs = Arrays.asList(
                "3.4028235E39",
                3.4028235E39d,
                -3.4028235E39d
        );
        this.validateInput(this.sut::parseFloat, outOfRangeInputs, "float", "number parsing error, float only supports finite values: attribute: %s | value: %s");

        final List<Object> invalidInputs = Arrays.asList(
                "garbage",
                "135.5ga",
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY
        );
        this.validateInput(this.sut::parseFloat, invalidInputs, "float", "number parsing error: attribute: %s | value: %s");
    }

    @Test
    public void should_parseDouble() {
        double result = this.sut.parseDouble("location", "");
        assertEquals(result, 0, 0);

        result = this.sut.parseDouble("location", null);
        assertEquals(result, 0, 0);

        result = this.sut.parseDouble("location", "20.0");
        assertEquals(result, 20.0, 0.01);

        result = this.sut.parseDouble("location", "20.0d");
        assertEquals(result, 20.0, 0.01);

        result = this.sut.parseDouble("location", 20.0d);
        assertEquals(result, 20.0, 0.01);

        result = this.sut.parseDouble("location", 1.1);
        assertEquals(result, 1.1, 0);

        result = this.sut.parseDouble("location", 139);
        assertEquals(result, 139, 0);
    }

    @Test
    public void should_throwException_parseInvalidDouble() {
        final List<Object> outOfRangeInputs = Arrays.asList(
                "1.7976931348623157E309",
                new BigDecimal("1.7976931348623157E309"),
                new BigDecimal("-1.7976931348623157E309")
        );
        this.validateInput(this.sut::parseDouble, outOfRangeInputs, "double", "number parsing error, double only supports finite values: attribute: %s | value: %s");

        final List<Object> invalidInputs = Arrays.asList(
                "garbage",
                "135.5ga",
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
        );
        this.validateInput(this.sut::parseDouble, invalidInputs, "double", "number parsing error: attribute: %s | value: %s");
    }

    private void validateInput(BiConsumer<String, Object> parser, List<Object> inputs, String type, String errorMessage) {
        for (Object attributeVal : inputs) {
            try {
                parser.accept("dummyAttribute", attributeVal);
                fail(String.format("Parsing exception expected for %s with value [ %s ]", type, attributeVal));
            } catch (IllegalArgumentException e) {
                assertThat(String.format("Incorrect error message for %s with value [ %s ]", type, attributeVal),
                        e.getMessage(), containsString(String.format(errorMessage, "dummyAttribute", attributeVal)));
            }
        }
    }
}
