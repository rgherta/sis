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
package org.apache.sis.metadata.sql;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.util.logging.WarningListeners;
import org.apache.sis.internal.system.Loggers;


/**
 * The result of a query for metadata attributes. This object {@linkplain PreparedStatement prepares a statement}
 * once for ever for a given table. When a particular record in this table is fetched, the {@link ResultSet} is
 * automatically constructed. If many attributes are fetched consecutively for the same record, then the same
 * {@link ResultSet} is reused.
 *
 * <div class="section"><b>Synchronization</b>:
 * This class is <strong>not</strong> thread-safe. Callers must perform their own synchronization in such a way
 * that only one query is executed on the same connection (JDBC connections can not be assumed thread-safe).
 * The synchronization block shall be the {@link Tables} which contain this entry.</div>
 *
 * <div class="section"><b>Closing</b>:
 * This class does not implement {@link java.lang.AutoCloseable} because it is typically closed by a different
 * thread than the one that created the {@code MetadataResult} instance. This object is closed by a background
 * thread of {@link Tables}.</div>
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @since   0.8
 * @version 0.8
 * @module
 */
final class MetadataResult {
    /**
     * The implemented interface, used for formatting error messages.
     */
    private final Class<?> type;

    /**
     * The statement associated with this entry.
     * This is the statement given to the constructor.
     */
    private final PreparedStatement statement;

    /**
     * The results, or {@code null} if not yet determined.
     */
    private ResultSet results;

    /**
     * The identifier (usually the primary key) for current results. If the record to fetch does not
     * have the same identifier, then the {@link #results} will need to be closed and reconstructed.
     */
    private String identifier;

    /**
     * The expiration time of this result. This is read and updated by {@link Tables} only.
     */
    long expireTime;

    /**
     * Where to report the warnings. This is not necessarily a logger, since users can register listeners.
     */
    private final WarningListeners<MetadataSource> listeners;

    /**
     * Constructs a metadata result from the specified connection.
     *
     * @param type       the GeoAPI interface to implement.
     * @param statement  the prepared statement.
     * @param listeners  where to report the warnings.
     */
    MetadataResult(final Class<?> type, final PreparedStatement statement,
            final WarningListeners<MetadataSource> listeners)
    {
        this.type      = type;
        this.statement = statement;
        this.listeners = listeners;
    }

    /**
     * Returns the attribute value in the given column for the given record.
     *
     * @param  id         the object identifier, usually the primary key value.
     * @param  attribute  the column name of the attribute to fetch.
     * @return the value of the requested attribute for the row identified by the given key.
     * @throws SQLException if an SQL operation failed.
     * @throws MetadataStoreException if no record has been found for the given key.
     */
    Object getValue(final String id, final String attribute) throws SQLException, MetadataStoreException {
        if (!id.equals(identifier)) {
            closeResultSet();
        }
        ResultSet r = results;
        if (r == null) {
            statement.setString(1, id);
            r = statement.executeQuery();
            if (!r.next()) {
                final String table = r.getMetaData().getTableName(1);
                r.close();
                throw new SQLException(Errors.format(Errors.Keys.RecordNotFound_2, table, id));
            }
            results = r;
            identifier = id;
        }
        return r.getObject(attribute);
    }

    /**
     * Closes the current {@link ResultSet}. Before doing so, we make an opportunist check for duplicated values
     * in the table. If a duplicate is found, a warning is logged. The log message pretends to be emitted by the
     * interface constructor, which does not exist. But this is the closest we can get from a public API.
     */
    private void closeResultSet() throws SQLException {
        final ResultSet r = results;
        results = null;               // Make sure that this field is cleared even if an exception occurs below.
        if (r != null) {
            final boolean hasNext = r.next();
            r.close();
            if (hasNext) {
                warning(type, "<init>", Errors.getResources((Locale) null).getLogRecord(
                        Level.WARNING, Errors.Keys.DuplicatedIdentifier_1, identifier));
            }
            identifier = null;
        }
    }

    /**
     * Closes the statement and free all resources.
     * After this method has been invoked, this object can not be used anymore.
     *
     * <p>This method is usually not invoked by the method or thread that created the
     * {@code StatementEntry} instance. It is invoked by {@link Tables#close()} instead.</p>
     *
     * @throws SQLException if an error occurred while closing the statement.
     */
    void close() throws SQLException {
        closeResultSet();
        statement.close();
    }

    /**
     * Closes the statement and free all resources. In case of failure while closing JDBC objects,
     * the message is logged but the process continue since we are not supposed to use the statement anymore.
     * This method is invoked from other methods that can not throws an SQL exception.
     */
    void closeQuietly() {
        try {
            close();
        } catch (Exception e) {
            /*
             * Catch Exception rather than SQLException because this method is invoked from semi-critical code
             * which need to never fail, otherwise some memory leak could occur. Pretend that the message come
             * from PreparedStatement.close(), which is the closest we can get to a public API.
             */
            final LogRecord record = new LogRecord(Level.WARNING, e.toString());
            record.setThrown(e);
            warning(PreparedStatement.class, "close", record);
        }
    }

    /**
     * Reports a warning.
     *
     * @param source  the class to report as the warning emitter.
     * @param method  the method to report as the warning emitter.
     * @param record  the warning to report.
     */
    private void warning(final Class<?> source, final String method, final LogRecord record) {
        record.setSourceClassName(source.getCanonicalName());
        record.setSourceMethodName(method);
        record.setLoggerName(Loggers.SQL);
        listeners.warning(record);
    }
}
