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
package org.apache.sis.storage;

import org.apache.sis.util.ThreadSafe;


/**
 * Creates {@link DataStore} instances for a specific format from a given {@link StorageConnector} input.
 * There is typically a different {@code DataStoreProvider} instance for each format provided by a library.
 *
 * {@section Packaging data stores}
 * JAR files that provide implementations of this class shall contain an entry with exactly the following path:
 *
 * {@preformat text
 *     META-INF/services/org.apache.sis.storage.DataStoreProvider
 * }
 *
 * The above entry shall contain one line for each {@code DataStoreProvider} implementation provided in the JAR file,
 * where each line is the fully qualified name of the implementation class.
 * See {@link java.util.ServiceLoader} for more general discussion about this lookup mechanism.
 *
 * {@section Thread safety policy}
 * All {@code DataStoreProvider} implementations shall be thread-safe.
 * However the {@code DataStore} instances created by the providers do not need to be thread-safe.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3
 * @version 0.3
 * @module
 */
@ThreadSafe
public abstract class DataStoreProvider {
    /**
     * Creates a new provider.
     */
    protected DataStoreProvider() {
    }

    /**
     * Returns {@code TRUE} if the given storage appears to be supported by the {@code DataStore}.
     * Returning {@code TRUE} from this method does not guarantee that reading or writing will succeed,
     * only that there appears to be a reasonable chance of success based on a brief inspection of the
     * {@linkplain StorageConnector#getStorage() storage object} or contents.
     *
     * <p>Implementations will typically check the first bytes of the stream for a "magic number"
     * associated with the format, as in the following example:</p>
     *
     * {@preformat java
     *     final ByteBuffer buffer = storage.getStorageAs(ByteBuffer.class);
     *     if (buffer == null) {
     *         // If StorageConnector can not provide a ByteBuffer, then the storage is probably
     *         // not a File, URL, URI, InputStream neither a ReadableChannel. In this example,
     *         // our provider can not handle such unknown source.
     *         return Boolean.FALSE;
     *     }
     *     if (buffer.remaining() < Integer.SIZE / Byte.SIZE) {
     *         // If the buffer does not contain enough bytes for the 'int' type, this is not necessarily
     *         // because the file is truncated. It may be because the data were not yet available at the
     *         // time this method has been invoked. Returning 'null' means "don't know".
     *         return null;
     *     }
     *     // Use ByteBuffer.getInt(int) instead than ByteBuffer.getInt() in order to keep buffer position
     *     // unchanged after this method call.
     *     return buffer.getInt(buffer.position()) == MAGIC_NUMBER;
     * }
     *
     * Implementors are responsible for restoring the input to its original stream position on return of this method.
     * Implementors can use a mark/reset pair for this purpose. Marks are available as
     * {@link java.nio.ByteBuffer#mark()}, {@link java.io.InputStream#mark(int)} and
     * {@link javax.imageio.stream.ImageInputStream#mark()}.
     *
     * <table width="80%" align="center" cellpadding="18" border="4" bgcolor="#FFE0B0">
     *   <tr><td>
     *     <b>Warning:</b> this method is likely to change. SIS 0.4 will probably return a set of enumeration
     *     values describing how the file can be open (read, write, append) similar to JDK7 open mode.
     *   </td></tr>
     * </table>
     *
     * @param  storage Information about the storage (URL, stream, JDBC connection, <i>etc</i>).
     * @return {@link Boolean#TRUE} if the given storage seems to be usable by the {@code DataStore} instances
     *         create by this provider, {@link Boolean#FALSE} if the {@code DataStore} will not be able to use
     *         the given storage, or {@code null} if this method does not have enough information.
     * @throws DataStoreException if an I/O or SQL error occurred. The error shall be unrelated to the logical
     *         structure of the storage.
     */
    public abstract Boolean canOpen(StorageConnector storage) throws DataStoreException;

    /**
     * Returns a data store implementation associated with this provider.
     *
     * <p><b>Implementation note:</b>
     * Implementors shall invoke {@link StorageConnector#closeAllExcept(Object)} after {@code DataStore}
     * creation, keeping open only the needed resource.</p>
     *
     * @param  storage Information about the storage (URL, stream, JDBC connection, <i>etc</i>).
     * @return A data store implementation associated with this provider for the given storage.
     * @throws DataStoreException if an error occurred while creating the data store instance.
     */
    public abstract DataStore open(StorageConnector storage) throws DataStoreException;
}
