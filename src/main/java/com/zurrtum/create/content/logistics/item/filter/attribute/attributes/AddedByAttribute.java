package com.zurrtum.create.content.logistics.item.filter.attribute.attributes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.zurrtum.create.AllItemAttributeTypes;
import com.zurrtum.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.zurrtum.create.content.logistics.item.filter.attribute.ItemAttributeType;
import io.netty.buffer.ByteBuf;
import com.zurrtum.create.neoforge.Platform;
import net.neoforged.neoforgespi.language.IModInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public record AddedByAttribute(String modId) implements ItemAttribute {
    public static final MapCodec<AddedByAttribute> CODEC = Codec.STRING.xmap(
        AddedByAttribute::new,
        AddedByAttribute::modId
    ).fieldOf("value");

    public static final StreamCodec<ByteBuf, AddedByAttribute> PACKET_CODEC = ByteBufCodecs.STRING_UTF8.map(AddedByAttribute::new,
        AddedByAttribute::modId
    );

    private static String getCreatorModId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    @Override
    public boolean appliesTo(ItemStack stack, Level world) {
        return modId.equals(getCreatorModId(stack));
    }

    @Override
    public String getTranslationKey() {
        return "added_by";
    }

    @Override
    public Object[] getTranslationParameters() {
        String name = Platform.getMod(modId).map(IModInfo::getDisplayName).orElseGet(() -> StringUtils.capitalize(modId));
        return new Object[]{name};
    }

    @Override
    public ItemAttributeType getType() {
        return AllItemAttributeTypes.ADDED_BY;
    }

    public static class Type implements ItemAttributeType {
        @Override
        public ItemAttribute createAttribute() {
            return new AddedByAttribute("dummy");
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            return List.of(new AddedByAttribute(getCreatorModId(stack)));
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
