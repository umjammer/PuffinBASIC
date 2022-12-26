package org.puffinbasic.file;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntList;
import org.puffinbasic.domain.PuffinBasicSymbolTable;
import org.puffinbasic.domain.STObjects;
import org.puffinbasic.error.PuffinBasicInternalError;
import org.puffinbasic.error.PuffinBasicRuntimeError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.puffinbasic.domain.STObjects.PuffinBasicAtomTypeId.STRING;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.ILLEGAL_FILE_ACCESS;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.IO_ERROR;

public class PuffinBasicRandomAccessFile implements PuffinBasicFile {

    private final String filename;
    private final FileAccessMode accessMode;
    private final RandomAccessFile file;
    private final int recordLength;
    private final byte[] recordBuffer;
    private IntList recordParts;
    private long currentFilePosBytes;
    private int lastGetRecordNumber;
    private int lastPutRecordNumber;
    private FileState fileState;

    public PuffinBasicRandomAccessFile(
            @NotNull String filename,
            @NotNull FileAccessMode accessMode,
            int recordLen)
    {
        Preconditions.checkNotNull(filename);
        Preconditions.checkArgument(recordLen > 0);
        Preconditions.checkNotNull(accessMode);

        this.filename = filename;
        this.accessMode = accessMode;
        this.recordLength = recordLen;
        this.recordBuffer = new byte[recordLength];
        this.lastPutRecordNumber = this.lastGetRecordNumber = -1;
        this.currentFilePosBytes = 0;

        try {
            this.file = new RandomAccessFile(filename, accessMode.mode);
        } catch (FileNotFoundException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to open file '" + filename + "' for writing, error: "
                            + e.getMessage()
            );
        }

        this.fileState = FileState.OPEN;
    }

    @Override
    public void setFieldParams(
            PuffinBasicSymbolTable symbolTable,
            IntList recordParts)
    {
        Preconditions.checkNotNull(symbolTable);
        Preconditions.checkNotNull(recordParts);

        int totalComputedLength = 0;
        for (int recordPart : recordParts) {
            STObjects.STEntry entry = symbolTable.get(recordPart);
            STObjects.STValue value = entry.getValue();
            STObjects.PuffinBasicAtomTypeId dataType = entry.getType().getAtomTypeId();
            if (dataType != STRING) {
                throw new PuffinBasicInternalError(
                        "Expected String recordPart but found: " + dataType
                );
            }
            totalComputedLength += value.getFieldLength();
        }
        if (totalComputedLength != recordLength) {
            throw new PuffinBasicInternalError(
                    "Sum of capacity of recordParts (=" + totalComputedLength
                            + ") don't match recordLength (=" + recordLength + ")"
            );
        }
        this.recordParts = recordParts;
    }

    @Override
    public int getCurrentRecordNumber() {
        assertOpen();
        return (int) (currentFilePosBytes / recordLength);
    }

    @Override
    public long getFileSizeInBytes() {
        assertOpen();
        try {
            return file.length();
        } catch (IOException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to get length of the file '" + filename
                            + "', error: " + e.getMessage()
            );
        }
    }

    @Override
    public boolean eof() {
        return currentFilePosBytes >= getFileSizeInBytes();
    }

    @Override
    public void put(@Nullable Integer recordNumber, PuffinBasicSymbolTable symbolTable) {
        assertOpen();
        if (accessMode == FileAccessMode.READ_ONLY) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FILE_ACCESS,
                    "File " + filename + " is open for read-only"
            );
        }

        if (recordNumber == null) {
            recordNumber = lastPutRecordNumber + 1;
        }
        seekToRecord(recordNumber);
        this.lastPutRecordNumber = recordNumber;

        // Create a new buffer and fill with spaces.
        ByteBuffer bb = clearAndGetRecordBuffer();

        for (int i = 0; i < recordParts.size(); i++) {
            STObjects.STValue entry = symbolTable.get(recordParts.getInt(i)).getValue();
            String value = entry.getString();
            int valueLength = value.length();
            int fieldLength = entry.getFieldLength();

            // Put first fieldLength bytes only
            if (fieldLength < valueLength) {
                value = value.substring(0, fieldLength);
            }
            bb.put(value.getBytes());

            // If fieldLength > valueLength, skip fieldLength - valueLength
            if (fieldLength > valueLength) {
                bb.position(bb.position() + fieldLength - valueLength);
            }

        }

        // Write the record buffer to file
        try {
            file.write(recordBuffer);
        } catch (IOException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to write to file '" + filename + "', error: " + e.getMessage()
            );
        }

        updateCurrentBytePos();
    }

    @Override
    public void get(@Nullable Integer recordNumber, PuffinBasicSymbolTable symbolTable) {
        assertOpen();
        if (accessMode == FileAccessMode.WRITE_ONLY) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FILE_ACCESS,
                    "File " + filename + " is open for write-only"
            );
        }

        if (recordNumber == null) {
            recordNumber = lastGetRecordNumber + 1;
        }
        seekToRecord(recordNumber);
        this.lastGetRecordNumber = recordNumber;

        // Seek to record number and read the record into record buffer
        try {
            file.readFully(recordBuffer);
        } catch (IOException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to read from file '" + filename
                            + ", recordNumber: " + recordNumber
                            + "', error: " + e.getMessage()
            );
        }

        ByteBuffer bb = ByteBuffer.wrap(recordBuffer);
        for (int i = 0; i < recordParts.size(); i++) {
            STObjects.STValue entry = symbolTable.get(recordParts.getInt(i)).getValue();
            int fieldLength = entry.getFieldLength();
            byte[] strBytes = new byte[fieldLength];
            bb.get(strBytes);
            entry.setString(new String(strBytes));
        }

        updateCurrentBytePos();
    }

    private ByteBuffer clearAndGetRecordBuffer() {
        Arrays.fill(recordBuffer, 0, recordBuffer.length, (byte) ' ');
        return ByteBuffer.wrap(recordBuffer);
    }

    private void updateCurrentBytePos() {
        currentFilePosBytes += recordLength;
    }

    private long getRecordBytePos(long recordNumber) {
        return recordNumber * recordLength;
    }

    private void seekToRecord(int recordNumber) {
        // Seek only when record number is not sequential
        try {
            long destPosBytes = getRecordBytePos(recordNumber);
            if (destPosBytes != currentFilePosBytes) {
                file.seek(destPosBytes);
                currentFilePosBytes = destPosBytes;
            }
        } catch (IOException e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to read from file '" + filename + "', error: " + e.getMessage()
            );
        }
    }

    private void assertOpen() {
        if (!isOpen()) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FILE_ACCESS,
                    "File " + filename + " is not open!"
            );
        }
    }

    @Override
    public boolean isOpen() {
        return fileState == FileState.OPEN;
    }

    @Override
    public void close() {
        assertOpen();
        try {
            this.file.close();
        } catch (Exception e) {
            throw new PuffinBasicRuntimeError(
                    IO_ERROR,
                    "Failed to close file '" + filename + "', error: " + e.getMessage()
            );
        }
        this.fileState = FileState.CLOSED;
    }

    @Override
    public byte[] readBytes(int n) {
        throw new PuffinBasicRuntimeError(
                ILLEGAL_FILE_ACCESS,
                "Can't read single bytes from RandomAccessFile!"
        );
    }

    @Override
    public void print(String s) {
        throw new PuffinBasicRuntimeError(
                ILLEGAL_FILE_ACCESS,
                "Not implemented for RandomAccessFile!"
        );
    }

    @Override
    public String readLine() {
        throw new PuffinBasicRuntimeError(
                ILLEGAL_FILE_ACCESS,
                "Not implemented for RandomAccessFile!"
        );
    }

    @Override
    public void writeByte(byte b) {
        throw new PuffinBasicRuntimeError(
                ILLEGAL_FILE_ACCESS,
                "Not implemented for RandomAccessFile!"
        );
    }
}
