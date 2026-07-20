// Copyright © Schlumberger
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

package org.opengroup.osdu.indexer.model.geojson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;
import org.opengroup.osdu.indexer.model.geojson.jackson.PositionDeserializer;
import org.opengroup.osdu.indexer.model.geojson.jackson.PositionSerializer;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = PositionDeserializer.class)
@JsonSerialize(using = PositionSerializer.class)
public class Position implements Serializable {

    private double longitude;
    private double latitude;
    @JsonIgnore
    private double altitude = Double.NaN;

    public Position(double longitude, double latitude) {
        this.setLongitude(longitude);
        this.setLatitude(latitude);
    }

    public Position(double longitude, double latitude, double altitude) {
        this.setLongitude(longitude);
        this.setLatitude(latitude);
        this.altitude = altitude;
    }

    public void setLongitude(double longitude) {
        if (Double.isNaN(longitude))
            throw new IllegalArgumentException("latitude must be number");
        if (longitude > 180 || longitude < -180)
            throw new IllegalArgumentException("'longitude' value is out of the range [-180, 180]");
        this.longitude = longitude;
    }

    public void setLatitude(double latitude) {
        if (Double.isNaN(latitude))
            throw new IllegalArgumentException("latitude must be number");
        if (latitude > 90 || latitude < -90)
            throw new IllegalArgumentException("latitude value is out of the range [-90, 90]");
        this.latitude = latitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public boolean hasAltitude() {
        return !Double.isNaN(altitude);
    }
}
