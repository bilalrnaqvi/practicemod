package com.bilal.practicemod.datagen;

import com.bilal.practicemod.PracticeMod;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, PracticeMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(PracticeMod.JETPACK.get());
    }
}
