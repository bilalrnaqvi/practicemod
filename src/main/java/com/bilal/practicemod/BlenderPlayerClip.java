package com.bilal.practicemod;

import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import com.zigythebird.playeranimcore.math.Vec3f;
import com.zigythebird.playeranimcore.molang.MolangLoader;
import java.io.IOException;
import java.io.InputStream;

/** PAL playback at an absolute sequence time, independent of render cadence. */
public final class BlenderPlayerClip extends AnimationController {
    public static final float LAST_TICK = BlenderBoardingTrack.DURATION;
    public static final String[] PARTS = {
            "body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"
    };

    public BlenderPlayerClip(InputStream input) throws IOException {
        super((controller, state, setter) -> PlayState.CONTINUE, MolangLoader::createNewEngine);
        var clips = UniversalAnimLoader.loadAnimations(input);
        if (clips.size() != 1) throw new IOException("Expected exactly one Blender player clip");
        Animation clip = clips.values().iterator().next();
        if (clip.length() < LAST_TICK) throw new IOException("Boarding clip is too short");
        for (String name : PARTS)
            if (clip.getBone(name) == null) throw new IOException("Missing player bone: " + name);
        setAnimation(RawAnimation.begin().then(clip, Animation.LoopType.PLAY_ONCE));
    }

    @Override
    public void registerBones() {
        for (String name : PARTS) registerPlayerAnimBone(name);
    }

    @Override
    public Vec3f getBonePosition(String name) {
        return switch (name) {
            case "body" -> new Vec3f(0, 12, 0);
            case "head", "torso" -> new Vec3f(0, 24, 0);
            case "right_arm" -> new Vec3f(5, 22, 0);
            case "left_arm" -> new Vec3f(-5, 22, 0);
            case "right_leg" -> new Vec3f(2, 12, 0);
            case "left_leg" -> new Vec3f(-2, 12, 0);
            default -> Vec3f.ZERO;
        };
    }

    public void sample(float sequenceTick) {
        if (!Float.isFinite(sequenceTick)) throw new IllegalArgumentException("Non-finite clip time");
        // Reset the clock, not the animation: seeking/repeated renders must not advance it.
        tick = 0;
        startAnimFrom = Math.clamp(sequenceTick, 0, LAST_TICK);
        setupAnim(new AnimationData(0, 0));
    }

    public PlayerAnimBone bone(String name) {
        return get3DTransformRaw(new PlayerAnimBone(name));
    }
}
