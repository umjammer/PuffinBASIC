package org.puffinbasic.runtime;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.puffinbasic.domain.PuffinBasicSymbolTable;
import org.puffinbasic.domain.STObjects;
import org.puffinbasic.domain.STObjects.AbstractSTEntry;
import org.puffinbasic.domain.STObjects.ArrayType;
import org.puffinbasic.domain.STObjects.STEntry;
import org.puffinbasic.domain.STObjects.STFloat32ArrayValue;
import org.puffinbasic.domain.STObjects.STFloat64ArrayValue;
import org.puffinbasic.domain.STObjects.STInt32ArrayValue;
import org.puffinbasic.domain.STObjects.STInt64ArrayValue;
import org.puffinbasic.domain.STObjects.STStringArrayValue;
import org.puffinbasic.error.PuffinBasicRuntimeError;
import org.puffinbasic.parser.PuffinBasicIR.Instruction;

import java.util.Arrays;
import java.util.List;

import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.DATA_TYPE_MISMATCH;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.ILLEGAL_FUNCTION_PARAM;
import static org.puffinbasic.runtime.Functions.throwUnsupportedType;

final class ArraysUtil {

    static final class ArrayState {
        private int dimIndex;

        int getAndIncrement() {
            return dimIndex++;
        }

        void reset() {
            dimIndex = 0;
        }
    }

    static void dim(PuffinBasicSymbolTable symbolTable, List<Instruction> params, Instruction instruction) {
        IntList dims = new IntArrayList(params.size());
        for (Instruction param : params) {
            dims.add(symbolTable.get(param.op1).getValue().getInt32());
        }
        symbolTable.get(instruction.op1).getValue().setArrayDimensions(dims);
    }

    static void resetIndex(ArrayState state, PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        state.reset();
        symbolTable.get(instruction.op1).getValue().resetArrayIndex();
    }

    static void allocArray(PuffinBasicSymbolTable symbolTable, List<Instruction> params, Instruction instruction) {
        IntList dims = new IntArrayList(params.size());
        for (Instruction param : params) {
            dims.add(symbolTable.get(param.op1).getValue().getInt32());
        }
        STEntry arrayEntry = symbolTable.get(instruction.result);
        ArrayType arrayType = (ArrayType) arrayEntry.getType();
        arrayType.setArrayDimensions(dims);
        arrayEntry.getValue().setArrayDimensions(dims);
    }

    static void reallocArray(PuffinBasicSymbolTable symbolTable, List<Instruction> params, Instruction instruction) {
        IntList dims = new IntArrayList(params.size());
        for (Instruction param : params) {
            dims.add(symbolTable.get(param.op1).getValue().getInt32());
        }
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        ArrayType arrayType = (ArrayType) arrayEntry.getType();
        arrayType.setArrayDimensions(dims);
        // Create new value
        ((AbstractSTEntry) arrayEntry).createAndSetInstance(symbolTable);
    }

    static void setIndex(ArrayState state, PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        int index = symbolTable.get(instruction.op2).getValue().getInt32();
        symbolTable.get(instruction.op1).getValue().setArrayIndex(state.getAndIncrement(), index);
    }

    static void arrayref(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        int index = symbolTable.get(instruction.op1).getValue().getArrayIndex1D();
        symbolTable.get(instruction.result).getValue().setArrayReferenceIndex1D(index);
    }

    static void arrayfill(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STObjects.STValue array = symbolTable.get(instruction.op1).getValue();
        STEntry fillEntry = symbolTable.get(instruction.op2);
        STObjects.STValue fill = fillEntry.getValue();

        switch (fillEntry.getType().getAtomTypeId()) {
            case INT32:
                array.fill(fill.getInt32());
                break;
            case INT64:
                array.fill(fill.getInt64());
                break;
            case FLOAT:
                array.fill(fill.getFloat32());
                break;
            case DOUBLE:
                array.fill(fill.getFloat64());
                break;
            case STRING:
                array.fillString(fill.getString());
                break;
            default:
                throwUnsupportedType(fillEntry.getType().getAtomTypeId());
        }
    }

    static void arrayCopy(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry array1Entry = symbolTable.get(instruction.op1);
        STObjects.STValue array1 = array1Entry.getValue();
        STEntry array2Entry = symbolTable.get(instruction.op2);
        STObjects.STValue array2 = array2Entry.getValue();
        if (array1Entry.getType().getAtomTypeId() != array2Entry.getType().getAtomTypeId()) {
            throw new PuffinBasicRuntimeError(
                    DATA_TYPE_MISMATCH,
                    "Array data type mismatch: " + array1Entry.getType().getAtomTypeId()
                            + " is not compatible with " + array2Entry.getType().getAtomTypeId()
            );
        }
        if (array1.getTotalLength() != array2.getTotalLength()) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FUNCTION_PARAM,
                    "Array length mismatch: " + array1.getTotalLength()
                            + " is not compatible with " + array2.getTotalLength()
            );
        }

        switch (array1Entry.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array1).getValue();
                System.arraycopy(value, 0, ((STInt32ArrayValue) array2).getValue(), 0, value.length);
            }
                break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array1).getValue();
                System.arraycopy(
                        value, 0, ((STInt64ArrayValue) array2).getValue(), 0, value.length
                );
            }
                break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array1).getValue();
                System.arraycopy(
                        value, 0, ((STFloat32ArrayValue) array2).getValue(), 0, value.length
                );
            }
                break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array1).getValue();
                System.arraycopy(
                        value, 0, ((STFloat64ArrayValue) array2).getValue(), 0, value.length
                );
            }
                break;
            case STRING: {
                String[] value = ((STStringArrayValue) array1).getValue();
                System.arraycopy(
                        value, 0, ((STStringArrayValue) array2).getValue(), 0, value.length
                );
            }
                break;
            default:
                throwUnsupportedType(array1Entry.getType().getAtomTypeId());
        }
    }

    static void array2dShiftVertical(
            PuffinBasicSymbolTable symbolTable,
            Instruction instruction)
    {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        int shift = symbolTable.get(instruction.op2).getValue().getInt32();
        IntList dims = array.getArrayDimensions();
        // Arrays are row-major.
        int dim1 = dims.getInt(0);
        int dim2 = dims.getInt(1);
        int n = array.getTotalLength();
        int delta = (Math.abs(shift) % dim1) * dim2;
        int src0, dst0, len = n - delta, fillSrc0;
        if (shift > 0) {
            src0 = 0;
            dst0 = delta;
            fillSrc0 = 0;
        } else {
            src0 = delta;
            dst0 = 0;
            fillSrc0 = n - delta;
        }

        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array).getValue();
                System.arraycopy(value, src0, value, dst0, len);
                Arrays.fill(value, fillSrc0, fillSrc0 + delta, 0);
            }
            break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array).getValue();
                System.arraycopy(value, src0, value, dst0, len);
                Arrays.fill(value, fillSrc0, fillSrc0 + delta, 0);
            }
            break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array).getValue();
                System.arraycopy(value, src0, value, dst0, len);
                Arrays.fill(value, fillSrc0, fillSrc0 + delta, 0);
            }
            break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array).getValue();
                System.arraycopy(value, src0, value, dst0, len);
                Arrays.fill(value, fillSrc0, fillSrc0 + delta, 0);
            }
            break;
            case STRING: {
                String[] value = ((STStringArrayValue) array).getValue();
                System.arraycopy(value, src0, value, dst0, len);
                Arrays.fill(value, fillSrc0, fillSrc0 + delta, "");
            }
            break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
    }

    static void array2dShiftHorizontal(
            PuffinBasicSymbolTable symbolTable,
            Instruction instruction)
    {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        int shift = symbolTable.get(instruction.op2).getValue().getInt32();
        IntList dims = array.getArrayDimensions();
        // Arrays are row-major.
        int dim1 = dims.getInt(0);
        int dim2 = dims.getInt(1);
        int n = array.getTotalLength();
        int delta = Math.abs(shift) % dim2;
        int src0, dst0, len = dim2 - delta, fillSrc0;
        if (shift > 0) {
            src0 = 0;
            dst0 = delta;
            fillSrc0 = 0;
        } else {
            src0 = delta;
            dst0 = 0;
            fillSrc0 = dim2 - delta;
        }

        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array).getValue();
                if (shift >= 0) {
                    for (int dc = dst0 + len - 1, sc = src0 + len - 1; dc >= dst0; dc--, sc--) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                } else {
                    for (int dc = dst0, sc = src0; dc < dst0 + len; dc++, sc++) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                }
                for (int c = fillSrc0; c < fillSrc0 + delta; c++) {
                    for (int r = 0; r < dim1; r++) {
                        value[r * dim2 + c] = 0;
                    }
                }
            }
            break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array).getValue();
                if (shift >= 0) {
                    for (int dc = dst0 + len - 1, sc = src0 + len - 1; dc >= dst0; dc--, sc--) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                } else {
                    for (int dc = dst0, sc = src0; dc < dst0 + len; dc++, sc++) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                }
                for (int c = fillSrc0; c < fillSrc0 + delta; c++) {
                    for (int r = 0; r < dim1; r++) {
                        value[r * dim2 + c] = 0;
                    }
                }
            }
            break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array).getValue();
                if (shift >= 0) {
                    for (int dc = dst0 + len - 1, sc = src0 + len - 1; dc >= dst0; dc--, sc--) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                } else {
                    for (int dc = dst0, sc = src0; dc < dst0 + len; dc++, sc++) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                }
                for (int c = fillSrc0; c < fillSrc0 + delta; c++) {
                    for (int r = 0; r < dim1; r++) {
                        value[r * dim2 + c] = 0;
                    }
                }
            }
            break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array).getValue();
                if (shift >= 0) {
                    for (int dc = dst0 + len - 1, sc = src0 + len - 1; dc >= dst0; dc--, sc--) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                } else {
                    for (int dc = dst0, sc = src0; dc < dst0 + len; dc++, sc++) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                }
                for (int c = fillSrc0; c < fillSrc0 + delta; c++) {
                    for (int r = 0; r < dim1; r++) {
                        value[r * dim2 + c] = 0;
                    }
                }
            }
            break;
            case STRING: {
                String[] value = ((STStringArrayValue) array).getValue();
                if (shift >= 0) {
                    for (int dc = dst0 + len - 1, sc = src0 + len - 1; dc >= dst0; dc--, sc--) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                } else {
                    for (int dc = dst0, sc = src0; dc < dst0 + len; dc++, sc++) {
                        for (int r = 0; r < dim1; r++) {
                            int dr = r * dim2;
                            value[dr + dc] = value[dr + sc];
                        }
                    }
                }
                for (int c = fillSrc0; c < fillSrc0 + delta; c++) {
                    for (int r = 0; r < dim1; r++) {
                        value[r * dim2 + c] = "";
                    }
                }
            }
            break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
    }

    static void array1DCopy(
            PuffinBasicSymbolTable symbolTable,
            Instruction i0,
            Instruction i1,
            Instruction instruction)
    {
        STEntry srcEntry = symbolTable.get(i0.op1);
        STObjects.STValue src = srcEntry.getValue();
        int src0 = symbolTable.get(i0.op2).getValue().getInt32();
        STEntry dstEntry = symbolTable.get(i1.op1);
        STObjects.STValue dst = dstEntry.getValue();
        int dst0 = symbolTable.get(i1.op2).getValue().getInt32();
        int len = symbolTable.get(instruction.op1).getValue().getInt32();
        if (srcEntry.getType().getAtomTypeId() != dstEntry.getType().getAtomTypeId()) {
            throw new PuffinBasicRuntimeError(
                    DATA_TYPE_MISMATCH,
                    "Array data type mismatch: " + srcEntry.getType().getAtomTypeId()
                            + " is not compatible with " + dstEntry.getType().getAtomTypeId()
            );
        }
        if (src.getNumArrayDimensions() != 1 && dst.getNumArrayDimensions() != 1) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FUNCTION_PARAM,
                    "Array #dim!=1 : src=" + src.getNumArrayDimensions()
                            + " and dst=" + dst.getNumArrayDimensions()
            );
        }
        if (src0 < 0 || src0 >= src.getTotalLength()|| dst0 < 0 || len < 0 || dst0 + len > dst.getTotalLength()) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FUNCTION_PARAM,
                    "Bad params: srcOrigin=" + src0
                            + " dstOrigin=" + dst0
                            + " len=" + len
                            + " srcArraySize=" + src.getTotalLength()
                            + " dstArraySize=" + dst.getTotalLength()
            );
        }

        switch (srcEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) src).getValue();
                System.arraycopy(value, src0, ((STInt32ArrayValue) dst).getValue(), dst0, len);
            }
            break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) src).getValue();
                System.arraycopy(
                        value, src0, ((STInt64ArrayValue) dst).getValue(), dst0, len
                );
            }
            break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) src).getValue();
                System.arraycopy(
                        value, src0, ((STFloat32ArrayValue) dst).getValue(), dst0, len
                );
            }
            break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) src).getValue();
                System.arraycopy(
                        value, src0, ((STFloat64ArrayValue) dst).getValue(), dst0, len
                );
            }
            break;
            case STRING: {
                String[] value = ((STStringArrayValue) src).getValue();
                System.arraycopy(
                        value, src0, ((STStringArrayValue) dst).getValue(), dst0, len
                );
            }
            break;
            default:
                throwUnsupportedType(srcEntry.getType().getAtomTypeId());
        }
    }

    static void array1dSort(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry entry = symbolTable.get(instruction.op1);
        STObjects.STValue array = entry.getValue();

        switch (entry.getType().getAtomTypeId()) {
            case INT32:
                Arrays.sort(((STInt32ArrayValue) array).getValue());
                break;
            case INT64:
                Arrays.sort(((STInt64ArrayValue) array).getValue());
                break;
            case FLOAT:
                Arrays.sort(((STFloat32ArrayValue) array).getValue());
                break;
            case DOUBLE:
                Arrays.sort(((STFloat64ArrayValue) array).getValue());
                break;
            case STRING:
                Arrays.sort(((STStringArrayValue) array).getValue());
                break;
            default:
                throwUnsupportedType(entry.getType().getAtomTypeId());
        }
    }

    static void array1dBinSearch(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        STObjects.STValue search = symbolTable.get(instruction.op2).getValue();
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        int index = -1;
        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32:
                index = Arrays.binarySearch(((STInt32ArrayValue) array).getValue(), search.getInt32());
                break;
            case INT64:
                index = Arrays.binarySearch(((STInt64ArrayValue) array).getValue(), search.getInt64());
                break;
            case FLOAT:
                index = Arrays.binarySearch(((STFloat32ArrayValue) array).getValue(), search.getFloat32());
                break;
            case DOUBLE:
                index = Arrays.binarySearch(((STFloat64ArrayValue) array).getValue(), search.getFloat64());
                break;
            case STRING:
                index = Arrays.binarySearch(((STStringArrayValue) array).getValue(), search.getString());
                break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
        result.setInt32(index);
    }

    static void array1dMin(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array).getValue();
                int min = Integer.MAX_VALUE;
                for (int v : value) {
                    if (v < min) {
                        min = v;
                    }
                }
                result.setInt32(min);
            }
                break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array).getValue();
                long min = Long.MAX_VALUE;
                for (long v : value) {
                    if (v < min) {
                        min = v;
                    }
                }
                result.setInt64(min);
            }
                break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array).getValue();
                float min = Float.MAX_VALUE;
                for (float v : value) {
                    if (v < min) {
                        min = v;
                    }
                }
                result.setFloat32(min);
            }
                break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array).getValue();
                double min = Double.MAX_VALUE;
                for (double v : value) {
                    if (v < min) {
                        min = v;
                    }
                }
                result.setFloat64(min);
            }
                break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
    }

    static void array1dMax(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array).getValue();
                int max = Integer.MIN_VALUE;
                for (int v : value) {
                    if (v > max) {
                        max = v;
                    }
                }
                result.setInt32(max);
            }
            break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array).getValue();
                long max = Long.MIN_VALUE;
                for (long v : value) {
                    if (v > max) {
                        max = v;
                    }
                }
                result.setInt64(max);
            }
            break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array).getValue();
                float max = Float.MIN_VALUE;
                for (float v : value) {
                    if (v > max) {
                        max = v;
                    }
                }
                result.setFloat32(max);
            }
            break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array).getValue();
                double max = Double.MIN_VALUE;
                for (double v : value) {
                    if (v > max) {
                        max = v;
                    }
                }
                result.setFloat64(max);
            }
            break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
    }

    static void array1dMean(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        SummaryStatistics stats = array1dSummaryStats(arrayEntry);
        result.setFloat64(stats.getMean());
    }

    static void array1dStddev(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        SummaryStatistics stats = array1dSummaryStats(arrayEntry);
        result.setFloat64(Math.sqrt(stats.getVariance()));
    }

    static void array1dSum(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        SummaryStatistics stats = array1dSummaryStats(arrayEntry);
        result.setFloat64(stats.getSum());
    }

    static void array1dMedian(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        DescriptiveStatistics stats = array1dDescriptiveStats(arrayEntry);
        result.setFloat64(stats.getPercentile(50));
    }

    static void array1dPercentile(PuffinBasicSymbolTable symbolTable, Instruction instruction) {
        STEntry arrayEntry = symbolTable.get(instruction.op1);
        double pct = symbolTable.get(instruction.op2).getValue().getFloat64();
        if (pct < 0 || pct > 100) {
            throw new PuffinBasicRuntimeError(
                    PuffinBasicRuntimeError.ErrorCode.DATA_OUT_OF_RANGE,
                    "Percentile value out of range: " + pct
            );
        }
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();
        DescriptiveStatistics stats = array1dDescriptiveStats(arrayEntry);
        result.setFloat64(stats.getPercentile(pct));
    }

    private static SummaryStatistics array1dSummaryStats(STEntry array) {
        SummaryStatistics stats = new SummaryStatistics();
        switch (array.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array.getValue()).getValue();
                for (int v : value) {
                    stats.addValue(v);
                }
            }
            break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array.getValue()).getValue();
                for (long v : value) {
                    stats.addValue(v);
                }
            }
            break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array.getValue()).getValue();
                for (float v : value) {
                    stats.addValue(v);
                }
            }
            break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array.getValue()).getValue();
                for (double v : value) {
                    stats.addValue(v);
                }
            }
            break;
            default:
                throwUnsupportedType(array.getType().getAtomTypeId());
        }
        return stats;
    }

    private static DescriptiveStatistics array1dDescriptiveStats(STEntry array) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        switch (array.getType().getAtomTypeId()) {
            case INT32: {
                int[] value = ((STInt32ArrayValue) array.getValue()).getValue();
                for (int v : value) {
                    stats.addValue(v);
                }
            }
            break;
            case INT64: {
                long[] value = ((STInt64ArrayValue) array.getValue()).getValue();
                for (long v : value) {
                    stats.addValue(v);
                }
            }
            break;
            case FLOAT: {
                float[] value = ((STFloat32ArrayValue) array.getValue()).getValue();
                for (float v : value) {
                    stats.addValue(v);
                }
            }
            break;
            case DOUBLE: {
                double[] value = ((STFloat64ArrayValue) array.getValue()).getValue();
                for (double v : value) {
                    stats.addValue(v);
                }
            }
            break;
            default:
                throwUnsupportedType(array.getType().getAtomTypeId());
        }
        return stats;
    }

    static void array2dFindRow(
            PuffinBasicSymbolTable symbolTable,
            List<Instruction> params,
            Instruction instruction)
    {
        Instruction i1 = params.get(0);
        Instruction i2 = params.get(1);

        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        STObjects.STValue search = symbolTable.get(instruction.op2).getValue();
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();

        IntList dims = array.getArrayDimensions();
        // Arrays are row-major.
        int numRows = dims.getInt(0);
        int numCols = dims.getInt(1);
        int n = array.getTotalLength();

        int x1 = Math.min(Math.max(0, symbolTable.get(i1.op1).getValue().getInt32()), numCols - 1);
        int y1 = Math.min(Math.max(0, symbolTable.get(i1.op2).getValue().getInt32()), numRows - 1);
        int x2 = Math.min(Math.max(0, symbolTable.get(i2.op1).getValue().getInt32()), numCols - 1);
        int y2 = Math.min(Math.max(0, symbolTable.get(i2.op2).getValue().getInt32()), numRows - 1);

        if (y1 * numCols + x1 >= n || y2 * numCols + x2 >= n) {
            throw new PuffinBasicRuntimeError(
                    PuffinBasicRuntimeError.ErrorCode.INDEX_OUT_OF_BOUNDS,
                    "x1=" + x1 + "/y1=" + y1 + "/x2=" + x2 + "/y2=" + y2
                        + " is out of bounds, array length=" + n
            );
        }
        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] int32Array = ((STInt32ArrayValue) array).getValue();
                result.setInt32(findRowWithValue(int32Array, numCols, x1, y1, x2, y2, search.getInt32()));
            }
            break;
            case INT64: {
                long[] int64Array = ((STInt64ArrayValue) array).getValue();
                result.setInt32(findRowWithValue(int64Array, numCols, x1, y1, x2, y2, search.getInt64()));
            }
            break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
    }

    static void array2dFindColumn(
            PuffinBasicSymbolTable symbolTable,
            List<Instruction> params,
            Instruction instruction)
    {
        Instruction i1 = params.get(0);
        Instruction i2 = params.get(1);

        STEntry arrayEntry = symbolTable.get(instruction.op1);
        STObjects.STValue array = arrayEntry.getValue();
        STObjects.STValue search = symbolTable.get(instruction.op2).getValue();
        STObjects.STValue result = symbolTable.get(instruction.result).getValue();

        IntList dims = array.getArrayDimensions();
        // Arrays are row-major.
        int numRows = dims.getInt(0);
        int numCols = dims.getInt(1);
        int n = array.getTotalLength();

        int x1 = Math.min(Math.max(0, symbolTable.get(i1.op1).getValue().getInt32()), numCols - 1);
        int y1 = Math.min(Math.max(0, symbolTable.get(i1.op2).getValue().getInt32()), numRows - 1);
        int x2 = Math.min(Math.max(0, symbolTable.get(i2.op1).getValue().getInt32()), numCols - 1);
        int y2 = Math.min(Math.max(0, symbolTable.get(i2.op2).getValue().getInt32()), numRows - 1);

        if (y1 * numCols + x1 >= n || y2 * numCols + x2 >=n) {
            throw new PuffinBasicRuntimeError(
                    PuffinBasicRuntimeError.ErrorCode.INDEX_OUT_OF_BOUNDS,
                    "x1=" + x1 + "/y1=" + y1 + "/x2=" + x2 + "/y2=" + y2
                            + " is out of bounds, array length=" + n
            );
        }
        switch (arrayEntry.getType().getAtomTypeId()) {
            case INT32: {
                int[] int32Array = ((STInt32ArrayValue) array).getValue();
                result.setInt32(findColumnWithValue(int32Array, numCols, x1, y1, x2, y2, search.getInt32()));
            }
            break;
            case INT64: {
                long[] int64Array = ((STInt64ArrayValue) array).getValue();
                result.setInt32(findColumnWithValue(int64Array, numCols, x1, y1, x2, y2, search.getInt64()));
            }
            break;
            default:
                throwUnsupportedType(arrayEntry.getType().getAtomTypeId());
        }
    }

    private static int findRowWithValue(int[] array, int w, int x1, int y1, int x2, int y2, int search) {
        if (y1 <= y2) {
            for (int r = y1; r <= y2; r++) {
                for (int c = x1; c <= x2; c++) {
                    int v = array[r * w + c];
                    if (v == search) {
                        return r;
                    }
                }
            }
        } else {
            for (int r = y1; r >= y2; r--) {
                for (int c = x1; c <= x2; c++) {
                    int v = array[r * w + c];
                    if (v == search) {
                        return r;
                    }
                }
            }
        }
        return -1;
    }

    private static int findColumnWithValue(int[] array, int w, int x1, int y1, int x2, int y2, int search) {
        if (x1 <= x2) {
            for (int c = x1; c <= x2; c++) {
                for (int r = y1; r <= y2; r++) {
                    int v = array[r * w + c];
                    if (v == search) {
                        return c;
                    }
                }
            }
        } else {
            for (int c = x1; c >= x2; c--) {
                for (int r = y1; r <= y2; r++) {
                    int v = array[r * w + c];
                    if (v == search) {
                        return c;
                    }
                }
            }
        }
        return -1;
    }

    private static int findRowWithValue(long[] array, int w, int x1, int y1, int x2, int y2, long search) {
        if (y1 <= y2) {
            for (int r = y1; r <= y2; r++) {
                for (int c = x1; c <= x2; c++) {
                    long v = array[r * w + c];
                    if (v == search) {
                        return r;
                    }
                }
            }
        } else {
            for (int r = y1; r >= y2; r--) {
                for (int c = x1; c <= x2; c++) {
                    long v = array[r * w + c];
                    if (v == search) {
                        return r;
                    }
                }
            }
        }
        return -1;
    }

    private static int findColumnWithValue(long[] array, int w, int x1, int y1, int x2, int y2, long search) {
        if (x1 <= x2) {
            for (int c = x1; c <= x2; c++) {
                for (int r = y1; r <= y2; r++) {
                    long v = array[r * w + c];
                    if (v == search) {
                        return c;
                    }
                }
            }
        } else {
            for (int c = x1; c >= x2; c--) {
                for (int r = y1; r <= y2; r++) {
                    long v = array[r * w + c];
                    if (v == search) {
                        return c;
                    }
                }
            }
        }
        return -1;
    }

    private int findRowWithValue(double[] array, int w, int x1, int y1, int x2, int y2, int search) {
        if (y1 <= y2) {
            for (int r = y1; r <= y2; r++) {
                for (int c = x1; c <= x2; c++) {
                    double v = array[r * w + c];
                    if (v == search) {
                        return r;
                    }
                }
            }
        } else {
            for (int r = y1; r >= y2; r--) {
                for (int c = x1; c <= x2; c++) {
                    double v = array[r * w + c];
                    if (v == search) {
                        return r;
                    }
                }
            }
        }
        return -1;
    }
}
