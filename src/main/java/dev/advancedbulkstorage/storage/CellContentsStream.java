package dev.advancedbulkstorage.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.CustomData;

public final class CellContentsStream {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_BYTES);
    private static final Cache<CustomData, byte[]> ENCODED = CacheBuilder.newBuilder()
            .weakKeys().maximumWeight(32L * 1024 * 1024)
            .weigher((CustomData key, byte[] bytes) -> bytes.length + 64).build();

    // Full data travels in both directions. Creative slot writes receive the original inventory too.
    public static final StreamCodec<ByteBuf, CustomData> CODEC = BYTES.map(CellContentsStream::read, CellContentsStream::write);

    private CellContentsStream() {}

    private static CustomData read(byte[] bytes) {
        try (var input = new ByteArrayInputStream(bytes)) {
            return CustomData.of(NbtIo.readCompressed(input, NbtAccounter.create(MAX_BYTES)));
        } catch (IOException exception) {
            throw new DecoderException("Cannot read bulk cell contents", exception);
        }
    }

    private static byte[] write(CustomData data) {
        return ENCODED.asMap().computeIfAbsent(data, CellContentsStream::compress);
    }

    @SuppressWarnings("deprecation")
    private static byte[] compress(CustomData data) {
        try (var output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(data.getUnsafe(), output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new EncoderException("Cannot write bulk cell contents", exception);
        }
    }
}
