/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.bucket.geogrid;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.unit.DistanceUnit.Distance;

/**
 * Encapsulates relevant parameter defaults and validations for the geo hash grid aggregation.
 */
final class GeoHashGridParams {

    /* recognized field names in JSON */
    static final ParseField FIELD_PRECISION = new ParseField("precision");
    static final ParseField FIELD_SIZE = new ParseField("size");
    static final ParseField FIELD_SHARD_SIZE = new ParseField("shard_size");

    static int checkPrecision(int precision) {
        if ((precision < 1) || (precision > 12)) {
            throw new IllegalArgumentException("Invalid geohash aggregation precision of " + precision
                    + ". Must be between 1 and 12.");
        }
        return precision;
    }

    /**
     * This method validates the precision (as a distance unit) that is passed in
     * If it is valid, it converts it into meters. Otherwise, an exception is thrown
     * @param precision precision of geoHash desired by user
     * @return precision converted to meters
     */
    static int distanceUnitToMeters(String precision) {

        Distance distance = Distance.parseDistance(precision);
        DistanceUnit distanceUnit = DistanceUnit.parseUnit(precision, DistanceUnit.METERS);

        return (int)Math.round(distanceUnit.toMeters(distance.value));
    }

    private GeoHashGridParams() {
        throw new AssertionError("No instances intended");
    }
}
