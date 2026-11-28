package dev.advancedbulkstorage.storage;

import java.math.BigInteger;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import org.jetbrains.annotations.Nullable;

/** Quantities in a cell are exact, but each AE2 operation has a signed long boundary. */
public final class StockAmount {
    private StockAmount() {}

    public static long toLong(BigInteger amount) {
        if (amount.signum() <= 0) return 0;
        return amount.bitLength() > 63 ? Long.MAX_VALUE : amount.longValue();
    }

    /** A bounded UI label. Extremely large imported counts remain exact in the cell. */
    public static String display(BigInteger amount) {
        return amount.bitLength() <= 1024 ? amount.toString() : "≥2^" + (amount.bitLength() - 1);
    }

    static BigInteger units(long amount, long factor) {
        var value = BigInteger.valueOf(amount);
        return factor == 1 ? value : value.multiply(BigInteger.valueOf(factor));
    }

    static BigInteger items(BigInteger units, long factor) {
        return factor == 1 ? units : units.divide(BigInteger.valueOf(factor));
    }

    /** Returns true if the saved quantity requires the wide inventory format. */
    static boolean write(CompoundTag tag, String key, BigInteger amount) {
        if (amount.bitLength() <= 63) {
            tag.putLong(key, amount.longValue());
            return false;
        }
        tag.putByteArray(key, amount.toByteArray());
        return true;
    }

    static @Nullable BigInteger read(CompoundTag tag, String key, boolean wideFormat) {
        var encoded = tag.get(key);
        if (encoded instanceof LongTag number && number.getAsLong() > 0) {
            return BigInteger.valueOf(number.getAsLong());
        }
        if (wideFormat && encoded instanceof ByteArrayTag bytes && bytes.size() > 0) {
            var amount = new BigInteger(bytes.getAsByteArray());
            return amount.signum() > 0 ? amount : null;
        }
        return null;
    }
}
