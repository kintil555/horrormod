package com.horrormod.client.entity;

import com.horrormod.HorrorMod;
import com.horrormod.entity.GrinningEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class GrinningRenderer extends MobEntityRenderer<GrinningEntity, GrinningModel<GrinningEntity>> {

    private static final Identifier TEXTURE =
        new Identifier(HorrorMod.MOD_ID, "textures/entity/grinning/grinning.png");

    public GrinningRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GrinningModel<>(GrinningModel.getTexturedModelData().createModel()), 0.5f);
    }

    @Override
    public Identifier getTexture(GrinningEntity entity) { return TEXTURE; }
}
