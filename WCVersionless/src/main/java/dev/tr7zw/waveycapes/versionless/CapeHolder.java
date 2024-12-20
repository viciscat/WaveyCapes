package dev.tr7zw.waveycapes.versionless;

import dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer;
import dev.tr7zw.waveycapes.versionless.sim.BasicSimulation;
import dev.tr7zw.waveycapes.versionless.sim.StickSimulation;
import dev.tr7zw.waveycapes.versionless.sim.StickSimulation.Vector2;
import dev.tr7zw.waveycapes.versionless.sim.StickSimulation3d;
import dev.tr7zw.waveycapes.versionless.sim.StickSimulationDungeons;
import dev.tr7zw.waveycapes.versionless.util.Mth;
import dev.tr7zw.waveycapes.versionless.util.Vector3;

import java.util.UUID;

public interface CapeHolder {
    public BasicSimulation getSimulation();

    public Vector3 getLastPlayerAnimatorPosition();

    public void setLastPlayerAnimatorPosition(Vector3 pos);

    public void setSimulation(BasicSimulation sim);

    UUID getUUID();

    void setDirty();

    public default void updateSimulation(int partCount) {
        BasicSimulation simulation = getSimulation();
        if (simulation == null || incorrectSimulation(simulation)) {
            simulation = createSimulation();
            setSimulation(simulation);
        }
        if (simulation == null) {
            return;
        }
        if (simulation.init(partCount))
            setDirty();

    }

    public default boolean incorrectSimulation(BasicSimulation sim) {
        CapeMovement style = ModBase.config.capeMovement;
        if (style == CapeMovement.BASIC_SIMULATION && sim.getClass() != StickSimulation.class) {
            return true;
        } else if (style == CapeMovement.BASIC_SIMULATION_3D && sim.getClass() != StickSimulation3d.class) {
            return true;
        } else if (style == CapeMovement.DUNGEONS && sim.getClass() != StickSimulationDungeons.class) {
            return true;
        }
        return false;
    }

    public default BasicSimulation createSimulation() {
        CapeMovement style = ModBase.config.capeMovement;
        if (style == CapeMovement.BASIC_SIMULATION) {
            return new StickSimulation();
        }
        if (style == CapeMovement.BASIC_SIMULATION_3D) {
            return new StickSimulation3d();
        }
        if (style == CapeMovement.DUNGEONS) {
            return new StickSimulationDungeons();
        }
        return null;
    }

    public default void simulate(MinecraftPlayer abstractClientPlayer) {
        BasicSimulation simulation = getSimulation();
        if (simulation == null || simulation.empty()) {
            return; // no cape, nothing to update
        }
        double d = abstractClientPlayer.getXCloak() - abstractClientPlayer.getX();
        double m = abstractClientPlayer.getZCloak() - abstractClientPlayer.getZ();
        float n = abstractClientPlayer.getYBodyRotO() + abstractClientPlayer.getYBodyRot()
                - abstractClientPlayer.getYBodyRotO();
        double o = Mth.sin(n * 0.017453292F);
        double p = -Mth.cos(n * 0.017453292F);
        float heightMul = ModBase.config.heightMultiplier;
        float straveMul = ModBase.config.straveMultiplier;
        if (abstractClientPlayer.isUnderWater()) {
            heightMul *= 2; // let the cape have more drag than the player underwater
        }
        // gives the cape a small swing when jumping/falling to not clip with
        // itself/simulate some air getting under it
        double fallHack = Mth.clamp((abstractClientPlayer.getYo() - abstractClientPlayer.getY()) * 10, 0, 1);
        if (abstractClientPlayer.isUnderWater()) {
            simulation.setGravity(ModBase.config.gravity / 10f);
        } else {
            simulation.setGravity(ModBase.config.gravity);
        }

        Vector3 gravity = new Vector3(0, -1, 0);
        Vector2 strave = new Vector2((float) (abstractClientPlayer.getX() - abstractClientPlayer.getXo()),
                (float) (abstractClientPlayer.getZ() - abstractClientPlayer.getZo()));
        strave.rotateDegrees(-abstractClientPlayer.getYRot());
        double changeX = (d * o + m * p) + fallHack
                + (abstractClientPlayer.isCrouching() && !simulation.isSneaking() ? 3 : 0);
        double changeY = ((abstractClientPlayer.getY() - abstractClientPlayer.getYo()) * heightMul)
                + (abstractClientPlayer.isCrouching() && !simulation.isSneaking() ? 1 : 0);
        double changeZ = -strave.x * straveMul;
        simulation.setSneaking(abstractClientPlayer.isCrouching());
        Vector3 change = new Vector3((float) changeX, (float) changeY, (float) changeZ);
        if (abstractClientPlayer.isVisuallySwimming()) {
            float rotation = abstractClientPlayer.getXRot(); // -90 = swimming up, 0 = straight, 90 = down
            // the simulation has the body as reference, so if the player is swimming
            // straight down, gravity needs to point up(the cape should move into the
            // direction of the head, not the feet)
            // offset the rotation to swimming up doesn't rotate the vector at all
            rotation += 90;
            // apply rotation
            gravity.rotateDegrees(rotation);

            change.rotateDegrees(rotation);
        }
        simulation.setGravityDirection(gravity);

        change = ModBase.getINSTANCE().applyModAnimations(abstractClientPlayer, change);
        simulation.applyMovement(change);
        simulation.simulate();
    }

}
