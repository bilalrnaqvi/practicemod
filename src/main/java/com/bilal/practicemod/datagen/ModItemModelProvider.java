package com.bilal.practicemod.datagen;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.client.renderer.block.model.BlockModel.GuiLight;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, PracticeMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(PracticeMod.JETPACK.get());

        var boots = getBuilder("rocket_boots")
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .texture("particle", modLoc("entity/rocket_boots_mk1"));
        boots.transforms().transform(ItemDisplayContext.GUI)
                .rotation(20.0F, -35.0F, 0.0F).scale(1.1F).end()
                .transform(ItemDisplayContext.GROUND).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .rotation(75.0F, 0.0F, 0.0F).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                .rotation(75.0F, 0.0F, 0.0F).scale(0.5F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                .rotation(0.0F, -35.0F, 0.0F).scale(0.7F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                .rotation(0.0F, 35.0F, 0.0F).scale(0.7F).end();

        var umbrella = getBuilder("umbrella_glider")
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .guiLight(GuiLight.FRONT)
                .texture("particle", modLoc("item/umbrella_glider"));
        var transforms = umbrella.transforms();
        transforms.transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .rotation(0.0F, 60.0F, 0.0F).translation(11.0F, 17.0F, -2.0F).end();
        transforms.transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                .rotation(0.0F, 60.0F, 0.0F).translation(3.0F, 17.0F, 12.0F).end();
        transforms.transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                .rotation(0.0F, -90.0F, 25.0F).translation(-3.0F, 17.0F, 1.0F).end();
        transforms.transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                .rotation(0.0F, 90.0F, -25.0F).translation(13.0F, 17.0F, 1.0F).end();
        transforms.transform(ItemDisplayContext.GUI)
                .rotation(15.0F, -25.0F, -5.0F).translation(2.0F, 3.0F, 0.0F).scale(0.75F).end();
        transforms.transform(ItemDisplayContext.FIXED)
                .rotation(0.0F, 180.0F, 0.0F).translation(-2.0F, 4.0F, -5.0F).scale(0.5F).end();
        transforms.transform(ItemDisplayContext.GROUND)
                .translation(4.0F, 4.0F, 2.0F).scale(0.25F).end();
    }
}
