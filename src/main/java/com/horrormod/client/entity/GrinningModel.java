package com.horrormod.client.entity;

import com.horrormod.entity.GrinningEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

public class GrinningModel<T extends GrinningEntity> extends SinglePartEntityModel<T> {

    private final ModelPart root;

    public GrinningModel(ModelPart root) {
        this.root = root.getChild("root");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        ModelPartData body = root.addChild("root", ModelPartBuilder.create(), ModelTransform.NONE);

        // Head
        body.addChild("head",
            ModelPartBuilder.create().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8),
            ModelTransform.pivot(0, 0, 0));
        // Body
        body.addChild("body",
            ModelPartBuilder.create().uv(16, 16).cuboid(-4, 0, -2, 8, 12, 4),
            ModelTransform.pivot(0, 0, 0));
        // Left arm
        body.addChild("left_arm",
            ModelPartBuilder.create().uv(32, 48).cuboid(-1, -2, -2, 4, 12, 4),
            ModelTransform.pivot(5, 2, 0));
        // Right arm
        body.addChild("right_arm",
            ModelPartBuilder.create().uv(40, 16).cuboid(-3, -2, -2, 4, 12, 4),
            ModelTransform.pivot(-5, 2, 0));
        // Left leg
        body.addChild("left_leg",
            ModelPartBuilder.create().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4),
            ModelTransform.pivot(2, 12, 0));
        // Right leg
        body.addChild("right_leg",
            ModelPartBuilder.create().uv(16, 48).cuboid(-2, 0, -2, 4, 12, 4),
            ModelTransform.pivot(-2, 12, 0));

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public ModelPart getPart() { return root; }

    @Override
    public void setAngles(T entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        // Basic walking animation
        ModelPart head  = root.getChild("head");
        ModelPart la    = root.getChild("left_arm");
        ModelPart ra    = root.getChild("right_arm");
        ModelPart ll    = root.getChild("left_leg");
        ModelPart rl    = root.getChild("right_leg");

        head.yaw   = headYaw   * 0.017453292f;
        head.pitch = headPitch * 0.017453292f;
        la.pitch   =  (float) Math.cos(limbAngle * 0.6662f + Math.PI) * 2f * limbDistance;
        ra.pitch   =  (float) Math.cos(limbAngle * 0.6662f)           * 2f * limbDistance;
        ll.pitch   =  (float) Math.cos(limbAngle * 0.6662f)           * 1.4f * limbDistance;
        rl.pitch   =  (float) Math.cos(limbAngle * 0.6662f + Math.PI) * 1.4f * limbDistance;
    }
}
