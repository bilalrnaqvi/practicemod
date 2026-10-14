package com.bilal.practicemod;

import net.minecraft.client.model.PlayerModel;

/** Client-only pose player. Entity time, not render calls, drives sampling. */
public final class BlenderBoardingPlayback {
    private static BlenderPlayerClip clip;
    private BlenderBoardingPlayback() {}
    public static void apply(PlayerModel<?> model,float tick) {
        if (clip==null) {
            try (var stream=BlenderBoardingPlayback.class.getResourceAsStream(
                    "/assets/practicemod/player_animations/deepseeker_entry.json")) {
                if (stream==null) throw new IllegalStateException("Missing boarding clip");
                clip=new BlenderPlayerClip(stream);
            } catch (java.io.IOException e) { throw new IllegalStateException("Invalid boarding clip",e); }
        }
        clip.sample(tick);
        BlenderPlayerPose.apply(model,clip);
    }
}
