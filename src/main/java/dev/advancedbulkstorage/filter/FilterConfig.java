package dev.advancedbulkstorage.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record FilterConfig(String expression, boolean blacklist) {
    public static final int MAX_INPUT_LENGTH = 4096;
    public static final FilterConfig EMPTY = new FilterConfig("", false);
    public static final Codec<FilterConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.string(0, MAX_INPUT_LENGTH).fieldOf("expression").forGetter(FilterConfig::expression),
            Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(FilterConfig::blacklist)
    ).apply(instance, FilterConfig::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterConfig> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_INPUT_LENGTH), FilterConfig::expression,
            ByteBufCodecs.BOOL, FilterConfig::blacklist,
            FilterConfig::new);
}
