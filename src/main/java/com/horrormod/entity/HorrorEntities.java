package com.horrormod.entity;

import com.horrormod.HorrorMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class HorrorEntities {

    public static EntityType<GrinningEntity> GRINNING;

    public static void register() {
        GRINNING = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(HorrorMod.MOD_ID, "grinning"),
            FabricEntityTypeBuilder.<GrinningEntity>create(SpawnGroup.MONSTER, GrinningEntity::new)
                .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                .build()
        );
        FabricDefaultAttributeRegistry.register(GRINNING, GrinningEntity.createAttributes());
        HorrorMod.LOGGER.info("[HorrorMod] Registered Grinning entity.");
    }
}
