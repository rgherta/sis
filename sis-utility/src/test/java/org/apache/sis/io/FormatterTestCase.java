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
package org.apache.sis.io;

import java.io.IOException;
import org.apache.sis.test.TestCase;
import org.apache.sis.test.DependsOnMethod;
import org.junit.Test;

import static org.apache.sis.test.Assert.*;


/**
 * Base class for the testing {@code *Formatter} implementation.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.00)
 * @version 0.3
 * @module
 */
public abstract class FormatterTestCase extends TestCase {
    /**
     * The buffer where to write test data.
     */
    private final StringBuilder buffer;

    /**
     * The formatter to test. Subclasses should initialize this field as below:
     *
     * {@preformat java
     *   formatter = MyFormatter(formatter);
     * }
     */
    Appendable formatter;

    /**
     * Creates a new test case.
     */
    FormatterTestCase() {
        buffer = new StringBuilder(128);
        formatter = buffer;
    }

    /**
     * Uses a formatter which will redirect every {@link Appendable#append(CharSequence)}
     * calls to a sequence of {@link Appendable#append(char)} calls.
     */
    private void useSingleChars() {
        formatter = new SingleCharAppendable(formatter);
    }

    /**
     * Tests a sequence of {@link Appendable#append(char)} calls,
     * with Unix line terminators mixed in.
     *
     * @throws IOException Should never happen.
     */
    @Test
    public void testCharsWithLF() throws IOException {
        useSingleChars();
        run("\n");
    }

    /**
     * Tests a few {@link Appendable#append(CharSequence)} calls,
     * with Unix line terminators in the sequences.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithLF")
    public void testSequencesWithLF() throws IOException {
        run("\n");
    }

    /**
     * Tests a sequence of {@link Appendable#append(char)} calls,
     * with CR line terminators mixed in.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithLF")
    public void testCharsWithCR() throws IOException {
        useSingleChars();
        run("\r");
    }

    /**
     * Tests a few {@link Appendable#append(CharSequence)} calls,
     * with CR line terminators in the sequences.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithCR")
    public void testSequencesWithCR() throws IOException {
        run("\r");
    }

    /**
     * Tests a sequence of {@link Appendable#append(char)} calls,
     * with Windows line terminators mixed in.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithCR")
    public void testCharsWithCRLF() throws IOException {
        useSingleChars();
        run("\r\n");
    }

    /**
     * Tests a few {@link Appendable#append(CharSequence)} calls,
     * with Windows line terminators in the sequences.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithCRLF")
    public void testSequencesWithCRLF() throws IOException {
        run("\r\n");
    }

    /**
     * Tests a sequence of {@link Appendable#append(char)} calls,
     * with Unicode line terminators mixed in.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithLF")
    public void testCharsWithUnicode() throws IOException {
        useSingleChars();
        run("\u2028");
    }

    /**
     * Tests a few {@link Appendable#append(CharSequence)} calls,
     * with Unicode line terminators in the sequences.
     *
     * @throws IOException Should never happen.
     */
    @Test
    @DependsOnMethod("testCharsWithUnicode")
    public void testSequencesWithUnicode() throws IOException {
        run("\u2028");
    }

    /**
     * Run the test.
     *
     * @param  lineSeparator The line separator to use.
     * @throws IOException Should never happen.
     */
    abstract void run(final String lineSeparator) throws IOException;

    /**
     * Ensures that the buffer content is equals to the given string.
     *
     * @param expected The expected content.
     * @throws IOException Should never happen.
     */
    final void assertOutputEquals(final String expected) throws IOException {
        IO.flush(formatter);
        final String actual = buffer.toString();
        assertMultilinesEquals("Ignoring line separators.", expected, actual);
        assertEquals          ("Checking line separators.", expected, actual);
    }
}
