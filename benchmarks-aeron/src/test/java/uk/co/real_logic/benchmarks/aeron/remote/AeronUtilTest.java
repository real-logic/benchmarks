/*
 * Copyright 2015-2023 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.benchmarks.aeron.remote;

import io.aeron.CncFileDescriptor;
import io.aeron.archive.ArchiveMarkFile;
import io.aeron.archive.codecs.mark.MarkFileHeaderEncoder;
import io.aeron.cluster.service.ClusterMarkFile;
import org.agrona.IoUtil;
import org.agrona.MarkFile;
import org.agrona.SemanticVersion;
import org.agrona.concurrent.CachedEpochClock;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.agrona.concurrent.errors.DistinctErrorLog;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;

import static io.aeron.CncFileDescriptor.createCountersMetaDataBuffer;
import static io.aeron.CncFileDescriptor.createCountersValuesBuffer;
import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.*;
import static org.agrona.IoUtil.mapNewFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

class AeronUtilTest
{
    private final SimpleDateFormat errorDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    @AfterEach
    void after()
    {
        clearProperty(DESTINATION_CHANNELS_PROP_NAME);
        clearProperty(DESTINATION_STREAMS_PROP_NAME);
        clearProperty(SOURCE_CHANNELS_PROP_NAME);
        clearProperty(SOURCE_STREAMS_PROP_NAME);
        clearProperty(ARCHIVE_CHANNEL_PROP_NAME);
        clearProperty(ARCHIVE_STREAM_PROP_NAME);
        clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
        clearProperty(IDLE_STRATEGY_PROP_NAME);
    }

    @Test
    void defaultConfigurationValues()
    {
        assertEquals("aeron:udp?endpoint=localhost:13333|mtu=1408", destinationChannel());
        assertEquals(1_000_000_000, destinationStreamId());
        assertEquals("aeron:udp?endpoint=localhost:13334|mtu=1408", sourceChannel());
        assertEquals(1_000_000_001, sourceStreamId());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_100_000, archiveStream());
        assertFalse(embeddedMediaDriver());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
    }

    @Test
    void defaultConfigurationValuesShouldBeUsedIfEmptyValuesAreSet()
    {
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "");
        setProperty(SOURCE_STREAMS_PROP_NAME, "");
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, "");
        setProperty(ARCHIVE_STREAM_PROP_NAME, "");
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "");
        setProperty(IDLE_STRATEGY_PROP_NAME, "");

        assertEquals("aeron:udp?endpoint=localhost:13333|mtu=1408", destinationChannel());
        assertEquals(1_000_000_000, destinationStreamId());
        assertEquals("aeron:udp?endpoint=localhost:13334|mtu=1408", sourceChannel());
        assertEquals(1_000_000_001, sourceStreamId());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_100_000, archiveStream());
        assertFalse(embeddedMediaDriver());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
    }

    @Test
    void explicitConfigurationValues()
    {
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "ch1:5001,ch2:5002,ch3:5003");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "100");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "ch1:8001,ch2:8002,ch3:8003");
        setProperty(SOURCE_STREAMS_PROP_NAME, "200");
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, "localhost");
        setProperty(ARCHIVE_STREAM_PROP_NAME, "777");
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "true");
        setProperty(IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());

        assertEquals("ch1:5001,ch2:5002,ch3:5003", destinationChannel());
        assertEquals(100, destinationStreamId());
        assertEquals("ch1:8001,ch2:8002,ch3:8003", sourceChannel());
        assertEquals(200, sourceStreamId());
        assertEquals("localhost", archiveChannel());
        assertEquals(777, archiveStream());
        assertTrue(embeddedMediaDriver());
        assertEquals(YieldingIdleStrategy.class, idleStrategy().getClass());
    }

    @Test
    void connectionTimeoutNsIsSixtySecondsByDefault()
    {
        System.setProperty(CONNECTION_TIMEOUT_PROP_NAME, "");
        try
        {
            assertEquals(SECONDS.toNanos(60), connectionTimeoutNs());
        }
        finally
        {
            System.clearProperty(CONNECTION_TIMEOUT_PROP_NAME);
        }
    }

    @ParameterizedTest
    @MethodSource("connectionTimeouts")
    void connectionTimeoutNsReturnsUserSpecifiedValue(final String connectionTimeout, final long expectedValueNs)
    {
        System.setProperty(CONNECTION_TIMEOUT_PROP_NAME, connectionTimeout);
        try
        {
            assertEquals(expectedValueNs, connectionTimeoutNs());
        }
        finally
        {
            System.clearProperty(CONNECTION_TIMEOUT_PROP_NAME);
        }
    }

    @Test
    void awaitConnectedReturnsImmediatelyIfAlreadyConnected()
    {
        final BooleanSupplier connection = mock(BooleanSupplier.class);
        when(connection.getAsBoolean()).thenReturn(true);
        final long connectionTimeoutNs = 0;
        final NanoClock clock = mock(NanoClock.class);

        awaitConnected(connection, connectionTimeoutNs, clock);

        final InOrder inOrder = inOrder(clock, connection);
        inOrder.verify(clock).nanoTime();
        inOrder.verify(connection).getAsBoolean();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void awaitConnectedShouldYieldUntilConnected()
    {
        final BooleanSupplier connection = mock(BooleanSupplier.class);
        when(connection.getAsBoolean()).thenReturn(false, false, true);
        final long connectionTimeoutNs = 10;
        final NanoClock clock = mock(NanoClock.class);
        when(clock.nanoTime()).thenReturn(0L, 3L, 8L, 15L);

        awaitConnected(connection, connectionTimeoutNs, clock);

        verify(connection, times(3)).getAsBoolean();
        verify(clock, times(3)).nanoTime();
        verifyNoMoreInteractions(clock, connection);
    }

    @Test
    void awaitConnectedShouldThrowIfNotConnectedUntilTimeout()
    {
        final BooleanSupplier connection = mock(BooleanSupplier.class);
        final long connectionTimeoutNs = 8;
        final NanoClock clock = mock(NanoClock.class);
        when(clock.nanoTime()).thenReturn(Long.MAX_VALUE);

        final IllegalStateException exception =
            assertThrows(IllegalStateException.class, () -> awaitConnected(connection, connectionTimeoutNs, clock));
        assertEquals("Failed to connect within timeout of 8ns", exception.getMessage());
    }

    @Test
    void resolveMarkFileShouldUseParentDirectoryIfLinkFileDoesNotExist(@TempDir final Path parentDir)
    {
        final File markFile = resolveMarkFile(parentDir.toFile(), "my.mark", "my.link");

        assertNotNull(markFile);
        assertEquals(parentDir.toFile(), markFile.getParentFile());
        assertEquals("my.mark", markFile.getName());
    }

    @Test
    void resolveMarkFileShouldUseParentDirectoryIfLinkFileIsNotARegularFile(@TempDir final Path parentDir)
        throws IOException
    {
        final String linkFileName = "my.link";
        final String markFileName = "my.mark";
        final Path dir = parentDir.resolve(linkFileName);
        Files.createDirectory(dir);
        assertTrue(Files.exists(dir));

        final File markFile = resolveMarkFile(parentDir.toFile(), markFileName, linkFileName);

        assertNotNull(markFile);
        assertEquals(parentDir.toFile(), markFile.getParentFile());
        assertEquals(markFileName, markFile.getName());
    }

    @Test
    void resolveMarkFileShouldReadLocationFromALinkFile(
        @TempDir final Path parentDir,
        @TempDir final Path someOtherLocation)
        throws IOException
    {
        final String linkFileName = "test.lnk";
        final String markFileName = "destination.txt";
        final Path expectedMarkFileDir = someOtherLocation.resolve("a/b/c/d");
        final Path linkFile = parentDir.resolve(linkFileName);
        Files.write(
            linkFile,
            expectedMarkFileDir.toAbsolutePath().toString().getBytes(US_ASCII),
            StandardOpenOption.CREATE_NEW);
        assertTrue(Files.exists(linkFile));

        final File markFile = resolveMarkFile(parentDir.toFile(), markFileName, linkFileName);

        assertNotNull(markFile);
        assertEquals(expectedMarkFileDir.toFile(), markFile.getParentFile());
        assertEquals(markFileName, markFile.getName());
    }

    @Test
    void dumpAeronStatsIsANoOpIfCncDoesNotExist(
        @TempDir final Path destDir, @TempDir final Path other)
    {
        final Path statsFile = destDir.resolve("media-driver-stats.txt");
        final Path errorsFile = destDir.resolve("media-driver-errors.txt");
        final Path cncFile = other.resolve("cnc.dat");
        assertFalse(Files.exists(statsFile));
        assertFalse(Files.exists(errorsFile));
        assertFalse(Files.exists(cncFile));

        dumpAeronStats(cncFile.toFile(), statsFile, errorsFile);

        assertFalse(Files.exists(statsFile));
        assertFalse(Files.exists(errorsFile));
    }

    @Test
    void dumpAeronStatsShouldSaveCountersAndErrors(
        @TempDir final Path destDir, @TempDir final Path other) throws IOException
    {
        final Path statsFile = destDir.resolve("aeron-stat.txt");
        final Path errorsFile = other.resolve("error-stat.txt");
        assertFalse(Files.exists(statsFile));
        assertFalse(Files.exists(errorsFile));
        final Path cncFile = other.resolve("cnc.dat");
        final MappedByteBuffer cncByteBuffer = mapNewFile(cncFile.toFile(), 1024 * 1024);
        final long timeMillis = System.currentTimeMillis();
        final CachedEpochClock epochClock = new CachedEpochClock();
        final Exception error1 = new Exception(new RuntimeException("nested exception"));
        final AssertionError error2 = new AssertionError("error2");
        final long startTimestampMs = 562378465238L;
        final int cncVersion = SemanticVersion.compose(3, 4, 19);
        try
        {
            final UnsafeBuffer metaDataBuffer = CncFileDescriptor.createMetaDataBuffer(cncByteBuffer);
            CncFileDescriptor.fillMetaData(
                metaDataBuffer,
                1024 + RingBufferDescriptor.TRAILER_LENGTH,
                1024 + RingBufferDescriptor.TRAILER_LENGTH,
                16 * 1024,
                4096,
                SECONDS.toNanos(5),
                512 * 1024,
                startTimestampMs,
                212);
            metaDataBuffer.putInt(CncFileDescriptor.CNC_VERSION_FIELD_OFFSET, cncVersion);

            final CountersManager countersManager = new CountersManager(
                createCountersMetaDataBuffer(cncByteBuffer, metaDataBuffer),
                createCountersValuesBuffer(cncByteBuffer, metaDataBuffer));

            countersManager.newCounter("test 1", 1111).set(42);
            countersManager.newCounter("another one").set(15);
            countersManager.newCounter("last", 456).set(Long.MIN_VALUE);
            final AtomicCounter deleteMe = countersManager.newCounter("delete me", 333);
            deleteMe.set(Long.MAX_VALUE);
            countersManager.free(deleteMe.id());

            epochClock.update(timeMillis);
            final DistinctErrorLog errorLog = new DistinctErrorLog(
                CncFileDescriptor.createErrorLogBuffer(cncByteBuffer, metaDataBuffer), epochClock, US_ASCII);

            assertTrue(errorLog.record(error1));
            epochClock.advance(111_111);
            assertTrue(errorLog.record(error1));
            epochClock.advance(222_222);
            assertTrue(errorLog.record(error1));

            epochClock.advance(333_333);
            assertTrue(errorLog.record(error2));
        }
        finally
        {
            IoUtil.unmap(cncByteBuffer);
        }

        dumpAeronStats(cncFile.toFile(), statsFile, errorsFile);

        assertEquals(Arrays.asList(
            "CnC version: " + SemanticVersion.toString(cncVersion),
            "PID: 212",
            "Start time: " + errorDateFormat.format(new Date(startTimestampMs)),
            "================================================================",
            "  0:                   42 - test 1",
            "  1:                   15 - another one",
            "  2: -9,223,372,036,854,775,808 - last"),
            Files.readAllLines(statsFile, US_ASCII));
        final String errors = new String(Files.readAllBytes(errorsFile), US_ASCII);
        assertTrue(errors.startsWith(
            System.lineSeparator() + "3 observations from " + errorDateFormat.format(new Date(timeMillis))));
        assertTrue(errors.contains(error1.getMessage()));
        assertTrue(errors.contains("Caused by: " + error1.getCause().toString()));
        assertTrue(errors.contains("1 observations from " + errorDateFormat.format(new Date(epochClock.time()))));
        assertTrue(errors.contains(error2.getMessage()));
        assertTrue(errors.endsWith("2 distinct errors observed." + System.lineSeparator()));
    }

    @Test
    void dumpArchiveIsANoOpIfMarkFileNotFound(
        @TempDir final Path resultsDir, @TempDir final Path archiveDir) throws IOException
    {
        final Path errorsFile = resultsDir.resolve("archive-errors.txt");
        assertFalse(Files.exists(errorsFile));

        dumpArchiveErrors(archiveDir.toFile(), errorsFile);

        assertFalse(Files.exists(errorsFile));
    }

    @Test
    void dumpArchiveErrorsSavesErrorsRecordedInTheMarkFile(
        @TempDir final Path resultsDir,
        @TempDir final Path archiveDir,
        @TempDir final Path markFileDir) throws IOException
    {
        final Path errorsFile = resultsDir.resolve("my.test");
        assertFalse(Files.exists(errorsFile));
        final Path markFile = markFileDir.resolve(ArchiveMarkFile.FILENAME);
        MarkFile.ensureMarkFileLink(archiveDir.toFile(), markFile.toFile(), ArchiveMarkFile.LINK_FILENAME);

        final CachedEpochClock epochClock = new CachedEpochClock();
        final long startTime = System.currentTimeMillis();
        epochClock.update(startTime);
        final IOError exception = new IOError(new IOException("I/O err"));
        final MappedByteBuffer mappedByteBuffer = mapNewFile(markFile.toFile(), 32 * 1024);
        try
        {
            final MarkFileHeaderEncoder headerEncoder = new MarkFileHeaderEncoder();
            headerEncoder.wrap(new UnsafeBuffer(mappedByteBuffer), 0);
            final int errorBufferLength = 16 * 1024;
            headerEncoder
                .version(ArchiveMarkFile.SEMANTIC_VERSION)
                .activityTimestamp(epochClock.time())
                .errorBufferLength(errorBufferLength)
                .headerLength(ArchiveMarkFile.HEADER_LENGTH);

            final DistinctErrorLog errorLog = new DistinctErrorLog(
                new UnsafeBuffer(mappedByteBuffer, ArchiveMarkFile.HEADER_LENGTH, errorBufferLength),
                epochClock,
                US_ASCII);
            assertTrue(errorLog.record(exception));
            epochClock.advance(23947623669L);
            assertTrue(errorLog.record(exception));
            assertTrue(errorLog.record(exception));
            assertTrue(errorLog.record(exception));
        }
        finally
        {
            IoUtil.unmap(mappedByteBuffer);
        }


        dumpArchiveErrors(archiveDir.toFile(), errorsFile);

        final String errors = new String(Files.readAllBytes(errorsFile), US_ASCII);
        assertTrue(errors.startsWith(
            System.lineSeparator() + "4 observations from " + errorDateFormat.format(new Date(startTime))));
        assertTrue(errors.contains("java.io.IOError: java.io.IOException: I/O err"));
        assertTrue(errors.endsWith("1 distinct errors observed." + System.lineSeparator()));
    }

    @Test
    void dumpClusterErrorsIsANoOpIfMarkFileNotFound(
        @TempDir final Path resultsDir, @TempDir final Path clusterDir)
    {
        final Path errorsFile = resultsDir.resolve("my.result");
        assertFalse(Files.exists(errorsFile));

        dumpClusterErrors(errorsFile, clusterDir.toFile(), ClusterMarkFile.FILENAME, ClusterMarkFile.LINK_FILENAME);

        assertFalse(Files.exists(errorsFile));
    }

    @Test
    void dumpClusterErrorsIsANoOpIfMarkFileHasNoErrors(
        @TempDir final Path resultsDir, @TempDir final Path clusterDir)
    {
        final Path errorsFile = resultsDir.resolve("my.result");
        assertFalse(Files.exists(errorsFile));
        final Path markFile = clusterDir.resolve(ClusterMarkFile.FILENAME);
        final MappedByteBuffer mappedByteBuffer = mapNewFile(
            markFile.toFile(), ClusterMarkFile.HEADER_LENGTH + ClusterMarkFile.ERROR_BUFFER_MIN_LENGTH);
        try
        {
            final io.aeron.cluster.codecs.mark.MarkFileHeaderEncoder headerEncoder =
                new io.aeron.cluster.codecs.mark.MarkFileHeaderEncoder();
            headerEncoder.wrap(new UnsafeBuffer(mappedByteBuffer), 0);
            headerEncoder
                .version(ClusterMarkFile.SEMANTIC_VERSION)
                .activityTimestamp(System.currentTimeMillis())
                .errorBufferLength(ClusterMarkFile.ERROR_BUFFER_MIN_LENGTH)
                .headerLength(ClusterMarkFile.HEADER_LENGTH);
        }
        finally
        {
            IoUtil.unmap(mappedByteBuffer);
        }

        dumpClusterErrors(errorsFile, clusterDir.toFile(), ClusterMarkFile.FILENAME, ClusterMarkFile.LINK_FILENAME);

        assertFalse(Files.exists(errorsFile));
    }

    @Test
    void dumpClusterErrorsSavesErrorsRecordedInTheMarkFile(
        @TempDir final Path resultsDir,
        @TempDir final Path clusterDir,
        @TempDir final Path markFileDir) throws IOException
    {
        final Path errorsFile = resultsDir.resolve("final.result.err");
        assertFalse(Files.exists(errorsFile));
        final Path markFile = markFileDir.resolve("cluster.data.file");
        final Path linkFile = markFileDir.resolve("this.is.link");
        MarkFile.ensureMarkFileLink(clusterDir.toFile(), markFile.toFile(), linkFile.getFileName().toString());

        final CachedEpochClock epochClock = new CachedEpochClock();
        final long startTime = System.currentTimeMillis();
        epochClock.update(startTime);
        final IndexOutOfBoundsException exception = new IndexOutOfBoundsException("Division by zero");
        final MappedByteBuffer mappedByteBuffer = mapNewFile(
            markFile.toFile(), ClusterMarkFile.HEADER_LENGTH + ClusterMarkFile.ERROR_BUFFER_MIN_LENGTH);
        try
        {
            final io.aeron.cluster.codecs.mark.MarkFileHeaderEncoder headerEncoder =
                new io.aeron.cluster.codecs.mark.MarkFileHeaderEncoder();
            headerEncoder.wrap(new UnsafeBuffer(mappedByteBuffer), 0);
            headerEncoder
                .version(ClusterMarkFile.SEMANTIC_VERSION)
                .activityTimestamp(epochClock.time())
                .errorBufferLength(ClusterMarkFile.ERROR_BUFFER_MIN_LENGTH)
                .headerLength(ClusterMarkFile.HEADER_LENGTH);

            final DistinctErrorLog errorLog = new DistinctErrorLog(
                new UnsafeBuffer(
                mappedByteBuffer, ClusterMarkFile.HEADER_LENGTH, ClusterMarkFile.ERROR_BUFFER_MIN_LENGTH),
                epochClock,
                US_ASCII);
            assertTrue(errorLog.record(exception));
            assertTrue(errorLog.record(exception));
        }
        finally
        {
            IoUtil.unmap(mappedByteBuffer);
        }

        dumpClusterErrors(
            errorsFile, clusterDir.toFile(), markFile.getFileName().toString(), linkFile.getFileName().toString());

        final String errors = new String(Files.readAllBytes(errorsFile), US_ASCII);
        assertTrue(errors.startsWith(
            System.lineSeparator() + "2 observations from " + errorDateFormat.format(new Date(startTime))));
        assertTrue(errors.contains("java.lang.IndexOutOfBoundsException: Division by zero"));
        assertTrue(errors.endsWith("1 distinct errors observed." + System.lineSeparator()));
    }

    private static List<Arguments> connectionTimeouts()
    {
        return Arrays.asList(
            Arguments.arguments("5ns", 5L),
            Arguments.arguments("16us", MICROSECONDS.toNanos(16)),
            Arguments.arguments("31ms", MILLISECONDS.toNanos(31)),
            Arguments.arguments("42s", SECONDS.toNanos(42)));
    }
}
