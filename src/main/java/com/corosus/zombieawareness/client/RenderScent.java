package com.corosus.zombieawareness.client;

import com.corosus.zombieawareness.EntityScent;
import com.corosus.zombieawareness.ZombieAwareness;
import com.corosus.zombieawareness.config.ZAConfigClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RenderScent extends EntityRenderer<EntityScent, RenderScent.ScentRenderState> {

    public static Identifier TEXTURE64 = Identifier.fromNamespaceAndPath(ZombieAwareness.MODID, "textures/entities/bloodx64.png");
    private static final RenderType SHADOW_RENDER_TYPE = RenderTypes.entityShadow(TEXTURE64);

    public static class ScentRenderState extends EntityRenderState {
        public int senseType;
        public float ageScale;
        public LevelReader level;
    }

    public RenderScent(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ScentRenderState createRenderState() {
        return new ScentRenderState();
    }

    @Override
    public void extractRenderState(EntityScent entity, ScentRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.senseType = entity.getSenseType();
        state.ageScale = (float) entity.getAgeScale();
        state.level = entity.level();
    }

    @Override
    public void submit(ScentRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        super.submit(state, poseStack, collector, cameraRenderState);
        if (ZAConfigClient.client_debugSensesVisual == false && !ZAConfigClient.client_renderBlood) {
            return;
        }
        if (ZAConfigClient.client_renderBlood && state.senseType == 0 && state.level != null) {
            float scale = 0.7F + (state.ageScale * 0.3F);
            float alpha = state.ageScale;
            renderBlood(poseStack, collector, state.x, state.y, state.z, state.level, scale, alpha);
        }
    }

    private static void renderBlood(PoseStack poseStack, SubmitNodeCollector collector, double d2, double d0, double d1, LevelReader level, float f, float alpha) {
        int i = Mth.floor(d2 - (double) f);
        int j = Mth.floor(d2 + (double) f);
        int k = Mth.floor(d0 - (double) f);
        int l = Mth.floor(d0);
        int i1 = Mth.floor(d1 - (double) f);
        int j1 = Mth.floor(d1 + (double) f);

        collector.submitCustomGeometry(poseStack, SHADOW_RENDER_TYPE, (pose, consumer) -> {
            for (BlockPos blockpos : BlockPos.betweenClosed(new BlockPos(i, k, i1), new BlockPos(j, l, j1))) {
                renderBlockShadow(pose, consumer, level, blockpos, d2, d0, d1, f, alpha);
            }
        });
    }

    private static void renderBlockShadow(PoseStack.Pose pose, VertexConsumer consumer, LevelReader level, BlockPos pos, double x, double y, double z, float radius, float alpha) {
        BlockPos blockpos = pos.below();
        BlockState blockstate = level.getBlockState(blockpos);
        float light = 0.5F;
        if (blockstate.isCollisionShapeFullBlock(level, blockpos)) {
            VoxelShape voxelshape = blockstate.getShape(level, pos.below());
            if (!voxelshape.isEmpty()) {
                float f = Mth.clamp(alpha, 0.0F, 1.0F);
                AABB axisalignedbb = voxelshape.bounds();
                double d0 = (double) pos.getX() + axisalignedbb.minX;
                double d1 = (double) pos.getX() + axisalignedbb.maxX;
                double d2 = (double) pos.getY() + axisalignedbb.minY;
                double d3 = (double) pos.getZ() + axisalignedbb.minZ;
                double d4 = (double) pos.getZ() + axisalignedbb.maxZ;
                float f1 = (float) (d0 - x);
                float f2 = (float) (d1 - x);
                float f3 = (float) (d2 - y);
                float f4 = (float) (d3 - z);
                float f5 = (float) (d4 - z);
                float f6 = -f1 / 2.0F / radius + 0.5F;
                float f7 = -f2 / 2.0F / radius + 0.5F;
                float f8 = -f4 / 2.0F / radius + 0.5F;
                float f9 = -f5 / 2.0F / radius + 0.5F;
                shadowVertex(pose, consumer, f, f1, f3, f4, f6, f8, light);
                shadowVertex(pose, consumer, f, f1, f3, f5, f6, f9, light);
                shadowVertex(pose, consumer, f, f2, f3, f5, f7, f9, light);
                shadowVertex(pose, consumer, f, f2, f3, f4, f7, f8, light);
            }
        }
    }

    private static void shadowVertex(PoseStack.Pose pose, VertexConsumer consumer, float alpha, float x, float y, float z, float u, float v, float light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(light, light, light, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
