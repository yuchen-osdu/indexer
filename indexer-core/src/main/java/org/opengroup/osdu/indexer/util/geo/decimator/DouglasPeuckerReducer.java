/*
 * Copyright © Schlumberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util.geo.decimator;

import org.opengroup.osdu.indexer.model.geojson.Position;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DouglasPeuckerReducer {

    public List<Integer> getPointIndexesToKeep(List<Position> coordinates, double xyScalar, double epsilon) {
        List<Integer> pointIndexesToKeep = new ArrayList<>();

        int firstPointIndex = 0;
        int lastPointIndex = coordinates.size() - 1;
        pointIndexesToKeep.add(firstPointIndex);
        pointIndexesToKeep.add(lastPointIndex);

        // The first and the last point cannot be the same when applying Doublas/Peucker algorithm
        while(firstPointIndex < lastPointIndex && arePointsEqual(coordinates.get(firstPointIndex), coordinates.get(lastPointIndex)))
            lastPointIndex--;

        // Keep full resolution for very small objects, i.e. to preserve small rectangles which might be used for accuracy testing
        if (lastPointIndex < 6)
        {
            for (int i = 1; i <= lastPointIndex; i++)
                pointIndexesToKeep.add(i);
        }
        else {
            // Save the last point used for reduction. It is ok to keep duplicate points
            pointIndexesToKeep.add(lastPointIndex);

            List<Integer> boundaryPointIndexes = getBoundaryPointIndexes(coordinates, firstPointIndex, lastPointIndex);
            pointIndexesToKeep.addAll(boundaryPointIndexes);

            reduce(coordinates, firstPointIndex, lastPointIndex, xyScalar, epsilon, pointIndexesToKeep);
        }

        // Make sure that the duplicate points are removed
        return pointIndexesToKeep.stream().distinct().sorted().collect(Collectors.toList());
    }

    private List<Integer> getBoundaryPointIndexes(List<Position> coordinates, int firstPointIndex, int lastPointIndex) {

        double xMin = Double.NaN;
        double xMax = Double.NaN;
        double yMin = Double.NaN;
        double yMax = Double.NaN;
        double zMin = Double.NaN;
        double zMax = Double.NaN;
        int xMinIndex = firstPointIndex;
        int xMaxIndex = firstPointIndex;
        int yMinIndex = firstPointIndex;
        int yMaxIndex = firstPointIndex;
        int zMinIndex = firstPointIndex;
        int zMaxIndex = firstPointIndex;
        for(int i = firstPointIndex; i <= lastPointIndex; i++) {
            Position position = coordinates.get(i);
            double xValue = position.getLongitude();
            double yValue = position.getLatitude();
            double zValue = position.getAltitude();

            if(!Double.isNaN(xValue)) {
                if (Double.isNaN(xMin) || xValue < xMin)
                {
                    xMin = xValue;
                    xMinIndex = i;
                }

                if (Double.isNaN(xMax) || xValue > xMax)
                {
                    xMax = xValue;
                    xMaxIndex = i;
                }
            }

            if (!Double.isNaN(yValue))
            {
                if (Double.isNaN(yMin) || yValue < yMin)
                {
                    yMin = yValue;
                    yMinIndex = i;
                }

                if (Double.isNaN(yMax) || yValue > yMax)
                {
                    yMax = yValue;
                    yMaxIndex = i;
                }
            }

            if (!Double.isNaN(zValue))
            {
                if (Double.isNaN(zMin) || zValue < zMin)
                {
                    zMin = zValue;
                    zMinIndex = i;
                }

                if (Double.isNaN(zMax) || zValue > zMax)
                {
                    zMax = zValue;
                    zMaxIndex = i;
                }
            }
        }

        Set<Integer> boundaryPointIndexes = new HashSet<> (Arrays.asList(xMinIndex, xMaxIndex, yMinIndex, yMaxIndex, zMinIndex, zMaxIndex));
        return new ArrayList<>(boundaryPointIndexes);
    }

    private void reduce(List<Position> coordinates, int firstPointIndex, int lastPointIndex, double xyScalar, double epsilon, List<Integer> pointIndexesToKeep) {
        double maxDistance = 0d;
        int indexFarthest = 0;

        Position startPoint = coordinates.get(firstPointIndex);
        Position endPoint = coordinates.get(lastPointIndex);
        for (int index = firstPointIndex + 1; index < lastPointIndex; index++)
        {
            Position testPoint = coordinates.get(index);
            double distance = calculatePerpendicularDistance(startPoint, endPoint, testPoint, xyScalar);
            if (distance > maxDistance)
            {
                maxDistance = distance;
                indexFarthest = index;
            }
        }

        if (maxDistance > epsilon && indexFarthest != 0)
        {
            // Add the largest point that exceeds the tolerance
            pointIndexesToKeep.add(indexFarthest);

            // Bisect and recur
            reduce(coordinates, firstPointIndex, indexFarthest, xyScalar, epsilon, pointIndexesToKeep);
            reduce(coordinates, indexFarthest, lastPointIndex, xyScalar, epsilon, pointIndexesToKeep);
        }
    }

    /// <summary>
    /// Calculates the perpendicular distance between a point and a line
    /// </summary>
    /// <returns></returns>
    private double calculatePerpendicularDistance(Position startPoint, Position endPoint, Position testPoint, double xyScalar) {
        boolean hasAltitude = startPoint.hasAltitude();
        double lineStartX = startPoint.getLongitude() * xyScalar;
        double lineStartY = startPoint.getLatitude() * xyScalar;
        double lineStartZ = startPoint.getAltitude();

        double lineEndX = endPoint.getLongitude() * xyScalar;
        double lineEndY = endPoint.getLatitude() * xyScalar;
        double lineEndZ = endPoint.getAltitude();

        double testPointX = testPoint.getLongitude() * xyScalar;
        double testPointY = testPoint.getLatitude() * xyScalar;
        double testPointZ = testPoint.getAltitude();

        double vX = lineEndX - lineStartX;
        double vY = lineEndY - lineStartY;
        double vZ = lineEndZ - lineStartZ;

        double wX = testPointX - lineStartX;
        double wY = testPointY - lineStartY;
        double wZ = testPointZ - lineStartZ;

        double c1 = (wX * vX) + (wY * vY) + (hasAltitude ? wZ * vZ : 0);
        double c2 = (vX * vX) + (vY * vY) + (hasAltitude ? vZ * vZ : 0);

        if (c2 == 0)
        {
            // It should not happen if start and end are not at the same point - return the distance between the test point and the line start point
            return Math.sqrt(Math.pow(testPointX - lineStartX, 2) + Math.pow(testPointY - lineStartY, 2) + (hasAltitude ? Math.pow(testPointZ - lineStartZ, 2) : 0));
        }

        double b = c1 / c2;
        double pbX = lineStartX + b * vX;
        double pbY = lineStartY + b * vY;
        double pbZ = lineStartZ + b * vZ;
        return Math.sqrt(Math.pow(pbX - testPointX, 2) + Math.pow(pbY - testPointY, 2) + (hasAltitude ? Math.pow(pbZ - testPointZ, 2) : 0));
    }

    private boolean arePointsEqual(Position point1, Position point2)
    {
        return point1.getLatitude() == point2.getLatitude() &&
                point1.getLongitude() == point2.getLongitude() &&
                (!point1.hasAltitude() || point1.getAltitude() == point2.getAltitude());
    }
}
