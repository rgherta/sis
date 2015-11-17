/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.internal.referencing.provider;

import java.util.Arrays;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.bind.annotation.XmlTransient;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterNotFoundException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransformFactory;
import org.opengis.referencing.operation.Transformation;
import org.opengis.util.FactoryException;
import org.apache.sis.internal.system.Loggers;
import org.apache.sis.parameter.ParameterBuilder;
import org.apache.sis.parameter.Parameters;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.Workaround;
import org.apache.sis.util.logging.Logging;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.util.collection.WeakValueHashMap;
import org.apache.sis.referencing.datum.DatumShiftGrid;


/**
 * The provider for <cite>"France geocentric interpolation"</cite> (ESPG:9655).
 * This operation requires a grid file provided by the French mapping agency.
 *
 * <p><b>Source:</b> IGN document {@code NTG_88.pdf},
 * <cite>"Grille de paramètres de transformation de coordonnées"</cite>
 * at <a href="http://www.ign.fr">http://www.ign.fr</a>.</p>
 *
 * @author  Simon Reynard (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.7
 * @version 0.7
 * @module
 */
@XmlTransient
public final class FranceGeocentricInterpolation extends AbstractProvider {
    /**
     * Serial number for inter-operability with different versions.
     */
    private static final long serialVersionUID = -4707304160205218546L;

    /**
     * The operation parameter descriptor for the <cite>Geocentric translations file</cite> parameter value.
     */
    private static final ParameterDescriptor<Path> FILE;

    /**
     * The group of all parameters expected by this coordinate operation.
     */
    private static final ParameterDescriptorGroup PARAMETERS;
    static {
        final ParameterBuilder builder = builder();
        FILE = builder
                .addIdentifier("8727")
                .addName("Geocentric translations file")
                .create(Path.class, Paths.get("gr3df97a.txt"));
        PARAMETERS = builder
                .addIdentifier("9655")
                .addName("France geocentric interpolation")
                .createGroup(FILE, GeocentricAffineBetweenGeographic.DIMENSION);
    }

    /**
     * Cache of the grids loaded so far.
     */
    private final WeakValueHashMap<Path,Grid> grids;

    /**
     * Constructs a provider.
     */
    public FranceGeocentricInterpolation() {
        super(2, 2, PARAMETERS);
        grids = new WeakValueHashMap<>(Path.class);
    }

    /**
     * Returns the base interface of the {@code CoordinateOperation} instances that use this method.
     *
     * @return Fixed to {@link Transformation}.
     */
    @Override
    public final Class<Transformation> getOperationType() {
        return Transformation.class;
    }

    /**
     * Notifies {@code DefaultMathTransformFactory} that map projections require values for the
     * {@code "src_semi_major"}, {@code "src_semi_minor"} , {@code "tgt_semi_major"} and
     * {@code "tgt_semi_minor"} parameters.
     *
     * @return 2, meaning that the operation requires source and target ellipsoids.
     */
    @Override
    public int getNumEllipsoids() {
        return 2;
    }

    /**
     * Creates a transform from the specified group of parameter values.
     *
     * @param  factory Ignored (can be null).
     * @param  values The group of parameter values.
     * @return The created math transform.
     * @throws ParameterNotFoundException if a required parameter was not found.
     * @throws FactoryException if an error occurred while loading the grid.
     */
    @Override
    public MathTransform createMathTransform(final MathTransformFactory factory, final ParameterValueGroup values)
            throws ParameterNotFoundException, FactoryException
    {
        final Path file = Parameters.castOrWrap(values).getValue(FILE);
        Grid grid;
        synchronized (grids) {
            grid = grids.get(file);
            if (grid == null) {
                try (final BufferedReader in = Files.newBufferedReader(file)) {
                    grid = new Grid(Grid.readHeader(in, file));
                    grid.load(in, file);
                } catch (IOException | RuntimeException e) {
                    // NumberFormatException, ArithmeticException, NoSuchElementException, possibly other.
                    throw new FactoryException(Errors.format(Errors.Keys.CanNotParseFile_2, Grid.HEADER, file), e);
                }
            }
            grids.put(file, grid);
        }
        return null; // TODO
    }

    /**
     * Helper class for loading the RGF93 data file.
     * This class is used only for loading the file, then discarded.
     */
    private static final class Grid extends DatumShiftGrid {
        /**
         * Serial number for inter-operability with different versions.
         */
        private static final long serialVersionUID = 2151790417501356919L;

        /**
         * The keyword expected at the beginning of every lines in the header.
         */
        static final String HEADER = "GR3D";

        /**
         * Geocentric translations among <var>X</var>, <var>Y</var>, <var>Z</var> axes.
         */
        private final float[][] offsets;

        /**
         * Creates an initially empty grid geometry.
         *
         * @param gridGeometry The value returned by {@link #readHeader(BufferedReader, Path)}.
         */
        Grid(final double[] gridGeometry) {
            super(gridGeometry[0],  // x₀
                  gridGeometry[1],  // y₀
                  gridGeometry[4],  // Δx
                  gridGeometry[5],  // Δy
                  numCells(gridGeometry, 0),
                  numCells(gridGeometry, 1));
            final int size = Math.multiplyExact(nx, ny);
            offsets = new float[3][];
            for (int i=0; i<offsets.length; i++) {
                Arrays.fill(offsets[i] = new float[size], Float.NaN);
            }
        }

        /**
         * Helper method for computing the {@link #nx} or {@link #ny} value.
         */
        private static int numCells(final double[] g, final int offset) {
            return Math.toIntExact(Math.round((g[offset+2] - g[offset]) / g[offset+4] + 1));
        }

        /**
         * Returns the value in the {@code GR3D1} line of the header.
         * The header should be like below, but the only essential line for this class is the one
         * starting with "GR3D1". We also check that "GR3D2" declares the expected interpolation.
         *
         * {@preformat text
         *     GR3D  002024 024 20370201
         *     GR3D1   -5.5000  10.0000  41.0000  52.0000    .1000    .1000
         *     GR3D2 INTERPOLATION BILINEAIRE
         *     GR3D3 PREC CM 01:5 02:10 03:20 04:50 99>100
         * }
         *
         * @param  in Reader of the RGF93 datum shift file.
         * @param  file Path to the file being read, used only for error reporting.
         * @throws IOException if an I/O error occurred.
         * @throws NumberFormatException if a number can not be parsed.
         * @throws FactoryException if an problem is found with the file content.
         */
        @Workaround(library="JDK", version="1.7")
        static double[] readHeader(final BufferedReader in, final Path file) throws IOException, FactoryException {
            double[] gridGeometry = null;
            String line;
            while (true) {
                in.mark(250);
                line = in.readLine();
                if (line == null) {
                    throw new EOFException(Errors.format(Errors.Keys.UnexpectedEndOfFile_1, file));
                }
                final int length = CharSequences.skipTrailingWhitespaces(line, 0, line.length());
                if (length < 0) {
                    continue;   // Skip empty lines.
                }
                int p = CharSequences.skipLeadingWhitespaces(line, 0, length);
                if (!line.regionMatches(true, p, HEADER, 0, HEADER.length())) {
                    break;      // End of header.
                }
                if ((p += HEADER.length()) < length) {
                    final char c = line.charAt(p);
                    p = CharSequences.skipLeadingWhitespaces(line, p+1, length);
                    switch (c) {
                        case '1': {
                            gridGeometry = CharSequences.parseDoubles(line.substring(p, length), ' ');
                            break;
                        }
                        case '2': {
                            final String interp = line.substring(p, length);
                            if (!interp.matches("(?i)INTERPOLATION[^A-Z]+BILINEAIRE")) {
                                final LogRecord record = Errors.getResources((Locale) null).getLogRecord(
                                        Level.WARNING, Errors.Keys.UnsupportedInterpolation_1, interp);
                                record.setLoggerName(Loggers.COORDINATE_OPERATION);
                                Logging.log(FranceGeocentricInterpolation.class, "createMathTransform", record);
                                // We declare 'createMathTransform' method because it is closer to public API.
                            }
                            break;
                        }
                    }
                }
            }
            if (gridGeometry == null || gridGeometry.length != 6) {
                throw new FactoryException(Errors.format(Errors.Keys.CanNotParseFile_2, HEADER, file));
            }
            in.reset();
            return gridGeometry;
        }

        /**
         * Loads the given file. Data columns are
         *
         *     (unknown), longitude, latitude, tX, tY, tZ, accuracy code, data sheet (ignored)
         *
         * Example:
         *
         * {@preformat text
         *     00002   -5.500000000   41.000000000  -165.027   -67.100   315.813  99  -0158
         *     00002   -5.500000000   41.100000000  -165.169   -66.948   316.007  99  -0157
         *     00002   -5.500000000   41.200000000  -165.312   -66.796   316.200  99  -0157
         * }
         *
         * @param  in Reader of the RGF93 datum shift file.
         * @param  file Path to the file being read, used only for error reporting.
         * @throws IOException if an I/O error occurred.
         * @throws NumberFormatException if a number can not be parsed.
         * @throws NoSuchElementException if a data line is missing a value.
         * @throws FactoryException if an problem is found with the file content.
         * @throws ArithmeticException if the width or the height exceed the integer capacity.
         */
        final void load(final BufferedReader in, final Path file) throws IOException, FactoryException {
            final float[] tX = offsets[0];
            final float[] tY = offsets[1];
            final float[] tZ = offsets[2];
            String line;
            while ((line = in.readLine()) != null) {
                final StringTokenizer t = new StringTokenizer(line.trim());
                final int    n = Integer.parseInt  (t.nextToken());               // Ignored
                final double x = Double.parseDouble(t.nextToken());               // Longitude
                final double y = Double.parseDouble(t.nextToken());               // Latitude
                final int    i = Math.toIntExact(Math.round((x - x0) * scaleX));  // Column index
                final int    j = Math.toIntExact(Math.round((y - y0) * scaleY));  // Row index
                if (i < 0 || i >= nx) {
                    throw new FactoryException(Errors.format(Errors.Keys.ValueOutOfRange_4, "x", x, x0, x0 + nx/scaleX));
                }
                if (j < 0 || j >= ny) {
                    throw new FactoryException(Errors.format(Errors.Keys.ValueOutOfRange_4, "y", y, y0, y0 + ny/scaleY));
                }
                final int p = j*nx + i;
                if (!Double.isNaN(tX[p]) || !Double.isNaN(tY[p]) || !Double.isNaN(tZ[p])) {
                    throw new FactoryException(Errors.format(Errors.Keys.ValueAlreadyDefined_1, x + ", " + y));
                }
                tX[p] = Float.parseFloat(t.nextToken());
                tY[p] = Float.parseFloat(t.nextToken());
                tZ[p] = Float.parseFloat(t.nextToken());
            }
        }

        /**
         * Returns the cell value at the given dimension and grid index.
         */
        @Override
        protected double getCellValue(final int dim, final int gridX, final int gridY) {
            return offsets[dim][gridX + gridY*nx];
        }
    }
}