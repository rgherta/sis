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
package org.apache.sis.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


/**
 * Base class of Apache SIS test suites (except the ones that extend GeoAPI suites).
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.16)
 * @version 0.3
 * @module
 */
@RunWith(Suite.class)
public abstract strictfp class TestSuite {
    /**
     * The {@value} system property for enabling verbose outputs.
     * If this {@linkplain System#getProperties() system property} is set to {@code true},
     * then the {@link TestCase#out} field will be set to a non-null value.
     */
    public static final String VERBOSE_OUTPUT_KEY = "org.apache.sis.test.verbose";

    /**
     * The {@value} system property for setting the output encoding.
     * This property is used only if the {@link #VERBOSE_OUTPUT_KEY} property
     * is set to "{@code true}". If this property is not set, then the system
     * encoding will be used.
     */
    public static final String OUTPUT_ENCODING_KEY = "org.apache.sis.test.encoding";

    /**
     * Creates a new test suite.
     */
    protected TestSuite() {
    }
}
